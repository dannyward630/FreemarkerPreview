import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

val platformLocalPath: String? = providers.gradleProperty("platformLocalPath").orNull
    ?.takeIf { file(it).exists() }

dependencies {
    // Template engine used for rendering and for syntax-aware template analysis. Bundled into the plugin's lib/.
    implementation("org.freemarker:freemarker:2.3.35")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    intellijPlatform {
        if (platformLocalPath != null) {
            local(platformLocalPath)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
        // JCEF moved into its own bundled plugin; required for the embedded browser preview.
        bundledPlugin("com.intellij.modules.jcef")
        // Optional at runtime (see freemarker-preview-java.xml): Java PSI for project-aware model inference.
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            if (platformLocalPath != null) {
                local(file(platformLocalPath))
            } else {
                recommended()
            }
        }
    }
}

tasks {
    processResources {
        // License and third-party notices travel inside the plugin jar.
        from(layout.projectDirectory) {
            include("LICENSE", "NOTICE", "licenses/**")
            into("META-INF")
        }
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
    }

    runIde {
        // Debug logging for the preview pipeline in the development IDE (see idea.log in the sandbox).
        systemProperty("idea.log.debug.categories", "#ke.co.scedar.plugins.freemarker.preview")
    }

    prepareTestSandbox {
        // The unified IntelliJ IDEA distribution's licensing module fails to start in the headless test sandbox
        // (a post-startup activity it registers cannot be created). The plugin does not use it.
        disabledPlugins.add("com.intellij.modules.ultimate")
    }

    test {
        useJUnit()
        // The platform test framework runs headless; JCEF is unavailable there and the preview uses its fallback.
        systemProperty("java.awt.headless", "true")
    }
}
