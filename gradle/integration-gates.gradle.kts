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

tasks.register("verifyIntegrationRuntimeSmoke") {
    group = "verification"
    description = "Verifies integration runtime detection and operation-state smoke evidence remains present."
    dependsOn(tasks.named("verifyIntegrationMatrix"))
    dependsOn(project(":cloudislands-paper").tasks.named("test"))
    val certification = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertification.java")
    val certificationTest = layout.projectDirectory.file("cloudislands-paper/src/test/java/kr/lunaf/cloudislands/paper/integration/IntegrationRuntimeCertificationTest.java")
    val registry = layout.projectDirectory.file("cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java")
    inputs.files(certification, certificationTest, registry)
    doLast {
        val source = certification.asFile.readText()
        val tests = certificationTest.asFile.readText()
        val registrySource = registry.asFile.readText()
        val requiredPlugins = listOf("Vault", "LuckPerms", "PlaceholderAPI", "WorldEdit", "CoreProtect")
        val failures = buildList {
            requiredPlugins.filterNot { source.contains("\"$it\"") && tests.contains("\"$it\"") }.forEach { plugin ->
                add("Integration runtime certification missing priority plugin $plugin")
            }
            if (!source.contains("certifyPriorityPlugins")) add("IntegrationRuntimeCertification must expose certifyPriorityPlugins")
            if (!tests.contains("priorityRuntimeCertificationRunsOperationSmokeForEveryPriorityPlugin")) add("Integration runtime certification operation-smoke test is missing")
            if (!tests.contains("priorityRuntimeCertificationRejectsProbeOnlySuccessWithoutOperationEvidence")) add("Integration runtime certification evidence rejection test is missing")
            if (!tests.contains("roundTripVerified") || !tests.contains("stateArtifactKey")) add("Integration runtime certification must require external operation evidence")
            if (!registrySource.contains("new VaultIntegration") || !registrySource.contains("new PlaceholderApiIntegration")) add("PaperIntegrationRegistry must wire Vault and PlaceholderAPI adapters")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}
