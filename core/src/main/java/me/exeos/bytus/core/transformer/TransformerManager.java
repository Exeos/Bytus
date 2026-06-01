package me.exeos.bytus.core.transformer;

import me.exeos.bytus.asmplus.jar.JarArchive;
import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.impl.PreProcessor;
import me.exeos.bytus.core.transformer.impl.constants.ConstantArrayTransformer;
import me.exeos.bytus.core.transformer.impl.constants.number.OverUnderFlowIntTransformer;
import me.exeos.bytus.core.transformer.impl.constants.string.SplitStringsTransformer;
import me.exeos.bytus.core.transformer.impl.constants.string.StringEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.flow.control.FlowFlattening;
import me.exeos.bytus.core.transformer.impl.flow.control.JumpFlattening;
import me.exeos.bytus.core.transformer.impl.flow.data.ParamGenerifier;
import me.exeos.bytus.core.transformer.impl.reference.ReferenceEncryptionTransformer;
import me.exeos.bytus.core.transformer.impl.reference.ReferenceProxyTransformer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TransformerManager {

    private static final List<Function<BytusConfig, AbstractTransformer>> registry = new ArrayList<>();

    static {
        registry.add(OverUnderFlowIntTransformer::new);
        registry.add(SplitStringsTransformer::new);
        registry.add(StringEncryptionTransformer::new);
        registry.add(ConstantArrayTransformer::new);
        registry.add(FlowFlattening::new);
        registry.add(JumpFlattening::new);
        registry.add(ParamGenerifier::new);
        registry.add(ReferenceEncryptionTransformer::new);
        registry.add(ReferenceProxyTransformer::new);
        registry.add(PreProcessor::new);
    }

    private final Pipeline pipeline;

    public TransformerManager(BytusConfig config) {
        List<AbstractTransformer> active = registry.stream()
                .map(factory -> factory.apply(config))
                .filter(AbstractTransformer::applies)
                .sorted(Comparator.comparingInt(AbstractTransformer::priority))
                .collect(Collectors.toList());

        pipeline = new Pipeline(active);
    }

    public void transform(JarArchive jar) {
        pipeline.run(new JarContext(jar, pipeline));
    }
}
