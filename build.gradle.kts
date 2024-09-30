import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

group = "io.github.mishkun"
version = "1.1.2"

intellijPlatform {
    instrumentCode.set(false)
    pluginConfiguration {
        changeNotes = """
                Updated plugin to work with IntelliJ IDEA 2024.2.3
            """.trimIndent()
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "203"
        }
    }
    pluginVerification {
        ides {
            select {
                types.set(listOf(IntelliJPlatformType.IntellijIdeaCommunity))
                channels.set(listOf(ProductRelease.Channel.RELEASE))
                sinceBuild = "234"
                untilBuild = "241.*"
            }
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.3")
        testFramework(TestFrameworkType.Platform)
        pluginVerifier()
    }
    implementation("com.typesafe:config:1.4.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.opentest4j:opentest4j:1.2.0")
}
