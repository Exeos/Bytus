package me.exeos.bytus.core;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.jar.JarLoader;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.exceptions.BytusInitException;
import me.exeos.bytus.core.exceptions.BytusPosTransformException;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.transformer.impl.constants.number.OverUnderFlowIntTransformer;
import me.exeos.bytus.core.transformer.impl.flow.FlowFlattening;
import me.exeos.bytus.core.transformer.impl.flow.JumpFlattening;
import me.exeos.bytus.core.transformer.impl.reference.ReferenceEncryptionTransformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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

        if (config.flow.enable()) {
            transformers.add(new FlowFlattening(jar, config.exclusions, List.of(), config.flow.minDispatcherChainLength(), config.flow.maxDispatcherChainLength()));
            transformers.add(new JumpFlattening(jar, config.exclusions, List.of(), config.flow.minDispatcherChainLength(), config.flow.maxDispatcherChainLength()));
        }

        if (config.constants.enable()) {
            if (config.constants.numbers()) {
                transformers.add(new OverUnderFlowIntTransformer(jar, config.exclusions, List.of()));
            }
        }

        if (config.references.enable()) {
            transformers.add(new ReferenceEncryptionTransformer(jar, config.exclusions, List.of(), config.references.methodCall(), config.references.fieldAccess()));
        }
        return new TransformerPipeline(transformers);
    }
}
