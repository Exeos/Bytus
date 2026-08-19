package me.exeos.bytus.core.transformer.impl.flow.data;

import me.exeos.asmplus.analysis.hierarchy.edge.ClassEdge;
import me.exeos.asmplus.analysis.hierarchy.edge.MethodEdge;
import me.exeos.asmplus.codegen.value.impl.ConstantPusher;
import me.exeos.asmplus.descriptor.DescriptorMember;
import me.exeos.asmplus.descriptor.DescriptorParser;
import me.exeos.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.asmplus.utils.InsnUtil;
import me.exeos.asmplus.utils.TypeUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.JarContext;
import org.objectweb.asm.tree.*;

import java.util.HashSet;
import java.util.Set;

public class ParamGenerify extends AbstractTransformer {

    private final static String OBJ_ARR_DESC = "([Ljava/lang/Object;)";

    public ParamGenerify(BytusConfig config) {
        super(config);
    }

    void a(Object o) {

    }

    void a(Object a, int b) {

    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void transform(JarContext context) {
        Set<String> needRefactoring = rewriteDescriptorsAndCollect(context);
        rewriteCallsites(context, needRefactoring);
    }

    private Set<String> rewriteDescriptorsAndCollect(JarContext context) {
        Set<String> needRefactoring = new HashSet<>();
        for (ClassNode classNode : context.jar().getClasses().values()) {
            ClassEdge classEdge = context.getExtension().getHierarchy().get(classNode);
            if (classEdge == null || classEdge.hasUnresolved()) {
                continue;
            }

            for (MethodEdge methodEdge : classEdge.methods) {
                if (!isValidGroup(methodEdge)) {
                    continue;
                }

                for (MethodEdge oge : methodEdge.getOverrideGroup()) {
                    // add to set of ids that need to be refactored (original desc)
                    needRefactoring.add(classNode.name + oge.getName() + oge.getDesc());
                    // update method desc
                    oge.methodNode().desc = generifyDesc(oge.getDesc());
                }
            }
        }

        return needRefactoring;
    }

    private void rewriteCallsites(JarContext context, Set<String> needRefactoring) {
        for (ClassNode classNode : context.jar().getClasses().values()) {
            for (MethodNode methodNode : classNode.methods) {
                int arrLocal = methodNode.maxLocals++;
                int tempStore = methodNode.maxLocals++;
                InsnUtil.loop(methodNode.instructions, insnNode -> {
                    if (insnNode instanceof MethodInsnNode methodInsnNode && needRefactoring.contains(methodInsnNode.owner + methodInsnNode.name + methodInsnNode.desc)) {
                        MethodDescriptor methodDescriptor = DescriptorParser.parseMethodDesc(methodInsnNode.desc);
                        InsnList packInsn = new InsnList();

                        packInsn.add(ConstantPusher.getIntPush(methodDescriptor.getParams().size()));
                        packInsn.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
                        packInsn.add(new VarInsnNode(ASTORE, arrLocal));

                        for (int i = methodDescriptor.getParams().size() - 1; i >= 0; i--) {
                            DescriptorMember param = methodDescriptor.getParams().get(i);
                            if (param.isPrimitive()) {
                                // box native
                                String primClassName = TypeUtil.primitiveToClass(param.getValue().charAt(0));
                                packInsn.add(new MethodInsnNode(INVOKESTATIC, primClassName, "valueOf", "(" + param.getValue() + ")L" + primClassName + ";"));
                            }

                            packInsn.add(new VarInsnNode(ASTORE, tempStore));

                            packInsn.add(new VarInsnNode(ALOAD, arrLocal));
                            packInsn.add(ConstantPusher.getIntPush(i));
                            packInsn.add(new VarInsnNode(ALOAD, tempStore));
                            packInsn.add(new InsnNode(AASTORE));
                        }

                        packInsn.add(new VarInsnNode(ALOAD, arrLocal));
                        methodNode.instructions.insertBefore(methodInsnNode, packInsn);
                        methodInsnNode.desc = generifyDesc(methodInsnNode.desc);
                    }
                });
            }
        }
    }

    private String generifyDesc(String desc) {
        String returnDesc = DescriptorParser.parseMethodDesc(desc).getReturnType().toDesc();
        return OBJ_ARR_DESC + returnDesc;
    }

    private boolean isValidGroup(MethodEdge root) {
        for (MethodEdge methodEdge : root.getOverrideGroup()) {
            if (methodEdge.owner().hasUnresolved() || nameCollides(methodEdge)) {
                return false;
            }
        }

        return true;
    }

    private boolean nameCollides(MethodEdge methodEdge) {
        Set<MethodEdge> sameName = methodEdge.owner().findAllMethods(methodEdge.getName());
        return sameName.size() > 1;
    }
}
