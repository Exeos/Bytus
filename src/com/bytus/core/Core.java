package com.bytus.core;

import com.bytus.core.jarloader.BytusJL;
import com.bytus.core.transformer.Transformer;

import java.io.IOException;
import java.util.ArrayList;

public class Core {

    public final BytusJL jarLoader = new BytusJL();

    public void transform(ArrayList<Transformer> transformers) {
        for (Transformer transformer : transformers) {
            try {
                System.out.println("Running: " + transformer.getClass().getSimpleName());
                if (!transformer.transform()) {
                    System.out.println("Transformer: " + transformer.getClass().getSimpleName() + " failed to execute.");
                } else {
                    System.out.println("Done.");
                }
            } catch (Exception e) {
                System.out.println("Transformer: " + transformer.getClass().getSimpleName() + " threw a fatal exception.");
                e.printStackTrace();
            }
        }
    }

    public void load(String location) throws IOException {
        jarLoader.load(location);
    }

    public void export(String location) throws Exception {
        jarLoader.export(location);
    }
}
