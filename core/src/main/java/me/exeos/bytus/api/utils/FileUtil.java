package me.exeos.bytus.api.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileUtil {

    public static byte[] readFileToByteArray(String filePath) throws IOException {
        File file = new File(filePath);
        byte[] fileData = new byte[(int) file.length()];

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(fileData);
        }

        return fileData;
    }

    public static void writeByteArrayToFile(byte[] data, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(data);
        }
    }
}
