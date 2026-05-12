plugins {
    application
}

repositories {
    mavenCentral()
    mavenLocal()
    maven(url = "https://stianloader.org/maven")
}

dependencies {
    implementation(libs.asmplus)
    implementation(libs.asm)
    implementation(libs.asm.analysis)
    implementation(libs.asm.commons)
    implementation(libs.asm.tree)
    implementation(libs.asm.util)

    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.3")
    implementation("org.stianloader:stianloader-remapper:0.1.0-a20240426")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("Main")
}