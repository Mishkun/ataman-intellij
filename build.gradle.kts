import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.0-RC"
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}


val uiTest by sourceSets.registering {
    kotlin.srcDirs("src/uiTest/kotlin")
    resources.srcDir("src/uiTest/resources")

    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val uiTestImplementation by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}

val uiTestRuntimeOnly by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

kover {
    merge {
        projects(":")
        sources {
            excludedSourceSets.add("uiTest")
        }
    }
}

val uiTests by intellijPlatformTesting.testIdeUi.registering {
    this.type = IntelliJPlatformType.IntellijIdeaCommunity
    version.set("2024.2.3")
    task {
        enabled = gradle.startParameter.taskNames.any { it.contains("uiTests") }
        archiveFile.set(tasks.buildPlugin.flatMap { it.archiveFile })
        testClassesDirs = uiTest.get().output.classesDirs
        classpath = uiTest.get().compileClasspath + uiTest.get().runtimeClasspath
        systemProperty("ataman.configFolder", layout.projectDirectory.dir("example-config").asFile.absolutePath)
        environment("ATAMAN_PLUGIN_PATH", tasks.buildPlugin.flatMap { it.archiveFile }.get().asFile.absolutePath)
    }
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

group = "io.github.mishkun"
version = "1.3.0"

intellijPlatform {
    instrumentCode.set(false)
    pluginConfiguration {
        changeNotes = """
            Add support for special characters with shift (Fix #9)
            Add option to perform multiple actions in one binding (Fix #11)
            Add repeat latest command action (Fix #2)
            Add option to create IDE-specific bindings (Fix #4)
            Use monospace font for key labels to make them aligned (Fix #15)
        """.trimIndent()
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = "243.*"
        }
    }
    pluginVerification {
        ides {
            select {
                types.set(listOf(IntelliJPlatformType.IntellijIdeaCommunity))
                channels.set(listOf(ProductRelease.Channel.RELEASE))
                sinceBuild = "242"
                untilBuild = "243.*"
            }
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.3")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = "uiTestImplementation")
        pluginVerifier()
    }
    implementation("com.typesafe:config:1.4.2")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.opentest4j:opentest4j:1.2.0")
    testImplementation("io.mockk:mockk:1.12.7") {
        exclude(group = "org.jetbrains.kotlin")
    }
}
