plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "Bytus"

include("asm-plus")
include("core")
include("cli")
//include("gui")