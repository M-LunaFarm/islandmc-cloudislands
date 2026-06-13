import org.gradle.jvm.tasks.Jar

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
