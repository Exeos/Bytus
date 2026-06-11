package me.exeos.bytus.core.transformer.impl.reference;

import me.exeos.bytus.asmplus.codegen.xswitch.SwitchCase;
import me.exeos.bytus.asmplus.codegen.xswitch.TableSwitchGenerator;
import me.exeos.bytus.asmplus.utils.ClassUtil;
import me.exeos.bytus.asmplus.utils.InsnUtil;
import me.exeos.bytus.asmplus.utils.JarUtil;
import me.exeos.bytus.asmplus.utils.MethodUtil;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.AbstractTransformer;
import me.exeos.bytus.core.transformer.context.ClassContext;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.context.MethodContext;
import me.exeos.bytus.core.utils.RandomUtil;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
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
        System.out.println(container.name);

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
        bsm.instructions.add(new VarInsnNode(ASTORE, methodTypeSlot));

        // declare MethodHandle target = null;
        bsm.instructions.add(new InsnNode(ACONST_NULL));
        bsm.instructions.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        // switch on provided type
        bsm.instructions.add(new VarInsnNode(ILOAD, typeSlot));
        LabelNode switchEnd = new LabelNode();
        SwitchCase fieldHandler = fieldHandlerDispatcher(lookupSlot, nameSlot, callerSlot, typeSlot, classLoaderSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd);
        bsm.instructions.add(TableSwitchGenerator.gen(List.of(
                staticMethodHandler(lookupSlot, nameSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd),
                virtualMethodHandler(lookupSlot, nameSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd),
                specialOrConstructorHandler(lookupSlot, nameSlot, callerSlot, classLoaderSlot, ownerClassSlot, methodTypeSlot, targetMHandleSlot, switchEnd),
                fieldHandler,
                fieldHandler,
                fieldHandler,
                fieldHandler
        ), 0, 6, defaultHandler(), false, switchEnd));
        bsm.instructions.add(switchEnd);

        bsm.instructions.add(new TypeInsnNode(NEW, "java/lang/invoke/ConstantCallSite"));
        bsm.instructions.add(new InsnNode(DUP));
        bsm.instructions.add(new VarInsnNode(ALOAD, targetMHandleSlot));
        bsm.instructions.add(new VarInsnNode(ALOAD, invokedTypeSlot));
        bsm.instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "asType", "(Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        bsm.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/invoke/ConstantCallSite", "<init>", "(Ljava/lang/invoke/MethodHandle;)V"));
        bsm.instructions.add(end);
        bsm.instructions.add(new InsnNode(ARETURN));

        bsm.instructions.add(handler);
        bsm.instructions.add(new VarInsnNode(ASTORE, classLoaderSlot));
        bsm.instructions.add(new TypeInsnNode(NEW, "java/lang/RuntimeException"));
        bsm.instructions.add(new InsnNode(DUP));
        bsm.instructions.add(new LdcInsnNode("Dynamic method invocation failed"));
        bsm.instructions.add(new MethodInsnNode(INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "(Ljava/lang/String;)V"));
        bsm.instructions.add(new InsnNode(ATHROW));

        context.pipeline().emit(new MethodContext(new ClassContext(context, container), bsm), Set.of(RCT2.class));
    }

    private SwitchCase staticMethodHandler(int lookupSlot, int nameSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findStatic", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase virtualMethodHandler(int lookupSlot, int nameSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findVirtual", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase specialOrConstructorHandler(int lookupSlot, int nameSlot, int callerSlot, int classLoaderSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new LdcInsnNode("<init>"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
        LabelNode constructorHandler = new LabelNode();
        caseInsn.add(new JumpInsnNode(IFEQ, constructorHandler));
        // handle special
        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new FieldInsnNode(GETSTATIC, "java/lang/Void", "TYPE", "Ljava/lang/Class;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodType", "changeReturnType", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", "findConstructor", "(Ljava/lang/Class;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));
        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        caseInsn.add(constructorHandler);
        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, methodTypeSlot));
        caseInsn.add(new VarInsnNode(ALOAD, callerSlot));
        caseInsn.add(new InsnNode(ICONST_1));
        caseInsn.add(new VarInsnNode(ALOAD, classLoaderSlot));
        caseInsn.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Class" ,"forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup" ,"findSpecial", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase fieldHandlerDispatcher(int lookupSlot, int nameSlot, int callerSlot, int typeSlot, int classLoaderSlot, int ownerClassSlot, int methodTypeSlot, int targetMHandleSlot, LabelNode switchEnd) {
        InsnList caseInsn = new InsnList();

        int fieldTypeSlot = 12;

        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;"));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/reflect/Field", "getType", "()Ljava/lang/Class;"));
        caseInsn.add(new VarInsnNode(ASTORE, fieldTypeSlot));

        caseInsn.add(new VarInsnNode(ILOAD, typeSlot));
        caseInsn.add(TableSwitchGenerator.gen(List.of(
                fieldHandler(3, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot),
                fieldHandler(4, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot),
                fieldHandler(5, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot),
                fieldHandler(6, lookupSlot, nameSlot, ownerClassSlot, targetMHandleSlot, fieldTypeSlot)
        ), 3, 6));
        caseInsn.add(new JumpInsnNode(GOTO, switchEnd));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase fieldHandler(int type, int lookupSlot, int nameSlot, int ownerClassSlot, int targetMHandleSlot, int fieldTypeSlot) {
        InsnList caseInsn = new InsnList();

        String methodName = switch (type) {
            case 3 -> "findStaticGetter";
            case 4 -> "findStaticSetter";
            case 5 -> "findGetter";
            case 6 -> "findSetter";
            default -> throw new IllegalStateException("Invalid type");
        };

        caseInsn.add(new VarInsnNode(ALOAD, lookupSlot));
        caseInsn.add(new VarInsnNode(ALOAD, ownerClassSlot));
        caseInsn.add(new VarInsnNode(ALOAD, nameSlot));
        caseInsn.add(new VarInsnNode(ALOAD, fieldTypeSlot));
        caseInsn.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/invoke/MethodHandles$Lookup", methodName, "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;"));
        caseInsn.add(new VarInsnNode(ASTORE, targetMHandleSlot));

        return new SwitchCase(0, caseInsn);
    }

    private SwitchCase defaultHandler() {
        InsnList caseInsn = new InsnList();
        caseInsn.add(MethodUtil.endMethodByThrow());
        return new SwitchCase(0, caseInsn);
    }
}
