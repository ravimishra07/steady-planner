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
        mavenCentral()
    }
}

rootProject.name = "steadyline"

include(":app")
include(":domain")
include(":core:common")
include(":core:design")
include(":core:data")
include(":feature:onboarding")
include(":feature:home")
include(":feature:syllabus")
include(":feature:focus")
include(":feature:progress")
include(":feature:settings")
