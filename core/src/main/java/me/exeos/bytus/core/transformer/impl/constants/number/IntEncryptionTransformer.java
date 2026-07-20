package me.exeos.bytus.core.transformer.impl.constants.number;

import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import org.objectweb.asm.tree.InsnList;

import java.util.Optional;

/**
 * This Transformer uses the fact that over and under -flowing int additions wrap
 * This can be used that calc 2 numbers that will wrap to the target
 */
public class IntEncryptionTransformer extends AbstractTransformer {

    public IntEncryptionTransformer(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.constants.enable() && config.constants.numbers();
    }

    @Override
    public int priority() {
        return Priority.NUM_ENC;
    }

    @Override
    public void transform(MethodContext context) {
        InsnUtil.loop(context.methodNode().instructions, current -> {
            Optional<Integer> value = InsnUtil.getIntValue(current);
            if (value.isEmpty()) {
                return;
            }

            InsnList obfuscated = new InsnList();
            obfuscated.add(context.getExtension().getObfuscatedIntPush(value.get()));

            context.methodNode().instructions.insertBefore(current, obfuscated);
            context.methodNode().instructions.remove(current);
        });
    }
}
