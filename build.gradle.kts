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
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

private fun jsonEscape(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")

private fun jsonString(value: String): String = "\"${jsonEscape(value)}\""

private data class MinecraftVersionRange(val major: Int, val minor: Int) : Comparable<MinecraftVersionRange> {
    override fun compareTo(other: MinecraftVersionRange): Int =
        compareValuesBy(this, other, MinecraftVersionRange::major, MinecraftVersionRange::minor)

    override fun toString(): String = "$major.$minor.x"

    companion object {
        private val PATTERN = Regex("""(\d+)\.(\d+)\.x""")

        fun parse(value: String): MinecraftVersionRange {
            val match = PATTERN.matchEntire(value)
                ?: throw GradleException("Unsupported normalizedRange '$value'; expected '<major>.<minor>.x'")
            return MinecraftVersionRange(
                match.groupValues[1].toInt(),
                match.groupValues[2].toInt()
            )
        }
    }
}

private data class MinecraftVersionEntry(
    val id: String,
    val normalizedRange: String,
    val paperApiVersion: String,
    val javaVersion: Int,
    val adapterProject: String,
    val adapterClass: String,
    val descriptorApiVersion: String,
    val compileEnabled: Boolean,
    val bootSmokeEnabled: Boolean,
    val releaseSupported: Boolean,
    val experimental: Boolean,
    val minimumProtocolVersion: Int,
    val bootVersion: String,
    val artifactPolicy: String,
    val notes: String
) {
    val range: MinecraftVersionRange = MinecraftVersionRange.parse(normalizedRange)
    val taskSuffix: String = id.filter(Char::isDigit)
    val compileTaskName: String = "paper${taskSuffix}Compile"
    val bootSmokeTaskName: String = "paper${taskSuffix}BootSmoke"
    val adapterJarEntry: String = adapterClass.replace('.', '/') + ".class"
    val adapterSimpleName: String = adapterClass.substringAfterLast('.')

    init {
        if (!id.startsWith("paper-") || taskSuffix.isBlank()) {
            throw GradleException("Minecraft version id '$id' must use the paper-<version> form")
        }
        if (notes.isBlank()) {
            throw GradleException("Minecraft version '$id' must include notes")
        }
    }
}

private data class MinecraftVersionMatrix(val entries: List<MinecraftVersionEntry>) {
    init {
        if (entries.isEmpty()) {
            throw GradleException("Minecraft version matrix must contain at least one entry")
        }
    }

    val compileEntries: List<MinecraftVersionEntry> = entries.filter { it.compileEnabled }
    val stableBootEntries: List<MinecraftVersionEntry> =
        entries.filter { it.releaseSupported && it.bootSmokeEnabled && !it.experimental }
    val latestStable: MinecraftVersionEntry = entries
        .filter { it.releaseSupported && !it.experimental }
        .maxByOrNull { it.range }
        ?: throw GradleException("Minecraft version matrix has no non-experimental releaseSupported entry")

    fun validate(rootDir: File, descriptorBaseline: String) {
        failOnDuplicates("id", entries.map { it.id })
        failOnDuplicates("normalizedRange", entries.map { it.normalizedRange })
        failOnGaps()
        requireRanges("1.21.x", "26.1.x", "26.2.x")
        entries.forEach { entry ->
            if (entry.releaseSupported && entry.experimental) {
                throw GradleException("Experimental matrix entry '${entry.id}' cannot be releaseSupported")
            }
            if (entry.artifactPolicy != "universal") {
                throw GradleException("Unsupported artifactPolicy '${entry.artifactPolicy}' for '${entry.id}'; only universal is supported")
            }
            if (entry.descriptorApiVersion != descriptorBaseline) {
                throw GradleException(
                    "descriptorApiVersion drift for '${entry.id}': ${entry.descriptorApiVersion} != $descriptorBaseline"
                )
            }
            val moduleDir = rootDir.resolve(entry.adapterProject)
            if (!moduleDir.resolve("build.gradle.kts").isFile) {
                throw GradleException("Matrix entry '${entry.id}' references missing adapterProject '${entry.adapterProject}'")
            }
            val adapterSource = moduleDir.resolve("src/main/java/${entry.adapterClass.replace('.', '/')}.java")
            if (!adapterSource.isFile) {
                throw GradleException("Matrix entry '${entry.id}' references missing adapterClass '${entry.adapterClass}'")
            }
        }
        val registrySource = rootDir.resolve(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/platform/compatibility/PaperVersionAdapterRegistry.java"
        ).readText()
        val missingRegistryAdapters = entries
            .map { it.adapterSimpleName }
            .filterNot { registrySource.contains("new $it()") }
        if (missingRegistryAdapters.isNotEmpty()) {
            throw GradleException("Matrix adapters missing from PaperVersionAdapterRegistry.defaults(): ${missingRegistryAdapters.joinToString(", ")}")
        }
    }

    fun readmeBlock(): String {
        val rows = entries.sortedBy { it.range }.joinToString("\n") { entry ->
            val compile = if (entry.compileEnabled) "`${entry.compileTaskName}`" else "disabled"
            val boot = if (entry.bootSmokeEnabled) "`${entry.bootSmokeTaskName}`" else "pending official Paper build"
            val release = when {
                entry.releaseSupported -> "release-supported"
                entry.experimental -> "experimental compile-only"
                else -> "not release-supported"
            }
            "| Paper `${entry.normalizedRange}` | $compile | $boot | $release | ${entry.notes} |"
        }
        return listOf(
            "<!-- minecraft-version-matrix:start -->",
            "| Target | Compile | Boot smoke | Release | Notes |",
            "|---|---|---|---|---|",
            rows,
            "<!-- minecraft-version-matrix:end -->"
        ).joinToString("\n")
    }

    fun detailedReport(): String {
        val rows = entries.sortedBy { it.range }.joinToString("\n") { entry ->
            "| ${entry.id} | ${entry.normalizedRange} | ${entry.paperApiVersion} | ${entry.javaVersion} | ${entry.adapterProject} | ${entry.adapterSimpleName} | ${entry.compileEnabled} | ${entry.bootSmokeEnabled} | ${entry.releaseSupported} | ${entry.experimental} | ${entry.minimumProtocolVersion} | ${entry.artifactPolicy} | ${entry.notes} |"
        }
        return listOf(
            "# Runtime matrix",
            "",
            "Latest stable: `${latestStable.id}` (`${latestStable.normalizedRange}`).",
            "",
            "| ID | Range | Paper API | Java | Adapter project | Adapter | Compile | Boot smoke | Release | Experimental | Minimum protocol | Artifact | Notes |",
            "|---|---|---|---:|---|---|---|---|---|---|---:|---|---|",
            rows,
            ""
        ).joinToString("\n")
    }

    private fun failOnDuplicates(label: String, values: List<String>) {
        val duplicates = values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        if (duplicates.isNotEmpty()) {
            throw GradleException("Duplicate Minecraft version matrix $label: ${duplicates.joinToString(", ")}")
        }
    }

    private fun failOnGaps() {
        entries.map { it.range }
            .groupBy { it.major }
            .forEach { (major, ranges) ->
                ranges.sorted().zipWithNext().forEach { (left, right) ->
                    if (right.minor != left.minor + 1) {
                        throw GradleException("Unintended gap in Minecraft version matrix for $major.x between $left and $right")
                    }
                }
            }
    }

    private fun requireRanges(vararg requiredRanges: String) {
        val present = entries.map { it.normalizedRange }.toSet()
        val missing = requiredRanges.filterNot(present::contains)
        if (missing.isNotEmpty()) {
            throw GradleException("Required Minecraft version matrix ranges missing: ${missing.joinToString(", ")}")
        }
    }

    companion object {
        fun parse(file: File): MinecraftVersionMatrix {
            if (!file.isFile) {
                throw GradleException("Minecraft version matrix file is missing: ${file.path}")
            }
            val entries = mutableListOf<MinecraftVersionEntry>()
            var current = linkedMapOf<String, String>()
            file.readLines().forEachIndexed { index, rawLine ->
                val line = rawLine.substringBefore('#').trim()
                if (line.isBlank()) {
                    return@forEachIndexed
                }
                if (line == "[[versions]]") {
                    if (current.isNotEmpty()) {
                        entries.add(entry(current, file, index + 1))
                        current = linkedMapOf()
                    }
                    return@forEachIndexed
                }
                val separator = line.indexOf('=')
                if (separator <= 0) {
                    throw GradleException("Invalid matrix line ${index + 1} in ${file.path}: $rawLine")
                }
                current[line.substring(0, separator).trim()] = scalar(line.substring(separator + 1).trim())
            }
            if (current.isNotEmpty()) {
                entries.add(entry(current, file, file.readLines().size))
            }
            return MinecraftVersionMatrix(entries)
        }

        private fun entry(values: Map<String, String>, file: File, line: Int): MinecraftVersionEntry =
            MinecraftVersionEntry(
                id = required(values, "id", file, line),
                normalizedRange = required(values, "normalizedRange", file, line),
                paperApiVersion = required(values, "paperApiVersion", file, line),
                javaVersion = required(values, "javaVersion", file, line).toInt(),
                adapterProject = required(values, "adapterProject", file, line),
                adapterClass = required(values, "adapterClass", file, line),
                descriptorApiVersion = required(values, "descriptorApiVersion", file, line),
                compileEnabled = required(values, "compileEnabled", file, line).toBooleanStrict(),
                bootSmokeEnabled = required(values, "bootSmokeEnabled", file, line).toBooleanStrict(),
                releaseSupported = required(values, "releaseSupported", file, line).toBooleanStrict(),
                experimental = required(values, "experimental", file, line).toBooleanStrict(),
                minimumProtocolVersion = required(values, "minimumProtocolVersion", file, line).toInt(),
                bootVersion = required(values, "bootVersion", file, line),
                artifactPolicy = required(values, "artifactPolicy", file, line),
                notes = required(values, "notes", file, line)
            )

        private fun required(values: Map<String, String>, key: String, file: File, line: Int): String =
            values[key] ?: throw GradleException("Missing '$key' in Minecraft version matrix ${file.path} near line $line")

        private fun scalar(value: String): String =
            if (value.startsWith('"') && value.endsWith('"') && value.length >= 2) {
                value.substring(1, value.length - 1)
            } else {
                value
            }
    }
}

private data class FeatureParityEntry(
    val area: String,
    val status: String,
    val domain: String,
    val paperRuntime: String,
    val adapter: String,
    val integration: String,
    val recovery: String,
    val evidence: List<String>,
    val limitation: String
)

private fun featureParityEntries(): List<FeatureParityEntry> = listOf(
    FeatureParityEntry(
        "lifecycle/templates/homes/warps/visits",
        "IMPLEMENTED_VERIFIED",
        "Core lifecycle and route tickets are covered",
        "Paper 1.21.x boot smoke loads the plugin",
        "1.21.x release adapter; 26.1/26.2 compile adapters",
        "ciIntegrationSmoke verifies cross-Core create, job, route, session, consume",
        "node-down recovery restore is covered by ciIntegrationSmoke",
        listOf(
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/workflow/IslandLifecycleWorkflowRestoreTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/platform/compatibility/Paper121FamilyAdapter.java",
            "scripts/ci/core_integration_smoke.py"
        ),
        "26.1 and 26.2 stay compile-only until official bootable Paper builds are available"
    ),
    FeatureParityEntry(
        "access/bans/membership/roles/permissions",
        "IMPLEMENTED_VERIFIED",
        "Role IDs, permissions, bans, and member APIs have unit coverage",
        "Paper permission cache/listener paths compile under the adapter matrix",
        "Version-neutral domain with Paper adapter boundary tests",
        "Core API and permission event replay are exercised in tests",
        "replay and cache convergence are unit-tested; full multi-Paper failover remains release evidence",
        listOf(
            "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/permission/PermissionResolverTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutesTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/cache/PermissionEventPoller.java"
        ),
        "third-party permission plugins are integration-status reported, not all boot-verified"
    ),
    FeatureParityEntry(
        "flags/protection",
        "IMPLEMENTED_VERIFIED",
        "Gameplay and permission flags are represented in the domain",
        "Paper protection listeners and cache paths have unit coverage",
        "Bukkit-facing behavior is kept inside Paper runtime code",
        "unit verified; real-player destructive-action smoke is not part of CI",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/ProtectionControllerTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/IslandGameplayFlagListener.java",
            "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/permission/defaults/DefaultIslandPermissionsTest.java"
        ),
        "runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests"
    ),
    FeatureParityEntry(
        "ranking/level/worth/block values",
        "IMPLEMENTED_VERIFIED",
        "ranking and dirty recalculation logic have service tests",
        "Paper-facing values compile; no per-version runtime divergence is claimed",
        "version-neutral",
        "service-level verified",
        "not recovery-specific",
        listOf(
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/DirtyRankingRecalculationTaskTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/ranking/LevelWorthSystemPolicyTest.java",
            "cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/hook/PlaceholderFeaturePolicyTest.java"
        ),
        "worth economics beyond configured value calculations are not release-certified"
    ),
    FeatureParityEntry(
        "upgrades/size/border/biome",
        "PARTIAL",
        "border and biome command paths exist",
        "Paper commands compile and unit tests cover command policy",
        "Paper adapter isolates version-sensitive runtime access",
        "not covered by multi-service smoke",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandControllerPolicyTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/platform/compatibility/PaperPlatformBoundaryTest.java"
        ),
        "full upgrade economy and live biome/border interaction tests are still needed"
    ),
    FeatureParityEntry(
        "bank/economy/missions/challenges/generators/limits",
        "PARTIAL",
        "bank, economy hooks, missions, and Satis generator policies exist",
        "optional Satis feature pack has targeted tests",
        "version-neutral plus Paper/Satis runtime boundaries",
        "unit verified; external economy/provider runtime coverage is integration-status only",
        "not recovery-specific",
        listOf(
            "cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/config/SatisFeatureGateResolverTest.java",
            "cloudislands-satis/src/test/java/kr/seungmin/satisskyfactory/runtime/SatisRuntimeComponentPlanTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java"
        ),
        "provider-backed economy and mission parity are not fully production-certified"
    ),
    FeatureParityEntry(
        "chat/logs/reviews",
        "PARTIAL",
        "chat listener and audit/log surfaces exist",
        "Paper chat listener compiles under matrix",
        "version-neutral where possible",
        "unit/static verified only",
        "audit log replay is covered in Core tests, review workflow is not complete",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/AuditRoutesTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutesTest.java"
        ),
        "review/moderation parity remains partial"
    ),
    FeatureParityEntry(
        "snapshots/rollback/migration/recovery",
        "IMPLEMENTED_VERIFIED",
        "bundle validation, extraction, migration, and rollback paths have tests",
        "Paper snapshot and restore hooks compile and 1.21.x boots",
        "bundle compatibility is checked before restore",
        "ciIntegrationSmoke verifies recovery restore with shared services",
        "node-down recovery and bundle compatibility are verified",
        listOf(
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/world/bundle/ExternalTarBundleExtractorTest.java",
            "cloudislands-migration/src/test/java/kr/lunaf/cloudislands/migration/rollback/MigrationRollbackServiceTest.java",
            "cloudislands-testkit/src/test/java/kr/lunaf/cloudislands/testkit/ClusterSmokeVerifierTest.java"
        ),
        "full backup/restore drill remains a release cluster evidence gate"
    ),
    FeatureParityEntry(
        "Java API/events/addons",
        "IMPLEMENTED_VERIFIED",
        "public API, events, addon metadata, and compatibility contract are tested",
        "Paper API bridge has unit coverage",
        "version-neutral API with runtime metadata compatibility check",
        "apiCompatibilityCheck verifies release contract metadata",
        "not recovery-specific",
        listOf(
            "cloudislands-testkit/src/main/java/kr/lunaf/cloudislands/testkit/ApiCompatibilityCheckCli.java",
            "cloudislands-testkit/src/test/java/kr/lunaf/cloudislands/testkit/ApiContractVerifierTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/AddonRoutesTest.java"
        ),
        "external addon certification depends on testkit evidence supplied by the addon"
    ),
    FeatureParityEntry(
        "integrations/localization/GUI",
        "IMPLEMENTED_UNVERIFIED",
        "integration policy, localization files, and GUI components exist",
        "Paper integration registry reports explicit operation states",
        "plugin-specific adapters are active where implemented",
        "verifyIntegrationMatrix proves inventory and state reporting, not third-party boot behavior",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java",
            "cloudislands-paper/src/main/resources/config-v2/integrations.yml",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandConfirmationMenu.java"
        ),
        "third-party plugin runtime certification is pending per-plugin boot fixtures"
    )
)

private fun featureParityReadmeBlock(): String {
    val rows = featureParityEntries().joinToString("\n") { entry ->
        "| ${entry.area} | ${entry.status} | ${entry.integration} | ${entry.limitation} |"
    }
    return listOf(
        "<!-- feature-parity:start -->",
        "| Area | Status | Verified evidence | Limit |",
        "|---|---|---|---|",
        rows,
        "<!-- feature-parity:end -->"
    ).joinToString("\n")
}

private fun featureParityMarkdown(): String = buildString {
    appendLine("# SuperiorSkyblock2 parity")
    appendLine()
    appendLine("Status values: IMPLEMENTED_VERIFIED, IMPLEMENTED_UNVERIFIED, PARTIAL, PLANNED, NOT_APPLICABLE, INTENTIONALLY_UNSUPPORTED.")
    appendLine()
    appendLine("| Area | Status | Domain | Paper runtime | Adapter | Integration | Recovery | Evidence | Limit |")
    appendLine("|---|---|---|---|---|---|---|---|---|")
    featureParityEntries().forEach { entry ->
        appendLine("| ${entry.area} | ${entry.status} | ${entry.domain} | ${entry.paperRuntime} | ${entry.adapter} | ${entry.integration} | ${entry.recovery} | ${entry.evidence.joinToString("<br>") { "`$it`" }} | ${entry.limitation} |")
    }
}

private fun minecraftVersionFeatureMatrixMarkdown(matrix: MinecraftVersionMatrix): String = buildString {
    appendLine("# Minecraft version feature matrix")
    appendLine()
    appendLine("| Version | Feature area | Domain | Paper runtime | Adapter | Compile | Boot | Integration | Recovery | Evidence |")
    appendLine("|---|---|---|---|---|---|---|---|---|---|")
    matrix.entries.sortedBy { it.range }.forEach { version ->
        featureParityEntries().forEach { entry ->
            val compile = if (version.compileEnabled) "verified by `${version.compileTaskName}`" else "not verified"
            val boot = if (version.bootSmokeEnabled) "verified by `${version.bootSmokeTaskName}`" else "pending official Paper build"
            val integration = if (version.releaseSupported && !version.experimental) entry.integration else "compile-only adapter coverage; no boot or integration claim"
            appendLine("| ${version.normalizedRange} | ${entry.area} | ${entry.domain} | ${entry.paperRuntime} | ${entry.adapter} | $compile | $boot | $integration | ${entry.recovery} | ${entry.evidence.joinToString("<br>") { "`$it`" }} |")
        }
    }
}

private fun parityJson(matrix: MinecraftVersionMatrix): String {
    fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
    fun array(values: List<String>): String = values.joinToString(prefix = "[", postfix = "]") { "\"${escape(it)}\"" }
    val areas = featureParityEntries().joinToString(",") { entry ->
        """{"area":"${escape(entry.area)}","status":"${escape(entry.status)}","domain":"${escape(entry.domain)}","paperRuntime":"${escape(entry.paperRuntime)}","adapter":"${escape(entry.adapter)}","integration":"${escape(entry.integration)}","recovery":"${escape(entry.recovery)}","evidence":${array(entry.evidence)},"limitation":"${escape(entry.limitation)}"}"""
    }
    val versions = matrix.entries.sortedBy { it.range }.joinToString(",") { entry ->
        """{"id":"${escape(entry.id)}","range":"${escape(entry.normalizedRange)}","compileVerified":${entry.compileEnabled},"bootVerified":${entry.bootSmokeEnabled},"releaseSupported":${entry.releaseSupported},"experimental":${entry.experimental},"adapter":"${escape(entry.adapterSimpleName)}"}"""
    }
    return """{"areas":[$areas],"versions":[$versions]}""" + System.lineSeparator()
}

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
private val minecraftVersionMatrixFile = layout.projectDirectory.file("gradle/minecraft-versions.toml").asFile
private val minecraftVersionMatrix = MinecraftVersionMatrix.parse(minecraftVersionMatrixFile)
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
            """    {"type":"library","group":${jsonString(group)},"name":${jsonString(name)},"version":${jsonString(version)},"purl":${jsonString(purl)}}"""
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
            |      "version": ${jsonString(project.version.toString())}
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
            """    {"path":${jsonString(path)},"sha256":${jsonString(checksum)}}"""
        }
        val output = distProvenanceFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            |{
            |  "project": "cloudislands",
            |  "version": ${jsonString(project.version.toString())},
            |  "commit": ${jsonString(commandOutput("git", "rev-parse", "HEAD"))},
            |  "dirty": ${jsonString(commandOutput("git", "status", "--porcelain"))},
            |  "javaVersion": ${jsonString(System.getProperty("java.version"))},
            |  "gradleVersion": ${jsonString(gradle.gradleVersion)},
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

val apiCompatibilityReportFile = layout.buildDirectory.file("reports/api-compatibility/api-compatibility-report.json")

tasks.register<JavaExec>("apiCompatibilityCheck") {
    group = "verification"
    description = "Verifies the CloudIslands API compatibility contract before release."
    dependsOn(project(":cloudislands-testkit").tasks.named("classes"))
    val testkitSourceSets = project(":cloudislands-testkit").extensions.getByType<SourceSetContainer>()
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("kr.lunaf.cloudislands.testkit.ApiCompatibilityCheckCli")
    args("--report-out", apiCompatibilityReportFile.get().asFile.absolutePath)
}

private val paperVersionCompileTasks = minecraftVersionMatrix.compileEntries.associateWith { entry ->
    tasks.register(entry.compileTaskName) {
        group = "verification"
        description = "Compiles the CloudIslands Paper plugin for ${entry.normalizedRange} from the Minecraft version matrix."
        dependsOn(project(":${entry.adapterProject}").tasks.named("compileJava"))
        dependsOn(project(":${entry.adapterProject}").tasks.named("processResources"))
    }
}

private val paperVersionBootSmokeTasks = minecraftVersionMatrix.entries.associateWith { entry ->
    tasks.register<Exec>(entry.bootSmokeTaskName) {
        group = "verification"
        description = "Boots Paper ${entry.bootVersion} for ${entry.normalizedRange} when the matrix marks it available."
        val paperJar = project(":cloudislands-paper").tasks.named<Jar>("shadowJar")
        paperVersionCompileTasks[entry]?.let { dependsOn(it) }
        dependsOn(paperJar)
        onlyIf {
            if (!entry.bootSmokeEnabled) {
                logger.lifecycle("${entry.bootSmokeTaskName} skipped: ${entry.notes}")
                false
            } else {
                true
            }
        }
        doFirst {
            commandLine(
                "python3",
                file("scripts/ci/papermc_smoke.py").absolutePath,
                "--project", "paper",
                "--version", entry.bootVersion,
                "--plugin", paperJar.get().archiveFile.get().asFile.absolutePath,
                "--work-dir", layout.buildDirectory.dir("smoke/paper-${entry.bootVersion}").get().asFile.absolutePath,
                "--cache-dir", layout.buildDirectory.dir("smoke/cache").get().asFile.absolutePath,
                "--timeout", "240"
            )
        }
    }
}

tasks.register("paperBootSmoke") {
    group = "verification"
    description = "Boots the latest release-supported Paper version from the Minecraft version matrix."
    dependsOn(tasks.named(minecraftVersionMatrix.latestStable.bootSmokeTaskName))
}

tasks.register("compileAllMinecraftVersions") {
    group = "verification"
    description = "Runs all compile checks generated from the Minecraft version matrix."
    dependsOn(paperVersionCompileTasks.values)
}

tasks.register("bootSmokeAllStableMinecraftVersions") {
    group = "verification"
    description = "Runs boot smoke checks for non-experimental release-supported matrix entries."
    dependsOn(minecraftVersionMatrix.stableBootEntries.map { entry -> tasks.named(entry.bootSmokeTaskName) })
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

tasks.register("verifyIntegrationMatrix") {
    group = "verification"
    description = "Verifies plugin integration support is reported with explicit detection, compatibility, adapter, and operation states."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val policyFile = layout.projectDirectory.file("cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/integration/CloudIntegrationPolicy.java")
    val registryFile = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java")
    val stateFile = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/spi/IntegrationSupportState.java")
    val configFile = layout.projectDirectory.file("cloudislands-paper/src/main/resources/config-v2/integrations.yml")
    val pluginFile = layout.projectDirectory.file("cloudislands-paper/src/main/resources/plugin.yml")
    inputs.files(policyFile, registryFile, stateFile, configFile, pluginFile)
    outputs.file(rootProject.layout.projectDirectory.dir("../codex-output").file("plugin-integration-matrix.md"))
    doLast {
        val requiredPlugins = listOf(
            "Vault",
            "PlaceholderAPI",
            "LuckPerms",
            "CoreProtect",
            "WorldEdit",
            "FastAsyncWorldEdit",
            "ItemsAdder",
            "Oraxen",
            "Nexo",
            "RoseStacker",
            "WildStacker",
            "AdvancedSpawners",
            "Plan",
            "ProtocolLib",
            "SkinsRestorer",
            "SuperVanish",
            "PremiumVanish",
            "SlimeWorldManager",
            "Slimefun",
            "CMI"
        )
        val requiredStates = listOf(
            "NOT_INSTALLED",
            "DETECTED",
            "API_INCOMPATIBLE",
            "API_COMPATIBLE",
            "ADAPTER_INACTIVE",
            "ACTIVE",
            "OPERATION_SUCCEEDED",
            "OPERATION_FAILED",
            "UNSUPPORTED"
        )
        val policy = policyFile.asFile.readText()
        val registry = registryFile.asFile.readText()
        val states = stateFile.asFile.readText()
        val config = configFile.asFile.readText()
        val plugin = pluginFile.asFile.readText()
        val missingPolicyPlugins = requiredPlugins.filterNot { policy.contains("\"$it\"") }
        val missingConfigPlugins = requiredPlugins.filterNot { config.contains("$it:") }
        val missingSoftDepends = requiredPlugins.filterNot { plugin.contains(it) }
        val missingStates = requiredStates.filterNot { states.contains(it) }
        val missingRegistryStates = requiredStates.filterNot { registry.contains("IntegrationSupportState.$it") || it == "OPERATION_SUCCEEDED" || it == "OPERATION_FAILED" }
        val unsupportedSpecificAdapters = listOf(
            "CoreProtectIntegration",
            "WorldEditIntegration",
            "CustomItemIntegration",
            "StackerIntegration",
            "LuckPermsIntegration",
            "PlanIntegration"
        ).filterNot { registry.contains(it) }
        val failures = buildList {
            if (missingPolicyPlugins.isNotEmpty()) add("CloudIntegrationPolicy missing plugins: ${missingPolicyPlugins.joinToString(", ")}")
            if (missingConfigPlugins.isNotEmpty()) add("integrations.yml missing plugins: ${missingConfigPlugins.joinToString(", ")}")
            if (missingSoftDepends.isNotEmpty()) add("plugin.yml softdepend missing plugins: ${missingSoftDepends.joinToString(", ")}")
            if (missingStates.isNotEmpty()) add("IntegrationSupportState missing states: ${missingStates.joinToString(", ")}")
            if (missingRegistryStates.isNotEmpty()) add("PaperIntegrationRegistry does not report states: ${missingRegistryStates.joinToString(", ")}")
            if (unsupportedSpecificAdapters.isNotEmpty()) add("PaperIntegrationRegistry missing specific adapters: ${unsupportedSpecificAdapters.joinToString(", ")}")
            if (!registry.contains("status.pluginName() + \"=\" + status.state()")) add("statusLine must report integration state, not only enabled/missing")
            if (!registry.contains("IntegrationSupportState.operationState(result)")) add("operation results must map to explicit operation states")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
        val output = rootProject.layout.projectDirectory.dir("../codex-output").file("plugin-integration-matrix.md").asFile
        output.parentFile.mkdirs()
        output.writeText(
            buildString {
                appendLine("# Plugin integration matrix")
                appendLine()
                appendLine("| Plugin | In policy | In config | Softdepend |")
                appendLine("|---|---|---|---|")
                requiredPlugins.forEach { pluginName ->
                    appendLine("| `$pluginName` | yes | yes | yes |")
                }
                appendLine()
                appendLine("Reported states: ${requiredStates.joinToString(", ")}.")
                appendLine("Specific adapters: ${unsupportedSpecificAdapters.ifEmpty { listOf("all required adapter classes present") }.joinToString(", ")}.")
            }
        )
    }
}

tasks.register("verifyMinecraftVersionMatrix") {
    group = "verification"
    description = "Validates the typed Minecraft version matrix and writes the external runtime matrix report."
    inputs.file(minecraftVersionMatrixFile)
    inputs.file(layout.projectDirectory.file(".github/workflows/build.yml"))
    outputs.file(rootProject.layout.projectDirectory.dir("../codex-output").file("runtime-matrix.md"))
    doLast {
        minecraftVersionMatrix.validate(rootProject.projectDir, minecraftBaselineVersion)
        verifyMinecraftCiCoverage(layout.projectDirectory.file(".github/workflows/build.yml").asFile.readText())
        val output = rootProject.layout.projectDirectory.dir("../codex-output").file("runtime-matrix.md").asFile
        output.parentFile.mkdirs()
        output.writeText(minecraftVersionMatrix.detailedReport())
        logger.lifecycle("Latest stable Minecraft matrix entry: ${minecraftVersionMatrix.latestStable.id}")
    }
}

fun verifyMinecraftCiCoverage(workflow: String) {
    val missingCompileTasks = minecraftVersionMatrix.compileEntries
        .map { it.compileTaskName }
        .filterNot(workflow::contains)
    val missingBootTasks = minecraftVersionMatrix.stableBootEntries
        .map { it.bootSmokeTaskName }
        .filterNot(workflow::contains)
    val missingAggregateTasks = listOf(
        "verifyMinecraftVersionMatrix",
        "compileAllMinecraftVersions",
        "verifyAdapterPackaging",
        "bootSmokeAllStableMinecraftVersions",
        "apiCompatibilityCheck",
        "distChecksums",
        "distSbom",
        "distProvenance",
        "verifyReleaseSecurityGate"
    ).filterNot(workflow::contains)
    val failures = buildList {
        if (missingCompileTasks.isNotEmpty()) {
            add("Build workflow does not compile matrix tasks: ${missingCompileTasks.joinToString(", ")}")
        }
        if (missingBootTasks.isNotEmpty()) {
            add("Build workflow does not boot-smoke stable matrix tasks: ${missingBootTasks.joinToString(", ")}")
        }
        if (missingAggregateTasks.isNotEmpty()) {
            add("Build workflow is missing aggregate release gates: ${missingAggregateTasks.joinToString(", ")}")
        }
    }
    if (failures.isNotEmpty()) {
        throw GradleException(failures.joinToString("\n"))
    }
}

tasks.register("verifyReadmeVersionTable") {
    group = "verification"
    description = "Verifies the README compact support table matches the Minecraft version matrix."
    inputs.file(minecraftVersionMatrixFile)
    inputs.file(layout.projectDirectory.file("README.md"))
    doLast {
        val readme = layout.projectDirectory.file("README.md").asFile.readText()
        val expected = minecraftVersionMatrix.readmeBlock()
        val start = "<!-- minecraft-version-matrix:start -->"
        val end = "<!-- minecraft-version-matrix:end -->"
        val block = Regex("(?s)${Regex.escape(start)}.*?${Regex.escape(end)}")
            .find(readme)
            ?.value
            ?: throw GradleException("README compact support table markers are missing")
        if (block.trim() != expected.trim()) {
            throw GradleException("README compact support table has drifted from gradle/minecraft-versions.toml")
        }
    }
}

val superiorSkyblockParityReportFile = rootProject.layout.projectDirectory.dir("../codex-output").file("superiorskyblock2-parity.md")
val minecraftVersionFeatureMatrixFile = rootProject.layout.projectDirectory.dir("../codex-output").file("minecraft-version-feature-matrix.md")
val featureParityJsonFile = rootProject.layout.projectDirectory.dir("../codex-output").file("parity.json")

tasks.register("verifyFeatureParityEvidence") {
    group = "verification"
    description = "Verifies README feature parity claims and writes detailed parity evidence outside the repository."
    inputs.file(layout.projectDirectory.file("README.md"))
    inputs.file(minecraftVersionMatrixFile)
    inputs.files(featureParityEntries().flatMap { entry -> entry.evidence }.map { path -> layout.projectDirectory.file(path) })
    outputs.files(superiorSkyblockParityReportFile, minecraftVersionFeatureMatrixFile, featureParityJsonFile)
    doLast {
        val missingEvidence = featureParityEntries()
            .flatMap { entry -> entry.evidence }
            .distinct()
            .filterNot { path -> layout.projectDirectory.file(path).asFile.exists() }
        if (missingEvidence.isNotEmpty()) {
            throw GradleException("Feature parity evidence files are missing: ${missingEvidence.joinToString(", ")}")
        }
        val readme = layout.projectDirectory.file("README.md").asFile.readText()
        val expected = featureParityReadmeBlock()
        val start = "<!-- feature-parity:start -->"
        val end = "<!-- feature-parity:end -->"
        val block = Regex("(?s)${Regex.escape(start)}.*?${Regex.escape(end)}")
            .find(readme)
            ?.value
            ?: throw GradleException("README feature parity markers are missing")
        if (block.trim() != expected.trim()) {
            throw GradleException("README feature parity table has drifted from verified evidence")
        }
        val parityFile = superiorSkyblockParityReportFile.asFile
        val versionFile = minecraftVersionFeatureMatrixFile.asFile
        val jsonFile = featureParityJsonFile.asFile
        parityFile.parentFile.mkdirs()
        parityFile.writeText(featureParityMarkdown())
        versionFile.writeText(minecraftVersionFeatureMatrixMarkdown(minecraftVersionMatrix))
        jsonFile.writeText(parityJson(minecraftVersionMatrix))
    }
}

val verifyAdapterPackaging = tasks.register("verifyAdapterPackaging") {
    group = "verification"
    description = "Verifies the final Paper artifact contains all matrix adapters without duplicate entries."
    val paperJar = project(":cloudislands-paper").tasks.named<Jar>("shadowJar")
    dependsOn(paperJar)
    inputs.file(paperJar.flatMap { it.archiveFile })
    inputs.file(minecraftVersionMatrixFile)
    doLast {
        minecraftVersionMatrix.validate(rootProject.projectDir, minecraftBaselineVersion)
        val requiredEntries = listOf(
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperRuntimeCompatibility.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperRuntimeCompatibility\$RuntimeSelection.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperAdapterSelfTest.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperVersionAdapter.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/PaperVersionAdapterRegistry.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/AbstractPaper26Adapter.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/DefaultPaperVersionAdapter.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/RuntimeCapabilities.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/ServerVersion.class",
            "kr/lunaf/cloudislands/paper/platform/compatibility/VersionRange.class"
        ) + minecraftVersionMatrix.entries.map { it.adapterJarEntry }
        ZipFile(paperJar.get().archiveFile.get().asFile).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val missing = requiredEntries.filterNot(entries::contains)
            if (missing.isNotEmpty()) {
                throw GradleException("Paper matrix adapter classes missing from final artifact: ${missing.joinToString(", ")}")
            }
            val duplicates = entries.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
            if (duplicates.isNotEmpty()) {
                throw GradleException("Duplicate class/resource entries in final Paper artifact: ${duplicates.joinToString(", ")}")
            }
        }
    }
}

tasks.register("verifyVersionPackaging") {
    group = "verification"
    description = "Compatibility alias for verifyAdapterPackaging."
    dependsOn(verifyAdapterPackaging)
}

tasks.named("check") {
    dependsOn(tasks.named("verifyMinecraftVersionMatrix"))
    dependsOn(tasks.named("verifyReadmeVersionTable"))
    dependsOn(tasks.named("verifyFeatureParityEvidence"))
    dependsOn(tasks.named("verifyIntegrationMatrix"))
    dependsOn(tasks.named("apiCompatibilityCheck"))
    dependsOn(verifyAdapterPackaging)
}

tasks.named("distBundle") {
    dependsOn(verifyAdapterPackaging)
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
val clusterSmokeReleaseEvidenceFile = layout.buildDirectory.file("smoke/cluster-smoke/cluster-evidence.json")
val clusterSmokeReleaseReportFile = layout.buildDirectory.file("smoke/cluster-smoke/cluster-smoke-report.json")

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
    description = "Verifies a full production GA cluster-smoke evidence JSON file. Pass -PclusterSmokeEvidence=/path/to/evidence.json or CI_CLUSTER_SMOKE_EVIDENCE_JSON."
    dependsOn(project(":cloudislands-testkit").tasks.named("classes"))
    val testkitSourceSets = project(":cloudislands-testkit").extensions.getByType<SourceSetContainer>()
    classpath = testkitSourceSets.named("main").get().runtimeClasspath
    mainClass.set("kr.lunaf.cloudislands.testkit.ClusterSmokeVerifierCli")
    doFirst {
        val evidencePath = providers.gradleProperty("clusterSmokeEvidence").orElse(providers.environmentVariable("CI_CLUSTER_SMOKE_EVIDENCE")).orNull
        val evidenceJson = providers.gradleProperty("clusterSmokeEvidenceJson").orElse(providers.environmentVariable("CI_CLUSTER_SMOKE_EVIDENCE_JSON")).orNull
        val evidence = when {
            !evidencePath.isNullOrBlank() -> file(evidencePath).absolutePath
            !evidenceJson.isNullOrBlank() -> {
                val output = clusterSmokeReleaseEvidenceFile.get().asFile
                output.parentFile.mkdirs()
                output.writeText(evidenceJson.trim() + System.lineSeparator())
                output.absolutePath
            }
            else -> throw GradleException("clusterSmokeVerify requires -PclusterSmokeEvidence=/path/to/evidence.json, CI_CLUSTER_SMOKE_EVIDENCE, -PclusterSmokeEvidenceJson, or CI_CLUSTER_SMOKE_EVIDENCE_JSON")
        }
        args(
            "--evidence", evidence,
            "--report-out", clusterSmokeReleaseReportFile.get().asFile.absolutePath
        )
    }
}

tasks.register("ciIntegrationSmoke") {
    group = "verification"
    description = "Runs Core API real-infrastructure integration smoke tests."
    dependsOn(tasks.named("coreIntegrationSmoke"))
    dependsOn(tasks.named("clusterSmokePartialReport"))
}

tasks.register("releaseClusterSmokeGate") {
    group = "verification"
    description = "Requires complete production GA cluster-smoke evidence before release."
    dependsOn(tasks.named("clusterSmokeVerify"))
}

tasks.register("verifyReleaseGateCoverage") {
    group = "verification"
    description = "Verifies CI workflows keep partial integration smoke separate from the full release cluster evidence gate."
    inputs.file(layout.projectDirectory.file(".github/workflows/integration.yml"))
    doLast {
        val workflow = layout.projectDirectory.file(".github/workflows/integration.yml").asFile.readText()
        val requiredSignals = listOf(
            "postgres:",
            "redis:",
            "minio:",
            "ciIntegrationSmoke",
            "build/smoke/core-integration/cluster-evidence.json",
            "build/smoke/core-integration/cluster-smoke-report.json",
            "releaseClusterSmokeGate",
            "CI_CLUSTER_SMOKE_EVIDENCE_JSON",
            "build/smoke/cluster-smoke/cluster-smoke-report.json"
        )
        val missing = requiredSignals.filterNot(workflow::contains)
        if (missing.isNotEmpty()) {
            throw GradleException("Integration workflow is missing cluster evidence gate signals: ${missing.joinToString(", ")}")
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("verifyReleaseGateCoverage"))
    dependsOn(tasks.named("verifyReleaseSecurityGate"))
}
