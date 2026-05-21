package me.exeos.bytus.core.transformer.impl.flow;

import me.exeos.bytus.asmplus.codegen.lookupswitch.LookupSwitchGenerator;
import me.exeos.bytus.asmplus.codegen.lookupswitch.SwitchCase;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

// TODO: make verifier shut up about frames, clean up code and document this
public class JumpFlattening extends Transformer {

    public JumpFlattening(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        Random random = new Random();

        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.instructions.size() == 0) {
                    continue;
                }

                // create label to key mapping
                Map<LabelNode, Integer> labelKeyMap = new HashMap<>();
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode instanceof LabelNode labelNode) {
                        int key;
                        do {
                            key = random.nextInt(Integer.MAX_VALUE);
                        } while (labelKeyMap.containsValue(key));

                        labelKeyMap.put(labelNode, key);
                    }
                }

                InsnList switcherInsn = new InsnList();

                LabelNode switcherStart = new LabelNode();
                LabelNode switcherEnd = new LabelNode();
                int keyVarIndex = methodNode.maxLocals++;

                switcherInsn.add(new JumpInsnNode(Opcodes.GOTO, switcherEnd));
                switcherInsn.add(switcherStart);
                switcherInsn.add(new VarInsnNode(Opcodes.ILOAD, keyVarIndex));

                // switch with jumps
                List<SwitchCase> handlers = new ArrayList<>();
                for (Map.Entry<LabelNode, Integer> entry : labelKeyMap.entrySet()) {
                    InsnList handerInsn = new InsnList();
                    handerInsn.add(new JumpInsnNode(Opcodes.GOTO, entry.getKey()));

                    handlers.add(new SwitchCase(entry.getValue(), handerInsn));
                }
                switcherInsn.add(LookupSwitchGenerator.gen(handlers, new SwitchCase(0, MethodUtil.endMethodByThrow()), false));
                switcherInsn.add(switcherEnd);


                boolean updatedJumps = false;
                for (AbstractInsnNode insnNode : methodNode.instructions) {
                    if (insnNode instanceof JumpInsnNode jumpInsnNode) {
                        Integer key = labelKeyMap.get(jumpInsnNode.label);
                        if (key == null) {
                            continue;
                        }

                        InsnList updateSwitcherKeyInsn = new InsnList();
                        updateSwitcherKeyInsn.add(InsnUtil.getIntPush(key));
                        updateSwitcherKeyInsn.add(new VarInsnNode(Opcodes.ISTORE, keyVarIndex));


                        jumpInsnNode.label = switcherStart;
                        methodNode.instructions.insertBefore(jumpInsnNode, updateSwitcherKeyInsn);

                        updatedJumps = true;
                    }
                }

                if (updatedJumps) {
                    methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), switcherInsn);
                }
            }
        }
    }
}
