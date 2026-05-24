package me.exeos.bytus.asmplus.jar;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class JarLoader {

    public static JarArchive load(File input, File dependencies) throws IOException {
        JarArchive archive = new JarArchive(new HashMap<>(), new HashMap<>(), new HashMap<>());

        try (JarFile jarFile = new JarFile(input)) {
            loadFiles(archive.classes(), archive.resources(), jarFile);
        }

        if (dependencies != null && dependencies.exists() && dependencies.isDirectory()) {
            for (File f : Objects.requireNonNull(dependencies.listFiles())) {
                try (JarFile jarFile = new JarFile(f)) {
                    loadFiles(archive.dependencies(), archive.resources(), jarFile);
                }
            }
        }

        return archive;
    }

    public static void loadFiles(HashMap<String, ClassNode> classes, HashMap<String, byte[]> resources, JarFile jarFile) throws IOException {
        Enumeration<? extends JarEntry> entries = jarFile.entries();
        JarEntry entry;

        while (entries.hasMoreElements()) {
            entry = entries.nextElement();
            if (!entry.isDirectory()) {
                InputStream stream = jarFile.getInputStream(entry);
                byte[] entryBytes = stream.readAllBytes();

                if (isClass(entryBytes) && entry.getName().endsWith(".class")) {
                    ClassReader classReader = new ClassReader(entryBytes);
                    ClassNode classNode = new ClassNode();

                    classReader.accept(classNode, ClassReader.SKIP_FRAMES + ClassReader.SKIP_DEBUG);
                    classes.put(classNode.name, classNode);
                } else {
                    resources.put(entry.getName(), entryBytes);
                }
            }
        }
    }

    public static void export(JarArchive jar, OutputStream output) throws IOException {
        JarOutputStream jarOut = new JarOutputStream(output);
        for (Map.Entry<String, ClassNode> entry : jar.classes().entrySet()) {
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            entry.getValue().accept(classWriter);

            writeJarEntry(jarOut, entry.getKey() + ".class", classWriter.toByteArray());
        }

        for (Map.Entry<String, byte[]> e : jar.resources().entrySet()) {
            writeJarEntry(jarOut, e.getKey(), e.getValue());
        }

        jarOut.finish();
    }

    private static void writeJarEntry(JarOutputStream outputStream, String name, byte[] bytes) throws IOException {
        JarEntry entry = new JarEntry(new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        entry.setSize(bytes.length);

        outputStream.putNextEntry(entry);
        outputStream.write(bytes);
        outputStream.closeEntry();
    }

    private static boolean isClass(byte[] file) {
        if (file.length < 4)
            return false;
        return new BigInteger(1, new byte[] { file[0], file[1], file[2], file[3] }).intValue() == -889275714;
    }
}
