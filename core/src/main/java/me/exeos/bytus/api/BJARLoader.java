package me.exeos.bytus.api;

import me.exeos.asmplus.JarLoader;
import me.exeos.bytus.api.config.ConfigInterface;
import me.exeos.bytus.api.utils.ByteUtil;
import me.exeos.bytus.api.utils.CryptUtil;
import me.exeos.bytus.api.utils.FileUtil;
import me.exeos.bytus.api.utils.NumberSysUtil;
import me.exeos.bytus.transformers.packer.ClassEncryptionTransformer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.KeyPair;
import java.util.Map;
import java.util.jar.JarOutputStream;


public class BJARLoader extends JarLoader implements ConfigInterface {

    @Override
    public void export(String outputPath) throws Exception {
        new File(new File(outputPath).getParent()).mkdirs();
        JarOutputStream jarOut = new JarOutputStream(new FileOutputStream(outputPath));
        for (Map.Entry<String, ClassNode> entry : classes.entrySet()) {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            ClassNode classNode = entry.getValue();
            classNode.accept(classWriter);

            String className = classNode.name;
            byte[] classBytes = classWriter.toByteArray();

            if (isPackEnabled() && !className.equals(getBootstrapName()) && !className.equals(ClassEncryptionTransformer.clName)) {
                KeyPair signKeyPair = CryptUtil.generateKeyPair();

                byte[] cryptKey = CryptUtil.genKey(32);
                byte[] signature = CryptUtil.signData(classBytes, signKeyPair.getPrivate());
                byte[] integrityKey = signKeyPair.getPublic().getEncoded();
                byte[] rawEncData = CryptUtil.encryptData(classBytes, cryptKey);

                className = classNode.name.replace("/", ".").hashCode() + ClassEncryptionTransformer.SUFFIX;
                classBytes = ByteUtil.mergeByteArrays(cryptKey, signature, integrityKey, rawEncData);
            } else {
                className = className + ".class";
            }

            writeJarEntry(jarOut, className, classBytes);
        }

        for (Map.Entry<String, byte[]> e : resources.entrySet()) {
            writeJarEntry(jarOut, e.getKey(), e.getValue());
        }

        jarOut.finish();

        /* exploit to hide jar content */
//        byte[] zipStart = new byte[] {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04};
//        byte[] zipEnd = new byte[] {(byte) 0x50, (byte) 0x4B, (byte) 0x05, (byte) 0x06};
        byte[] jar = FileUtil.readFileToByteArray(outputPath);
        byte[] zipContent = ByteUtil.readResourceAsBytes("/watermark.zip", getClass());
        byte[] combined = ByteUtil.mergeByteArrays(zipContent, jar);

        try {
            FileUtil.writeByteArrayToFile(combined, outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        BasicFileAttributeView attributeView = Files.getFileAttributeView(Paths.get(outputPath), BasicFileAttributeView.class);
        attributeView.setTimes(lastModifiedTime, null, creationTime);
    }
}
