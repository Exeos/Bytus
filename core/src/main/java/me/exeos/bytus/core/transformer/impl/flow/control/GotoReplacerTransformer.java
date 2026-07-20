package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import org.objectweb.asm.tree.JumpInsnNode;

public class GotoReplacerTransformer extends AbstractTransformer {

    public GotoReplacerTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable() && config.flow.controlFlow().replaceGotos();
    }

    @Override
    public int priority() {
        return Priority.FLOW_GOTO_REPLACE;
    }

    @Override
    public void transform(MethodContext context) {
        InsnUtil.loop(context.methodNode().instructions, insnNode -> {
            if (insnNode.getOpcode() == GOTO && insnNode instanceof JumpInsnNode jumpInsnNode && RandomUtil.chance(30)) {
                context.methodNode().instructions.insertBefore(insnNode, context.getExtension().getObfuscatedJump(jumpInsnNode.label));
                context.methodNode().instructions.remove(insnNode);
            }
        });
    }
}
