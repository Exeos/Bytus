package me.exeos.bytus.api.utils;

public class NumberSysUtil {

    public static String toBase26(long number) throws NumberFormatException {
        if (number < 0)
            throw new NumberFormatException("Negative number");
        number = Math.abs(number);
        StringBuilder converted = new StringBuilder();

        do {
            long remainder = number % 26;
            converted.insert(0, (char) (remainder + 'a'));
            number = (number - remainder) / 26;
        } while (number > 0);
        return converted.toString();
    }
}
