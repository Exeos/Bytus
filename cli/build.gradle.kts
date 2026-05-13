plugins {
    application
}

group = "me.exeos.bytus"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(26))
    }
}

application {
    mainClass.set("me.exeos.bytus.cli.Main")
}