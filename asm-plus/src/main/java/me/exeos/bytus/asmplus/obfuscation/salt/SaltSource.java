package me.exeos.bytus.asmplus.obfuscation.salt;

import me.exeos.bytus.asmplus.InsnFactory;

public record SaltSource(int salt, InsnFactory pushSaltInsn) {
}
