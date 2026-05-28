package me.exeos.bytus.core;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.asmplus.jar.JarLoader;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.exceptions.BytusInitException;
import me.exeos.bytus.core.exceptions.BytusPosTransformException;
import me.exeos.bytus.core.transformer.Transformer;
import me.exeos.bytus.core.transformer.TransformerPipeline;
import me.exeos.bytus.core.transformer.impl.constants.ConstantArrayTransformer;
import me.exeos.bytus.core.transformer.impl.PreProcessor;
import me.exeos.bytus.core.transformer.impl.constants.number.OverUnderFlowIntTransformer;
import me.exeos.bytus.core.transformer.impl.constants.string.StringEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.flow.control.FlowFlattening;
import me.exeos.bytus.core.transformer.impl.flow.control.JumpFlattening;
import me.exeos.bytus.core.transformer.impl.flow.data.ParamGenerifier;
import me.exeos.bytus.core.transformer.impl.reference.ReferenceEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.reference.ReferenceProxyTransformer;
import me.exeos.bytus.core.transformer.impl.constants.string.SplitStringsTransformer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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

        transformers.add(new PreProcessor(jar, config.exclusions, List.of()));

        if (config.flow.dataFlow()) {
            transformers.add(new ParamGenerifier(jar, config.exclusions, List.of(), config.mainClassName));
        }

        if (config.flow.controlFlow().enable()) {
            transformers.add(new FlowFlattening(jar, config.exclusions, List.of(), config.flow.controlFlow().minDispatcherChainLength(), config.flow.controlFlow().maxDispatcherChainLength()));
            transformers.add(new JumpFlattening(jar, config.exclusions, List.of(), config.flow.controlFlow().minDispatcherChainLength(), config.flow.controlFlow().maxDispatcherChainLength()));
        }

        if (config.constants.enable()) {
            if (config.constants.splitStrings()) {
                transformers.add(new SplitStringsTransformer(jar, config.exclusions, List.of()));
            }
            if (config.constants.strings()) {
                transformers.add(new StringEncryptionTransformer(jar, config.exclusions, List.of()));
            }
        }

        if (config.references.proxy().enable()) {
                transformers.add(new ReferenceProxyTransformer(jar, config.exclusions, List.of(), config.references.proxy().minDepth(), config.references.proxy().maxDepth()));
        }
        if (config.references.encryption().enable()) {
            transformers.add(new ReferenceEncryptionTransformer(jar, config.exclusions, List.of(), config.references.encryption().methodCalls(), config.references.encryption().fieldAccess()));
        }

        if (config.constants.enable()) {
            if (config.constants.constantArray()) {
                transformers.add(new ConstantArrayTransformer(jar, config.exclusions, List.of()));
            }
            if (config.constants.numbers()) {
                transformers.add(new OverUnderFlowIntTransformer(jar, config.exclusions, List.of()));
            }
        }

        return new TransformerPipeline(transformers);
    }
}
