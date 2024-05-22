package me.exeos.bytus.api.transformer;

import me.exeos.bytus.Bytus;
import me.exeos.bytus.api.config.Config;
import me.exeos.bytus.api.config.ConfigInterface;
import me.exeos.bytus.api.logger.Task;

import java.util.LinkedList;

public class TransformerManager implements ConfigInterface {

    private final LinkedList<Transformer> transformers = new LinkedList<>();

    public TransformerManager() {
        Config config = Bytus.instance.config;
        if (config == null) {
            throw new IllegalStateException("Can't construct TransformerManager before loading Config");
        }


    }

    public void applyTransformers() {
        for (Transformer transformer : transformers) {
            Task transformTask = new Task("Running transformer: " + transformer.getClass().getSimpleName()).start();
            if (transformer.transform()) {
                transformTask.finish();
            } else {
                transformTask.fail();
            }
        }
    }
}
