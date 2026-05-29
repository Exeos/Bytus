package me.exeos.bytus.core.trans;

import me.exeos.bytus.core.config.BytusConfig;
import me.exeos.bytus.core.trans.context.JarContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TransformerManager {

    private final static List<AbstractTransformer> allTransformers = new ArrayList<>();
    private final Pipeline configPipeline;

    static {
        allTransformers.addAll(List.of(
        ));
    }

    public TransformerManager(BytusConfig config) {
        // filer and order transformers
        List<AbstractTransformer> transformers = new ArrayList<>(allTransformers);
        transformers = transformers
                .stream()
                .filter(transformer -> transformer.applies(config))
                .sorted(Comparator.comparingInt(AbstractTransformer::priority))
                .collect(Collectors.toList());

        configPipeline = new Pipeline(transformers);;
    }

    public void transform(JarContext context) {
        configPipeline.transform(context);
    }

    public static <T extends AbstractTransformer> T getTransformer(Class<T> clazz) {
        for (AbstractTransformer transformer : allTransformers) {
            if (transformer.getClass() == clazz) {
                return clazz.cast(transformer);
            }
        }

        return null;
    }
}
