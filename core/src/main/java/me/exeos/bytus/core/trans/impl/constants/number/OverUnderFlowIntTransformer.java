package me.exeos.bytus.core.trans.impl.constants.number;

import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.trans.AbstractTransformer;
import me.exeos.bytus.core.trans.context.InsnListContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Optional;

/**
 * This Transformer uses the fact that over and under -flowing int additions wrap
 * This can be used that calc 2 numbers that will wrap to the target
 */
public class OverUnderFlowIntTransformer implements AbstractTransformer {

    @Override
    public void transform(InsnListContext context) {
        AbstractInsnNode current = context.insnList().getFirst();
        while (current != null) {
            AbstractInsnNode next = current.getNext();

            Optional<Integer> value = InsnUtil.getIntValue(current);
            if (value.isEmpty()) {
                current = next;
                continue;
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

            current = next;
        }
    }

    @Override
    public boolean applies(BytusConfig config) {
        return config.constants.enable() && config.constants.numbers();
    }

    @Override
    public int priority() {
        return 1;
    }
}
