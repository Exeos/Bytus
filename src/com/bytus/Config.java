package com.bytus;

import com.bytus.core.transformer.Transformer;
import com.bytus.impl.transformer.PreProcessor;
import com.bytus.impl.transformer.TestTransformer;
import com.bytus.impl.transformer.enc.EncryptionTransformer;
import com.bytus.impl.transformer.renamer.RenameTransformer;

import java.util.ArrayList;

public class Config {


    /* General */
    public static String BOOTLOADER_NAME = "Main";
    public static String ENTRY_CLASS = "dev/sim0n/evaluator/Main";
    public static String ENTRY_METHOD = "main";
    /* _______ */

    /* Rename  */
    public static boolean DO_RENAME = false;
    /* _______ */

    /* Encryptor */
    public static boolean DO_ENC = true;
    public static String ENCRYPTOR_CNAME = "BytusCL";
    /* _______ */

    public static ArrayList<Transformer> transformers() {
        ArrayList<Transformer> transformers = new ArrayList<>();
        transformers.add(new PreProcessor());

        if (DO_RENAME) {
            transformers.add(new RenameTransformer());
        }

        if (DO_ENC) {
            transformers.add(new EncryptionTransformer());
        }

        return transformers;
    }

    public static boolean canRenameClass(String className) {
        return !className.equals(BOOTLOADER_NAME);
    }
}
