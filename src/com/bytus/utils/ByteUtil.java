package com.bytus.utils;

public class ByteUtil {

    public static byte[] mergeByteArrays(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }

        byte[] mergedArray = new byte[totalLength];

        int destPos = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, mergedArray, destPos, array.length);
            destPos += array.length;
        }

        return mergedArray;
    }
}
