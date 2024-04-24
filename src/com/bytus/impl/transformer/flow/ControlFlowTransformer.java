package com.bytus.impl.transformer.flow;

import com.bytus.core.transformer.Transformer;
import com.bytus.utils.NameGen;
import me.exeos.asmplus.codegen.lookupswitch.LookupSwitchGenerator;
import me.exeos.asmplus.codegen.lookupswitch.SwitchCase;
import me.exeos.asmplus.utils.ASMUtils;
import me.exeos.asmplus.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

public class ControlFlowTransformer extends Transformer {

    @Override
    public boolean transform() {
        for (ClassNode classNode : getClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                NameGen sharedNameGen = new NameGen();
                /* Insert proxy jumps after method return */
                ArrayList<AbstractInsnNode> proxyJumps = new ArrayList<>();
                AbstractInsnNode methodEnd = ASMUtils.getMethodEnd(methodNode);
                if (methodEnd == null) {
                    System.out.println("Skipped " + classNode.name + "." + methodNode.name + methodNode.desc + " because couldn't find return insn");
                    continue;
                }
                int DELETE_ME = 0;
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (!ASMUtils.isJumpOrCondition(insnNode)) {
                        continue;
                    }

                    JumpInsnNode realJumpInsn = (JumpInsnNode) insnNode;
                    LabelNode afterInsn = new LabelNode();

                    /* blocks */
                    /* 1 */
                    LabelNode firstBlockEntry = new LabelNode();
                    LabelNode firstBlockSwitchEntry = new LabelNode();

                    /* 2 */
                    LabelNode secondBlockEntry = new LabelNode();
                    ArrayList<AbstractInsnNode> firstBlock = new ArrayList<>();
                    ArrayList<AbstractInsnNode> secondBlock = new ArrayList<>();
                    ArrayList<AbstractInsnNode> blocksCombined = new ArrayList<>();

                    /* proxy jumps */
                    /* Proxy jump works like this:
                    * jump original
                    * to
                    * condition proxy block1
                    * block1 proxy block2
                    * block2 original
                    *
                    * proxy jumps at method ends:
                    * condition block 1 entry
                    * condition block 2 entry
                    * */

                    LabelNode proxyFirstBlock = new LabelNode();
                    LabelNode proxySecondBlock = new LabelNode();
                    LabelNode proxyHashString = new LabelNode();

                    // proxy jump block 1
                    proxyJumps.add(proxyFirstBlock);
                    proxyJumps.addAll(ASMUtils.getDebugInsn("pf1_" + DELETE_ME));
                    proxyJumps.addAll(ASMUtils.getJump(firstBlockEntry));
                    // proxy jump block 2
                    proxyJumps.add(proxySecondBlock);
                    proxyJumps.addAll(ASMUtils.getDebugInsn("pf2_" + DELETE_ME));
                    proxyJumps.addAll(ASMUtils.getJump(secondBlockEntry));
                    // proxy to hash string
                    proxyJumps.add(proxyHashString);
                    proxyJumps.addAll(ASMUtils.getDebugInsn("pfSH_" + DELETE_ME));
                    proxyJumps.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I"));
                    proxyJumps.addAll(ASMUtils.getJump(firstBlockSwitchEntry));

                    /* First block */
                    firstBlock.addAll(ASMUtils.getJump(proxyFirstBlock));
                    firstBlock.add(firstBlockEntry);
                    switch (0) {
                        /* branch at case */
                        case 0:
                            int switchSize = RandomUtil.getInt(3, 5);
                            int branchIndex = RandomUtil.getInt(0, switchSize - 1);
                            ArrayList<AbstractInsnNode> switchInsns = new ArrayList<>();

                            /* Build switch value */
                            String switchValue = sharedNameGen.name();
                            switchInsns.add(new LdcInsnNode(switchValue));
                            /* Hash string */
                            switchInsns.addAll(ASMUtils.getJump(proxyHashString));
                            switchInsns.add(firstBlockSwitchEntry);

                            /* Switch cases */
                            ArrayList<SwitchCase> cases = new ArrayList<>();
                            for (int i = 0; i < switchSize; i++) {
                                if (i == branchIndex) {
                                    /* got to block 2 proxy */
                                    cases.add(new SwitchCase(switchValue.hashCode(), ASMUtils.getJump(proxySecondBlock)));
                                } else {
                                    /* do bogus code */
                                    int hashOffset = RandomUtil.getInt(-500, 500);
                                    while (hashOffset == 0) {
                                        hashOffset = RandomUtil.getInt(-500, 500);
                                    }
                                    cases.add(new SwitchCase(switchValue.hashCode() + hashOffset, ASMUtils.getJump(firstBlockEntry)));
                                }
                            }
                            /* Gen switch and add to switch insns */
                            switchInsns.addAll(new LookupSwitchGenerator(cases).gen());
                            /* finally add switch to first block */
                            firstBlock.addAll(switchInsns);
                            break;
                        /* branch at default */
                        case 1:
                            break;
                        default:
                            throw new IllegalStateException("This switch branch should never be reached");
                    }
                    blocksCombined.addAll(firstBlock);

                    /* Second block */
                    secondBlock.addAll(ASMUtils.getJump(proxySecondBlock));
                    secondBlock.add(secondBlockEntry);
                    switch (0) {
                        case 0:
                            secondBlock.addAll(ASMUtils.getDebugInsn("second_block_" + DELETE_ME));
                            secondBlock.add(new JumpInsnNode(realJumpInsn.getOpcode(), realJumpInsn.label));
                            secondBlock.addAll(ASMUtils.getJump(afterInsn));
                            break;
                        case 1:
                            break;
                        default:
                            throw new IllegalStateException("This switch branch should never be reached");
                    }
                    blocksCombined.addAll(secondBlock);

                    /* insert flow blocks */
                    methodNode.instructions.insert(insnNode.getPrevious(), ASMUtils.convertToIList(blocksCombined));
                    methodNode.instructions.insert(insnNode.getPrevious(), afterInsn);
                    methodNode.instructions.remove(insnNode);
                    DELETE_ME++;
                }
                methodNode.maxStack += 30;
                methodNode.maxLocals += 30;
                methodNode.instructions.insert(methodEnd, ASMUtils.convertToIList(proxyJumps));
            }
        }
        return true;
    }
}
