import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer

val latestStableBootVersion = extra["cloudislandsLatestStableBootVersion"] as String

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

tasks.register<Exec>("mysqlCoreIntegrationSmoke") {
    group = "verification"
    description = "Runs the full dual-Core API integration smoke against MySQL, Redis, and S3-compatible object storage."
    val coreService = project(":cloudislands-core-service")
    val installTask = coreService.tasks.named("installDist")
    dependsOn(installTask)
    mustRunAfter(tasks.named("coreIntegrationSmoke"))
    environment("CI_DATABASE_TYPE", "MYSQL")
    environment("CI_JDBC_URL", "jdbc:mysql://127.0.0.1:3306/cloudislands")
    environment("CI_DB_USERNAME", "cloudislands")
    environment("CI_DB_PASSWORD", "cloudislands")
    doFirst {
        commandLine(
            "python3",
            file("scripts/ci/core_integration_smoke.py").absolutePath,
            "--core-bin",
            coreService.layout.buildDirectory.file("install/cloudislands-core-service/bin/cloudislands-core-service").get().asFile.absolutePath,
            "--work-dir",
            layout.buildDirectory.dir("smoke/core-integration-mysql").get().asFile.absolutePath,
            "--port",
            "18643",
            "--timeout",
            "90",
            "--evidence-out",
            layout.buildDirectory.file("smoke/core-integration-mysql/mysql-evidence.json").get().asFile.absolutePath
        )
    }
}

val clusterSmokeEvidenceFile = layout.buildDirectory.file("smoke/core-integration/cluster-evidence.json")
val clusterSmokePartialReportFile = layout.buildDirectory.file("smoke/core-integration/cluster-smoke-report.json")
val clusterSmokeReleaseEvidenceFile = layout.buildDirectory.file("smoke/cluster-smoke/cluster-evidence.json")
val clusterSmokeReleaseReportFile = layout.buildDirectory.file("smoke/cluster-smoke/cluster-smoke-report.json")

tasks.register<Exec>("generateReleaseClusterEvidence") {
    group = "verification"
    description = "Combines Core integration smoke and boot smoke logs into production GA cluster evidence."
    dependsOn(tasks.named("coreIntegrationSmoke"))
    dependsOn(tasks.named("ciBootSmoke"))
    doFirst {
        commandLine(
            "python3",
            file("scripts/ci/release_cluster_evidence.py").absolutePath,
            "--core-evidence", clusterSmokeEvidenceFile.get().asFile.absolutePath,
            "--paper-log", layout.buildDirectory.file("smoke/paper-$latestStableBootVersion/server.log").get().asFile.absolutePath,
            "--velocity-log", layout.buildDirectory.file("smoke/velocity/server.log").get().asFile.absolutePath,
            "--repo-root", layout.projectDirectory.asFile.absolutePath,
            "--out", clusterSmokeReleaseEvidenceFile.get().asFile.absolutePath
        )
    }
}

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
    dependsOn(tasks.named("generateReleaseClusterEvidence"))
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
            clusterSmokeReleaseEvidenceFile.get().asFile.exists() -> clusterSmokeReleaseEvidenceFile.get().asFile.absolutePath
            else -> throw GradleException("clusterSmokeVerify requires generated release evidence, -PclusterSmokeEvidence=/path/to/evidence.json, CI_CLUSTER_SMOKE_EVIDENCE, -PclusterSmokeEvidenceJson, or CI_CLUSTER_SMOKE_EVIDENCE_JSON")
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
    dependsOn(tasks.named("mysqlCoreIntegrationSmoke"))
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
    inputs.files(
        layout.projectDirectory.file(".github/workflows/integration.yml"),
        layout.projectDirectory.file("scripts/ci/core_integration_smoke.py"),
        layout.projectDirectory.file("gradle/release-gates.gradle.kts")
    )
    doLast {
        val workflow = layout.projectDirectory.file(".github/workflows/integration.yml").asFile.readText()
        val coreSmoke = layout.projectDirectory.file("scripts/ci/core_integration_smoke.py").asFile.readText()
        val buildLogic = layout.projectDirectory.file("gradle/release-gates.gradle.kts").asFile.readText()
        val requiredSignals = listOf(
            "postgres:",
            "mysql:",
            "default-mysql-client",
            "redis:",
            "minio:",
            "ciIntegrationSmoke",
            "build/smoke/core-integration/cluster-evidence.json",
            "build/smoke/core-integration/cluster-smoke-report.json",
            "build/smoke/core-integration-mysql/mysql-evidence.json",
            "./gradlew releaseClusterSmokeGate --no-daemon",
            "build/smoke/cluster-smoke/cluster-smoke-report.json"
        )
        val missing = requiredSignals.filterNot(workflow::contains)
        if (missing.isNotEmpty()) {
            throw GradleException("Integration workflow is missing cluster evidence gate signals: ${missing.joinToString(", ")}")
        }
        val mysqlBuildSignals = listOf(
            "tasks.register<Exec>(\"mysqlCoreIntegrationSmoke\")",
            "CI_DATABASE_TYPE\", \"MYSQL",
            "dependsOn(tasks.named(\"mysqlCoreIntegrationSmoke\"))"
        )
        val missingMysqlBuildSignals = mysqlBuildSignals.filterNot(buildLogic::contains)
        if (missingMysqlBuildSignals.isNotEmpty()) {
            throw GradleException("Integration build logic is missing MySQL runtime smoke signals: ${missingMysqlBuildSignals.joinToString(", ")}")
        }
        val mysqlRuntimeSignals = listOf(
            "{\"postgresql\", \"mysql\", \"mariadb\"}",
            "mysqldump",
            "databaseBackend\", database_type",
            "shared-{database_authority}-authority"
        )
        val missingMysqlRuntimeSignals = mysqlRuntimeSignals.filterNot(coreSmoke::contains)
        if (missingMysqlRuntimeSignals.isNotEmpty()) {
            throw GradleException("Core integration smoke is missing MySQL runtime assertions: ${missingMysqlRuntimeSignals.joinToString(", ")}")
        }
        for (forbidden in listOf(
            "secrets.CI_CLUSTER_SMOKE_EVIDENCE_JSON",
            "CI_CLUSTER_SMOKE_EVIDENCE_JSON is required"
        )) {
            if (workflow.contains(forbidden)) {
                throw GradleException("Integration workflow must verify release evidence generated by the current run, not require an external JSON secret: $forbidden")
            }
        }
        val loadProbe = coreSmoke.substringAfter("def run_load_probe(").substringBefore("def player_interaction_evidence(")
        val clearIndex = loadProbe.indexOf("/v1/admin/routes/clear")
        val eventBaselineIndex = loadProbe.indexOf("primary_since_seq =")
        if (clearIndex < 0 || eventBaselineIndex < 0 || clearIndex > eventBaselineIndex) {
            throw GradleException("Core load probe must clear the reused route session before capturing its event replay baseline")
        }
        for (signal in listOf(
            "LOAD_TEST_EVENT_REPLAY_RETRY",
            "event_replay_attempts < 20",
            "heartbeat(secondary_url, active_node, active_server_name",
            "eventPublishObserved",
            "eventReplayAttempts"
        )) {
            if (!loadProbe.contains(signal)) {
                throw GradleException("Core load probe must retry event production while preserving replay evidence: $signal")
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("verifyCommandCoverage"))
    dependsOn(tasks.named("verifyCommandHelpCoverage"))
    dependsOn(tasks.named("verifyApiRouteCoverage"))
    dependsOn(tasks.named("verifyRouteDomainCoverage"))
    dependsOn(tasks.named("verifyEventCoverage"))
    dependsOn(tasks.named("verifyMissionEventProgress"))
    dependsOn(tasks.named("verifyMissionRewardCoverage"))
    dependsOn(tasks.named("verifyGuiActionCoverage"))
    dependsOn(tasks.named("verifyGuiButtonCoverage"))
    dependsOn(tasks.named("verifyPermissionCoverage"))
    dependsOn(tasks.named("verifyConfigCoverage"))
    dependsOn(tasks.named("verifyMetricCoverage"))
    dependsOn(tasks.named("verifyMigrationFixtures"))
    dependsOn(tasks.named("verifyGeneratorRules"))
    dependsOn(tasks.named("verifyUpgradeEffectCoverage"))
    dependsOn(tasks.named("verifyReviewModerationCoverage"))
    dependsOn(tasks.named("verifyEconomyTransactionSafety"))
    dependsOn(tasks.named("verifyCoreMutationIdempotency"))
    dependsOn(tasks.named("verifyWarehouseSettlementRecovery"))
    dependsOn(tasks.named("verifyIntegrationRuntimeSmoke"))
    dependsOn(tasks.named("verifyRoutingRefactorCoverage"))
    dependsOn(tasks.named("verifyFeatureParityMatrix"))
    dependsOn(tasks.named("verifyReleaseGateCoverage"))
    dependsOn(tasks.named("verifyReleaseSecurityGate"))
}
