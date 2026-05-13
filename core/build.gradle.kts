plugins {
    id("java")
}

group = "me.exeos.bytus"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":asm-plus"))
    implementation(libs.jackson)
    implementation(libs.asm)
    implementation(libs.asm.analysis)
    implementation(libs.asm.commons)
    implementation(libs.asm.tree)
    implementation(libs.asm.util)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}