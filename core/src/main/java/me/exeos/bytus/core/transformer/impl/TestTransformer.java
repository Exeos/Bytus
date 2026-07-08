package me.exeos.bytus.core.transformer.impl;

import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.ClassContext;
import org.objectweb.asm.tree.*;

public class TestTransformer extends AbstractTransformer {

    public TestTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return false;
    }

    @Override
    public int priority() {
        return 67;
    }

    @Override
    public void transform(ClassContext context) {
        MethodNode clinit = ClassUtil.getOrCreateStaticInitializer(context.classNode());

        InsnList funny = new InsnList();

        funny.add(context.pipeline().getExtension(context.classNode(), clinit).getObfuscatedIntPush(67));
        funny.add(context.pipeline().getExtension(context.classNode(), clinit).getObfuscatedIntPush(67));
        LabelNode labelNode = new LabelNode();
        funny.add(new JumpInsnNode(IF_ICMPEQ, labelNode));
        funny.add(new FieldInsnNode(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;"));
        funny.add(new LdcInsnNode("Invalid at: " + context.classNode().name));
        funny.add(new MethodInsnNode(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V"));
        funny.add(labelNode);


        InsnUtil.loop(clinit.instructions, insnNode -> {
            if (InsnUtil.isReturn(insnNode)) {
                clinit.instructions.insertBefore(insnNode, funny);
            }
        });
    }
}
