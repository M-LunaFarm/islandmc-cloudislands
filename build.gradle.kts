import org.gradle.jvm.tasks.Jar
import org.gradle.api.tasks.bundling.Zip

plugins {
    `java-library`
}

allprojects {
    group = "kr.lunaf.cloudislands"
    version = "0.1.0"
}

subprojects {
    if (name != "cloudislands-bom") {
        apply(plugin = "java-library")

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withSourcesJar()
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        }
    }
}

tasks.register<Copy>("distPlugins") {
    group = "distribution"
    description = "Collects CloudIslands plugin jars, including the Satis addon."

    val pluginProjects = listOf(
        "cloudislands-paper",
        "cloudislands-velocity",
        "cloudislands-satis"
    )
    pluginProjects.forEach { projectName ->
        val jarTask = project(":$projectName").tasks.named<Jar>("jar")
        dependsOn(jarTask)
        from(jarTask.flatMap { it.archiveFile })
    }
    into(layout.buildDirectory.dir("dist/plugins"))
}

tasks.register<Copy>("distServices") {
    group = "distribution"
    description = "Collects CloudIslands service runtime images."

    val coreService = project(":cloudislands-core-service")
    val installTask = coreService.tasks.named("installDist")
    dependsOn(installTask)
    from(coreService.layout.buildDirectory.dir("install/cloudislands-core-service"))
    into(layout.buildDirectory.dir("dist/services/core"))
}

tasks.register<Zip>("distBundle") {
    group = "distribution"
    description = "Packages CloudIslands plugin jars and Core API service runtime."
    dependsOn(tasks.named("distPlugins"))
    dependsOn(tasks.named("distServices"))
    archiveBaseName.set("cloudislands-plugins")
    archiveVersion.set(project.version.toString())
    from(layout.buildDirectory.dir("dist/plugins")) {
        into("plugins")
    }
    from(layout.buildDirectory.dir("dist/services")) {
        into("services")
    }
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}
