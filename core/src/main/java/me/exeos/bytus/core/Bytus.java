package me.exeos.bytus.core;

import me.exeos.asmplus.jar.JarArchive;
import me.exeos.asmplus.jar.JarLoader;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.exceptions.BytusInitException;
import me.exeos.bytus.core.exceptions.BytusPosTransformException;
import me.exeos.bytus.core.transformer.TransformerManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class Bytus {

    private final BytusConfig config;

    public Bytus(String config) {
        this.config = BytusConfig.fromJson(config);
    }

    public void obfuscate() {
        File inputFile = new File(config.io.inputPath());
        File outputFile = new File(config.io.outputPath());
        File dependenciesPath = new File(config.io.dependenciesPath());
        if (!outputFile.exists()) {
            try {
                if (!outputFile.createNewFile()) {
                    throw new BytusInitException("Failed to create output File");
                }
            } catch (IOException e) {
                throw new BytusInitException("Failed to create output File", e);
            }
        }
        if (!inputFile.exists()) {
            throw new BytusInitException("Provided input does not exist");
        }

        JarArchive jar;
        try {
            jar = JarLoader.load(inputFile, dependenciesPath);
        } catch (IOException e) {
            throw new BytusInitException("Failed to load jarCtx from input File", e);
        }

        new TransformerManager(config).transform(jar);

        try {
            JarLoader.export(jar, new FileOutputStream(outputFile), !config.flow.controlFlow().enable() && false);
        } catch (IOException e) {
            throw new BytusPosTransformException("Failed to export transformed archive", e);
        }
    }
}
