package me.exeos.bytus.core.transformer.impl.arithmetic;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.concurrent.atomic.AtomicBoolean;

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
        int var1 = methodNode.maxLocals += 2;
        int var2 = methodNode.maxLocals += 2;

        MethodExtension.SaltInfo saltInfo = context.pipeline().getSaltInfo(methodNode);

        for (int i = 0; i < RandomUtil.getInt(config.mba.minPasses(), config.mba.maxPasses()); i++) {
            InsnUtil.loop(methodNode.instructions, current -> {
                if (current instanceof JumpInsnNode && current.getOpcode() >= IFEQ && current.getOpcode() <= IF_ICMPLE) {
                    InsnList mba = new InsnList();
                    mba.add(InsnUtil.getIntPushSalted(0, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                    mba.add(new InsnNode(RandomUtil.chance(50) ? IOR : IXOR));
                    if (current.getOpcode() >= IF_ICMPEQ && current.getOpcode() <= IF_ICMPLE) {
                        mba.add(new InsnNode(SWAP));
                        mba.add(InsnUtil.getIntPushSalted(0, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        mba.add(new InsnNode(RandomUtil.chance(50) ? IOR : IXOR));
                        mba.add(new InsnNode(SWAP));
                    }
                    methodNode.instructions.insertBefore(current, mba);
                    return;
                }

                InsnList expression = new InsnList();
                switch (current.getOpcode()) {
                    // +
                    case IADD -> {
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IXOR));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(IAND));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(ISHL));
                        expression.add(new InsnNode(IADD));
                    }
                    case LADD -> {
                        expression.add(new VarInsnNode(LSTORE, var2));
                        expression.add(new VarInsnNode(LSTORE, var1));

                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LXOR));
                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LAND));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(LSHL));
                        expression.add(new InsnNode(LADD));
                    }
                    // -
                    case ISUB -> {
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IXOR));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(SWAP));
                        expression.add(InsnUtil.getIntPushSalted(-1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(IXOR));
                        expression.add(new InsnNode(IAND));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(ISHL));
                        expression.add(new InsnNode(ISUB));
                    }
                    case LSUB -> {
                        expression.add(new VarInsnNode(LSTORE, var2));
                        expression.add(new VarInsnNode(LSTORE, var1));

                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LXOR));
                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(InsnUtil.getLongPush(-1));
                        expression.add(new InsnNode(LXOR));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LAND));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(LSHL));
                        expression.add(new InsnNode(LSUB));
                    }
                    // &
                    case IAND -> {
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IADD));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(IXOR));
                        expression.add(new InsnNode(ISUB));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(ISHR));
                    }
                    // &
                    case LAND -> {
                        expression.add(new VarInsnNode(LSTORE, var2));
                        expression.add(new VarInsnNode(LSTORE, var1));

                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LADD));
                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LXOR));
                        expression.add(new InsnNode(LSUB));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(LSHR));
                    }
                    // |
                    case IOR -> {
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IXOR));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(IAND));
                        expression.add(new InsnNode(IADD));
                    }
                    // |
                    case LOR -> {
                        expression.add(new VarInsnNode(LSTORE, var2));
                        expression.add(new VarInsnNode(LSTORE, var1));

                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LXOR));
                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LAND));
                        expression.add(new InsnNode(LADD));
                    }
                    // ^
                    case IXOR -> {
                        expression.add(new InsnNode(DUP2));
                        expression.add(new InsnNode(IADD));
                        expression.add(new InsnNode(DUP_X2));
                        expression.add(new InsnNode(POP));
                        expression.add(new InsnNode(IAND));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(ISHL));
                        expression.add(new InsnNode(ISUB));
                    }
                    // ^
                    case LXOR -> {
                        expression.add(new VarInsnNode(LSTORE, var2));
                        expression.add(new VarInsnNode(LSTORE, var1));

                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LADD));
                        expression.add(new VarInsnNode(LLOAD, var1));
                        expression.add(new VarInsnNode(LLOAD, var2));
                        expression.add(new InsnNode(LAND));
                        expression.add(InsnUtil.getIntPushSalted(1, saltInfo.hasSalt(), saltInfo.getSaltOrDefault(), saltInfo.getSaltSlotOrDefault()));
                        expression.add(new InsnNode(LSHL));
                        expression.add(new InsnNode(LSUB));
                    }
                }

                if (expression.size() > 0) {
                    methodNode.instructions.insert(current, expression);
                    methodNode.instructions.remove(current);
                }
            });
        }
    }
}
