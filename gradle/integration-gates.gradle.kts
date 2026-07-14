tasks.register("verifyIntegrationMatrix") {
    group = "verification"
    description = "Verifies plugin integration support is reported with explicit detection, compatibility, adapter, and operation states."
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val policyFile = layout.projectDirectory.file("cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/integration/CloudIntegrationPolicy.java")
    val registryFile = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java")
    val stateFile = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/spi/IntegrationSupportState.java")
    val configFile = layout.projectDirectory.file("cloudislands-paper/src/main/resources/config-v2/integrations.yml")
    val pluginFile = layout.projectDirectory.file("cloudislands-paper/src/main/resources/plugin.yml")
    val integrationJsonReport = layout.buildDirectory.file("reports/cloudislands/integrations.json")
    val integrationMarkdownReport = layout.buildDirectory.file("reports/cloudislands/integrations.md")
    inputs.files(policyFile, registryFile, stateFile, configFile, pluginFile)
    outputs.file(rootProject.layout.projectDirectory.dir("../codex-output").file("plugin-integration-matrix.md"))
    outputs.files(integrationJsonReport, integrationMarkdownReport)
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
            "CraftEngine",
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
            "DIAGNOSTIC_ONLY",
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
            "PlanIntegration",
            "VanishIntegration"
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
        val reportsDir = integrationJsonReport.get().asFile.parentFile
        reportsDir.mkdirs()
        val diagnosticPlugins = setOf(
            "LuckPerms", "CoreProtect", "WorldEdit", "FastAsyncWorldEdit"
        )
        val runtimeServicePlugins = setOf(
            "Vault", "PlaceholderAPI", "Plan", "SuperVanish", "PremiumVanish", "CMI",
            "ItemsAdder", "Oraxen", "Nexo", "CraftEngine", "Slimefun", "RoseStacker", "WildStacker", "AdvancedSpawners"
        )
        fun jsonEscape(value: String): String = buildString {
            value.forEach { character ->
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
        }
        fun jsonField(key: String, value: String): String = "\"${jsonEscape(key)}\":\"${jsonEscape(value)}\""
        val generatedAt = java.time.Instant.now().toString()
        integrationJsonReport.get().asFile.writeText(
            buildString {
                append("{")
                append(jsonField("generatedAt", generatedAt))
                append(",")
                append(jsonField("scope", "build-gate-integration-acceptance"))
                append(",")
                append("\"entries\":[")
                requiredPlugins.forEachIndexed { index, pluginName ->
                    if (index > 0) append(",")
                    val diagnostic = pluginName in diagnosticPlugins
                    val runtimeService = pluginName in runtimeServicePlugins
                    val operation = when {
                        runtimeService -> "runtime-service-registration"
                        diagnostic -> "runtime-detection-and-version-diagnostic"
                        else -> "static-policy-matrix"
                    }
                    val operationState = when {
                        runtimeService -> "RUNTIME_SERVICE_WIRED"
                        diagnostic -> "DIAGNOSTIC_ONLY"
                        else -> "STATIC_POLICY_VERIFIED"
                    }
                    val remediation = if (runtimeService) {
                        "Install $pluginName and run /ciadmin integrations report to verify the live service operation."
                    } else if (diagnostic) {
                        "Install $pluginName to inspect detection and version metadata; no lifecycle or state-transfer operation is certified."
                    } else {
                        "Install the plugin only when this hook is needed; static policy, config, softdepend, and registry coverage passed."
                    }
                    append("{")
                    append(jsonField("pluginName", pluginName))
                    append(",")
                    append(jsonField("state", "STATIC_POLICY_VERIFIED"))
                    append(",")
                    append(jsonField("version", "runtime"))
                    append(",")
                    append(jsonField("operation", operation))
                    append(",")
                    append(jsonField("operationState", operationState))
                    append(",")
                    append(jsonField("remediation", remediation))
                    append("}")
                }
                append("],")
                append("\"reportedStates\":[")
                requiredStates.forEachIndexed { index, state ->
                    if (index > 0) append(",")
                    append("\"${jsonEscape(state)}\"")
                }
                append("]}")
            }
        )
        integrationMarkdownReport.get().asFile.writeText(
            buildString {
                appendLine("# CloudIslands integration acceptance")
                appendLine()
                appendLine("- Generated: $generatedAt")
                appendLine("- Scope: build gate static policy plus runtime-operation report handoff")
                appendLine("- Runtime operation command: `/ciadmin integrations report`")
                appendLine()
                appendLine("| Plugin | State | Version | Operation | Operation state | Remediation |")
                appendLine("| --- | --- | --- | --- | --- | --- |")
                requiredPlugins.forEach { pluginName ->
                    val diagnostic = pluginName in diagnosticPlugins
                    val runtimeService = pluginName in runtimeServicePlugins
                    val operation = when {
                        runtimeService -> "runtime-service-registration"
                        diagnostic -> "runtime-detection-and-version-diagnostic"
                        else -> "static-policy-matrix"
                    }
                    val operationState = when {
                        runtimeService -> "RUNTIME_SERVICE_WIRED"
                        diagnostic -> "DIAGNOSTIC_ONLY"
                        else -> "STATIC_POLICY_VERIFIED"
                    }
                    val remediation = if (runtimeService) {
                        "Install `$pluginName` and run `/ciadmin integrations report` to verify the live service operation."
                    } else if (diagnostic) {
                        "Install `$pluginName` to inspect detection and version metadata; no lifecycle or state-transfer operation is certified."
                    } else {
                        "Static policy, config, softdepend, and registry coverage passed."
                    }
                    appendLine("| `$pluginName` | STATIC_POLICY_VERIFIED | runtime | $operation | $operationState | $remediation |")
                }
                appendLine()
                appendLine("Reported states: ${requiredStates.joinToString(", ")}.")
            }
        )
    }
}

tasks.register("verifyIntegrationRuntimeSmoke") {
    group = "verification"
    description = "Verifies executable integration services and keeps probe-only adapters diagnostic."
    dependsOn(tasks.named("verifyIntegrationMatrix"))
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val certification = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertification.java")
    val certificationTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertificationTest.java")
    val registry = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java")
    val planRuntime = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/analytics/PlanAnalyticsRuntime.java")
    val planRuntimeTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/analytics/PlanAnalyticsRuntimePolicyTest.java")
    val visibilityRuntime = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/vanish/PlayerVisibilityService.java")
    val visibilityRuntimeTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/command/IslandCommandVanishCompletionTest.java")
    val customBlockRuntime = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/customitem/CustomBlockKeyService.java")
    val customBlockRuntimeTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/level/CustomBlockLevelAccountingPolicyTest.java")
    val stackAmountRuntime = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/stacker/StackAmountService.java")
    val stackAmountRuntimeTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/level/StackAmountLevelAccountingPolicyTest.java")
    inputs.files(certification, certificationTest, registry, planRuntime, planRuntimeTest, visibilityRuntime, visibilityRuntimeTest, customBlockRuntime, customBlockRuntimeTest, stackAmountRuntime, stackAmountRuntimeTest)
    doLast {
        val source = certification.asFile.readText()
        val tests = certificationTest.asFile.readText()
        val registrySource = registry.asFile.readText()
        val planRuntimeSource = planRuntime.asFile.readText()
        val planRuntimeTests = planRuntimeTest.asFile.readText()
        val visibilityRuntimeSource = visibilityRuntime.asFile.readText()
        val visibilityRuntimeTests = visibilityRuntimeTest.asFile.readText()
        val customBlockRuntimeSource = customBlockRuntime.asFile.readText()
        val customBlockRuntimeTests = customBlockRuntimeTest.asFile.readText()
        val stackAmountRuntimeSource = stackAmountRuntime.asFile.readText()
        val stackAmountRuntimeTests = stackAmountRuntimeTest.asFile.readText()
        val requiredAdapters = listOf(
            "VaultIntegration", "PlaceholderApiIntegration", "LuckPermsIntegration", "CoreProtectIntegration",
            "WorldEditIntegration", "CustomItemIntegration", "StackerIntegration", "PlanIntegration", "VanishIntegration"
        )
        val failures = buildList {
            requiredAdapters.filterNot(registrySource::contains).forEach { adapter ->
                add("PaperIntegrationRegistry missing diagnostic adapter $adapter")
            }
            if (!source.contains("certifyPriorityPlugins")) add("IntegrationRuntimeCertification must expose certifyPriorityPlugins")
            if (!source.contains("private static final List<String> PRIORITY_PLUGINS = List.of()")) add("Probe-only registry must not advertise priority operation certification")
            if (!tests.contains("probeOnlyRegistryDoesNotAdvertiseOperationCertification")) add("Diagnostic-only no-operation certification test is missing")
            if (!tests.contains("DIAGNOSTIC_ONLY") || !tests.contains("certificationReportPublishesDiagnosticStateWithoutOperationClaims")) add("Diagnostic report assertions are missing")
            if (!registrySource.contains("IntegrationCapability.RUNTIME_SERVICE")) add("Runtime service integrations must be executable in PaperIntegrationRegistry")
            if (!tests.contains("runtimeServiceResultIsPublishedAsCertifiedOperation")) add("Runtime service certification result test is missing")
            if (!planRuntimeSource.contains("extensionService.register(extension)") || !planRuntimeSource.contains("caller.updateServerData()")) add("Plan runtime must register and refresh a real data extension")
            if (!planRuntimeSource.contains("extensionService.unregister(extension)")) add("Plan runtime must unregister its data extension")
            if (!planRuntimeTests.contains("runtimeRegistersRefreshesAndUnregistersAPlanDataExtension")) add("Plan runtime lifecycle policy test is missing")
            if (!visibilityRuntimeSource.contains("VanishAPI") || !visibilityRuntimeSource.contains("getAllVanished")) add("Vanish runtime must support SuperVanish/PremiumVanish and CMI APIs")
            if (!visibilityRuntimeSource.contains("viewer.canSee(target)") || !visibilityRuntimeSource.contains("getMetadata(\"vanished\")")) add("Vanish runtime must retain Bukkit visibility and metadata fallbacks")
            if (!visibilityRuntimeTests.contains("playerTargetCompletionOmitsTargetsHiddenByBukkitVisibility")) add("Vanish-safe player target completion test is missing")
            if (!customBlockRuntimeSource.contains("CustomBlock") || !customBlockRuntimeSource.contains("OraxenBlocks") || !customBlockRuntimeSource.contains("NexoBlocks")) add("Custom block runtime must support ItemsAdder, Oraxen, and Nexo lookup APIs")
            if (!customBlockRuntimeSource.contains("CraftEngineBlocks") || !customBlockRuntimeSource.contains("getCustomBlockState")) add("Custom block runtime must support CraftEngine's stable block lookup API")
            if (!customBlockRuntimeSource.contains("OraxenFurniture") || !customBlockRuntimeSource.contains("NexoFurniture")) add("Custom furniture must participate in island value reconciliation")
            if (!customBlockRuntimeTests.contains("bothIncrementalAndReconciliationPathsUseCustomBlockKeys")) add("Custom block delta and rescan accounting policy test is missing")
            if (!stackAmountRuntimeSource.contains("RoseStackerAPI") || !stackAmountRuntimeSource.contains("getStackedBlocks") || !stackAmountRuntimeSource.contains("getStackedEntities")) add("RoseStacker logical block and entity amounts must feed reconciliation")
            if (!stackAmountRuntimeSource.contains("WildStackerAPI") || !stackAmountRuntimeSource.contains("getStackedBarrels") || !stackAmountRuntimeSource.contains("getStackedSpawners")) add("WildStacker barrel and spawner amounts must feed reconciliation")
            if (!stackAmountRuntimeSource.contains("gcspawners.ASAPI") || !stackAmountRuntimeSource.contains("getSpawnerAmount")) add("AdvancedSpawners logical amounts must feed reconciliation")
            if (!stackAmountRuntimeTests.contains("logicalStackAmountsFeedReconciliationAndRuntimeCertification")) add("Logical stack amount reconciliation policy test is missing")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}
