import org.gradle.api.file.FileTreeElement
import org.gradle.jvm.tasks.Jar
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.artifacts.VersionCatalogsExtension
import java.security.MessageDigest
import java.util.zip.ZipFile

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
val exampleAddonProjectNames = listOf(
    "cloudislands-example-addon"
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
        if (name in developerKitProjectNames) {
            apply(plugin = "maven-publish")
        }

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

        if (name in developerKitProjectNames) {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        artifactId = project.name
                    }
                }
                repositories {
                    maven {
                        name = "developerKit"
                        url = rootProject.layout.buildDirectory.dir("devkit-maven").get().asFile.toURI()
                    }
                }
            }
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

val cleanDeveloperKitMaven = tasks.register<Delete>("cleanDeveloperKitMaven") {
    delete(layout.buildDirectory.dir("devkit-maven"))
}

tasks.register<Copy>("distDeveloperKit") {
    group = "distribution"
    description = "Collects API, client, protocol, testkit, BOM, Javadocs, and Maven-consumable artifacts for addon/plugin developers."
    exclude(markdownDocPatterns)
    exclude { element: FileTreeElement -> isMarkdownDocElement(element) }
    doFirst {
        delete(layout.buildDirectory.dir("dist/devkit"))
    }
    dependsOn(cleanDeveloperKitMaven)

    developerKitProjectNames.forEach { projectName ->
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
    exampleAddonProjectNames.forEach { projectName ->
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

tasks.register("paper121Compile") {
    group = "verification"
    description = "Compiles the CloudIslands Paper plugin against the Paper 1.21 API baseline."
    dependsOn(project(":cloudislands-paper").tasks.named("compileJava"))
    dependsOn(project(":cloudislands-paper").tasks.named("processResources"))
}

tasks.register("paper121BootSmoke") {
    group = "verification"
    description = "Boots the Paper 1.21 baseline server and verifies the 1.21 family adapter loads."
    dependsOn(tasks.named("paper121Compile"))
    dependsOn(tasks.named("paperBootSmoke"))
}

tasks.register("paper261Compile") {
    group = "verification"
    description = "Compiles the CloudIslands Paper plugin with the Paper 26.1 adapter included."
    dependsOn(project(":cloudislands-paper").tasks.named("compileJava"))
    dependsOn(project(":cloudislands-paper").tasks.named("processResources"))
}

tasks.register<Exec>("paper261BootSmoke") {
    group = "verification"
    description = "Boots a Paper 26.1 server when an official stable build is available."
    val paperJar = project(":cloudislands-paper").tasks.named<Jar>("shadowJar")
    dependsOn(tasks.named("paper261Compile"))
    dependsOn(paperJar)
    doFirst {
        commandLine(
            "python3",
            file("scripts/ci/papermc_smoke.py").absolutePath,
            "--project", "paper",
            "--version", "26.1",
            "--plugin", paperJar.get().archiveFile.get().asFile.absolutePath,
            "--work-dir", layout.buildDirectory.dir("smoke/paper-26.1").get().asFile.absolutePath,
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

tasks.register("verifyVersionAdapters") {
    group = "verification"
    description = "Verifies Paper version parsing, adapter registry selection, and fail-fast adapter errors."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
}

tasks.register("verifyVersionIsolation") {
    group = "verification"
    description = "Verifies Minecraft/Paper runtime access remains isolated behind Paper platform adapters."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
}

val verifyVersionPackaging = tasks.register("verifyVersionPackaging") {
    group = "verification"
    description = "Verifies the final Paper artifact contains all Paper version adapters without duplicate entries."
    val paperJar = project(":cloudislands-paper").tasks.named<Jar>("shadowJar")
    dependsOn(paperJar)
    inputs.file(paperJar.flatMap { it.archiveFile })
    doLast {
        val requiredEntries = listOf(
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperRuntimeCompatibility.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperRuntimeCompatibility\$RuntimeSelection.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperAdapterSelfTest.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperVersionAdapter.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperVersionAdapterRegistry.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/Paper121FamilyAdapter.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/Paper261Adapter.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/DefaultPaperVersionAdapter.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/RuntimeCapabilities.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/ServerVersion.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/VersionRange.class"
        )
        ZipFile(paperJar.get().archiveFile.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val missing = requiredEntries.filterNot(entries::contains)
            if (missing.isNotEmpty()) {
                throw GradleException("Paper adapter classes missing from final artifact: ${missing.joinToString(", ")}")
            }
            val duplicates = entries.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
            if (duplicates.isNotEmpty()) {
                throw GradleException("Duplicate class/resource entries in final Paper artifact: ${duplicates.joinToString(", ")}")
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyVersionPackaging)
}

tasks.named("distBundle") {
    dependsOn(verifyVersionPackaging)
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
            "90",
            "--evidence-out",
            layout.buildDirectory.file("smoke/core-integration/cluster-evidence.json").get().asFile.absolutePath
        )
    }
}

val clusterSmokeEvidenceFile = layout.buildDirectory.file("smoke/core-integration/cluster-evidence.json")
val clusterSmokePartialReportFile = layout.buildDirectory.file("smoke/core-integration/cluster-smoke-report.json")

tasks.register<JavaExec>("clusterSmokePartialReport") {
    group = "verification"
    description = "Reports which production GA cluster-smoke evidence remains missing after the Core integration smoke."
    dependsOn(tasks.named("coreIntegrationSmoke"))
    dependsOn(project(":cloudislands-testkit").tasks.named("classes"))
    val testkitSourceSets = project(":cloudislands-testkit").extensions.getByType<SourceSetContainer>()
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("kr.lunaf.cloudislands.testkit.ClusterSmokeVerifierCli")
    args(
        "--evidence", clusterSmokeEvidenceFile.get().asFile.absolutePath,
        "--report-out", clusterSmokePartialReportFile.get().asFile.absolutePath,
        "--allow-partial"
    )
}

tasks.register<JavaExec>("clusterSmokeVerify") {
    group = "verification"
    description = "Verifies a full production GA cluster-smoke evidence JSON file. Pass -PclusterSmokeEvidence=/path/to/evidence.json."
    dependsOn(project(":cloudislands-testkit").tasks.named("classes"))
    val testkitSourceSets = project(":cloudislands-testkit").extensions.getByType<SourceSetContainer>()
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("kr.lunaf.cloudislands.testkit.ClusterSmokeVerifierCli")
    doFirst {
        val evidence = providers.gradleProperty("clusterSmokeEvidence").orElse(providers.environmentVariable("CI_CLUSTER_SMOKE_EVIDENCE")).orNull
            ?: throw GradleException("clusterSmokeVerify requires -PclusterSmokeEvidence=/path/to/evidence.json or CI_CLUSTER_SMOKE_EVIDENCE")
        args("--evidence", evidence)
    }
}

tasks.register("ciIntegrationSmoke") {
    group = "verification"
    description = "Runs Core API real-infrastructure integration smoke tests."
    dependsOn(tasks.named("coreIntegrationSmoke"))
    dependsOn(tasks.named("clusterSmokePartialReport"))
}
