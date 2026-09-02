
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = "0.3.4-beta2"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val localJava25 = javaToolchains.compilerFor {
    languageVersion.set(JavaLanguageVersion.of(25))
}
val minecraftJava8 = javaToolchains.compilerFor {
    languageVersion.set(JavaLanguageVersion.of(8))
    vendor.set(JvmVendorSpec.AZUL)
}

tasks.withType<JavaCompile>().configureEach {
    if (name == "compileMcLauncherJava" || name == "compilePatchedMcJava") {
        javaCompiler.set(minecraftJava8)
    } else {
        javaCompiler.set(localJava25)
    }
}

tasks.test {
    useJUnitPlatform()
}
