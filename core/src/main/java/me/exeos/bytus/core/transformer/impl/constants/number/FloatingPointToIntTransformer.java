package me.exeos.bytus.core.transformer.impl.constants.number;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class FloatingPointToIntTransformer extends AbstractTransformer {

    public FloatingPointToIntTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.constants.enable() && config.constants.numbers();
    }

    @Override
    public int priority() {
        return Priority.FLOATING_TO_INT;
    }

    @Override
    public void transform(MethodContext context) {
        InsnUtil.loop(context.methodNode().instructions, insnNode -> {
            if (!(insnNode instanceof LdcInsnNode ldcInsnNode)) {
                return;
            }

            InsnList replacement = new InsnList();

            if (ldcInsnNode.cst instanceof Double cstDouble) {
                long value = Double.doubleToLongBits(cstDouble);

                replacement.add(InsnUtil.getLongPush(value));
                replacement.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "longBitsToDouble", "(J)D"));
            }

            if (ldcInsnNode.cst instanceof Float cstFloat) {
                int value = Float.floatToIntBits(cstFloat);

                replacement.add(context.getExtension().getObfuscatedIntPush(value));
                replacement.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "intBitsToFloat", "(I)F"));
            }

            if (replacement.size() > 0) {
                context.methodNode().instructions.insertBefore(insnNode, replacement);
                context.methodNode().instructions.remove(insnNode);
            }
        });
    }
}
