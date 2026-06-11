package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.JarUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.Set;

public class RCT2 extends AbstractTransformer {

    public RCT2(BytusConfig config) {
        super(config);
    }

    @Override
    public boolean applies() {
        return true;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public void transform(JarContext context) {
        addBootstrap(context);
    }

    @Override
    public void transform(MethodContext context) {
        super.transform(context);
    }

    private void addBootstrap(JarContext context) {
        ClassNode container = JarUtil.getRandomClass(context.jar());

        /*
        CallSite bootstrap(MethodHandles.Lookup lookup,
                                     String invokedName,
                                     MethodType invokedType,
                                     String owner,
                                     String name,
                                     String desc,
                                     String caller,
                                     int type)
         */
        MethodNode bsm = new MethodNode(
                ACC_PUBLIC | ACC_STATIC,
                ClassUtil.getNoneCollidingClassName(context.jar(), RandomUtil::getString),
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/invoke/CallSite;",
                null, null
        );

        final int lookupSlot = 0;
        final int invokedTypeSlot = 2;
        final int ownerSlot = 3;
        final int nameSlot = 4;
        final int descSlot = 5;
        final int callerSlot = 6;
        final int typeSlot = 7;

        final int classLoaderSlot = 8;
        final int ownerClassSlot = 9;
        final int methodTypeSlot = 10;
        final int targetMHandleSlot = 11;

        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        bsm.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Exception"));

        bsm.instructions.add(start);
        // store lookup.lookupClass().getClassLoader() in classLoaderSlot
        bsm.instructions.add(new VarInsnNode(ALOAD, lookupSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "lookupClass", "()Ljava/lang/Class"));
        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getClassLoader", "()Ljava/lang/ClassLoader"));
        bsm.instructions.add(new VarInsnNode(ASTORE, classLoaderSlot));

        // store Class.forName(owner, true, classLoader) in ownerClassSlot
        bsm.instructions.add(new VarInsnNode(ALOAD, ownerSlot));
        bsm.instructions.add(new InsnNode(ICONST_1));
        bsm.instructions.add(new VarInsnNode(ALOAD, classLoaderSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"));
        bsm.instructions.add(new VarInsnNode(ASTORE, ownerClassSlot));

        // store MethodType.fromMethodDescriptorString(desc, classLoader) in methodTypeSlot
        bsm.instructions.add(new VarInsnNode(ALOAD, descSlot));
        bsm.instructions.add(new VarInsnNode(ALOAD, classLoaderSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKESTATIC, "java/lang/invoke/MethodType", "fromMethodDescriptorString", "(Ljava/lang/String;Ljava/lang/ClassLoader;)Ljava/lang/invoke/MethodType;"));
        bsm.instructions.add(new VarInsnNode(ALOAD, methodTypeSlot));

        // declare MethodHandle target = null;
        bsm.instructions.add(new InsnNode(ACONST_NULL));
        bsm.instructions.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        // switch on provided type
        bsm.instructions.add(new VarInsnNode(ILOAD, typeSlot));

        bsm.instructions.add(end);

        bsm.instructions.add(handler);


        container.methods.add(bsm);
        context.pipeline().emit(new MethodContext(new ClassContext(context, container), bsm), Set.of(RCT2.class));
    }
}
