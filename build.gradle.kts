buildscript {
    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("jvm") version "1.5.30"
    id("org.jetbrains.intellij") version "1.1.6"
}


repositories {
    mavenCentral()
}

group = "io.github.mishkun"
version = "1.0"

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    version.set("2020.3")
}

tasks.getByName<org.jetbrains.intellij.tasks.PatchPluginXmlTask>("patchPluginXml") {
    changeNotes.set("""
        Initial release
    """.trimIndent())
}

dependencies {
    implementation("com.typesafe:config:1.4.1")
}
