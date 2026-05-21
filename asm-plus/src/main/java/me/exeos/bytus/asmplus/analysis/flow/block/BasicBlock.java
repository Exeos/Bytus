package me.exeos.bytus.asmplus.analysis.flow.block;

import org.objectweb.asm.tree.AbstractInsnNode;

import java.util.*;

public class BasicBlock {

    public List<AbstractInsnNode> instructions = new ArrayList<>();

    /**
     * All possible immediate following blocks after this Block
     */
    public Set<BasicBlock> successors = new HashSet<>();

    /**
     * Immediate following blocks, excluding exceptions
     */
    public Set<BasicBlock> normalSuccessors = new HashSet<>();

    /**
     * All immediate blocks before this Block
     */
    public Set<BasicBlock> predecessors = new HashSet<>();

    /**
     * Maps instructions that are inside try catch block protected regions to their handler blocks
     */
    public Map<AbstractInsnNode, HashSet<BasicBlock>> exceptionDispatchMap = new HashMap<>();

}
