package me.exeos.bytus.transformers.flow;

import me.exeos.asmplus.codegen.lookupswitch.LookupSwitchGenerator;
import me.exeos.asmplus.codegen.lookupswitch.SwitchCase;
import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.asmplus.utils.RandomUtil;
import me.exeos.bytus.api.generation.UniqueNameGen;
import me.exeos.bytus.api.transformer.Transformer;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

public class ControlFlowTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                UniqueNameGen sharedNameGen = new UniqueNameGen();
                /* Insert proxy jumps after method return */
                ArrayList<AbstractInsnNode> proxyJumps = new ArrayList<>();
                AbstractInsnNode methodEnd = ASMUtils.getMethodEnd(methodNode);
                if (methodEnd == null) {
                    System.out.println("Skipped " + classNode.name + "." + methodNode.name + methodNode.desc + " because couldn't find return insn");
                    continue;
                }
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (insnNode.getOpcode() >= IRETURN && insnNode.getOpcode() <= RETURN) {
                        break;
                    }
                    if (!ASMUtils.isJumpOrCondition(insnNode)) {
                        continue;
                    }

                    JumpInsnNode realJumpInsn = (JumpInsnNode) insnNode;
                    LabelNode afterInsn = new LabelNode();

                    /* blocks */
                    /* 1 */
                    LabelNode firstBlockEntry = new LabelNode();

                    /* 2 */
                    LabelNode secondBlockEntry = new LabelNode();

                    ArrayList<AbstractInsnNode> firstBlock = new ArrayList<>();
                    ArrayList<AbstractInsnNode> secondBlock = new ArrayList<>();
                    ArrayList<AbstractInsnNode> blocksCombined = new ArrayList<>();

                    /* First block */
                    firstBlock.add(firstBlockEntry);
                    switch (0) {
                        /* branch at case */
                        case 0:
                            int switchSize = RandomUtil.getInt(3, 5);
                            int branchIndex = RandomUtil.getInt(0, switchSize - 1);
                            ArrayList<AbstractInsnNode> switchInsns = new ArrayList<>();

                            /* Build switch value */
                            String switchValue = sharedNameGen.next();
                            switchInsns.add(ASMUtils.getIntPush(switchValue.hashCode()));

                            /* Switch cases */
                            ArrayList<SwitchCase> cases = new ArrayList<>();
                            for (int i = 0; i < switchSize; i++) {
                                if (i == branchIndex) {
                                    /* got to block 2 proxy */
                                    cases.add(new SwitchCase(switchValue.hashCode(), ASMUtils.getJumpInsns(secondBlockEntry)));
                                } else {
                                    /* do bogus code */
                                    int hashOffset = RandomUtil.getInt(-500, 500);
                                    while (hashOffset == 0) {
                                        hashOffset = RandomUtil.getInt(-500, 500);
                                    }
                                    cases.add(new SwitchCase(switchValue.hashCode() + hashOffset, ASMUtils.getJumpInsns(firstBlockEntry)));
                                }
                            }
                            /* Gen switch and add to switch insns */
                            switchInsns.addAll(new LookupSwitchGenerator(cases).gen());
                            /* finally add switch to first block */
                            firstBlock.addAll(switchInsns);
                            break;
                        /* branch at default */
                        case 1:
                            throw new IllegalStateException("This switch branch should never be reached");
                        default:
                            throw new IllegalStateException("This switch branch should never be reached");
                    }
                    blocksCombined.addAll(firstBlock);

                    /* Second block */
                    secondBlock.add(secondBlockEntry);
                    switch (0) {
                        case 0:
                            /* actual jump */
                            secondBlock.add(new JumpInsnNode(realJumpInsn.getOpcode(), realJumpInsn.label));
                            secondBlock.addAll(ASMUtils.getJumpInsns(afterInsn));

                            if (RandomUtil.chance(30)) {
//                                secondBlock.addAll(ASMUtils.getCheckCastMessage("Why even try?", false));
//                                secondBlock.add(new VarInsnNode(ASTORE, RandomUtil.getInt(0, methodNode.maxLocals)));
                            }

                            /* bogus jumps */
//                            secondBlock.addAll(ASMUtils.getJumpInsns(ASMUtils.getRandomLabel(methodNode)));
                            break;
                        case 1:
                            throw new IllegalStateException("This switch branch should never be reached");
                        default:
                            throw new IllegalStateException("This switch branch should never be reached");
                    }
                    blocksCombined.addAll(secondBlock);

                    /* insert flow blocks */
                    methodNode.instructions.insert(insnNode.getPrevious(), ASMUtils.convertToIList(blocksCombined));
                    methodNode.instructions.insert(insnNode.getPrevious(), afterInsn);
                    methodNode.instructions.remove(insnNode);
                }
                methodNode.maxStack += 30;
                methodNode.maxLocals += 30;
                methodNode.instructions.insert(methodEnd, ASMUtils.convertToIList(proxyJumps));
            }
        }
        return true;
    }
}
