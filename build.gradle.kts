import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.1.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.0-RC"
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val uiTestConfiguration by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
}

val uiTest by sourceSets.registering {
    kotlin.srcDirs("src/uiTest/kotlin")
    resources.srcDir("src/uiTest/resources")

    compileClasspath += sourceSets.main.get().output + configurations.testCompileClasspath.get() + configurations.compileClasspath.get() + uiTestConfiguration.get()
    runtimeClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get() + configurations.runtimeClasspath.get() + uiTestConfiguration.get()
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
        languageVersion.set(JavaLanguageVersion.of(17))
    })
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
            sinceBuild = "231"
            untilBuild = ""
        }
    }
    pluginVerification {
        ides {
            select {
                types.set(listOf(IntelliJPlatformType.IntellijIdeaCommunity))
                channels.set(listOf(ProductRelease.Channel.RELEASE))
                sinceBuild = "231"
                untilBuild = "243.*"
            }
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.3")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = uiTestConfiguration.name)
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
