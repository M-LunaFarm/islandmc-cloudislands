import java.util.zip.ZipFile

plugins {
    `java-library`
    alias(libs.plugins.shadow)
}

dependencies {
    compileOnly(libs.paper.api)
    compileOnly(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly(libs.placeholderapi)
    implementation(libs.sqlite.jdbc)
    implementation(libs.postgresql)
    implementation(libs.mysql.connector)
    implementation(libs.mariadb.client)
    implementation(libs.hikaricp)
    implementation(project(":cloudislands-protocol"))
    implementation(project(":cloudislands-common"))
    compileOnly(project(":cloudislands-api"))
    testImplementation(project(":cloudislands-api"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.paper.api)
    testImplementation(libs.vault.api) {
        exclude(group = "org.bukkit", module = "bukkit")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filteringCharset = "UTF-8"
    inputs.property("projectVersion", project.version)
    inputs.property("paperApiBaseline", libs.versions.minecraft.baseline.get())
    filesMatching("plugin.yml") {
        expand(
            "projectVersion" to project.version,
            "paperApiBaseline" to libs.versions.minecraft.baseline.get()
        )
    }
}

tasks.shadowJar {
    archiveBaseName.set("CloudIslands-Satis")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    manifest {
        attributes(
            "CloudIslands-Addon" to "cloudislands-satis",
            "CloudIslands-Addon-Product-Role" to "official-feature-pack",
            "CloudIslands-Satis-Origin" to "M-LunaFarm/satismc",
            "CloudIslands-Satis-Origin-Dependency-Policy" to "replace-superiorskyblock2-depend-and-api-with-cloudislands-depend-and-api",
            "CloudIslands-Satis-Origin-API-Replacement" to "SuperiorSkyblockAPI-compileOnly-removed-cloudislands-api-used-instead",
            "CloudIslands-Satis-Legacy-Command-Roots" to "factory,sfactory",
            "CloudIslands-Satis-Owned-Command-Roots" to "factory,sfactory",
            "CloudIslands-Satis-Core-Admin-Root-Owner" to "CloudIslands",
            "CloudIslands-Satis-CIAdmin-Policy" to "not-owned-by-satis-addon",
            "CloudIslands-Addon-Packaging" to "external-plugin-or-built-in-compatible",
            "CloudIslands-Addon-Integration-Modes" to "EXTERNAL_ADDON,BUILT_IN_COMPATIBLE,DISABLED",
            "CloudIslands-Addon-Requires-Plugin" to "CloudIslands",
            "CloudIslands-Addon-Plugin-Yml-Hard-Depend" to "CloudIslands",
            "CloudIslands-Core-Depends-On-Addon" to "false",
            "CloudIslands-Core-Isolation-Policy" to "core-lifecycle-never-depends-on-satis-jar-or-state",
            "CloudIslands-Satis-Standalone-Island-Runtime" to "false",
            "CloudIslands-Addon-Removal-Safe" to "true",
            "CloudIslands-Addon-Data-Retention" to "preserve-addon-state-by-island-uuid",
            "CloudIslands-Addon-State-Authority" to "core-api-table-key-value-or-shared-database",
            "CloudIslands-Addon-Database-Setup-Path" to "setup.database",
            "CloudIslands-Addon-Database-Setup-Source-Policy" to "env-type-explicit-type-core-api-marker-jdbc-url-single-backend-section-legacy-database",
            "CloudIslands-Addon-Database-Supported-Backends" to "CORE_API,POSTGRESQL,MYSQL,MARIADB,SQLITE",
            "CloudIslands-Addon-Database-Shared-State-Safe" to "CORE_API,POSTGRESQL,MYSQL,MARIADB",
            "CloudIslands-Addon-Database-Core-API-Setup-Path" to "setup.database.core-api.enabled",
            "CloudIslands-Addon-Database-Core-API-Flattened-Fallback-Path" to "setup.database.core-api.flattened-fallback.enabled",
            "CloudIslands-Addon-Database-Fallback-Order" to "POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE",
            "CloudIslands-Addon-Database-Split-Brain-Warning" to "keep-shared-backend-before-sqlite-for-multi-island-node-pools",
            "CloudIslands-Addon-Database-Fallback-Safety-Keys" to "database-fallback-risk,database-fallback-production-safe,database-fallback-ready-chain-risk,database-fallback-ready-chain-production-safe,database-fallback-first-shared-backend,database-fallback-local-position",
            "CloudIslands-Addon-Database-Fallback-Safety-Policy" to "production-safe-when-shared-backend-precedes-local-sqlite",
            "CloudIslands-Addon-Database-Env-Keys" to "CLOUDISLANDS_SATIS_DATABASE_TYPE,CLOUDISLANDS_SATIS_JDBC_URL,CLOUDISLANDS_SATIS_DB_USERNAME,CLOUDISLANDS_SATIS_DB_PASSWORD,CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED,CLOUDISLANDS_SATIS_DB_FALLBACK_ORDER",
            "CloudIslands-Satis-Placement-Source-Policy" to "record-core-payload-or-paper-allocator-on-activate-and-migrate",
            "CloudIslands-Satis-AB-Node-Scenario" to "save-on-source-node-remap-active-world-and-cell-on-target-node-restore-state-by-island-uuid",
            "CloudIslands-Satis-Recovery-Scenario" to "heartbeat-expiry-fencing-token-guards-last-confirmed-state-no-duplicate-tick-or-write",
            "CloudIslands-Satis-Relocation-Audit-Policy" to "publish-global-and-island-addon-state-for-active-placement-remaps",
            "CloudIslands-Satis-Relocation-State-Keys" to "last-relocation-island,last-relocation-operation,last-relocation-source-node,last-relocation-target-node,last-relocation-previous-world,last-relocation-previous-center,last-relocation-target-world,last-relocation-target-center,last-relocation-delta,last-relocation-machine-delta,last-relocation-resource-node-delta,last-relocation-placement-changed,last-relocation-machines-remapped,last-relocation-resource-nodes-remapped,last-relocation-machine-remap-deferred,last-relocation-resource-node-remap-deferred,last-relocation-remap-source,last-relocation-policy,last-relocation-at",
            "CloudIslands-Satis-Core-API-Bulk-Endpoints" to "global:/v1/addons/state/table/bulk,/v1/addons/state/table-key-value/bulk-save,/v1/addons/state/table/key-value/bulk-save,/v1/addons/state/table/key-value/bulk/save,/v1/addons/state/table/key-value/bulk,/v1/addons/state/table/key-value/bulk-load,/v1/addons/state/table/load,/v1/addons/state/table/bulk-set;island:/v1/addons/islands/state/table/bulk,/v1/addons/islands/state/table-key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk-save,/v1/addons/islands/state/table/key-value/bulk/save,/v1/addons/islands/state/table/key-value/bulk,/v1/addons/islands/state/table/key-value/bulk-load,/v1/addons/islands/state/table/load,/v1/addons/islands/state/table/bulk-set",
            "CloudIslands-Satis-Core-API-Bulk-Fallback" to "flattened-addon-state-when-enabled",
            "CloudIslands-Satis-Core-State-Writer-Gate" to "addonRuntimeEnabled&&features.addon-state&&databaseBackend=CORE_API&&cloudislands-addon-state-api",
            "CloudIslands-Satis-Core-State-Writer-State-Keys" to "runtime-core-api-state-writer,runtime-core-api-state-writer-gate,runtime-core-api-state-writer-block-reason",
            "CloudIslands-Satis-Placeholder-Exposure-Policy" to "allow-listed-public-island-metrics-only-no-server-node-world-cell-coordinate-placement-or-route-identifiers",
            "CloudIslands-Satis-Placeholder-State-Keys" to "runtime-placeholder-exposure-policy,runtime-placeholder-exposed-keys,runtime-placeholder-denied-internal-fields,runtime-placeholder-internal-placement-exposure",
            "CloudIslands-Addon-Feature-Gates" to "commands,machines,storage,factories,generators,upgrades,missions,menus,gui,lifecycle,resource-nodes,market,contracts,research,maintenance,placeholders,migration,addon-state,route-events,members,permissions,level-values,warps,biomes,chat,templates",
            "CloudIslands-Addon-Feature-Dependencies" to "resource-nodes:machines,market:storage,contracts:storage,missions:contracts+storage,upgrades:research,menus:gui,route-events:addon-state,members:lifecycle,permissions:lifecycle,level-values:lifecycle,warps:lifecycle,biomes:lifecycle,chat:lifecycle,templates:lifecycle",
            "CloudIslands-Addon-Compound-Dependency-Separator" to "+",
            "CloudIslands-Addon-Feature-Disable-Policy" to "disabled-features-preserve-data-and-skip-runtime-components",
            "CloudIslands-Addon-Component-Audit-Keys" to "runtime-active-components,runtime-skipped-components,runtime-blocked-components,runtime-feature-block-reasons,runtime-component-audit",
            "CloudIslands-Satis-Command-List-Format" to "one-line-per-command",
            "CloudIslands-Satis-Command-List-Paging" to "factory command list [page],factory admin command list [page]",
            "CloudIslands-Satis-Command-List-Page-Size" to "12",
            "CloudIslands-Satis-Dirty-Save-State-Keys" to "runtime-dirty-save-running,runtime-dirty-save-pending-writes,runtime-dirty-save-pending-machines,runtime-dirty-save-pending-inventories,runtime-dirty-save-pending-nodes,runtime-dirty-save-pending-islands,runtime-dirty-save-stop-policy,last-preflush-status,last-preflush-writes,last-preflush-failures,last-preflush-at",
            "CloudIslands-Satis-Dirty-Save-Stop-Policy" to "runtime-stop-preflushes-queued-dirty-state-before-task-cancel-and-addon-unregister",
            "CloudIslands-Satis-Preflush-Audit-Policy" to "publish-global-and-island-addon-state-for-migration-deactivation-and-disable-flushes",
            "CloudIslands-Satis-Preflush-Audit-State-Keys" to "last-preflush-island,last-preflush-operation,last-preflush-reason,last-preflush-status,last-preflush-writes,last-preflush-failures,last-preflush-attempts,last-preflush-at,last-preflush-write-fence,last-preflush-handoff-policy",
            "CloudIslands-Satis-Bulk-Last-Publish-State-Keys" to "last-core-bulk-publish-status,last-core-bulk-publish-mode,last-core-bulk-publish-write-path,last-core-bulk-publish-primary-endpoint,last-core-bulk-publish-fallback-endpoint,last-core-bulk-publish-error,last-core-bulk-publish-pending-retries,last-core-global-bulk-publish-status,last-core-global-bulk-publish-mode,last-core-global-bulk-publish-write-path,last-core-global-bulk-publish-primary-endpoint,last-core-global-bulk-publish-fallback-endpoint,last-core-global-bulk-publish-error,last-core-global-bulk-publish-pending-retries",
            "CloudIslands-Satis-Bulk-Retry-State-Keys" to "addon-state-sync-bulk-max-pending-retries,addon-state-sync-island-bulk-pending-retries,addon-state-sync-island-bulk-retries-queued,addon-state-sync-island-bulk-retries-drained,addon-state-sync-island-bulk-retries-dropped,addon-state-sync-global-bulk-pending-retries,addon-state-sync-global-bulk-retries-queued,addon-state-sync-global-bulk-retries-drained,addon-state-sync-global-bulk-retries-dropped",
            "CloudIslands-Addon-Island-Move-Policy" to "island-uuid-stable-remap-active-world-and-cell",
            "CloudIslands-Satis-Legacy-Migration-Source" to "sqlite",
            "CloudIslands-Satis-Legacy-Migration-Approval" to "CONFIRM_IMPORT or CONFIRM_IMPORT:<dryrun-sha256>",
            "CloudIslands-Satis-Legacy-Migration-Read-Only" to "scan,dryrun,verify",
            "CloudIslands-Satis-Legacy-Migration-Import-Policy" to "cross-backend-sqlite-copy-insert-ignore-existing-rows",
            "CloudIslands-Satis-Legacy-Migration-Rollback" to "sqlite-snapshot-restore-or-shared-backend-table-restore",
            "CloudIslands-Addon-API-Only" to "true",
            "SuperiorSkyblock2-Migration-Input-Only" to "true",
            "SuperiorSkyblock2-Runtime-Dependency" to "false",
            "SuperiorSkyblock2-Plugin-Yml-Dependency" to "false",
            "SuperiorSkyblock2-Live-Provider-Hooks" to "false",
            "SuperiorSkyblock2-Provider-Service-Check" to "plugin-enabled-only-no-bukkit-service-binding",
            "CloudIslands-Satis-Runtime-Dependency-Whitelist" to "CloudIslands,Vault,PlaceholderAPI",
            "CloudIslands-Satis-Forbidden-Skyblock-Runtime-Providers" to "SuperiorSkyblock2,BentoBox,ASkyBlock,uSkyBlock,IridiumSkyblock",
            "CloudIslands-Satis-Forbidden-Skyblock-Runtime-Action" to "warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes"
        )
    }
}

tasks.jar {
    enabled = false
}

tasks.register("verifyPackagingCoverage") {
    group = "verification"
    description = "Verifies Satis uses shadowJar dependency packaging without duplicate Paper libraries."
    dependsOn(tasks.named("shadowJar"))
    dependsOn(tasks.named("test"))
    val satisBuild = layout.projectDirectory.file("build.gradle.kts")
    val pluginYml = layout.projectDirectory.file("src/main/resources/plugin.yml")
    val metadataTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/SatisPluginMetadataTest.java")
    val runtimePolicyTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/RuntimeDependencyPolicyTest.java")
    inputs.files(satisBuild, pluginYml, metadataTest, runtimePolicyTest)
    doLast {
        val buildSource = satisBuild.asFile.readText()
        val pluginSource = pluginYml.asFile.readText()
        val tests = metadataTest.asFile.readText() + "\n" + runtimePolicyTest.asFile.readText()
        val jarFile = layout.buildDirectory.dir("libs").get().asFile
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("CloudIslands-Satis-") && it.extension == "jar" }
            .maxByOrNull { it.lastModified() }
            ?: throw GradleException("Satis shadow jar was not built")
        val entries: Set<String> = ZipFile(jarFile).use { zip ->
            zip.entries().asSequence().map { entry -> entry.name }.toSet()
        }
        val missingBuildPolicy = listOf(
            "alias(libs.plugins.shadow)",
            "tasks.shadowJar",
            "mergeServiceFiles()",
            "implementation(libs.sqlite.jdbc)",
            "implementation(libs.postgresql)",
            "implementation(libs.mysql.connector)",
            "implementation(libs.mariadb.client)",
            "implementation(libs.hikaricp)"
        ).filterNot(buildSource::contains)
        val forbiddenPaperLibraries = listOf(
            "libraries:",
            "org.xerial:sqlite-jdbc",
            "org.postgresql:postgresql",
            "com.mysql:mysql-connector-j",
            "org.mariadb.jdbc:mariadb-java-client",
            "com.zaxxer:HikariCP"
        ).filter(pluginSource::contains)
        val requiredJarEntries = listOf(
            "org/sqlite/JDBC.class",
            "org/postgresql/Driver.class",
            "com/mysql/cj/jdbc/Driver.class",
            "org/mariadb/jdbc/Driver.class",
            "com/zaxxer/hikari/HikariDataSource.class"
        )
        val missingJarEntries = requiredJarEntries.filterNot { entry -> entries.contains(entry) }
        val missingTests = listOf(
            "pluginMetadataUsesCentralProjectVersionPaperBaselineAndShadowBundledDependencies",
            "cloudIslandsApiStaysProvidedForSatisRuntime"
        ).filterNot(tests::contains)
        val failures = buildList {
            if (missingBuildPolicy.isNotEmpty()) add("Satis shadow packaging build policy missing: ${missingBuildPolicy.joinToString(", ")}")
            if (forbiddenPaperLibraries.isNotEmpty()) add("Satis plugin.yml still declares duplicate Paper libraries: ${forbiddenPaperLibraries.joinToString(", ")}")
            if (missingJarEntries.isNotEmpty()) add("Satis shadow jar missing bundled database runtime classes: ${missingJarEntries.joinToString(", ")}")
            if (missingTests.isNotEmpty()) add("Satis packaging policy tests missing: ${missingTests.joinToString(", ")}")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.register("verifyRuntimeComponentCoverage") {
    group = "verification"
    description = "Verifies Satis feature/runtime component planning is separated from the plugin and unit tested."
    dependsOn(tasks.named("test"))
    val pluginSource = layout.projectDirectory.file("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java")
    val addonRegistration = layout.projectDirectory.file("src/main/java/kr/seungmin/satisskyfactory/runtime/SatisAddonRegistration.java")
    val featureRuntime = layout.projectDirectory.file("src/main/java/kr/seungmin/satisskyfactory/runtime/SatisFeatureRuntime.java")
    val commandRuntime = layout.projectDirectory.file("src/main/java/kr/seungmin/satisskyfactory/runtime/SatisCommandRuntime.java")
    val listenerRuntime = layout.projectDirectory.file("src/main/java/kr/seungmin/satisskyfactory/runtime/SatisListenerRuntime.java")
    val placeholderRuntime = layout.projectDirectory.file("src/main/java/kr/seungmin/satisskyfactory/runtime/SatisPlaceholderRuntime.java")
    val componentPlan = layout.projectDirectory.file("src/main/java/kr/seungmin/satisskyfactory/runtime/SatisRuntimeComponentPlan.java")
    val addonRegistrationTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/runtime/SatisAddonRegistrationTest.java")
    val featureRuntimeTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/runtime/SatisFeatureRuntimeTest.java")
    val commandRuntimeTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/runtime/SatisCommandRuntimeTest.java")
    val listenerRuntimeTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/runtime/SatisListenerRuntimeTest.java")
    val placeholderRuntimeTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/runtime/SatisPlaceholderRuntimeTest.java")
    val componentPlanTest = layout.projectDirectory.file("src/test/java/kr/seungmin/satisskyfactory/runtime/SatisRuntimeComponentPlanTest.java")
    inputs.files(pluginSource, addonRegistration, featureRuntime, commandRuntime, listenerRuntime, placeholderRuntime, componentPlan, addonRegistrationTest, featureRuntimeTest, commandRuntimeTest, listenerRuntimeTest, placeholderRuntimeTest, componentPlanTest)
    doLast {
        val plugin = pluginSource.asFile.readText()
        val addonRegistrationSource = addonRegistration.asFile.readText()
        val runtime = featureRuntime.asFile.readText()
        val commandRuntimeSource = commandRuntime.asFile.readText()
        val listenerRuntimeSource = listenerRuntime.asFile.readText()
        val placeholderRuntimeSource = placeholderRuntime.asFile.readText()
        val tests = addonRegistrationTest.asFile.readText() + "\n" + featureRuntimeTest.asFile.readText() + "\n" + commandRuntimeTest.asFile.readText() + "\n" + listenerRuntimeTest.asFile.readText() + "\n" + placeholderRuntimeTest.asFile.readText() + "\n" + componentPlanTest.asFile.readText()
        val missingRuntime = listOf(
            "public final class SatisFeatureRuntime",
            "public SatisRuntimeComponentPlan plan(ComponentSnapshot snapshot)",
            "public record ComponentSnapshot"
        ).filterNot(runtime::contains)
        val missingAddonRegistration = listOf(
            "public final class SatisAddonRegistration",
            "public CloudIslandsApi resolveApi()",
            "public static RuntimeState registeredState",
            "public record RuntimeState"
        ).filterNot(addonRegistrationSource::contains)
        val missingCommandRuntime = listOf(
            "public final class SatisCommandRuntime",
            "public CommandRegistrationResult bindPluginCommand",
            "public void unregisterPluginCommand(String commandName)",
            "private Optional<CommandMap> commandMap()"
        ).filterNot(commandRuntimeSource::contains)
        val missingListenerRuntime = listOf(
            "public final class SatisListenerRuntime",
            "public boolean registerListener(Listener listener, boolean registered)",
            "public boolean unregisterListener(Listener listener, boolean registered)",
            "HandlerList.unregisterAll(listener)"
        ).filterNot(listenerRuntimeSource::contains)
        val missingPlaceholderRuntime = listOf(
            "public final class SatisPlaceholderRuntime",
            "public boolean runtimeEnabled(boolean placeholdersEnabled, boolean machinesEnabled)",
            "public <T extends PlaceholderExpansionHandle> T refresh",
            "public <T extends PlaceholderExpansionHandle> T unregister",
            "public static RuntimeGate gate"
        ).filterNot(placeholderRuntimeSource::contains)
        val missingPluginWiring = listOf(
            "private final SatisAddonRegistration addonRegistration",
            "cloudIslandsApi = addonRegistration.resolveApi()",
            "SatisAddonRegistration.registeredState",
            "applyAddonRegistrationState(SatisAddonRegistration.unregisteredState())",
            "private final SatisCommandRuntime commandRuntime",
            "commandRuntime.bindPluginCommand(\"factory\"",
            "commandRuntime.unregisterPluginCommand(\"factory\")",
            "private final SatisFeatureRuntime featureRuntime",
            "private final SatisListenerRuntime listenerRuntime",
            "listenerRuntime.registerListener(machineListener",
            "listenerRuntime.unregisterListener(machineListener",
            "private final SatisPlaceholderRuntime placeholderRuntime",
            "placeholderRuntime.refresh(placeholderHook",
            "placeholderRuntime.unregister(placeholderHook)",
            "featureRuntime.plan(new SatisFeatureRuntime.ComponentSnapshot"
        ).filterNot(plugin::contains)
        val forbiddenPluginCommandMap = listOf(
            "getMethod(\"getCommandMap\")",
            "private java.util.Optional<org.bukkit.command.CommandMap> commandMap()"
        ).filter(plugin::contains)
        val forbiddenPluginListenerRuntime = listOf(
            "getPluginManager().registerEvents",
            "HandlerList.unregisterAll"
        ).filter(plugin::contains)
        val missingTests = listOf(
            "buildsComponentPlanForFeatureGateRuntime",
            "registeredStateCarriesFeatureSnapshotWhenActivationAllowsRuntime",
            "cloudIslandsApiMissingBlocksStandaloneRuntime",
            "commandRegistrationResultReportsMissingCommandsAsInactive",
            "listenerStateSeparatesMissingUnregisteredAndRegisteredComponents",
            "placeholderRuntimeGateRequiresFeatureMachinesAndPlaceholderApi",
            "addonDisabledSkipsEveryActiveRuntimeComponent"
        ).filterNot(tests::contains)
        val failures = buildList {
            if (missingRuntime.isNotEmpty()) add("Satis feature runtime component missing: ${missingRuntime.joinToString(", ")}")
            if (missingAddonRegistration.isNotEmpty()) add("Satis addon registration runtime component missing: ${missingAddonRegistration.joinToString(", ")}")
            if (missingCommandRuntime.isNotEmpty()) add("Satis command runtime component missing: ${missingCommandRuntime.joinToString(", ")}")
            if (missingListenerRuntime.isNotEmpty()) add("Satis listener runtime component missing: ${missingListenerRuntime.joinToString(", ")}")
            if (missingPlaceholderRuntime.isNotEmpty()) add("Satis placeholder runtime component missing: ${missingPlaceholderRuntime.joinToString(", ")}")
            if (missingPluginWiring.isNotEmpty()) add("Satis plugin must delegate runtime component planning: ${missingPluginWiring.joinToString(", ")}")
            if (forbiddenPluginCommandMap.isNotEmpty()) add("Satis plugin still owns Bukkit command map access: ${forbiddenPluginCommandMap.joinToString(", ")}")
            if (forbiddenPluginListenerRuntime.isNotEmpty()) add("Satis plugin still owns Bukkit listener runtime access: ${forbiddenPluginListenerRuntime.joinToString(", ")}")
            if (missingTests.isNotEmpty()) add("Satis runtime component tests missing: ${missingTests.joinToString(", ")}")
        }
        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("verifyPackagingCoverage"))
    dependsOn(tasks.named("verifyRuntimeComponentCoverage"))
}
