import java.lang.invoke.*;

public class IndyBootstrapMethod {

    public static CallSite bootstrap(MethodHandles.Lookup lookup,
                                     String invokedName,
                                     MethodType invokedType,
                                     String owner,
                                     String name,
                                     String desc,
                                     String caller,
                                     int type) {

        try {
            ClassLoader classLoader = lookup.lookupClass().getClassLoader();
            Class<?> ownerClass = Class.forName(owner, true, classLoader);
            MethodType methodType = MethodType.fromMethodDescriptorString(desc, classLoader);

            MethodHandle target = null;
            switch (type) {
                case 0:
                    target = lookup.findStatic(ownerClass, name, methodType);
                    break;
                case 1:
                    target = lookup.findVirtual(ownerClass, name, methodType);
                    break;
                case 2:
                    if (name.equals("<init>")) {
                        target = lookup.findConstructor(ownerClass, methodType.changeReturnType(void.class));
                    } else {
                        target = lookup.findSpecial(ownerClass, name, methodType, Class.forName(caller, true, classLoader));
                    }
                    break;
                case 3:
                case 4:
                case 5:
                case 6:
                    Class<?> fieldType = ownerClass.getDeclaredField(name).getType();
                    target = switch (type) {
                        case 3 -> lookup.findStaticGetter(ownerClass, name, fieldType);
                        case 4 -> lookup.findStaticSetter(ownerClass, name, fieldType);
                        case 5 -> lookup.findGetter(ownerClass, name, fieldType);
                        case 6 -> lookup.findSetter(ownerClass, name, fieldType);
                        default -> target;
                    };
                    break;
                default:
                    throw new IllegalStateException();
            }

            return new ConstantCallSite(target.asType(invokedType));
        } catch (Exception e) {
            throw new RuntimeException("Dynamic method invocation failed.");
        }
    }
}
