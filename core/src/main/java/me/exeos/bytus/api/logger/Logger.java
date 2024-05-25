package me.exeos.bytus.api.logger;

public class Logger {

    private final String prefix = "[Bytus->";

    private void print(String prefix, Object out) {
        System.out.println(this.prefix + prefix + "]: " + out);
    }

    public void success(Object out) {
        print("s", out);
    }

    public void info(Object out) {
        print("i", out);
    }

    public void warning(Object out) {
        print("w", out);
    }

    public void error(Object out) {
        print("e", out);
    }

}
