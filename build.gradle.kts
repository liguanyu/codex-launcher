plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "com.github.x0x0b"
version = "1.1.18.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("org.apache.commons:commons-text:1.15.0")
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    intellijPlatform {
        rider("2026.1.5")
        bundledPlugin("org.jetbrains.plugins.terminal")
        bundledModule("intellij.terminal.frontend")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
            Require Rider or a compatible JetBrains IDE 2026.1 or later.
            Support file and selection input into manually opened Reworked terminals as well as Classic terminals.
            Report unavailable terminal sessions without opening a new Codex tab. Toolbar right-click still opens or focuses Codex.
        """.trimIndent()
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    test {
        useJUnitPlatform()
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
