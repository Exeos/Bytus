package com.bytus.core;

import com.bytus.core.transformer.exclutions.ExType;
import com.bytus.core.transformer.exclutions.Exclution;

import java.util.ArrayList;

public class Constants {

    private static final ArrayList<Exclution> EXCLUTIONS = new ArrayList<>();

    public static boolean isExcludet(Object inQuestion, ExType from) {
        for (Exclution exclution : EXCLUTIONS) {
            if (exclution.match(inQuestion, from)) {
                return true;
            }
        }

        return false;
    }

    public static void exclude(Object excludet, ExType from) {
        EXCLUTIONS.add(new Exclution(excludet, from));
    }
}
