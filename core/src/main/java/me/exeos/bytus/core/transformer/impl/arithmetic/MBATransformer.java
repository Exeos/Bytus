package me.exeos.bytus.core.transformer.impl.arithmetic;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

public class MBATransformer extends AbstractTransformer {

    public MBATransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.mba.enable();
    }

    @Override
    public int priority() {
        return Priority.MBA;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();

        for (int i = 0; i < RandomUtil.getInt(config.mba.minPasses(), config.mba.maxPasses()); i++) {
            InsnUtil.loop(methodNode.instructions, current -> {
                InsnList expression = new InsnList();
                switch (current.getOpcode()) {
                    case IADD -> {
                        // (a | b) + (a & b)
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IOR));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(IAND));
                        expression.add(new InsnNode(IADD));
                    }
                    case LADD -> {}
                    case ISUB -> {
                        expression.add(new InsnNode(ICONST_M1));
                        expression.add(new InsnNode(IXOR));
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IAND));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(IXOR));
                        expression.add(new InsnNode(SWAP));
                        expression.add(new InsnNode(ICONST_2));
                        expression.add(new InsnNode(IMUL));
                        expression.add(new InsnNode(IADD));
                        expression.add(new InsnNode(ICONST_1));
                        expression.add(new InsnNode(IADD));
                    }
                    case LSUB -> {}
                    case IAND -> {}
                    case LAND -> {}
                    case IOR -> {
                        // a + b - (a & b)
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IADD));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(IAND));
                        expression.add(new InsnNode(ISUB));
                    }
                    case LOR -> {}
                    case IXOR -> {}
                    case LXOR -> {}
                }

                if (expression.size() > 0) {
                    methodNode.instructions.insert(current, expression);
                    methodNode.instructions.remove(current);
                }
            });
        }
    }

    private InsnList storeAndLoad(boolean isLong, int var1, int var2) {
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(isLong ? LSTORE : ISTORE, var2));
        insns.add(new VarInsnNode(isLong ? LSTORE : ISTORE, var1));
        insns.add(new VarInsnNode(isLong ? LLOAD : ILOAD, var1));
        insns.add(new VarInsnNode(isLong ? LLOAD : ILOAD, var2));

        return insns;
    }
}
