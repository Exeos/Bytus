package me.exeos.bytus.core.transformer;

import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.transformer.context.JarContext;
import me.exeos.bytus.core.transformer.impl.flow.control.FlowFlattening;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TransformerManager {

    private static final List<Function<BytusConfig, AbstractTransformer>> registry = new ArrayList<>();

    static {
        registry.add(FlowFlattening::new);
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

    public void transform(JarContext context) {
        pipeline.run(context);
    }
}
