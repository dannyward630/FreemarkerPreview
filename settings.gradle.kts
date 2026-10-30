plugins {
    // Lets Gradle provision the JDK 25 toolchain required by IntelliJ Platform 2026.2 when none is installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "FreemarkerPreview"
