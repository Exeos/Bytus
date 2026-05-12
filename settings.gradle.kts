plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Bytus"
include("core")

includeBuild(providers.gradleProperty("dependencies.asmplus.path")) {
    dependencySubstitution {
        substitute(module("me.exeos:asmplus")).using(project(":asmplus-lib"))
    }
}
