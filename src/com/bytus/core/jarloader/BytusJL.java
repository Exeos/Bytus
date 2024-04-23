package com.bytus.core.jarloader;

import com.bytus.Bytus;
import com.bytus.Config;
import com.bytus.utils.ByteUtil;
import com.bytus.utils.CryptUtil;
import com.bytus.utils.FileUtil;
import me.exeos.asmplus.JarLoader;
import me.exeos.asmplus.utils.RandomUtil;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarOutputStream;

public class BytusJL extends JarLoader {

    @Override
    public void load(String inputPath) throws IOException {
        super.load(inputPath);
    }

    @Override
    public void export(String outputPath) throws Exception {
        if (!new File(new File(outputPath).getParent()).mkdirs()) {
            System.out.println("Failed to mkdirs");
        }
        JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(outputPath));
        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassNode classNode = entry.getValue();
            classNode.accept(classWriter);
            byte[] classBytes = classWriter.toByteArray();
            if (Config.DO_ENC && !classNode.name.equals(Config.BOOTLOADER_NAME) && !classNode.name.equals(Config.ENCRYPTOR_CNAME)) {
                KeyPair signKeyPair = CryptUtil.generateKeyPair();

                byte[] cryptKey = CryptUtil.genKey(32);
                byte[] signature = CryptUtil.signData(classBytes, signKeyPair.getPrivate());
                byte[] integrityKey = signKeyPair.getPublic().getEncoded();
                byte[] rawEncData = CryptUtil.encryptData(classBytes, cryptKey);

                writeJarEntry(jarOut, classNode.name + ".bytus", ByteUtil.mergeByteArrays(cryptKey, signature, integrityKey, rawEncData));
            } else {
                writeJarEntry(jarOut, classNode.name + ".class", classWriter.toByteArray());
            }
        }

        for (Map.Entry<String, byte[]> e : resources.entrySet()) {
            writeJarEntry(jarOut, e.getKey(), e.getValue());
        }

        jarOut.finish();

        if (Config.DO_ENC) {
            byte[] zipStart = new byte[] {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04};
            byte[] zipContent = FileUtil.readFileToByteArray("C:\\Users\\valentin\\Desktop\\coding\\java\\Bytus\\jars\\jvm.zip");
            byte[] zipEnd = new byte[] {(byte) 0x50, (byte) 0x4B, (byte) 0x05, (byte) 0x06};

            byte[] jar = FileUtil.readFileToByteArray(outputPath);
            byte[] combined = ByteUtil.mergeByteArrays(zipContent, jar);

//            byte[] prefix = new byte[zipStart.length + zipEnd.length];
//            byte[] combined = new byte[prefix.length + jar.length];
//            System.arraycopy(prefix, 0, combined, 0, prefix.length);
//            System.arraycopy(jar, 0, combined, prefix.length, jar.length);

            FileUtil.writeByteArrayToFile(combined, outputPath);
        }

        BasicFileAttributeView attributeView = Files.getFileAttributeView(Paths.get(outputPath), BasicFileAttributeView.class);
        attributeView.setTimes(lastModifiedTime, null, creationTime);
    }

    public static HashMap<String, ClassNode> getClasses() {
        return Bytus.INSTANCE.core.jarLoader.classes;
    }

    public static HashMap<String, byte[]> getResources() {
        return Bytus.INSTANCE.core.jarLoader.resources;
    }

    public static void putClass(String mapping, ClassNode classNode) {
        Bytus.INSTANCE.core.jarLoader.classes.put(mapping, classNode);
    }

    public static void putResource(String mapping, byte[] data) {
        Bytus.INSTANCE.core.jarLoader.resources.put(mapping, data);
    }

    public static void remClass(String mapping) {
        Bytus.INSTANCE.core.jarLoader.classes.remove(mapping);
    }

    public static void remResource(String mapping) {
        Bytus.INSTANCE.core.jarLoader.resources.remove(mapping);
    }
}
