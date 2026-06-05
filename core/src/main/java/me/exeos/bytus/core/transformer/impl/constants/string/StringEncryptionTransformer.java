package me.exeos.bytus.core.transformer.impl.constants.string;

import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.InsnListContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.Set;

/**
 * Rewrites String constants into an encrypted form and injects a runtime decrypt call.
 *
 * <h2>Overview</h2>
 * <ul>
 *   <li>For each LDC String constant:
 *     <ul>
 *       <li>Generate a random key.</li>
 *       <li>Encrypt constant with key</li>
 *       <li>Insert a static call to a generated decryptor method that XOR-decrypts it at runtime.</li>
 *     </ul>
 *   </li>
 *   <li>Generate a single decryptor class (added to the jarCtx) containing the decrypt method.</li>
 * </ul>
 */
public class StringEncryptionTransformer extends AbstractTransformer {

    /**
     * Target ClassFile version for generated decryptor class.
     */
    private final static int CLASS_VERSION = V1_8;
    private final static String DEC_METHOD_DESC = "(Ljava/lang/String;I)Ljava/lang/String;";
    private static String DEC_CLASS_NAME = null;
    private static String DEC_METHOD_NAME = null;

    public StringEncryptionTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.constants.enable() && config.constants.strings();
    }

    @Override
    public int priority() {
        return Priority.STR_ENCRYPT_STRINGS;
    }

    @Override
    public void transform(JarContext context) {
        DEC_CLASS_NAME = ClassUtil.getNoneCollidingClassName(context.jar(), RandomUtil::getString);
        DEC_METHOD_NAME = RandomUtil.getString(1);

        super.transform(context);
        context.pipeline().emit(new ClassContext(context, cryptClass()), Set.of(StringEncryptionTransformer.class));
    }

    @Override
    public void transform(MethodContext context) {
        context.pipeline().getExtension(context.methodNode()).ifPresentOrElse(
                extension -> applyTransformation(
                        context.methodNode().instructions,
                        extension.hasSalt(),
                        extension.hasSalt() ? extension.getSalt() : 0,
                        extension.hasSalt() ? extension.getSaltSlot() : 0
                ),
                () -> super.transform(context)
        );
    }

    @Override
    public void transform(InsnListContext context) {
        applyTransformation(context.insnList(), false, 0, 0);
    }

    private void applyTransformation(InsnList target, boolean hasSalt, int salt, int saltSlot) {
        InsnUtil.loop(target, insnNode -> {
            if (insnNode instanceof LdcInsnNode ldcInsnNode && ldcInsnNode.cst instanceof String cstString) {
                int key = RandomUtil.getInt(1, 100);

                // stack goes from: plain_str -> encrypted_str, key
                // then decrypt method is called
                // stack after: plain_str
                InsnList callToDecrypt = new InsnList();
                System.out.println(salt);
                if (hasSalt) {
                    callToDecrypt.add(InsnUtil.getIntPushSalted(key, salt, saltSlot));
                } else {
                    callToDecrypt.add(InsnUtil.getIntPush(key));
                }
                callToDecrypt.add(new MethodInsnNode(INVOKESTATIC, DEC_CLASS_NAME, DEC_METHOD_NAME, DEC_METHOD_DESC));

                ldcInsnNode.cst = crypt(cstString, key);
                target.insert(insnNode, callToDecrypt);
            }
        });
    }

    /**
     * Generates:
     * <pre>
     * public static String decrypt(String encrypted int key) {
     *   char[] in = encrypted.toCharArray();
     *   int len = in.length;
     *   char[] out = new char[len];
     *   for (int i=0; i<len; i++) out[i] = (char)(in[i] ^ key);
     *   return new String(out);
     * }
     * </pre>
     */
    private ClassNode cryptClass() {
        ClassNode cc = new ClassNode();
        cc.visit(CLASS_VERSION, ACC_PUBLIC, DEC_CLASS_NAME, null, "java/lang/Object", null);

        MethodNode cm = new MethodNode(ACC_PUBLIC | ACC_STATIC, DEC_METHOD_NAME, DEC_METHOD_DESC, null, null);
        cm.maxLocals = 2;

        // load string from params
        cm.instructions.add(new VarInsnNode(ALOAD, 0));
        // convert string to char[]
        cm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "toCharArray", "()[C"));
        cm.instructions.add(new InsnNode(DUP));
        // store char[] in local
        int charsVarIndex = cm.maxLocals++;
        cm.instructions.add(new VarInsnNode(ASTORE, charsVarIndex));
        // get length of char[]
        cm.instructions.add(new InsnNode(ARRAYLENGTH));
        cm.instructions.add(new InsnNode(DUP));
        // store length of char[] in local
        int charsLenVarIndex = cm.maxLocals++;
        cm.instructions.add(new VarInsnNode(ISTORE, charsLenVarIndex));
        // create new char[] storing decrypted chars
        cm.instructions.add(new IntInsnNode(NEWARRAY, T_CHAR));
        int decCharsVarIndex = cm.maxLocals++;
        cm.instructions.add(new VarInsnNode(ASTORE, decCharsVarIndex));

        int indexVarIndex = cm.maxLocals++;
        cm.instructions.add(new InsnNode(ICONST_0));
        cm.instructions.add(new VarInsnNode(ISTORE, indexVarIndex));

        LabelNode decLoopStart = new LabelNode();
        LabelNode decLoopEnd = new LabelNode();

        cm.instructions.add(decLoopStart);
        cm.instructions.add(new VarInsnNode(ILOAD, indexVarIndex));
        cm.instructions.add(new VarInsnNode(ILOAD, charsLenVarIndex));
        cm.instructions.add(new JumpInsnNode(IF_ICMPGE, decLoopEnd));

        // prep array store
        cm.instructions.add(new VarInsnNode(ALOAD, decCharsVarIndex));
        cm.instructions.add(new VarInsnNode(ILOAD, indexVarIndex));

        // value for store
        cm.instructions.add(new VarInsnNode(ALOAD, charsVarIndex));
        cm.instructions.add(new VarInsnNode(ILOAD, indexVarIndex));
        cm.instructions.add(new InsnNode(CALOAD));

        cm.instructions.add(new VarInsnNode(ILOAD, 1));

        cm.instructions.add(new InsnNode(IXOR));
        cm.instructions.add(new InsnNode(I2C));

        // store in array
        cm.instructions.add(new InsnNode(CASTORE));

        cm.instructions.add(new IincInsnNode(indexVarIndex, 1));
        cm.instructions.add(new JumpInsnNode(GOTO, decLoopStart));

        cm.instructions.add(decLoopEnd);

        cm.instructions.add(new TypeInsnNode(NEW, "java/lang/String"));
        cm.instructions.add(new InsnNode(DUP));
        cm.instructions.add(new VarInsnNode(ALOAD, decCharsVarIndex));
        cm.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/String", "<init>", "([C)V"));
        cm.instructions.add(new InsnNode(ARETURN));

        cc.methods.add(cm);
        return cc;
    }

    private String crypt(String string, int key) {
        char[] chars = string.toCharArray();
        char[] cryptedChars = new char[chars.length];

        for (int i = 0; i < chars.length; i++) {
            cryptedChars[i] = (char) ((int) chars[i] ^ key);
        }

        return new String(cryptedChars);
    }
}
