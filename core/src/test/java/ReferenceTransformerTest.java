import java.lang.invoke.*;

public class ReferenceTransformerTest {

    // TODO: das machen

    public static String[] methodSignatures;

    public static String decryptString(String string) {
        return string;
    }

    public static int getIndex(long l1, long l2) {
        return (int) ((l1 ^ l2) % methodSignatures.length);
    }

    public static CallSite bootstrap(MethodHandles.Lookup lookup, String ignored, MethodType methodType, long l1, long l2) {
        try {
            int index = getIndex(l1, l2);
            String methodSignature = decryptString(methodSignatures[index]);
            String memberDesc = methodSignature.split("#")[0];
            String accessCode = methodSignature.split("#")[1];
            String memberName = methodSignature.split("#")[2];
            String className = methodSignature.split("#")[3].replace("/", ".");

            Class<?> clazz = Class.forName(className);

            MethodType correctType = MethodType.fromMethodDescriptorString(memberDesc,
                    clazz.getClassLoader());

            MethodHandle handle = null;

            if (accessCode.equals("182") || accessCode.equals("184")) {
                if (accessCode.equals("182")) {
                    handle = lookup.findVirtual(clazz, memberName, correctType).asType(methodType);
                } else {
                    handle = lookup.findStatic(clazz, memberName, correctType).asType(methodType);
                }
            }

            if (handle == null)
                return null;

            return new MutableCallSite(handle);
        } catch (Exception e) {
            System.out.println("e -> " + e.getMessage());
            return null;
        }
    }

}
