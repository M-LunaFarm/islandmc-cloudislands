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
        maven("https://jitpack.io")
    }
}

rootProject.name = "cloudislands"

extensions.extraProperties["cloudislandsVersionMatrixFile"] = file("gradle/minecraft-versions.toml")

include(
    "cloudislands-api",
    "cloudislands-common",
    "cloudislands-protocol",
    "cloudislands-core-client",
    "cloudislands-core-service",
    "cloudislands-velocity",
    "cloudislands-paper",
    "cloudislands-satis",
    "cloudislands-storage",
    "cloudislands-migration",
    "cloudislands-testkit",
    "cloudislands-example-addon",
    "cloudislands-bom"
)
