package me.exeos.bytus.core.transformer.impl.flow;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import me.exeos.bytus.asmplus.descriptor.DescriptorParser;
import me.exeos.bytus.asmplus.descriptor.descriptors.method.MethodDescriptor;
import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import org.objectweb.asm.tree.*;

import java.util.*;

public class ParamGenerifier extends Transformer {

    public ParamGenerifier(JarArchive jar, List<String> exclusions, List<String> inclusions) {
        super(jar, exclusions, inclusions);
    }

    @Override
    public void transform(TransformerPipeline pipeline) {
        Map<String, String> methodDescMapping = new HashMap<>();

        for (ClassNode classNode : getIncludedClasses()) {
            for (MethodNode methodNode : classNode.methods) {
                // parse desc
                MethodDescriptor descriptor = DescriptorParser.parseMethodDesc(methodNode.desc);

                Map<Integer, String> replaceMap = new HashMap<>();
                for (int i = 0; i < descriptor.args.size(); i++) {
                    DescriptorMember arg = descriptor.args.get(i);
                    if (arg.isPrimitive) {
                        continue;
                    }

                    replaceMap.put(i, arg.value);
                    arg.value = "java/lang/Object";
                }

                for (ListIterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
                    AbstractInsnNode insnNode = it.next();
                    if (insnNode instanceof VarInsnNode varInsnNode && InsnUtil.isParamLoad(insnNode)) {
                        methodNode.instructions.insert(insnNode, new TypeInsnNode(CHECKCAST, replaceMap.get(varInsnNode.var)));
                    }
                }
            }
        }
    }
}
