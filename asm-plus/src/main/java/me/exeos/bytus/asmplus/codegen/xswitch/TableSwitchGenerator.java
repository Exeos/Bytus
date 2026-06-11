package me.exeos.bytus.asmplus.codegen.xswitch;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;

import java.util.List;

public class TableSwitchGenerator {

    public static InsnList gen(List<SwitchCase> cases, int min, int max) {
        return gen(cases, min, max, null);
    }

    public static InsnList gen(List<SwitchCase> cases, int min, int max, SwitchCase defaultCase) {
        return gen(cases, min, max, defaultCase, true);
    }

    public static InsnList gen(List<SwitchCase> cases, int min, int max, SwitchCase defaultCase, boolean gotoSwitchEnd) {
        InsnList switchInsn = new InsnList();
        LabelNode switchEnd = new LabelNode();

        switchInsn.add(new TableSwitchInsnNode(min, max, defaultCase == null ? switchEnd : defaultCase.caseStart, LookupSwitchGenerator.getLabels(cases)));
        for (SwitchCase switchCase : cases) {
            switchInsn.add(LookupSwitchGenerator.getCaseInsn(switchCase, gotoSwitchEnd, switchEnd));
        }

        if (defaultCase != null) {
            switchInsn.add(LookupSwitchGenerator.getCaseInsn(defaultCase, gotoSwitchEnd, switchEnd));
        }

        if (defaultCase == null || gotoSwitchEnd) {
            switchInsn.add(switchEnd);
        }

        return switchInsn;
    }
}
