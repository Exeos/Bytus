package me.exeos.bytus.core.transformer.impl.flow.control;

import me.exeos.asmplus.analysis.flow.FlowAnalyzer;
import me.exeos.asmplus.analysis.flow.block.BasicBlock;
import me.exeos.asmplus.analysis.flow.block.impl.FallTroughBlock;
import me.exeos.asmplus.analysis.flow.block.impl.JumpBlock;
import me.exeos.asmplus.analysis.flow.block.impl.SwitchBlock;
import me.exeos.asmplus.analysis.flow.block.impl.TerminalBlock;
import me.exeos.asmplus.codegen.value.impl.ConstantPusher;
import me.exeos.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.Priority;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.Collectors;

public class FlowFlattening2 extends AbstractTransformer {

    public FlowFlattening2(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return config.flow.controlFlow().enable() || true;
    }

    @Override
    public int priority() {
        return Priority.FLOW_CTRL_FLATTENING;
    }

    @Override
    public void transform(MethodContext context) {
        MethodNode methodNode = context.methodNode();
        if (!methodNode.tryCatchBlocks.isEmpty()) {
            return;
        }

        List<BasicBlock> blocks = FlowAnalyzer.getBasicBlocks(methodNode, true);
        if (blocks.isEmpty()) {
            return;
        }
        Map<BasicBlock, int[]> keyByBlock = generateKeyMap(blocks);
        int stateVarSlot = MethodUtil.getFirstFreeSlot(methodNode);

        InsnList flattened = new InsnList();

        flattened.add(ConstantPusher.getIntPush(keyByBlock.get(blocks.getFirst())[0]));
        flattened.add(new VarInsnNode(ISTORE, stateVarSlot));

        flattened.add(generateDispatcher(blocks, keyByBlock, stateVarSlot));

        methodNode.instructions = flattened;
    }

    private InsnList generateDispatcher(List<BasicBlock> blocks, Map<BasicBlock, int[]> keyByBlock, int stateVarSlot) {
        Map<BasicBlock, LabelNode> labelByBlock = new LinkedHashMap<>();
        int[] switchKeys = new int[blocks.size()];
        int i = 0;
        for (Map.Entry<BasicBlock, int[]> entry : keyByBlock.entrySet()) {
            labelByBlock.put(entry.getKey(), new LabelNode());
            switchKeys[i] = entry.getValue()[0];
            i++;
        }

        InsnList dispatcher = new InsnList();
        LabelNode dispatcherEntry = new LabelNode();
        LabelNode defaultCase = new LabelNode();

        dispatcher.add(dispatcherEntry);
        dispatcher.add(new VarInsnNode(ILOAD, stateVarSlot));
        dispatcher.add(new LookupSwitchInsnNode(defaultCase, switchKeys, labelByBlock.values().toArray(new LabelNode[0])));

        for (BasicBlock block : blocks) {
            dispatcher.add(labelByBlock.get(block));
            switch (block) {
                case JumpBlock jumpBlock -> {
                    InsnList updatedJump = jumpBlock.insnList();
                    updatedJump.remove(jumpBlock.dispatcher);

                    updatedJump.add(updateStateVar(jumpBlock.trueBranchBlock, dispatcherEntry, keyByBlock, stateVarSlot, false));
                    updatedJump.add(new JumpInsnNode(jumpBlock.dispatcher.getOpcode(), dispatcherEntry));
                    jumpBlock.falseBranchBlock.ifPresent(fbb -> {
                        updatedJump.add(updateStateVar(fbb, dispatcherEntry, keyByBlock, stateVarSlot, false));
                        updatedJump.add(new JumpInsnNode(GOTO, dispatcherEntry));
                    });

                    dispatcher.add(updatedJump);
                }
                case SwitchBlock switchBlock -> {
                    List<LabelNode> labels = null;
                    if (switchBlock.dispatcher instanceof LookupSwitchInsnNode ls) {
                        labels = new ArrayList<>(ls.labels.size());
                    } else if (switchBlock.dispatcher instanceof TableSwitchInsnNode ts) {
                        labels = new ArrayList<>(ts.labels.size());
                    }

                    for (BasicBlock caseBlock : switchBlock.keyCaseMap.values()) {
                        LabelNode newLabel = new LabelNode();
                        Objects.requireNonNull(labels).add(newLabel);

                        dispatcher.add(newLabel);
                        dispatcher.add(updateStateVar(caseBlock, dispatcherEntry, keyByBlock, stateVarSlot));
                    }

                    if (switchBlock.dispatcher instanceof LookupSwitchInsnNode ls) {
                        ls.labels = labels;
                    } else if (switchBlock.dispatcher instanceof TableSwitchInsnNode ts) {
                        ts.labels = labels;
                    }
                }
                case FallTroughBlock fallTroughBlock -> {
                    dispatcher.add(fallTroughBlock.insnList());
                    dispatcher.add(updateStateVar(fallTroughBlock.fallTroughBlock, dispatcherEntry, keyByBlock, stateVarSlot));
                }
                case TerminalBlock terminalBlock -> {
                    dispatcher.add(terminalBlock.insnList());
                }
                default -> throw new IllegalStateException("Invalid block: " + block.getClass().getSimpleName());
            }
        }

        dispatcher.add(defaultCase);
        dispatcher.add(MethodUtil.endMethodByThrow());

        return dispatcher;
    }

    private InsnList updateStateVar(BasicBlock target, LabelNode dispatcherEntry, Map<BasicBlock, int[]> keyByBlock, int stateVarSlot) {
        return updateStateVar(target, dispatcherEntry, keyByBlock, stateVarSlot, true);
    }

    private InsnList updateStateVar(BasicBlock target, LabelNode dispatcherEntry, Map<BasicBlock, int[]> keyByBlock, int stateVarSlot, boolean jump) {
        int[] key = keyByBlock.get(target);
        if (key == null) {
            throw new IllegalArgumentException("Block not mapped: " + target);
        }

        InsnList updateInsn = new InsnList();

        updateInsn.add(ConstantPusher.getIntPush(key[0]));
        updateInsn.add(new VarInsnNode(ISTORE, stateVarSlot));
        if (jump) {
            updateInsn.add(new JumpInsnNode(GOTO, dispatcherEntry));
        }


        return updateInsn;
    }

    private Map<BasicBlock, int[]> generateKeyMap(List<BasicBlock> blocks) {
        Map<BasicBlock, int[]> unordered = new HashMap<>();
        Set<Integer> usedKeys = new HashSet<>();

        for (BasicBlock block : blocks) {
            int[] pathKeys = new int[RandomUtil.getInt(Math.max(1, config.flow.controlFlow().minDispatcherChainLength()), Math.max(1, config.flow.controlFlow().maxDispatcherChainLength() + 1))];

            for (int i = 0; i < pathKeys.length; i++) {
                do {
                    pathKeys[i] = RandomUtil.getInt();
                } while (usedKeys.contains(pathKeys[i]));
                usedKeys.add(pathKeys[i]);
            }

            unordered.put(block, pathKeys);
        }

        return unordered.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.comparingInt(o -> o[0]))).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (ints, _) -> ints,
                LinkedHashMap::new
        ));
    }
}
