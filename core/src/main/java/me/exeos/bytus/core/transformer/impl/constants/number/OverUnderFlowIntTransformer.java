package me.exeos.bytus.core.transformer.impl.constants.number;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.List;
import java.util.Optional;

/**
 * This Transformer uses the fact that over and under -flowing int additions wrap
 * This can be used that calc 2 numbers that will wrap to the target
 */
public class OverUnderFlowIntTransformer extends Transformer {

    public OverUnderFlowIntTransformer(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                AbstractInsnNode current = methodNode.instructions.getFirst();
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

                    methodNode.instructions.insert(current, obfInsn);
                    methodNode.instructions.remove(current);

                    current = next;
                }
            }
        }
    }
}
