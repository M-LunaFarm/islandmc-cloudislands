import org.gradle.api.file.FileTreeElement
import org.gradle.jvm.tasks.Jar
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.VersionCatalogsExtension
import java.security.MessageDigest

plugins {
    `java-library`
    alias(libs.plugins.shadow) apply false
}

val markdownDocPatterns = listOf(
    "**/*.md",
    "**/*.MD",
    "**/*.mdx",
    "**/*.MDX",
    "**/*.mdown",
    "**/*.MDOWN",
    "**/*.mkdn",
    "**/*.MKDN",
    "**/*.markdown",
    "**/*.MARKDOWN",
    "**/*.mkd",
    "**/*.MKD"
)

val markdownDocExtensions = listOf(".md", ".mdx", ".mdown", ".mkdn", ".markdown", ".mkd")
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val cloudislandsVersion = versionCatalog.findVersion("cloudislands").orElseThrow().requiredVersion
val javaCurrentVersion = versionCatalog.findVersion("java-current").orElseThrow().requiredVersion.toInt()
val minecraftBaselineVersion = versionCatalog.findVersion("minecraft-baseline").orElseThrow().requiredVersion
val developerKitProjectNames = listOf(
    "cloudislands-api",
    "cloudislands-common",
    "cloudislands-protocol",
    "cloudislands-core-client",
    "cloudislands-storage",
    "cloudislands-migration",
    "cloudislands-testkit"
)

fun isMarkdownDocPath(path: String): Boolean =
    path.replace('\\', '/') != "README.md" && markdownDocExtensions.any { path.lowercase().endsWith(it) }

fun isMarkdownDocElement(element: FileTreeElement): Boolean =
    isMarkdownDocPath(element.path)

allprojects {
    group = "kr.lunaf.cloudislands"
    version = cloudislandsVersion

    dependencyLocking {
        lockAllConfigurations()
    }
}

subprojects {
    if (name != "cloudislands-bom") {
        apply(plugin = "java-library")

        java {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(javaCurrentVersion))
            }
            withSourcesJar()
            if (name in developerKitProjectNames) {
                withJavadocJar()
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
        }

        tasks.withType<Javadoc>().configureEach {
            options.encoding = "UTF-8"
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }

        tasks.withType<Test>().configureEach {
            systemProperty("cloudislands.version", project.version.toString())
            systemProperty("cloudislands.minecraftBaseline", minecraftBaselineVersion)
        }

        tasks.withType<Jar>().configureEach {
            exclude(markdownDocPatterns)
            exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
        }
    }
}

tasks.register("verifyMarkdownDocsExcludedFromArtifacts") {
    group = "verification"
    description = "Verifies markdown documents are allowed in source but excluded from packaged artifacts."
    doLast {
        val markdownFiles = fileTree(projectDir) {
            exclude(".git/**", ".gradle/**", "**/build/**", "**/.gradle/**")
        }.files
            .filter { isMarkdownDocPath(it.relativeTo(projectDir).path) }
            .sortedBy { it.relativeTo(projectDir).path }
        if (markdownFiles.isNotEmpty()) {
            logger.lifecycle(
                "Markdown source documents are allowed and will be excluded from packaged artifacts: {}",
                markdownFiles.joinToString(", ") { it.relativeTo(projectDir).path }
            )
        }
    }
}

tasks.named("build") {
    dependsOn(tasks.named("verifyMarkdownDocsExcludedFromArtifacts"))
}

tasks.named("check") {
    dependsOn(tasks.named("verifyMarkdownDocsExcludedFromArtifacts"))
}

tasks.register<Copy>("distPlugins") {
    group = "distribution"
    description = "Collects required CloudIslands plugin jars."
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/plugins"))
    }

    val pluginProjects = listOf(
        "cloudislands-paper",
        "cloudislands-velocity"
    )
    pluginProjects.forEach { projectName ->
        val jarTask = project(":$projectName").tasks.named<Jar>("shadowJar")
        dependsOn(jarTask)
        from(jarTask.flatMap { it.archiveFile })
    }
    into(layout.buildDirectory.dir("dist/plugins"))
}

tasks.register<Copy>("distAddons") {
    group = "distribution"
    description = "Collects optional CloudIslands addon jars."
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/addons"))
    }

    val addonProjects = listOf(
        "cloudislands-satis"
    )
    addonProjects.forEach { projectName ->
        val addonProject = project(":$projectName")
        val jarTask = addonProject.tasks.named<Jar>("shadowJar")
        dependsOn(jarTask)
        from(jarTask.flatMap { it.archiveFile })
    }
    into(layout.buildDirectory.dir("dist/addons"))
}

tasks.register<Copy>("distAddonDescriptors") {
    group = "distribution"
    description = "Collects optional CloudIslands addon descriptors separately from addon jars."
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/addon-descriptors"))
    }

    val addonProjects = listOf(
        "cloudislands-satis"
    )
    addonProjects.forEach { projectName ->
        val addonProject = project(":$projectName")
        from(addonProject.layout.projectDirectory.file("src/main/resources/cloudislands-addon.yml")) {
            rename { "$projectName.yml" }
        }
    }
    into(layout.buildDirectory.dir("dist/addon-descriptors"))
}

tasks.register<Copy>("distServices") {
    group = "distribution"
    description = "Collects CloudIslands service runtime images."
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/services"))
    }

    val coreService = project(":cloudislands-core-service")
    val installTask = coreService.tasks.named("installDist")
    dependsOn(installTask)
    from(coreService.layout.buildDirectory.dir("install/cloudislands-core-service"))
    into(layout.buildDirectory.dir("dist/services/core"))
}

tasks.register<Copy>("distTools") {
    group = "distribution"
    description = "Collects CloudIslands migration support jars used by the Core admin API."
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/tools"))
    }

    val migrationJar = project(":cloudislands-migration").tasks.named<Jar>("jar")
    dependsOn(migrationJar)
    from(migrationJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("dist/tools"))
}

tasks.register<Copy>("distDeveloperKit") {
    group = "distribution"
    description = "Collects API, client, protocol, testkit, and BOM artifacts for addon/plugin developers."
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/devkit"))
    }

    developerKitProjectNames.forEach { projectName ->
        val jarTask = project(":$projectName").tasks.named<Jar>("jar")
        val sourcesJarTask = project(":$projectName").tasks.named<Jar>("sourcesJar")
        val javadocJarTask = project(":$projectName").tasks.named<Jar>("javadocJar")
        dependsOn(jarTask)
        dependsOn(sourcesJarTask)
        dependsOn(javadocJarTask)
        from(jarTask.flatMap { it.archiveFile }) {
            into("libs")
        }
        from(sourcesJarTask.flatMap { it.archiveFile }) {
            into("sources")
        }
        from(javadocJarTask.flatMap { it.archiveFile }) {
            into("javadocs")
        }
    }

    val bomProject = project(":cloudislands-bom")
    val bomPomTask = bomProject.tasks.named("generatePomFileForCloudIslandsBomPublication")
    dependsOn(bomPomTask)
    from(bomProject.layout.buildDirectory.file("publications/cloudIslandsBom/pom-default.xml")) {
        rename { "cloudislands-bom-${project.version}.pom" }
        into("bom")
    }
    into(layout.buildDirectory.dir("dist/devkit"))
}

tasks.register<Zip>("distBundle") {
    group = "distribution"
    description = "Packages the CloudIslands plugins, optional addons, Core API service runtime, migration support jars, and developer artifacts."
    dependsOn(tasks.named("verifyMarkdownDocsExcludedFromArtifacts"))
    dependsOn(tasks.named("distPlugins"))
    dependsOn(tasks.named("distAddons"))
    dependsOn(tasks.named("distAddonDescriptors"))
    dependsOn(tasks.named("distServices"))
    dependsOn(tasks.named("distTools"))
    dependsOn(tasks.named("distDeveloperKit"))
    archiveBaseName.set("cloudislands")
    archiveVersion.set(project.version.toString())
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(archiveFile)
    }
    from(layout.buildDirectory.dir("dist/plugins")) {
        into("plugins")
    }
    from(layout.buildDirectory.dir("dist/addons")) {
        into("addons")
    }
    from(layout.buildDirectory.dir("dist/addon-descriptors")) {
        into("addon-descriptors")
    }
    from(layout.buildDirectory.dir("dist/services")) {
        into("services")
    }
    from(layout.buildDirectory.dir("dist/tools")) {
        into("tools")
    }
    from(layout.buildDirectory.dir("dist/devkit")) {
        into("devkit")
    }
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

tasks.register<Zip>("distAddonBundle") {
    group = "distribution"
    description = "Packages optional CloudIslands addon jars separately from the required core bundle."
    dependsOn(tasks.named("verifyMarkdownDocsExcludedFromArtifacts"))
    dependsOn(tasks.named("distAddons"))
    dependsOn(tasks.named("distAddonDescriptors"))
    archiveBaseName.set("cloudislands-addons")
    archiveVersion.set(project.version.toString())
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(archiveFile)
    }
    from(layout.buildDirectory.dir("dist/addons")) {
        into("addons")
    }
    from(layout.buildDirectory.dir("dist/addon-descriptors")) {
        into("addon-descriptors")
    }
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}

tasks.register("distChecksums") {
    group = "distribution"
    description = "Writes SHA-256 checksums for distribution archives and plugin jars."
    dependsOn(tasks.named("distBundle"))
    dependsOn(tasks.named("distAddonBundle"))
    doLast {
        val files = fileTree(layout.buildDirectory.dir("dist")) {
            include("**/*.zip")
            include("**/*.jar")
        }.files.sortedBy { it.relativeTo(layout.buildDirectory.dir("dist").get().asFile).path }
        val digest = MessageDigest.getInstance("SHA-256")
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        val output = distDir.resolve("checksums-sha256.txt")
        output.parentFile.mkdirs()
        output.writeText(files.joinToString(System.lineSeparator()) { file ->
            digest.reset()
            val checksum = digest.digest(file.readBytes()).joinToString("") { byte: Byte -> "%02x".format(byte) }
            "$checksum  ${file.relativeTo(distDir).path.replace('\\', '/')}"
        } + System.lineSeparator())
    }
}

tasks.register<Exec>("paperBootSmoke") {
    group = "verification"
    description = "Boots a supported Paper server and verifies the CloudIslands Paper plugin loads."
    val paperJar = project(":cloudislands-paper").tasks.named<Jar>("shadowJar")
    dependsOn(paperJar)
    doFirst {
        commandLine(
            "python3",
            file("scripts/ci/papermc_smoke.py").absolutePath,
            "--project", "paper",
            "--version", minecraftBaselineVersion,
            "--plugin", paperJar.get().archiveFile.get().asFile.absolutePath,
            "--work-dir", layout.buildDirectory.dir("smoke/paper-$minecraftBaselineVersion").get().asFile.absolutePath,
            "--cache-dir", layout.buildDirectory.dir("smoke/cache").get().asFile.absolutePath,
            "--timeout", "240"
        )
    }
}

tasks.register<Exec>("velocityBootSmoke") {
    group = "verification"
    description = "Boots a supported Velocity proxy and verifies the CloudIslands Velocity plugin loads."
    val velocityJar = project(":cloudislands-velocity").tasks.named<Jar>("shadowJar")
    dependsOn(velocityJar)
    doFirst {
        commandLine(
            "python3",
            file("scripts/ci/papermc_smoke.py").absolutePath,
            "--project", "velocity",
            "--version", versionCatalog.findVersion("velocity-api").orElseThrow().requiredVersion,
            "--plugin", velocityJar.get().archiveFile.get().asFile.absolutePath,
            "--work-dir", layout.buildDirectory.dir("smoke/velocity").get().asFile.absolutePath,
            "--cache-dir", layout.buildDirectory.dir("smoke/cache").get().asFile.absolutePath,
            "--timeout", "180"
        )
    }
}

tasks.register("ciBootSmoke") {
    group = "verification"
    description = "Runs supported Paper and Velocity boot smoke tests."
    dependsOn(tasks.named("paperBootSmoke"))
    dependsOn(tasks.named("velocityBootSmoke"))
}

tasks.register<Exec>("coreIntegrationSmoke") {
    group = "verification"
    description = "Runs Core API integration smoke against PostgreSQL, Redis, and S3-compatible object storage."
    val coreService = project(":cloudislands-core-service")
    val installTask = coreService.tasks.named("installDist")
    dependsOn(installTask)
    doFirst {
        commandLine(
            "python3",
            file("scripts/ci/core_integration_smoke.py").absolutePath,
            "--core-bin",
            coreService.layout.buildDirectory.file("install/cloudislands-core-service/bin/cloudislands-core-service").get().asFile.absolutePath,
            "--work-dir",
            layout.buildDirectory.dir("smoke/core-integration").get().asFile.absolutePath,
            "--port",
            "18443",
            "--timeout",
            "90"
        )
    }
}

tasks.register("ciIntegrationSmoke") {
    group = "verification"
    description = "Runs Core API real-infrastructure integration smoke tests."
    dependsOn(tasks.named("coreIntegrationSmoke"))
}
