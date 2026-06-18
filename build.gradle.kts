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

tasks.register("verifyNoMarkdownDocs") {
    group = "verification"
    description = "Fails when markdown documents are present in this deliverable repository."
    doLast {
        val markdownFiles = fileTree(projectDir) {
            include("**/*.md", "**/*.MD", "**/*.mdx", "**/*.MDX", "**/*.markdown", "**/*.MARKDOWN", "**/*.mkd", "**/*.MKD")
            exclude(".git/**", ".gradle/**", "**/build/**", "**/.gradle/**")
        }.files.sortedBy { it.relativeTo(projectDir).path }
        check(markdownFiles.isEmpty()) {
            "Markdown documents are not allowed in result/: " + markdownFiles.joinToString(", ") { it.relativeTo(projectDir).path }
        }
    }
}

tasks.named("build") {
    dependsOn(tasks.named("verifyNoMarkdownDocs"))
}

tasks.register<Copy>("distPlugins") {
    group = "distribution"
    description = "Collects required CloudIslands plugin jars."

    val pluginProjects = listOf(
        "cloudislands-paper",
        "cloudislands-velocity"
    )
    pluginProjects.forEach { projectName ->
        val jarTask = project(":$projectName").tasks.named<Jar>("jar")
        dependsOn(jarTask)
        from(jarTask.flatMap { it.archiveFile })
    }
    into(layout.buildDirectory.dir("dist/plugins"))
}

tasks.register<Copy>("distAddons") {
    group = "distribution"
    description = "Collects optional CloudIslands addon jars."

    val addonProjects = listOf(
        "cloudislands-satis"
    )
    addonProjects.forEach { projectName ->
        val jarTask = project(":$projectName").tasks.named<Jar>("jar")
        dependsOn(jarTask)
        from(jarTask.flatMap { it.archiveFile })
    }
    into(layout.buildDirectory.dir("dist/addons"))
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

tasks.register<Copy>("distTools") {
    group = "distribution"
    description = "Collects CloudIslands migration support jars used by the Core admin API."

    val migrationJar = project(":cloudislands-migration").tasks.named<Jar>("jar")
    dependsOn(migrationJar)
    from(migrationJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("dist/tools"))
}

tasks.register<Zip>("distBundle") {
    group = "distribution"
    description = "Packages the CloudIslands plugins, optional addons, Core API service runtime, and migration support jars."
    dependsOn(tasks.named("verifyNoMarkdownDocs"))
    dependsOn(tasks.named("distPlugins"))
    dependsOn(tasks.named("distAddons"))
    dependsOn(tasks.named("distServices"))
    dependsOn(tasks.named("distTools"))
    archiveBaseName.set("cloudislands")
    archiveVersion.set(project.version.toString())
    from(layout.buildDirectory.dir("dist/plugins")) {
        into("plugins")
    }
    from(layout.buildDirectory.dir("dist/addons")) {
        into("addons")
    }
    from(layout.buildDirectory.dir("dist/services")) {
        into("services")
    }
    from(layout.buildDirectory.dir("dist/tools")) {
        into("tools")
    }
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

tasks.register<Zip>("distAddonBundle") {
    group = "distribution"
    description = "Packages optional CloudIslands addon jars separately from the required core bundle."
    dependsOn(tasks.named("verifyNoMarkdownDocs"))
    dependsOn(tasks.named("distAddons"))
    archiveBaseName.set("cloudislands-addons")
    archiveVersion.set(project.version.toString())
    from(layout.buildDirectory.dir("dist/addons")) {
        into("addons")
    }
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}
