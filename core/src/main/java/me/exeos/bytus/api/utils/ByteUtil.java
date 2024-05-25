package me.exeos.bytus.api.utils;

import me.exeos.bytus.Bytus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

    public static byte[] readResourceAsBytes(String resourcePath, Class<?> klass) throws IOException {
        try (InputStream in = klass.getResourceAsStream(resourcePath)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int data;
            while ((data = in.read()) != -1) {
                buffer.write(data);
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
