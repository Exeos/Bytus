package me.exeos.bytus;

import me.exeos.bytus.api.BJARLoader;
import me.exeos.bytus.api.config.Config;
import me.exeos.bytus.api.config.ConfigInterface;
import me.exeos.bytus.api.config.ConfigLoader;
import me.exeos.bytus.api.logger.Logger;
import me.exeos.bytus.api.logger.Task;
import me.exeos.bytus.api.transformer.TransformerManager;

import java.io.IOException;

public class Bytus implements ConfigInterface {

    public static Bytus instance;

    public final Logger logger = new Logger();

    public Config config;
    public BJARLoader jarLoader;

    public Bytus() {
        if (instance != null) {
            throw new RuntimeException("Bytus Singleton already exists");
        }

        instance = this;
    }

    public void bootstrap(String[] args) {
        if (args.length == 0 || (!args[0].startsWith("config=") && !args[0].startsWith("configPath="))) {
            logger.error("Please start with argument config=configJson or configPath=pathToConfigJson");
            return;
        }

        try {
            /* init before transformer */
            if (loadConfig(args) && loadJar()) {
                transform();
                exportJar();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean loadConfig(String[] args) throws IOException {
        Task configTask = new Task("Loading Config").start();
        ConfigLoader configLoader = new ConfigLoader();

        String[] configArgSplit = args[0].split("=");
        String configType = configArgSplit[0];

        if (configArgSplit.length < 2) {
            configTask.fail("Invalid config argument: " + args[0]);
            return false;
        }

        switch (configType) {
            case "config":
                config = configLoader.loadString(configArgSplit[1]);
            break; case "configPath":
                config = configLoader.loadFile(configArgSplit[1]);
                break;
            default:
                configTask.fail("Invalid config type: " + configType);
                return false;
        }

        configTask.finish();
        return true;
    }

    private boolean loadJar() {
        Task jarLoadTask = new Task("Loading jar").start();
        try {
            jarLoader = new BJARLoader();
            jarLoader.load(getInputPath());
        } catch (IOException e) {
            jarLoadTask.fail("Failed to load jar: " + e.getCause() + " " + e.getMessage());
            return false;
        }
        jarLoadTask.finish();
        return true;
    }

    private void transform() {
        Task transformTask = new Task("Applying transformers").start();
        new TransformerManager().applyTransformers();
        transformTask.finish();
    }

    private void exportJar() {
        Task jarLoadTask = new Task("Exporting jar").start();
        try {
            jarLoader.export(getOutputPath());
        } catch (Exception e) {
            jarLoadTask.fail("Failed to export jar: " + e.getCause() + " " + e.getMessage());
        }
        jarLoadTask.finish();
    }
}
