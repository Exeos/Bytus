package me.exeos.bytus.core.transformer.impl.constants.string;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.HierarchyUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StringEncryptionTransformer extends Transformer {

    private final static int CLASS_VERSION = V1_8;
    private final static String decMethodDesc = "(Ljava/lang/String;[II)Ljava/lang/String;";

    public StringEncryptionTransformer(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        String decClassName = ClassUtil.getNoneCollidingClassName(getJar(), RandomUtil::getString);
        String decMethodName = RandomUtil.getString(1);

        for (ClassNode classNode : getIncludedClasses()) {
            FieldNode keyArrField = new FieldNode(ACC_PRIVATE | ACC_STATIC, ClassUtil.getNoneCollidingFieldName(getJar(), classNode, RandomUtil::getString), "[I", null, null);
            classNode.fields.add(keyArrField);

            Map<String, Integer> strConstKeyIndex = new HashMap<>();
            Map<Integer, Integer> indexKeyMap = new HashMap<>();
            int i = 0;
            for (MethodNode methodNode : classNode.methods) {
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode instanceof LdcInsnNode ldcInsnNode && ldcInsnNode.cst instanceof String cstString) {
                        int key = RandomUtil.getInt(1, 100);

                        strConstKeyIndex.put(cstString, i);
                        indexKeyMap.put(i, key);

                        InsnList callToDecrypt = new InsnList();
                        callToDecrypt.add(new FieldInsnNode(GETSTATIC, classNode.name, keyArrField.name, keyArrField.desc));
                        callToDecrypt.add(InsnUtil.getIntPush(i));
                        callToDecrypt.add(new MethodInsnNode(INVOKESTATIC, decClassName, decMethodName, decMethodDesc));

                        ldcInsnNode.cst = crypt(cstString, key);
                        methodNode.instructions.insert(insnNode, callToDecrypt);

                        i++;
                    }
                }
            }

            MethodNode staticInitializer = ClassUtil.getOrCreateStaticInitializer(classNode);
            staticInitializer.instructions.insertBefore(staticInitializer.instructions.getFirst(), buildKeyArrayInit(indexKeyMap, classNode.name, keyArrField));
        }

        getJar().classes().put(decClassName, cryptClass(decClassName, decMethodName));
    }

    private InsnList buildKeyArrayInit(Map<Integer, Integer> indexKeyMap, String owner, FieldNode keyArrField) {
        InsnList instructions = new InsnList();

        instructions.add(InsnUtil.getIntPush(indexKeyMap.size()));
        instructions.add(new IntInsnNode(NEWARRAY, T_INT));
        instructions.add(new FieldInsnNode(PUTSTATIC, owner, keyArrField.name, keyArrField.desc));

        for (Map.Entry<Integer, Integer> entry : indexKeyMap.entrySet()) {
            instructions.add(new FieldInsnNode(GETSTATIC, owner, keyArrField.name, keyArrField.desc));
            instructions.add(InsnUtil.getIntPush(entry.getKey()));
            instructions.add(InsnUtil.getIntPush(entry.getValue()));
            instructions.add(new InsnNode(IASTORE));
        }

        return instructions;
    }

    private ClassNode cryptClass(String className, String methodName) {
        ClassNode cc = new ClassNode();
        cc.visit(CLASS_VERSION, ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodNode cm = new MethodNode(ACC_PUBLIC | ACC_STATIC, methodName, decMethodDesc, null, null);
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

    private String crypt(String x, int key) {
        char[] chars = x.toCharArray();
        char[] cryptedChars = new char[chars.length];

        for (int i = 0; i < chars.length; i++) {
            cryptedChars[i] = (char) ((int) chars[i] ^ key);
        }

        return new String(cryptedChars);
    }
}
