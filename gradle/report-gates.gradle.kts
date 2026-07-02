private data class ReportMinecraftVersionRange(val major: Int, val minor: Int) : Comparable<ReportMinecraftVersionRange> {
    override fun compareTo(other: ReportMinecraftVersionRange): Int =
        compareValuesBy(this, other, ReportMinecraftVersionRange::major, ReportMinecraftVersionRange::minor)

    companion object {
        private val PATTERN = Regex("""(\d+)\.(\d+)\.x""")

        fun parse(value: String): ReportMinecraftVersionRange {
            val match = PATTERN.matchEntire(value)
                ?: throw GradleException("Unsupported normalizedRange '$value'; expected '<major>.<minor>.x'")
            return ReportMinecraftVersionRange(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }
}

private data class ReportMinecraftVersionEntry(
    val id: String,
    val normalizedRange: String,
    val compileEnabled: Boolean,
    val bootSmokeEnabled: Boolean,
    val releaseSupported: Boolean,
    val experimental: Boolean,
    val adapterClass: String
) {
    val range: ReportMinecraftVersionRange = ReportMinecraftVersionRange.parse(normalizedRange)
    val taskSuffix: String = id.filter(Char::isDigit)
    val compileTaskName: String = "paper${taskSuffix}Compile"
    val bootSmokeTaskName: String = "paper${taskSuffix}BootSmoke"
    val adapterSimpleName: String = adapterClass.substringAfterLast('.')
}

private data class ReportMinecraftVersionMatrix(val entries: List<ReportMinecraftVersionEntry>) {
    companion object {
        fun parse(file: File): ReportMinecraftVersionMatrix {
            if (!file.isFile) {
                throw GradleException("Minecraft version matrix file is missing: ${file.path}")
            }
            val entries = mutableListOf<ReportMinecraftVersionEntry>()
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
            return ReportMinecraftVersionMatrix(entries)
        }

        private fun entry(values: Map<String, String>, file: File, line: Int): ReportMinecraftVersionEntry =
            ReportMinecraftVersionEntry(
                id = required(values, "id", file, line),
                normalizedRange = required(values, "normalizedRange", file, line),
                compileEnabled = required(values, "compileEnabled", file, line).toBooleanStrict(),
                bootSmokeEnabled = required(values, "bootSmokeEnabled", file, line).toBooleanStrict(),
                releaseSupported = required(values, "releaseSupported", file, line).toBooleanStrict(),
                experimental = required(values, "experimental", file, line).toBooleanStrict(),
                adapterClass = required(values, "adapterClass", file, line)
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
        "replay and cache convergence are unit-tested; releaseClusterSmokeGate covers multi-Paper failover evidence",
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
        "IMPLEMENTED_VERIFIED",
        "upgrade effects apply size, limits, fly, generator tier, biome validation, and player border policy",
        "Paper commands compile and tests cover command policy plus border runtime calculation",
        "Paper adapter isolates version-sensitive runtime access",
        "verifyUpgradeEffectCoverage covers Core upgrade effects, biome normalization, and Paper world-border policy",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/command/IslandEnvironmentCommandHandler.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandControllerPolicyTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/platform/compatibility/PaperPlatformBoundaryTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/upgrade/UpgradeEffectApplierTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutesTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/IslandBorderRuntimePolicyTest.java"
        ),
        "operator live-server biome painting acceptance is still recommended; CI verifies the Core mutation and Paper border application policy"
    ),
    FeatureParityEntry(
        "bank/economy/missions/challenges/generators/limits",
        "IMPLEMENTED_VERIFIED",
        "bank safety, economy hooks, mission triggers/rewards, challenges, generator rules, and limits have verification gates",
        "Paper mission listeners, bank rollback UX, and generator listeners have targeted tests",
        "version-neutral plus Paper/Satis runtime boundaries",
        "verifyMissionEventProgress, verifyMissionRewardCoverage, verifyGeneratorRules, verifyEconomyTransactionSafety, and verifyIntegrationRuntimeSmoke cover the current scope",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/mission/IslandMissionProgressListener.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/mission/MissionRewardServiceTest.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/application/BankUseCaseTest.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/generator/IslandGeneratorListener.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/GeneratorRoutesTest.java"
        ),
        "operator live-server economy/provider acceptance is still recommended; fixture-backed priority Vault certification is enforced"
    ),
    FeatureParityEntry(
        "chat/logs/reviews",
        "IMPLEMENTED_VERIFIED",
        "chat listener, audit/log routes, visitor stats, and review moderation have verification gates",
        "Paper chat listener compiles under matrix and Core review moderation has route/repository/schema tests",
        "version-neutral where possible",
        "verifyReviewModerationCoverage plus Core audit/visitor route tests cover current workflow",
        "audit log replay is covered in Core tests",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/session/PaperChatListener.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/AuditRoutesTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutesTest.java",
            "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/review/InMemoryIslandReviewRepositoryTest.java",
            "cloudislands-core-service/src/main/resources/db/migration/V70__review_moderation.sql"
        ),
        "live multi-player chat moderation acceptance is deployment-specific outside unit CI"
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
        "releaseClusterSmokeGate now includes database backup, object bundle, manifest checksum, restore, route, and audit evidence"
    ),
    FeatureParityEntry(
        "Java API/events/addons",
        "IMPLEMENTED_VERIFIED",
        "public API, events, addon metadata, and compatibility contract are tested",
        "Paper API bridge has unit coverage",
        "version-neutral API with runtime metadata compatibility check",
        "apiCompatibilityCheck verifies release contract metadata and the public API signature baseline",
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
        "IMPLEMENTED_VERIFIED",
        "integration policy, localization files, GUI components, and priority plugin runtime certification fixtures exist",
        "Paper integration registry reports explicit operation states and priority runtime certification",
        "plugin-specific adapters are active where implemented",
        "verifyIntegrationRuntimeSmoke proves priority plugin operation smoke fixtures for Vault, LuckPerms, PlaceholderAPI, WorldEdit, and CoreProtect",
        "not recovery-specific",
        listOf(
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java",
            "cloudislands-paper/src/main/resources/config-v2/integrations.yml",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/gui/IslandConfirmationMenu.java",
            "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertification.java",
            "cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertificationTest.java"
        ),
        "full third-party server farms remain operator acceptance; CI verifies fixture-backed priority operation certification"
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

private fun minecraftVersionFeatureMatrixMarkdown(matrix: ReportMinecraftVersionMatrix): String = buildString {
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

private fun parityJson(matrix: ReportMinecraftVersionMatrix): String {
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

private val reportMinecraftVersionMatrixFile = layout.projectDirectory.file("gradle/minecraft-versions.toml").asFile
private val reportMinecraftVersionMatrix = ReportMinecraftVersionMatrix.parse(reportMinecraftVersionMatrixFile)

val superiorSkyblockParityReportFile = rootProject.layout.projectDirectory.dir("../codex-output").file("superiorskyblock2-parity.md")
val minecraftVersionFeatureMatrixFile = rootProject.layout.projectDirectory.dir("../codex-output").file("minecraft-version-feature-matrix.md")
val featureParityJsonFile = rootProject.layout.projectDirectory.dir("../codex-output").file("parity.json")

tasks.register("verifyFeatureParityEvidence") {
    group = "verification"
    description = "Verifies README feature parity claims and writes detailed parity evidence outside the repository."
    inputs.file(layout.projectDirectory.file("README.md"))
    inputs.file(reportMinecraftVersionMatrixFile)
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
        versionFile.writeText(minecraftVersionFeatureMatrixMarkdown(reportMinecraftVersionMatrix))
        jsonFile.writeText(parityJson(reportMinecraftVersionMatrix))
    }
}

tasks.register("verifyFeatureParityMatrix") {
    group = "verification"
    description = "Compatibility alias for the feature parity evidence gate."
    dependsOn(tasks.named("verifyFeatureParityEvidence"))
}
