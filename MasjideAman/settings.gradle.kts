pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Jitpack yahan sahi syntax ke saath add karein
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Masjid-e-Aman"
include(":app")