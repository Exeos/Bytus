package pipeline;

import me.exeos.bytus.core.config.BytusConfig;
import pipeline.context.JarContext;
import pipeline.impl.TestTransformer;

import java.util.*;
import java.util.stream.Collectors;

public class TransformerManager {

    private static final List<Transformer> allTransformers = new ArrayList<>();
    private static Pipeline configPipeline;

    public TransformerManager(BytusConfig config) {
        allTransformers.addAll(List.of(
                new TestTransformer()
        ));

        // filer and order transformers
        List<Transformer> transformers = new ArrayList<>(allTransformers);
        transformers = transformers
                .stream()
                .filter(transformer -> transformer.applies(config))
                .sorted(Comparator.comparingInt(Transformer::priority))
                .collect(Collectors.toList());

        // store to static pipeline
        configPipeline = new Pipeline(transformers);;
    }

    public void transform(JarContext context) {
        configPipeline.transform(context);
    }

    public static <T extends Transformer> T getTransformer(Class<T> clazz) {
        for (Transformer transformer : allTransformers) {
            if (transformer.getClass() == clazz) {
                return clazz.cast(transformer);
            }
        }

        return null;
    }

    public static Pipeline getConfigPipeline() {
        return configPipeline;
    }
}
