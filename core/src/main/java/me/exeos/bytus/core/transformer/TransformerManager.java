package me.exeos.bytus.core.transformer;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.impl.PreProcessor;
import me.exeos.bytus.core.transformer.impl.Renamer;
import me.exeos.bytus.core.transformer.impl.TestTransformer;
import me.exeos.bytus.core.transformer.impl.arithmetic.MBATransformer;
import me.exeos.bytus.core.transformer.impl.constants.ConstantArrayTransformer;
import me.exeos.bytus.core.transformer.impl.constants.number.FloatingPointToIntTransformer;
import me.exeos.bytus.core.transformer.impl.constants.number.IntEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.constants.string.SplitStringsTransformer;
import me.exeos.bytus.core.transformer.impl.constants.string.StringEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.flow.control.*;
import me.exeos.bytus.core.transformer.impl.flow.data.ParamGenerifier;
import me.exeos.bytus.core.transformer.impl.reference.ReferenceEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.reference.ReferenceProxyTransformer;
import me.exeos.bytus.core.transformer.impl.salt.ClassSaltTransformer;
import me.exeos.bytus.core.transformer.impl.salt.MethodSaltTransformer;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds and runs the {@link Pipeline} of {@link AbstractTransformer transformers}.
 * <p>
 * The manager instantiates all registered transformer factories for a given {@link BytusConfig},
 * filters out transformers that do not {@link AbstractTransformer#applies() apply} to the current
 * configuration, sorts them by {@link AbstractTransformer#priority() priority} and then executes
 * them against a {@link JarArchive} via {@link #transform(JarArchive)}.
 */
public class TransformerManager {

    private static final Set<Function<BytusConfig, AbstractTransformer>> REGISTRY = new HashSet<>();

    static {
        REGISTRY.add(TestTransformer::new);
        REGISTRY.add(MBATransformer::new);
        REGISTRY.add(FloatingPointToIntTransformer::new);
        REGISTRY.add(IntEncryptionTransformer::new);
        REGISTRY.add(SplitStringsTransformer::new);
        REGISTRY.add(StringEncryptionTransformer::new);
        REGISTRY.add(ConstantArrayTransformer::new);
//        REGISTRY.add(BlockSplitTransformer::new); // unstable
        REGISTRY.add(FlowFlatteningTransformer::new);
        REGISTRY.add(JumpFlatteningTransformer::new);
//        REGISTRY.add(FlowBlockEntryDispatchTransformer::new); // unstable
        REGISTRY.add(BlockRearranger::new);
        REGISTRY.add(GotoReplacerTransformer::new);
        REGISTRY.add(ParamGenerifier::new);
        REGISTRY.add(ReferenceEncryptionTransformer::new);
        REGISTRY.add(ReferenceProxyTransformer::new);
        REGISTRY.add(ClassSaltTransformer::new);
        REGISTRY.add(MethodSaltTransformer::new);
        REGISTRY.add(PreProcessor::new);
        REGISTRY.add(Renamer::new);
    }

    private final Pipeline pipeline;

    /**
     * Creates a new {@link TransformerManager} for the provided configuration.
     *
     * @param config configuration used to instantiate and enable/disable transformers
     */
    public TransformerManager(BytusConfig config) {
        List<AbstractTransformer> active = REGISTRY.stream()
                .map(factory -> factory.apply(config))
                .filter(AbstractTransformer::applies)
                .sorted(Comparator.comparingInt(AbstractTransformer::priority))
                .collect(Collectors.toList());

        pipeline = new Pipeline(active);
    }

    /**
     * Runs the configured transformer pipeline against the provided jar.
     *
     * @param jar jar archive to transform
     */
    public void transform(JarArchive jar) {
        pipeline.run(new JarContext(jar, pipeline));
    }
}
