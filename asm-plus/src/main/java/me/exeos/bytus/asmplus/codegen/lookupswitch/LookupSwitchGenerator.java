package me.exeos.bytus.asmplus.codegen.lookupswitch;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.Comparator;
import java.util.List;


public class LookupSwitchGenerator {

    public static InsnList gen(List<SwitchCase> cases) {
        return gen(cases, null);
    }

    public static InsnList gen(List<SwitchCase> cases, SwitchCase dfltCase) {
        return gen(cases, dfltCase, true);
    }

    public static InsnList gen(List<SwitchCase> cases, SwitchCase dfltCase, boolean gotoSwitchEnd) {
        return gen(cases, dfltCase, gotoSwitchEnd, false);
    }

    /**
     * Generate a new lookup switch
     * @return Lookup switch instructions
     */
    public static InsnList gen(List<SwitchCase> cases, SwitchCase dfltCase, boolean gotoSwitchEnd, boolean casesOnly) {
        cases.sort(Comparator.comparingInt(o -> o.key));

        InsnList switchInsns = new InsnList();
        LabelNode switchEnd = new LabelNode();

        switchInsns.add(new LookupSwitchInsnNode(dfltCase != null ? dfltCase.caseStart : switchEnd, getKeys(cases), getLabels(cases)));
        for (SwitchCase switchCase : cases) {
            switchInsns.add(switchCase.caseStart);
            switchInsns.add(switchCase.instructions);
            if (gotoSwitchEnd) {
                switchInsns.add(new JumpInsnNode(Opcodes.GOTO, switchEnd));
            }
        }
        if (dfltCase != null) {
            switchInsns.add(dfltCase.caseStart);
            switchInsns.add(dfltCase.instructions);
            if (gotoSwitchEnd) {
                switchInsns.add(new JumpInsnNode(Opcodes.GOTO, switchEnd));
            }
        }

        if (gotoSwitchEnd || dfltCase == null) {
            switchInsns.add(switchEnd);
        }

        return switchInsns;
    }

    private static int[] getKeys(List<SwitchCase> cases) {
        int[] keys = new int[cases.size()];

        for (int i = 0; i < cases.size(); i++) {
            keys[i] = cases.get(i).key;
        }

        return keys;
    }

    private static LabelNode[] getLabels(List<SwitchCase> cases) {
        LabelNode[] labels = new LabelNode[cases.size()];

        for (int i = 0; i < cases.size(); i++) {
            labels[i] = cases.get(i).caseStart;
        }

        return labels;
    }
}
