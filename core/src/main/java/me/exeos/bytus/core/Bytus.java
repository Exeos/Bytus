package me.exeos.bytus.core;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.jar.JarLoader;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.exceptions.BytusInitException;
import me.exeos.bytus.core.exceptions.BytusPosTransformException;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.transformer.impl.constants.number.OverUnderFlowIntTransformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Bytus {

    private final BytusConfig config;

    private Bytus(String config) {
        this.config = BytusConfig.fromJson(config);
    }

    public void obfuscate() {
        File inputFile = new File(config.io.inputPath());
        File outputFile = new File(config.io.outputPath());
        if (!outputFile.exists()) {
            try {
                if (!outputFile.createNewFile()) {
                    throw new BytusInitException("Failed to create output File");
                }
            } catch (IOException e) {
                throw new BytusInitException("Failed to create output File", e);
            }
        }
        if (inputFile.exists()) {
            throw new BytusInitException("Provided input does not exist");
        }

        JarArchive jar;
        try {
            jar = JarLoader.load(inputFile);
        } catch (IOException e) {
            throw new BytusInitException("Failed to load jar from input File", e);
        }

        mapConfigToTransformers(jar).executeTransformers();

        try {
            JarLoader.export(jar, new FileOutputStream(outputFile));
        } catch (IOException e) {
            throw new BytusPosTransformException("Failed to export transformed archive", e);
        }
    }

    private TransformerPipeline mapConfigToTransformers(JarArchive jar) {
        List<Transformer> transformers = new ArrayList<>();

        if (config.constants.enable()) {
            if (config.constants.numbers()) {
                transformers.add(new OverUnderFlowIntTransformer(jar, config.exclusions, List.of()));
            }
        }

        return new TransformerPipeline(transformers);
    }
}
