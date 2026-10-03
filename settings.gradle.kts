pluginManagement {
    repositories {
        google {
            // https://maven.google.com/web/index.html
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx?\\..*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                // for com.github.topjohnwu.libsu
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

include(":app")
rootProject.name = "OS Updater"
