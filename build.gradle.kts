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
              <li><b>Go to Definition</b> on <code>&lt;Link to="..."&gt;</code>, <code>to={"/path"}</code>, <code>navigate({ to })</code>, and <code>redirect({ to })</code></li>
              <li>Absolute and <b>relative</b> <code>to</code> paths (<code>.</code>, <code>..</code>, <code>../edit</code>)</li>
              <li>Resolves via generated <code>routeTree.gen.ts</code> to the real route module file</li>
              <li>Gutter icons on resolvable route links</li>
              <li><b>TanStack Routes</b> tool window listing indexed paths</li>
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
