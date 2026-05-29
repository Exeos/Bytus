package me.exeos.bytus.core.transformer.impl.constants.string;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Rewrites String constants into an encrypted form and injects a runtime decrypt call.
 *
 * <h2>Overview</h2>
 * <ul>
 *   <li>For each included class, create a private static int[] field that stores per-string XOR keys.</li>
 *   <li>For each LDC String constant:
 *     <ul>
 *       <li>Generate a random key.</li>
 *       <li>Replace the constant with an XOR-encrypted version of that String.</li>
 *       <li>Insert a static call to a generated decryptor method that XOR-decrypts it at runtime.</li>
 *     </ul>
 *   </li>
 *   <li>Populate the int[] key array in the class' &lt;clinit&gt; (static initializer).</li>
 *   <li>Generate a single decryptor class (added to the jarCtx) containing the decrypt method.</li>
 * </ul>
 */
public class StringEncryptionTransformer extends Transformer {

    /**
     * Target ClassFile version for generated decryptor class.
     */
    private final static int CLASS_VERSION = V1_8;

    private final static String DEC_METHOD_DESC = "(Ljava/lang/String;[II)Ljava/lang/String;";

    public StringEncryptionTransformer(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        String decClassName = ClassUtil.getNoneCollidingClassName(getJar(), RandomUtil::getString);
        String decMethodName = RandomUtil.getString(1);

        for (ClassNode classNode : getIncludedClasses()) {
            FieldNode keyArrField = new FieldNode(
                    ACC_PRIVATE | ACC_STATIC,
                    ClassUtil.getNoneCollidingFieldName(getJar(), classNode, RandomUtil::getString),
                    "[I",
                    null,
                    null
            );
            classNode.fields.add(keyArrField);

            Map<Integer, Integer> keyByIndex = new HashMap<>();
            int stringId = 0;

            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode instanceof LdcInsnNode ldcInsnNode && ldcInsnNode.cst instanceof String cstString) {
                        int key = RandomUtil.getInt(1, 100);

                        keyByIndex.put(stringId, key);

                        // stack goes from: plain_str -> encrypted_str, keyArr_reference, string_id
                        // then decrypt method is called
                        // stack after: plain_str
                        InsnList callToDecrypt = new InsnList();
                        callToDecrypt.add(new FieldInsnNode(GETSTATIC, classNode.name, keyArrField.name, keyArrField.desc));
                        callToDecrypt.add(InsnUtil.getIntPush(stringId));
                        callToDecrypt.add(new MethodInsnNode(INVOKESTATIC, decClassName, decMethodName, DEC_METHOD_DESC));

                        ldcInsnNode.cst = crypt(cstString, key);
                        methodNode.instructions.insert(insnNode, callToDecrypt);

                        stringId++;
                    }
                }
            }

            // initialize the key array in <clinit>.
            MethodNode staticInitializer = ClassUtil.getOrCreateStaticInitializer(classNode);
            staticInitializer.instructions.insertBefore(staticInitializer.instructions.getFirst(), buildKeyArrayInit(keyByIndex, classNode.name, keyArrField));
        }

        // add class containing decrypt method to classes
        getJar().classes().put(decClassName, cryptClass(decClassName, decMethodName));
    }

    private InsnList buildKeyArrayInit(Map<Integer, Integer> indexKeyMap, String owner, FieldNode keyArrField) {
        InsnList insns = new InsnList();

        // keys = new int[keysSize];
        insns.add(InsnUtil.getIntPush(indexKeyMap.size()));
        insns.add(new IntInsnNode(NEWARRAY, T_INT));
        insns.add(new FieldInsnNode(PUTSTATIC, owner, keyArrField.name, keyArrField.desc));

        List<Map.Entry<Integer, Integer>> indexKeyList = new ArrayList<>(indexKeyMap.entrySet());
        Collections.shuffle(indexKeyList);

        // keys[id] = key;
        for (Map.Entry<Integer, Integer> entry : indexKeyList) {
            insns.add(new FieldInsnNode(GETSTATIC, owner, keyArrField.name, keyArrField.desc));
            insns.add(InsnUtil.getIntPush(entry.getKey()));
            insns.add(InsnUtil.getIntPush(entry.getValue()));
            insns.add(new InsnNode(IASTORE));
        }

        return insns;
    }


    /**
     * Generates:
     * <pre>
     * public static String decrypt(String encrypted, int[] keys, int id) {
     *   char[] in = encrypted.toCharArray();
     *   int len = in.length;
     *   char[] out = new char[len];
     *   for (int i=0; i<len; i++) out[i] = (char)(in[i] ^ keys[id]);
     *   return new String(out);
     * }
     * </pre>
     */
    private ClassNode cryptClass(String className, String methodName) {
        ClassNode cc = new ClassNode();
        cc.visit(CLASS_VERSION, ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodNode cm = new MethodNode(ACC_PUBLIC | ACC_STATIC, methodName, DEC_METHOD_DESC, null, null);
        cm.maxLocals = 3;

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

        cm.instructions.add(new VarInsnNode(ALOAD, 1));
        cm.instructions.add(new VarInsnNode(ILOAD, 2));
        cm.instructions.add(new InsnNode(IALOAD));

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
