package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.descriptor.DescriptorMember;
import org.objectweb.asm.Opcodes;

public class TypeUtil implements Opcodes {

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

    public static int loadOpcodeForType(DescriptorMember member) {
        if (!member.isPrimitive() || member.isArray()) {
            return ALOAD;
        }

        return switch (member.value().charAt(0)) {
            case 'J' -> LLOAD;
            case 'D' -> DLOAD;
            case 'F' -> FLOAD;
            default  -> ILOAD;
        };
    }

    public static int storeOpcodeForType(DescriptorMember member) {
        if (!member.isPrimitive() || member.isArray()) {
            return ASTORE;
        }

        return switch (member.value().charAt(0)) {
            case 'J' -> LSTORE;
            case 'D' -> DSTORE;
            case 'F' -> FSTORE;
            default  -> ISTORE;
        };
    }
}
