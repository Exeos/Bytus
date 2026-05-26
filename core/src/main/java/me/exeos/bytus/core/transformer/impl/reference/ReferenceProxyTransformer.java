package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Arrays;
import java.util.List;

public final class ReferenceProxyTransformer extends Transformer {

    private final int minDepth, maxDepth;

    public ReferenceProxyTransformer(JarArchive jar, List<String> exclusions, List<String> inclusions, int minDepth, int maxDepth) {
        super(jar, exclusions, inclusions);
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        for (ClassNode classNode : getIncludedClasses()) {
            if ((classNode.access & ACC_INTERFACE) != 0) continue;

            for (MethodNode methodNode : classNode.methods.toArray(new MethodNode[0])) {
                for (AbstractInsnNode insnNode : methodNode.instructions.toArray()) {
                    if (!(insnNode instanceof MethodInsnNode methodInsnNode)) continue;

                    if (methodInsnNode.getOpcode() != INVOKESTATIC
                            && methodInsnNode.getOpcode() != INVOKEVIRTUAL
                            && methodInsnNode.getOpcode() != INVOKEINTERFACE)
                        continue;

                    MethodNode[] proxyMethods = this.generateProxyMethods(classNode, methodInsnNode);
                    MethodNode firstProxy = proxyMethods[0];

                    // replace the methodInsnNode with the call to the first proxy
                    methodNode.instructions.set(methodInsnNode, new MethodInsnNode(
                            INVOKESTATIC,
                            classNode.name,
                            firstProxy.name,
                            firstProxy.desc,
                            false
                    ));

                    classNode.methods.addAll(Arrays.asList(proxyMethods));
                }
            }
        }
    }

    // TODO: add more stuff into the proxy methods, maybe move more logic into instead of just the actual call
    private MethodNode[] generateProxyMethods(ClassNode classNode, MethodInsnNode methodInsnNode) {
        int proxyAmount = RandomUtil.getInt(Math.max(1, this.minDepth), Math.max(1, this.maxDepth + 1));
        MethodNode[] proxyMethods = new MethodNode[proxyAmount];

        String proxyDesc = methodInsnNode.desc;
        if (methodInsnNode.getOpcode() != INVOKESTATIC) {
            proxyDesc = proxyDesc.replace("(", "(" + Type.getObjectType(methodInsnNode.owner).getDescriptor());
        }

        Type returnType = Type.getReturnType(proxyDesc);
        Type[] argumentTypes = Type.getArgumentTypes(proxyDesc);

        // build call chain: proxy[0] -> proxy[1] -> ... -> proxy[n-1] -> original
        for (int i = proxyAmount - 1; i >= 0; i--) {
            MethodNode proxyMethod = new MethodNode(
                    ACC_PRIVATE | ACC_STATIC,
                    RandomUtil.getString(RandomUtil.getInt(12, 32)),
                    proxyDesc,
                    null,
                    null
            );

            int var = 0;
            for (Type argType : argumentTypes) {
                proxyMethod.instructions.add(new VarInsnNode(argType.getOpcode(ILOAD), var));
                var += argType.getSize();
            }

            if (i == proxyAmount - 1) {
                // last calls the original
                proxyMethod.instructions.add(new MethodInsnNode(
                        methodInsnNode.getOpcode(),
                        methodInsnNode.owner,
                        methodInsnNode.name,
                        methodInsnNode.desc,
                        methodInsnNode.itf
                ));
            } else {
                MethodNode nextProxy = proxyMethods[i + 1];
                proxyMethod.instructions.add(new MethodInsnNode(
                        INVOKESTATIC,
                        classNode.name,
                        nextProxy.name,
                        nextProxy.desc,
                        false
                ));
            }

            proxyMethod.instructions.add(new InsnNode(returnType.getOpcode(IRETURN)));
            proxyMethods[i] = proxyMethod;
        }

        return proxyMethods;
    }
}
