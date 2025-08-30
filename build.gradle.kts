plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "org.orangejam"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Configure IntelliJ Platform Gradle Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("guru.nidi:graphviz-java:0.18.1")
    implementation("org.slf4j:slf4j-nop:2.0.16")

    // ported from zihengs repository
    implementation("org.jetbrains.kotlin:kotlin-metadata-jvm:2.1.20")
    implementation("org.ow2.asm:asm-tree:9.8")
    implementation("io.github.rchowell:dotlin:1.0.2")
    // use whatever they published
    implementation("io.github.tiktok.knit:knit:0.1.5")
    implementation("io.github.tiktok.knit:knit-asm:0.1.5")

    intellijPlatform {
        create("IC", "2025.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("org.jetbrains.plugins.gradle")

        // Add necessary plugin dependencies for compilation here, example:
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }

        changeNotes = """
            Initial version
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
