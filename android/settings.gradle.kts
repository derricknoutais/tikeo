pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Le SDK d'impression Sunmi (com.sunmi:printerlibrary) est sur Maven Central.
        mavenCentral()
    }
}

rootProject.name = "tikeo"
include(":app")

// Les pilotes vivent dans leurs propres dépôts, clonés à côté de celui-ci :
// ~/Herd/sunmi-print et ~/Herd/zcs-print. Ils entrent dans cette construction
// comme des modules ordinaires — un seul Gradle, un seul plugin Android.
include(":pilote-sunmi")
project(":pilote-sunmi").projectDir = file("../../sunmi-print/android/pilote")
include(":pilote-zcs")
project(":pilote-zcs").projectDir = file("../../zcs-print/android/pilote")
// Le SDK du fabricant, dont le pilote ZCS dépend, vit dans le même dépôt que lui.
include(":sdk-zcs")
project(":sdk-zcs").projectDir = file("../../zcs-print/android/sdk")
