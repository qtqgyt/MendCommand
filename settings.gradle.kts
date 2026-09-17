pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9-alpha.7"
}

rootProject.name = "MendCommand"

stonecutter {
    create(rootProject) {
        versions("26.2", "26.3")
    }
}