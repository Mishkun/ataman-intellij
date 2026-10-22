import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.io.File

plugins {
    kotlin("jvm") version "2.0.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
}

val platformVersion = providers.gradleProperty("platformVersion")
val pluginVerificationVersions = providers.gradleProperty("pluginVerificationVersions").map { versions ->
    versions.split(',').map(String::trim).filter(String::isNotEmpty).distinct().also {
        require(it.isNotEmpty()) { "pluginVerificationVersions must contain at least one IDE version" }
    }
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
    this.type = IntelliJPlatformType.fromCode("IU", platformVersion.get())
    version.set(platformVersion)
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
version = "2.1.1"

intellijPlatform {
    instrumentCode.set(false)
    pluginConfiguration {
        changeNotes = providers.fileContents(layout.projectDirectory.file("changelog.txt"))
            .asText.orElse("")
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = "262.*"
        }
    }
    publishing {
        token = providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN")
        channels.set(listOf("beta"))
        channels.disallowChanges()
    }
    pluginVerification {
        // Keep API warnings visible; compatibility errors block the release.
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
        )
        ides {
            val explicitVersions = pluginVerificationVersions.orNull
            if (explicitVersions != null) {
                explicitVersions.forEach { ideVersion ->
                    create(IntelliJPlatformType.IntellijIdea, ideVersion)
                }
                return@ides
            }
            create(IntelliJPlatformType.IntellijIdea, "2024.3")
            latest {
                types.set(listOf(IntelliJPlatformType.IntellijIdea))
                channels.set(listOf(ProductRelease.Channel.RELEASE))
                sinceBuild = "243"
                untilBuild = "262.*"
            }
        }
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(platformVersion)
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

tasks.verifyPlugin {
    dependsOn(tasks.buildPlugin)
}

// Publishing must never fall back to a newly built ZIP or the default channel.
val publishingArchive = layout.file(providers.gradleProperty("publishingArchive").map { path ->
    File(path).also {
        require(it.isAbsolute && it.isFile) { "publishingArchive must be an existing absolute file" }
    }
})

tasks.publishPlugin {
    // The plugin otherwise adds explicit buildPlugin/signPlugin dependencies,
    // even when archiveFiles is overridden with a frozen external ZIP.
    setDependsOn(emptyList<Any>())
    archiveFiles.setFrom(publishingArchive)
    archiveFiles.disallowChanges()
    channels.set(intellijPlatform.publishing.channels)
    channels.disallowChanges()
    doFirst {
        require(channels.get() == listOf("beta")) { "Only beta publication is allowed" }
        require(!providers.environmentVariable("JETBRAINS_MARKETPLACE_TOKEN").orNull.isNullOrBlank()) {
            "JETBRAINS_MARKETPLACE_TOKEN is required"
        }
        require(publishingArchive.isPresent) { "The frozen publishingArchive is required" }
        require(archiveFiles.files == setOf(publishingArchive.get().asFile)) { "Only the frozen ZIP may be published" }
    }
}
