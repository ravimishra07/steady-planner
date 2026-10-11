pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../android/gradle/libs.versions.toml")) }
    }
}
rootProject.name = "steadyline-onboarding"
include(":app")
