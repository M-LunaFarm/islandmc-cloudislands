pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.extendedclip.com/releases/")
    }
}

rootProject.name = "cloudislands"

include(
    "cloudislands-api",
    "cloudislands-common",
    "cloudislands-protocol",
    "cloudislands-core-client",
    "cloudislands-core-service",
    "cloudislands-velocity",
    "cloudislands-paper",
    "cloudislands-storage",
    "cloudislands-migration",
    "cloudislands-testkit",
    "cloudislands-bom"
)
