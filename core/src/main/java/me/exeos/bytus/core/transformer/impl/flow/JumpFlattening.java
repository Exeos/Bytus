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

/**
 * Control-flow obfuscation transformer that "flattens" jumps by routing them through
 * a central dispatcher implemented as a {@code lookupswitch}.
 *
 * <p>Overview:
 * <ol>
 *   <li>Assign each {@link LabelNode} in the method a random integer "key".</li>
 *   <li>Add a new local int variable that holds the next key to execute.</li>
 *   <li>Rewrite every {@link JumpInsnNode} so it:
 *     <ol>
 *       <li>stores the target label's key into the key local</li>
 *       <li>jumps to the dispatcher entry label</li>
 *     </ol>
 *   </li>
 *   <li>Insert a dispatcher block near the start of the method that:
 *     loads the state local and jumps to the matching label via {@code lookupswitch}.</li>
 * </ol>
 */
public class JumpFlattening extends Transformer {

    public JumpFlattening(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                if (methodNode.instructions.size() == 0) {
                    continue;
                }

                // create label to key mapping
                Map<LabelNode, Integer> labelKeyMap = assignLabelsToKeys(methodNode.instructions);
                LabelNode dispatcherEntry = new LabelNode();
                int keyVarIndex = methodNode.maxLocals++;

                // update target label of each JumpInsn node to dispatcher entry and assign key
                boolean didUpdateAnyJumps = rewriteJumpsToDispatcher(
                        methodNode,
                        dispatcherEntry,
                        labelKeyMap,
                        keyVarIndex
                );

                if (didUpdateAnyJumps) {
                    // insert dispatcher
                    methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), buildDispatcher(dispatcherEntry, labelKeyMap, keyVarIndex));
                }
            }
        }
    }

    /**
     * Assigns each {@link LabelNode} in the instruction list a unique, random int.
     *
     * <p>Keys become the case keys in the {@code lookupswitch} dispatcher.</p>
     *
     * @param instructions method instruction list
     * @return map of label -> assigned key
     */
    private Map<LabelNode, Integer> assignLabelsToKeys(InsnList instructions) {
        Random random = new Random();
        Map<LabelNode, Integer> labelKeyMap = new HashMap<>();

        for (AbstractInsnNode insnNode : instructions) {
            if (insnNode instanceof LabelNode labelNode) {
                int key;
                do {
                    key = random.nextInt(Integer.MAX_VALUE);
                } while (labelKeyMap.containsValue(key));

                labelKeyMap.put(labelNode, key);
            }
        }

        return labelKeyMap;
    }

    /**
     * Builds the dispatcher instructions.
     *
     * <pre>
     *   goto dispatcherEnd
     * dispatcherEntry:
     *   iload key
     *   lookupswitch { key -> goto corresponding label ... default -> throw }
     * dispatcherEnd:
     * </pre>
     *
     * <p>The initial {@code goto dispatcherEnd} ensures dispatcher doesn't get executed unless explicitly jumped to</p>
     */
    private InsnList buildDispatcher(LabelNode dispatcherEntry, Map<LabelNode, Integer> labelKeyMap, int keyVarIndex) {
        InsnList insn = new InsnList();

        LabelNode dispatcherEnd = new LabelNode();

        // unless explicitly jumped to
        insn.add(new JumpInsnNode(Opcodes.GOTO, dispatcherEnd));

        insn.add(dispatcherEntry);
        insn.add(new VarInsnNode(Opcodes.ILOAD, keyVarIndex));

        // Create switch.
        // cases: key -> goto original label.
        List<SwitchCase> cases = new ArrayList<>();
        for (Map.Entry<LabelNode, Integer> entry : labelKeyMap.entrySet()) {
            int caseKey = entry.getValue();
            LabelNode targetLabel = entry.getKey();

            InsnList caseInsn = new InsnList();
            caseInsn.add(new JumpInsnNode(Opcodes.GOTO, targetLabel));

            cases.add(new SwitchCase(caseKey, caseInsn));
        }

        // Default: Method ends by throw. This should never be reached.
        insn.add(LookupSwitchGenerator.gen(cases, new SwitchCase(0, MethodUtil.endMethodByThrow()), false));
        insn.add(dispatcherEnd);

        return insn;
    }

    /**
     * Rewrites all {@link JumpInsnNode} instructions so they:
     * <ol>
     *   <li>store the target label's key into {@code keyVarIndex}</li>
     *   <li>jump to {@code dispatcherEntry}</li>
     * </ol>
     *
     * @return true if at least one jump was rewritten
     */
    private boolean rewriteJumpsToDispatcher(MethodNode methodNode, LabelNode dispatcherEntry, Map<LabelNode, Integer> labelKeyMap, int keyVarIndex) {
        boolean updatedJumps = false;

        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof JumpInsnNode jumpInsnNode) {
                Integer key = labelKeyMap.get(jumpInsnNode.label);
                if (key == null) {
                    continue;
                }

                InsnList updateKeyInsn = new InsnList();
                updateKeyInsn.add(InsnUtil.getIntPush(key));
                updateKeyInsn.add(new VarInsnNode(Opcodes.ISTORE, keyVarIndex));


                jumpInsnNode.label = dispatcherEntry;
                methodNode.instructions.insertBefore(jumpInsnNode, updateKeyInsn);

                updatedJumps = true;
            }
        }

        return updatedJumps;
    }
}
