import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.5.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
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
    version.set("2025.1")
    task {
        enabled = gradle.startParameter.taskNames.any { it.contains("uiTests") }
        archiveFile.set(tasks.buildPlugin.flatMap { it.archiveFile })
        testClassesDirs = uiTest.get().output.classesDirs
        classpath = uiTest.get().compileClasspath + uiTest.get().runtimeClasspath
        systemProperty("ataman.configFolder", layout.projectDirectory.dir("example-config").asFile.absolutePath)
        systemProperty("intellij.testFramework.rethrow.logged.errors", "true")
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
version = "1.4.0"

intellijPlatform {
    instrumentCode.set(false)
    pluginConfiguration {
        changeNotes = providers.fileContents(layout.projectDirectory.file("changelog.txt"))
            .asText.orElse("")
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "253.*"
        }
    }
    pluginVerification {
        ides {
            select {
                types.set(listOf(IntelliJPlatformType.IntellijIdeaCommunity))
                channels.set(listOf(ProductRelease.Channel.RELEASE))
                sinceBuild = "243"
                untilBuild = "253.*"
            }
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Starter, configurationName = "uiTestImplementation")
        pluginVerifier()
    }
    implementation("com.typesafe:config:1.4.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.hamcrest:hamcrest:3.0")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
    testImplementation("io.mockk:mockk:1.14.0") {
        exclude(group = "org.jetbrains.kotlin")
    }

    uiTestImplementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}
