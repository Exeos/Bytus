plugins {
    id("java")
}

group = "me.exeos.bytus"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(libs.jackson)
    implementation(libs.asm)
    implementation(libs.asm.analysis)
    implementation(libs.asm.commons)
    implementation(libs.asm.tree)
    implementation(libs.asm.util)
    implementation("me.exeos:asmplus:1.0.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}