import org.jetbrains.intellij.platform.gradle.TestFrameworkType

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
version = "1.1.1"

intellijPlatform {
    instrumentCode.set(false)
    pluginConfiguration {
        changeNotes = """
                Parse config file on startup to speedup action popup
            """.trimIndent()
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "201"
            untilBuild = ""
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.3")
        testFramework(TestFrameworkType.Platform)
    }
    implementation("com.typesafe:config:1.4.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:3.0")
}
