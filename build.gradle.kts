buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.8.21"
    id("org.jetbrains.intellij") version "1.14.2"
}


repositories {
    mavenCentral()
}

group = "io.github.mishkun"
version = "1.1.1"

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("2023.1")
}

tasks.getByName<org.jetbrains.intellij.tasks.PatchPluginXmlTask>("patchPluginXml") {
    changeNotes.set("""
        Parse config file on startup to speedup action popup
    """.trimIndent())
    sinceBuild.set("201")
    untilBuild.set("")
}

dependencies {
    implementation("com.typesafe:config:1.4.2")
}
