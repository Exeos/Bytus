package me.exeos.bytus.api.logger;

public class Logger {

    private final String prefix = "[Bytus]-> ";

    private void print(String prefix, Object out) {
        System.out.println(this.prefix + prefix + ": " + out);
    }

    public void success(Object out) {
        print("success", out);
    }

    public void info(Object out) {
        print("info", out);
    }

    public void warning(Object out) {
        print("warning", out);
    }

    public void error(Object out) {
        print("error", out);
    }

}
