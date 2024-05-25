package me.exeos.bytus.api.transformer;

import me.exeos.bytus.Bytus;
import me.exeos.bytus.api.config.Config;
import me.exeos.bytus.api.config.ConfigInterface;
import me.exeos.bytus.api.logger.Task;
import me.exeos.bytus.transformers.Bootstraper;
import me.exeos.bytus.transformers.PreProcessor;
import me.exeos.bytus.transformers.encryption.Encryptor;
import me.exeos.bytus.transformers.encryption.StringEncryptionTransformer;
import me.exeos.bytus.transformers.packer.ClassEncryptionTransformer;
import me.exeos.bytus.transformers.renamer.RenameTransformer;

import java.util.LinkedList;

public class TransformerManager implements ConfigInterface {

    private final LinkedList<Transformer> transformers = new LinkedList<>();

    public TransformerManager() {
        Config config = Bytus.instance.config;
        if (config == null) {
            throw new IllegalStateException("Can't construct TransformerManager before loading Config");
        }

        if (isPackEnabled()) {
            transformers.add(new ClassEncryptionTransformer());
        }
        transformers.add(new Bootstraper());
        transformers.add(new PreProcessor());

        if (isStrEncEnabled()) {
            transformers.add(new Encryptor());
        }

        if (isStrEncEnabled()) {
            transformers.add(new StringEncryptionTransformer());
        }

        if (isRenamerEnabled()) {
            transformers.add(new RenameTransformer());
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
