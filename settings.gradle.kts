// SPDX-License-Identifier: AGPL-3.0-only
// Google's Maven Central mirror goes first: repo.maven.apache.org answers HTTP 429 to shared
// cloud IPs, and Gradle aborts on a 429 instead of trying the next repository. A mirror miss
// (404) still falls through to mavenCentral().
pluginManagement {
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(url = "https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "TeslaBatteryBLE"
include(":app")
include(":core")
include(":protocol")
