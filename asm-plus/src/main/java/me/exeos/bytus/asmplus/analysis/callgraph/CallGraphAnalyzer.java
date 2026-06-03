package me.exeos.bytus.asmplus.analysis.callgraph;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CallGraphAnalyzer {

    public static MethodEdge analyze(JarArchive archive, MethodNode entryMethod) {
        MethodEdge entryEdge = new MethodEdge(entryMethod);
        analyze(archive, entryEdge);

        for (MethodEdge call : entryEdge.getCalls()) {
            call.calledBy.add(entryEdge);
        }

        return entryEdge;
    }

    private static void analyze(JarArchive archive, MethodEdge edge) {
        InsnUtil.loop(edge.getMethodNode().instructions, insnNode -> {
            if (insnNode instanceof MethodInsnNode methodInsnNode) {
                Optional<MethodNode> called = ClassUtil.findMethod(archive, methodInsnNode.owner, methodInsnNode.name, methodInsnNode.desc);
                if (called.isPresent()) {
                    MethodEdge newEdge = new MethodEdge(called.get());
                    analyze(archive, newEdge);
                    edge.addCalls(newEdge);
                }
            }
        });
    }

    private static void calledBy(MethodEdge edge) {
        for (MethodEdge call : edge.getCalls()) {
            call.calledBy.add(edge);
            calledBy(call);
        }
    }

    public static class MethodEdge {
        private final MethodNode methodNode;
        private final List<MethodEdge> calledBy = new ArrayList<>();
        private final List<MethodEdge> calls = new ArrayList<>();

        public MethodEdge(MethodNode methodNode) {
            this.methodNode = methodNode;
        }

        public void addCalledBy(MethodEdge me) {
            calledBy.add(me);
        }

        public void addCalls(MethodEdge me) {
            calls.add(me);
        }

        public MethodNode getMethodNode() {
            return methodNode;
        }

        public List<MethodEdge> getCalledBy() {
            return calledBy;
        }

        public List<MethodEdge> getCalls() {
            return calls;
        }
    }
}
