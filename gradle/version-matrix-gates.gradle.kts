import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.jvm.tasks.Jar
import java.util.zip.ZipFile

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

private val gateVersionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
private val gateMinecraftBaselineVersion = gateVersionCatalog.findVersion("minecraft-baseline").orElseThrow().requiredVersion
private val minecraftVersionMatrixFile = layout.projectDirectory.file("gradle/minecraft-versions.toml").asFile
private val minecraftVersionMatrix = MinecraftVersionMatrix.parse(minecraftVersionMatrixFile)

tasks.register("verifyMinecraftVersionMatrix") {
    group = "verification"
    description = "Validates the typed Minecraft version matrix and writes the external runtime matrix report."
    inputs.file(minecraftVersionMatrixFile)
    inputs.file(layout.projectDirectory.file(".github/workflows/build.yml"))
    outputs.file(rootProject.layout.projectDirectory.dir("../codex-output").file("runtime-matrix.md"))
    doLast {
        minecraftVersionMatrix.validate(rootProject.projectDir, gateMinecraftBaselineVersion)
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
        "protocolCompatibilityCheck",
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

tasks.register("verifyAdapterPackaging") {
    group = "verification"
    description = "Verifies the final Paper artifact contains all matrix adapters without duplicate entries."
    val paperJar = project(":cloudislands-paper").tasks.named<Jar>("shadowJar")
    dependsOn(paperJar)
    inputs.file(paperJar.flatMap { it.archiveFile })
    inputs.file(minecraftVersionMatrixFile)
    doLast {
        minecraftVersionMatrix.validate(rootProject.projectDir, gateMinecraftBaselineVersion)
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
    dependsOn(tasks.named("verifyAdapterPackaging"))
}



tasks.named("distBundle") {
    dependsOn(tasks.named("verifyAdapterPackaging"))
}
