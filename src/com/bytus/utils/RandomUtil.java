package com.bytus.utils;

public class RandomUtil {

    public static String randomPhrase() {
        String[] phrases = new String[] {
                "YourMom", "GiveUp", "WhyEvenTry", "Loser", "FuckYou", "KeepTryingLoser", "BytusBest", "Bytus"
        };

        return phrases[me.exeos.asmplus.utils.RandomUtil.getInt(0, phrases.length - 1)];
    }
}
