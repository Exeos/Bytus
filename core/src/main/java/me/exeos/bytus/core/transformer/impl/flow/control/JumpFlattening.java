package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.bytus.asmplus.codegen.lookupswitch.LookupSwitchGenerator;
import me.exeos.bytus.asmplus.codegen.lookupswitch.SwitchCase;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.transformer.extensions.MethodExtension;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * Control-flow obfuscation transformer that "flattens" jumps by routing them through
 * a central {@code lookupswitch}-based dispatcher.
 *
 * <p>Overview:
 * <ol>
 *   <li>Assign each {@link LabelNode} a chain (path) of unique, random integer keys.</li>
 *   <li>Add a new local int variable that holds the current dispatcher key.</li>
 *   <li>Rewrite every {@link JumpInsnNode} so it:
 *     <ol>
 *       <li>stores the target label’s path entry key into the key local</li>
 *       <li>jumps to the dispatcher entry label</li>
 *     </ol>
 *   </li>
 *   <li>Insert a dispatcher block near the start of the method that:
 *     loads the key local and uses {@code lookupswitch} to either
 *     <ol>
 *       <li>advance along the key chain (updating the key local and re-entering the dispatcher), or</li>
 *       <li>jump to the final key that transfers control to the original target label.</li>
 *     </ol>
 *   </li>
 * </ol>
 */
public class JumpFlattening extends AbstractTransformer {

    private final int minDispatcherChainLength, maxDispatcherChainLength;

    public JumpFlattening(BytusConfig config) {
        super(config);
        minDispatcherChainLength = config.flow.controlFlow().minDispatcherChainLength();
        maxDispatcherChainLength = config.flow.controlFlow().maxDispatcherChainLength();
    }

    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable();
    }

    @Override
    public int priority() {
        return Priority.FLOW_JUMP_FLATTENING;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();
        MethodExtension methodExtension = context.pipeline().getExtension(methodNode);

        if (methodNode.instructions.size() == 0) {
            return;
        }

        // create label to key mapping
        Map<LabelNode, int[]> labelPathMap = assignLabelsToPaths(methodNode.instructions);
        LabelNode dispatcherEntry = new LabelNode();
        int keyVarIndex = methodNode.maxLocals++;

        // update target label of each JumpInsn node to dispatcher entry and assign key
        boolean didUpdateAnyJumps = rewriteJumpsToDispatcher(
                methodNode,
                dispatcherEntry,
                labelPathMap,
                keyVarIndex,
                methodExtension
        );

        if (didUpdateAnyJumps) {
            // insert dispatcher
            methodNode.instructions.insertBefore(methodNode.instructions.getFirst(), buildDispatcher(dispatcherEntry, labelPathMap, keyVarIndex, methodExtension));
        }
    }

    /**
     * Assigns each {@link LabelNode} in the instruction list a path consisting of unique, random ints.
     *
     * <p>The intent is to avoid a simple 1-to-1:
     * the dispatcher will have to traverse multiple states before reaching the final block handler.</p>
     *
     * @param instructions method instruction list
     * @return map of label -> array of path states (first is entry state for that block)
     */
    private Map<LabelNode, int[]> assignLabelsToPaths(InsnList instructions) {
        Map<LabelNode, int[]> labelPathMap = new HashMap<>();
        Set<Integer> usedKeys = new HashSet<>();

        for (AbstractInsnNode insnNode : instructions) {
            if (insnNode instanceof LabelNode labelNode) {
                int[] pathKeys = new int[RandomUtil.getInt(Math.max(1, this.minDispatcherChainLength), Math.max(1, maxDispatcherChainLength + 1))];

                for (int i = 0; i < pathKeys.length; i++) {
                    do {
                        pathKeys[i] = RandomUtil.nextInt();
                    } while (usedKeys.contains(pathKeys[i]));
                    usedKeys.add(pathKeys[i]);
                }

                labelPathMap.put(labelNode, pathKeys);
            }
        }

        return labelPathMap;
    }

    /**
     * Builds the dispatcher instructions.
     *
     * <pre>
     *   goto dispatcherEnd
     * dispatcherEntry:
     *   iload key
     *   lookupswitch {
     *      chain_entry -> goto next_in_chain...,
     *      last_in_chain -> goto actual_dispatcher,
     *      actual_dispatcher -> goto real_label,
     *      default -> throw
     *  }
     * dispatcherEnd:
     * </pre>
     *
     * <p>The initial {@code goto dispatcherEnd} ensures dispatcher doesn't get executed unless explicitly jumped to</p>
     */
    private InsnList buildDispatcher(LabelNode dispatcherEntry, Map<LabelNode, int[]> labelPathMap, int keyVarIndex, MethodExtension methodExtension) {
        InsnList insn = new InsnList();

        LabelNode dispatcherEnd = new LabelNode();

        // unless explicitly jumped to
        insn.add(new JumpInsnNode(Opcodes.GOTO, dispatcherEnd));

        insn.add(dispatcherEntry);
        insn.add(new VarInsnNode(Opcodes.ILOAD, keyVarIndex));

        // shuffle for randomness
        List<Map.Entry<LabelNode, int[]>> labelPathList = new ArrayList<>(labelPathMap.entrySet());
        Collections.shuffle(labelPathList);

        // Create switch.
        // cases: key -> goto original label.
        List<SwitchCase> cases = new ArrayList<>();
        for (Map.Entry<LabelNode, int[]> entry : labelPathList) {
            int[] path = entry.getValue();
            LabelNode targetLabel = entry.getKey();

            InsnList caseInsn = new InsnList();
            caseInsn.add(new JumpInsnNode(Opcodes.GOTO, targetLabel));

            cases.add(new SwitchCase(path[path.length - 1], caseInsn));

            // Add path chain
            // entry -> chain -> .. -> handler
            for (int i = 0; i < path.length - 1; i++) {
                InsnList pathInsn = new InsnList();
                pathInsn.add(methodExtension.getObfuscatedIntPush(path[i + 1]));
                pathInsn.add(new VarInsnNode(Opcodes.ISTORE, keyVarIndex));
                pathInsn.add(new JumpInsnNode(Opcodes.GOTO, dispatcherEntry));

                cases.add(new SwitchCase(path[i], pathInsn));
            }
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
    private boolean rewriteJumpsToDispatcher(MethodNode methodNode, LabelNode dispatcherEntry, Map<LabelNode, int[]> labelPathMap, int keyVarIndex, MethodExtension methodExtension) {
        boolean updatedJumps = false;

        for (AbstractInsnNode insnNode : methodNode.instructions) {
            if (insnNode instanceof JumpInsnNode jumpInsnNode) {
                int[] path = labelPathMap.get(jumpInsnNode.label);
                if (path == null) {
                    continue;
                }

                InsnList updateKeyInsn = new InsnList();
                updateKeyInsn.add(methodExtension.getObfuscatedIntPush(path[0]));
                updateKeyInsn.add(new VarInsnNode(Opcodes.ISTORE, keyVarIndex));


                jumpInsnNode.label = dispatcherEntry;
                methodNode.instructions.insertBefore(jumpInsnNode, updateKeyInsn);

                updatedJumps = true;
            }
        }

        return updatedJumps;
    }
}
