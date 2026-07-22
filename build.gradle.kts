import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // Prefer a local WebStorm install when provided (faster / offline-friendly).
        val localPlatformPath = providers.gradleProperty("localPlatformPath")
        if (localPlatformPath.isPresent) {
            local(localPlatformPath)
        } else {
            webstorm(providers.gradleProperty("platformVersion"))
        }
        // Needed for JS/TS/TSX PSI + language extensions used by navigation.
        bundledPlugin("JavaScript")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = providers.gradleProperty("pluginId")
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description = """
            TanStack support for WebStorm — route link navigation for TanStack Router / Start.
            <ul>
              <li><b>Go to Definition</b> on Link / Navigate / MatchRoute / *Link, navigate, redirect, linkOptions</li>
              <li>Lands on <code>createFileRoute(...)</code>; absolute + relative <code>to</code></li>
              <li><b>Unresolved route</b> inspection, <b>Find Usages</b>, <b>completion</b>, <b>hover docs</b></li>
              <li>Resolves via <code>routeTree.gen.ts</code>; Routes tool window with physical files + usages</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
        }

        vendor {
            name = "iPF Softwares"
            url = "https://ipfsoftwares.com"
        }
    }

    // Speeds up packaging; searchable options can be generated later if needed.
    buildSearchableOptions = false
}

tasks {
    wrapper {
        gradleVersion = providers.gradleProperty("gradleVersion").get()
    }

    test {
        // Pure unit tests + platform tests
        useJUnit()
    }
}
