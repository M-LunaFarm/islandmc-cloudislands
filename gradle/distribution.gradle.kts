import org.gradle.api.file.FileTreeElement
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

fun cloudIslandsJsonEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

fun cloudIslandsJsonString(value: String): String = "\"${cloudIslandsJsonEscape(value)}\""

val cloudIslandsMarkdownDocPatterns = listOf(
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

val cloudIslandsMarkdownDocExtensions = listOf(".md", ".mdx", ".mdown", ".mkdn", ".markdown", ".mkd")
val cloudIslandsDeveloperKitProjectNames = listOf(
    "cloudislands-api",
    "cloudislands-common",
    "cloudislands-protocol",
    "cloudislands-core-client",
    "cloudislands-storage",
    "cloudislands-migration",
    "cloudislands-testkit"
)
val cloudIslandsExampleAddonProjectNames = listOf(
    "cloudislands-example-addon"
)

fun cloudIslandsIsMarkdownDocPath(path: String): Boolean =
    path.replace('\\', '/') != "README.md" && cloudIslandsMarkdownDocExtensions.any { path.lowercase().endsWith(it) }

fun cloudIslandsIsMarkdownDocElement(element: FileTreeElement): Boolean =
    cloudIslandsIsMarkdownDocPath(element.path)

tasks.register<Copy>("distPlugins") {
    group = "distribution"
    description = "Collects required CloudIslands plugin jars."
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
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
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
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
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
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
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
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
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/tools"))
    }

    val migrationJar = project(":cloudislands-migration").tasks.named<Jar>("jar")
    dependsOn(migrationJar)
    from(migrationJar.flatMap { it.archiveFile })
    into(layout.buildDirectory.dir("dist/tools"))
}

val cleanDeveloperKitMaven = tasks.register<Delete>("cleanDeveloperKitMaven") {
    delete(layout.buildDirectory.dir("devkit-maven"))
}

tasks.register<Copy>("distDeveloperKit") {
    group = "distribution"
    description = "Collects API, client, protocol, testkit, BOM, Javadocs, and Maven-consumable artifacts for addon/plugin developers."
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/devkit"))
    }
    dependsOn(cleanDeveloperKitMaven)

    cloudIslandsDeveloperKitProjectNames.forEach { projectName ->
        val jarTask = project(":$projectName").tasks.named<Jar>("jar")
        val sourcesJarTask = project(":$projectName").tasks.named<Jar>("sourcesJar")
        val javadocJarTask = project(":$projectName").tasks.named<Jar>("javadocJar")
        val publishTask = project(":$projectName").tasks.named("publishMavenJavaPublicationToDeveloperKitRepository")
        publishTask.configure {
            mustRunAfter(cleanDeveloperKitMaven)
        }
        dependsOn(jarTask)
        dependsOn(sourcesJarTask)
        dependsOn(javadocJarTask)
        dependsOn(publishTask)
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
    from(layout.buildDirectory.dir("devkit-maven")) {
        into("maven")
    }
    cloudIslandsExampleAddonProjectNames.forEach { projectName ->
        val exampleProject = project(":$projectName")
        dependsOn(exampleProject.tasks.named("test"))
        from(exampleProject.layout.projectDirectory) {
            into("examples/$projectName")
            exclude("build/**", ".gradle/**")
        }
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
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
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
    exclude(cloudIslandsMarkdownDocPatterns)
    exclude { element: FileTreeElement -> cloudIslandsIsMarkdownDocElement(element) }
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

val distSbomFile = layout.buildDirectory.file("dist/cloudislands-sbom.cdx.json")
val distProvenanceFile = layout.buildDirectory.file("dist/provenance.json")

tasks.register("distSbom") {
    group = "distribution"
    description = "Writes a CycloneDX-style SBOM from locked release dependencies."
    val lockfiles = provider {
        subprojects
            .filter { it.name != "cloudislands-bom" }
            .map { it.layout.projectDirectory.file("gradle.lockfile").asFile }
    }
    inputs.files(lockfiles)
    outputs.file(distSbomFile)
    doLast {
        val components = linkedMapOf<String, Triple<String, String, String>>()
        subprojects
            .filter { it.name != "cloudislands-bom" }
            .forEach { project ->
                val lockfile = project.layout.projectDirectory.file("gradle.lockfile").asFile
                if (!lockfile.isFile) {
                    throw GradleException("Missing dependency lockfile for ${project.path}: ${lockfile.relativeTo(rootProject.projectDir)}")
                }
                lockfile.readLines()
                    .map(String::trim)
                    .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("empty=") }
                    .forEach { line ->
                        val coordinate = line.substringBefore("=")
                        val parts = coordinate.split(":")
                        if (parts.size != 3) {
                            throw GradleException("Unsupported dependency lock entry in ${lockfile.relativeTo(rootProject.projectDir)}: $line")
                        }
                        val key = "${parts[0]}:${parts[1]}:${parts[2]}"
                        components.putIfAbsent(key, Triple(parts[0], parts[1], parts[2]))
                    }
            }
        val output = distSbomFile.get().asFile
        output.parentFile.mkdirs()
        val componentJson = components.values.joinToString(",\n") { (group, name, version) ->
            val purl = "pkg:maven/${group}/${name}@${version}"
            """    {"type":"library","group":${cloudIslandsJsonString(group)},"name":${cloudIslandsJsonString(name)},"version":${cloudIslandsJsonString(version)},"purl":${cloudIslandsJsonString(purl)}}"""
        }
        output.writeText(
            """
            |{
            |  "bomFormat": "CycloneDX",
            |  "specVersion": "1.5",
            |  "version": 1,
            |  "metadata": {
            |    "component": {
            |      "type": "application",
            |      "name": "cloudislands",
            |      "version": ${cloudIslandsJsonString(project.version.toString())}
            |    }
            |  },
            |  "components": [
            |$componentJson
            |  ]
            |}
            |
            """.trimMargin()
        )
    }
}

tasks.register("distProvenance") {
    group = "distribution"
    description = "Writes release provenance for distribution artifacts and security manifests."
    dependsOn(tasks.named("distChecksums"))
    dependsOn(tasks.named("distSbom"))
    inputs.file(layout.buildDirectory.file("dist/checksums-sha256.txt"))
    inputs.file(distSbomFile)
    outputs.file(distProvenanceFile)
    doLast {
        fun commandOutput(vararg command: String): String {
            val output = ByteArrayOutputStream()
            exec {
                commandLine(*command)
                standardOutput = output
            }
            return output.toString().trim()
        }
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(file.readBytes()).joinToString("") { byte: Byte -> "%02x".format(byte) }
        }
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        val checksumFile = distDir.resolve("checksums-sha256.txt")
        if (!checksumFile.isFile) {
            throw GradleException("Missing distribution checksum file: ${checksumFile.absolutePath}")
        }
        val artifacts = checksumFile.readLines()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size != 2) {
                    throw GradleException("Invalid checksum line: $line")
                }
                parts[1] to parts[0]
            }
            .toMutableList()
        val sbom = distSbomFile.get().asFile
        artifacts.add(sbom.relativeTo(distDir).path.replace('\\', '/') to sha256(sbom))
        val artifactJson = artifacts.joinToString(",\n") { (path, checksum) ->
            """    {"path":${cloudIslandsJsonString(path)},"sha256":${cloudIslandsJsonString(checksum)}}"""
        }
        val output = distProvenanceFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            |{
            |  "project": "cloudislands",
            |  "version": ${cloudIslandsJsonString(project.version.toString())},
            |  "commit": ${cloudIslandsJsonString(commandOutput("git", "rev-parse", "HEAD"))},
            |  "dirty": ${cloudIslandsJsonString(commandOutput("git", "status", "--porcelain"))},
            |  "javaVersion": ${cloudIslandsJsonString(System.getProperty("java.version"))},
            |  "gradleVersion": ${cloudIslandsJsonString(gradle.gradleVersion)},
            |  "artifacts": [
            |$artifactJson
            |  ]
            |}
            |
            """.trimMargin()
        )
    }
}

tasks.register("verifyReleaseSecurityGate") {
    group = "verification"
    description = "Verifies release SBOM, provenance, vulnerability review, and dependency-lock gates are wired."
    inputs.file(layout.projectDirectory.file(".github/workflows/build.yml"))
    inputs.file(layout.projectDirectory.file(".github/dependabot.yml"))
    inputs.files(provider {
        subprojects
            .filter { it.name != "cloudislands-bom" }
            .map { it.layout.projectDirectory.file("gradle.lockfile").asFile }
    })
    doLast {
        val buildWorkflow = layout.projectDirectory.file(".github/workflows/build.yml").asFile.readText()
        val dependabot = layout.projectDirectory.file(".github/dependabot.yml").asFile.readText()
        val requiredWorkflowSignals = listOf(
            "actions/dependency-review-action@v4",
            "fail-on-severity: high",
            "distSbom",
            "distProvenance",
            "verifyReleaseSecurityGate",
            "cloudislands-sbom.cdx.json",
            "provenance.json"
        )
        val missingWorkflowSignals = requiredWorkflowSignals.filterNot(buildWorkflow::contains)
        if (missingWorkflowSignals.isNotEmpty()) {
            throw GradleException("Build workflow is missing release security gate signals: ${missingWorkflowSignals.joinToString(", ")}")
        }
        if (!dependabot.contains("package-ecosystem: \"gradle\"") && !dependabot.contains("package-ecosystem: gradle")) {
            throw GradleException("Dependabot must scan Gradle dependencies")
        }
        val missingLockfiles = subprojects
            .filter { it.name != "cloudislands-bom" }
            .map { it.layout.projectDirectory.file("gradle.lockfile").asFile }
            .filterNot(File::isFile)
        if (missingLockfiles.isNotEmpty()) {
            throw GradleException("Missing dependency lockfiles: ${missingLockfiles.joinToString(", ") { it.relativeTo(rootProject.projectDir).path }}")
        }
    }
}
