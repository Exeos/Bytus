package me.exeos.bytus.cli;

import me.exeos.bytus.core.Bytus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    static void main(String[] args) {
        try {
            new Bytus(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8)).obfuscate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
