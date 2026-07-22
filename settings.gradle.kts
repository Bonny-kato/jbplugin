import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

rootProject.name = "jbplugin"

pluginManagement {
    plugins {
        // Must be new enough to read Kotlin metadata shipped with WebStorm 2026.2 (2.3/2.4).
        id("org.jetbrains.kotlin.jvm") version "2.4.10"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        intellijPlatform {
            defaultRepositories()
        }
    }
}
