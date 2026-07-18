tasks.register("verifyGradleGateSplit") {
    group = "verification"
    description = "Verifies release, version, migration, integration, distribution, and report build gates stay split from the root build script."
    val rootBuild = layout.projectDirectory.file("build.gradle.kts")
    val splitScripts = listOf(
        "gradle/version-matrix-gates.gradle.kts",
        "gradle/release-gates.gradle.kts",
        "gradle/migration-gates.gradle.kts",
        "gradle/integration-gates.gradle.kts",
        "gradle/distribution.gradle.kts",
        "gradle/report-gates.gradle.kts"
    )
    inputs.file(rootBuild)
    inputs.files(splitScripts.map { layout.projectDirectory.file(it) })
    doLast {
        val rootSource = rootBuild.asFile.readText()
        val distributionSource = layout.projectDirectory.file("gradle/distribution.gradle.kts").asFile.readText()
        val missingScripts = splitScripts.filterNot { layout.projectDirectory.file(it).asFile.isFile }
        val missingApplies = splitScripts.filterNot { rootSource.contains("apply(from = \"$it\")") }
        val forbiddenRootSignals = listOf(
            "private data class MinecraftVersionRange",
            "private data class MinecraftVersionMatrix",
            "private val paperVersionCompileTasks",
            "tasks.register(\"verifyMinecraftVersionMatrix\")",
            "tasks.register(\"verifyReadmeVersionTable\")",
            "tasks.register(\"verifyAdapterPackaging\")",
            "tasks.register(\"verifyFeatureParityEvidence\")",
            "tasks.register(\"verifyIntegrationMatrix\")",
            "tasks.register(\"verifyMigrationFixtures\")",
            "tasks.register<Exec>(\"coreIntegrationSmoke\")",
            "tasks.register(\"ciReleaseClusterEvidenceGate\")",
            "tasks.register(\"releaseClusterSmokeGate\")",
            "tasks.register<Zip>(\"distBundle\")",
            "tasks.register(\"distChecksums\")",
            "tasks.register(\"distChangelog\")",
            "tasks.register(\"distSbom\")",
            "tasks.register(\"distProvenance\")",
            "tasks.register(\"verifyOperatorReleaseDocumentation\")"
        ).filter(rootSource::contains)
        val requiredTasks = listOf(
            "verifyMinecraftVersionMatrix",
            "verifyReadmeVersionTable",
            "verifyAdapterPackaging",
            "compileAllMinecraftVersions",
            "bootSmokeAllStableMinecraftVersions",
            "ciBootSmoke",
            "coreIntegrationSmoke",
            "ciIntegrationSmoke",
            "ciReleaseClusterEvidenceGate",
            "releaseClusterSmokeGate",
            "verifyReleaseGateCoverage",
            "verifyMigrationFixtures",
            "verifyIntegrationMatrix",
            "verifyIntegrationRuntimeSmoke",
            "distBundle",
            "distChecksums",
            "distChangelog",
            "distSbom",
            "distProvenance",
            "verifyOperatorReleaseDocumentation",
            "verifyFeatureParityEvidence"
        )
        val missingTasks = requiredTasks.filterNot(tasks.names::contains)
        val requiredProvenanceFreshnessSignals = listOf(
            "inputs.property(\"projectVersion\", distProjectVersion)",
            "inputs.property(\"gitCommit\", distGitCommit)",
            "inputs.property(\"gitDirty\", distGitDirty)",
            "Release provenance version is stale",
            "Release provenance commit is stale",
            "Release provenance dirty state is stale",
            "Release SBOM version is stale"
        )
        val missingProvenanceFreshnessSignals = requiredProvenanceFreshnessSignals.filterNot(distributionSource::contains)
        val failures = buildList {
            if (missingScripts.isNotEmpty()) add("Gradle split scripts missing: ${missingScripts.joinToString(", ")}")
            if (missingApplies.isNotEmpty()) add("Root build.gradle.kts missing split script apply statements: ${missingApplies.joinToString(", ")}")
            if (forbiddenRootSignals.isNotEmpty()) add("Root build.gradle.kts still owns split gate logic: ${forbiddenRootSignals.joinToString(", ")}")
            if (missingTasks.isNotEmpty()) add("Split Gradle task names were not preserved: ${missingTasks.joinToString(", ")}")
            if (missingProvenanceFreshnessSignals.isNotEmpty()) add("Distribution provenance freshness guards missing: ${missingProvenanceFreshnessSignals.joinToString(", ")}")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}
