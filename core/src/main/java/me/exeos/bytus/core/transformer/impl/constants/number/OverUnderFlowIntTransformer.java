package me.exeos.bytus.core.transformer.impl.constants.number;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.InsnListContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.util.Optional;

/**
 * This Transformer uses the fact that over and under -flowing int additions wrap
 * This can be used that calc 2 numbers that will wrap to the target
 */
public class OverUnderFlowIntTransformer extends AbstractTransformer {

    public OverUnderFlowIntTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.constants.enable() && config.constants.numbers();
    }

    @Override
    public int priority() {
        return Priority.NUM_UNDER_OVER_FLOW;
    }

    @Override
    public void transform(InsnListContext context) {
        InsnUtil.loop(context.insnList(), current -> {
            Optional<Integer> value = InsnUtil.getIntValue(current);
            if (value.isEmpty()) {
                return;
            }

            int intVal = value.get();
            int rand = RandomUtil.getInt(1, 1000);
            int a = intVal < 0 ? Integer.MAX_VALUE - rand : Integer.MIN_VALUE + rand;
            int b = intVal - (intVal < 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE);

            InsnList obfInsn = new InsnList();
            obfInsn.add(InsnUtil.getIntPush(a));
            obfInsn.add(InsnUtil.getIntPush(intVal < 0 ? rand : -rand));
            obfInsn.add(new InsnNode(Opcodes.IADD));
            obfInsn.add(InsnUtil.getIntPush(b));
            obfInsn.add(new InsnNode(Opcodes.IADD));

            context.insnList().insert(current, obfInsn);
            context.insnList().remove(current);
        });
    }
}
