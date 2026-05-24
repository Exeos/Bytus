package me.exeos.bytus.asmplus.utils;

public class TypeUtil {

    public static String primitiveToClass(char primitive) {
        return switch (primitive) {
            case 'B' -> "java/lang/Byte";
            case 'C' -> "java/lang/Character";
            case 'D' -> "java/lang/Double";
            case 'F' -> "java/lang/Float";
            case 'I' -> "java/lang/Integer";
            case 'J' -> "java/lang/Long";
            case 'S' -> "java/lang/Short";
            case 'Z' -> "java/lang/Boolean";
            default -> throw new IllegalArgumentException("Provided char isn't valid primitive: " + primitive);
        };
    }

    public static String clsInstanceToPrimMethodName(char primitive) {
        return switch (primitive) {
            case 'B' -> "byteValue";
            case 'C' -> "charValue";
            case 'D' -> "doubleValue";
            case 'F' -> "floatValue";
            case 'I' -> "intValue";
            case 'J' -> "longValue";
            case 'S' -> "shortValue";
            case 'Z' -> "booleanValue";
            default -> throw new IllegalArgumentException("Provided char isn't valid primitive: " + primitive);
        };
    }

}
