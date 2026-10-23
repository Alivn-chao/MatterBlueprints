
pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.gtnewhorizons.gtnhsettingsconvention") {
                useModule("com.gtnewhorizons:gtnhgradle:${requested.version}")
            }
        }
    }
    repositories {
        maven("https://maven.wagyourtail.xyz/releases") {
            content { includeGroup("xyz.wagyourtail.jvmdowngrader") }
        }
        // Keep mainland mirrors for local builds; GitHub runners use the upstream repositories.
        if (System.getenv("GITHUB_ACTIONS") != "true") {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/central")
        }
        maven {
            // RetroFuturaGradle
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version("2.0.29")
}
