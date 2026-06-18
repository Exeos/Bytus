package me.exeos.bytus.asmplus.utils;

import me.exeos.bytus.asmplus.analysis.hierarchy.edge.ClassEdge;

import java.util.*;
import java.util.function.Function;

public class HierarchyUtil {

    public static String genNoneCollidingFieldName(ClassEdge owner, String fieldDesc, Function<Integer, String> nameGen) {
        String name;
        int tryCount = 1;
        do {
            name = nameGen.apply(tryCount);
            tryCount++;
        } while (owner.findNearestField(name, fieldDesc).isPresent());

        return name;
    }

    public static String genNoneCollidingMethodName(ClassEdge owner, String methodDesc, Function<Integer, String> nameGen) {
        return genNoneCollidingMethodName(owner, methodDesc, Set.of(), nameGen);
    }

    public static String genNoneCollidingMethodName(ClassEdge owner, String methodDesc, Set<String> excludedNames, Function<Integer, String> nameGen) {
        String name;
        int tryCount = 1;
        do {
            name = nameGen.apply(tryCount);
            tryCount++;
        } while (excludedNames.contains(name) || owner.findNearestMethod(name, methodDesc).isPresent());

        return name;
    }
}
