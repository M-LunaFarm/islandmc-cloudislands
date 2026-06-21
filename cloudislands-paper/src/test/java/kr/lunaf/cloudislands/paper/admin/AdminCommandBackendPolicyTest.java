package kr.lunaf.cloudislands.paper.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminCommandBackendPolicyTest {
    @Test
    void diagnosticsExportIsAFirstClassAdminCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"diagnostics\""), "Diagnostics root command must be registered");
        assertTrue(adminSurface.contains("ciadmin diagnostics export"), "Diagnostics export must be listed in help");
        assertTrue(source.contains("handleDiagnostics"), "Diagnostics command must have a handler");
        assertTrue(source.contains("cloudislands.admin.\" + root"), "Diagnostics must be covered by admin permission mapping");
        assertTrue(source.contains("redactDiagnostic"), "Diagnostics export must redact secrets");
        assertTrue(source.contains("coreApiClient.storageStatus()"), "Diagnostics export must include storage health");
        assertTrue(source.contains("coreApiClient.metrics()"), "Diagnostics export must include metrics");
        assertTrue(source.contains("coreApiClient.adminRoutes().debug(new UUID(0L, 0L))"), "Diagnostics export must include typed route ticket debug state");
        assertTrue(source.contains("diagnosticSection(\"route-debug\""), "Diagnostics bundle must have a route debug section");
        assertTrue(source.contains("diagnosticSection(\"heartbeat-lag\""), "Diagnostics bundle must have a heartbeat lag section");
        assertTrue(source.contains("heartbeatLagDiagnosticBody"), "Diagnostics export must summarize heartbeat lag from node state");
        assertTrue(source.contains("\"staleNodeCount\""), "Heartbeat diagnostics must expose stale node count");
        assertTrue(source.contains("\"heartbeatTimeoutSeconds\""), "Heartbeat diagnostics must expose heartbeat timeout");
        assertTrue(source.contains("coreApiClient.adminAudit().list(25)"), "Diagnostics export must include bounded typed audit context");
        assertTrue(source.contains("configValidationDiagnosticSection"), "Diagnostics export must include local config validation");
        assertTrue(source.contains("effectiveConfigDiagnosticSection"), "Diagnostics export must include redacted effective config");
        assertTrue(source.contains("## config-validation"), "Diagnostics bundle must have a config validation section");
        assertTrue(source.contains("## effective-config-redacted"), "Diagnostics bundle must have a redacted effective config section");
        assertTrue(source.contains("pluginVersion="), "Diagnostics bundle must include runtime version context");
        assertTrue(source.contains("validateConfigV2Bundle()"), "Diagnostics config validation must use the same validator as config reload");
        assertTrue(source.contains("effectiveConfigV2Yaml(true)"), "Diagnostics effective config must be redacted");
        assertTrue(plugin.contains("cloudislands.admin.diagnostics"), "Diagnostics command must have a plugin permission");
    }

    @Test
    void configOperationsAreFirstClassAdminCommands() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(source.contains("CONFIG_COMMANDS"), "Config subcommands must be registered for completion");
        assertTrue(adminSurface.contains("ciadmin config validate"), "Config validate must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config diff"), "Config diff must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config reload"), "Config reload must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config effective"), "Config effective must be listed in help");
        assertTrue(adminSurface.contains("ciadmin config sources"), "Config sources must be listed in help");
        assertTrue(source.contains("handleConfig"), "Config command must have a local operation handler");
        assertTrue(source.contains("ConfigV2Validator.validateYaml"), "Config validate must run schema and secret validation");
        assertTrue(source.contains("ConfigV2Validator.redactYaml"), "Effective config output must redact secrets");
        assertTrue(source.contains("if (!validation.valid())"), "Config reload must keep the current config when validation fails");
        assertTrue(source.contains("reloadRuntimeConfig()"), "Config reload must refresh the active Config v2 runtime snapshot after validation passes");
        assertTrue(source.contains("plugin.reloadRuntimeConfig()"), "Admin config reload must call the Paper runtime snapshot reload boundary");
        assertTrue(source.contains("ConfigDiff.between"), "Config diff must report changed and restart-required paths");
        assertTrue(source.contains("currentConfigYaml"), "Config diff must compare against the current runtime config when available");
        assertTrue(plugin.contains("cloudislands.admin.config"), "Config command must have a plugin permission");
    }

    @Test
    void integrationsCommandCoversMajorHookPlugins() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String policy = Files.readString(Path.of("../cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/integration/CloudIntegrationPolicy.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("\"integrations\""), "Integrations root command must be registered");
        assertTrue(adminSurface.contains("ciadmin integrations"), "Integrations command must be listed in help");
        assertTrue(source.contains("integrationStatusMessage"), "Integrations command must have a status handler");
        assertTrue(source.contains("integrationRegistry().statusLine()"), "Integrations command must use the runtime integration registry");
        assertTrue(source.contains("integrationsDiagnosticSection"), "Diagnostics export must include integration policy state");
        assertTrue(source.contains("CloudIntegrationPolicy.knownPlugins()"), "Integrations command must use the shared integration policy");
        assertTrue(policy.contains("LuckPerms"), "LuckPerms must be covered by integration status");
        assertTrue(policy.contains("CoreProtect"), "CoreProtect must be covered by integration status");
        assertTrue(policy.contains("FastAsyncWorldEdit"), "FAWE must be covered by integration status");
        assertTrue(policy.contains("DISTRIBUTED_HOOK_POLICY"), "Integrations must publish the distributed hook policy");
        assertTrue(policy.contains("requiredRuntimeClaims"), "Integration policy must expose required runtime claims");
        assertTrue(policy.contains("validateHookContext"), "Integration policy must validate hook authority context");
        assertTrue(plugin.contains("cloudislands.admin.integrations"), "Integrations command must have a plugin permission");
        assertTrue(plugin.contains("LuckPerms"), "LuckPerms must be declared as a soft dependency");
        assertTrue(plugin.contains("CoreProtect"), "CoreProtect must be declared as a soft dependency");
        assertTrue(plugin.contains("FastAsyncWorldEdit"), "FAWE must be declared as a soft dependency");
        assertTrue(plugin.contains("ItemsAdder"), "ItemsAdder must be declared as a soft dependency");
        assertTrue(plugin.contains("Oraxen"), "Oraxen must be declared as a soft dependency");
        assertTrue(plugin.contains("Nexo"), "Nexo must be declared as a soft dependency");
        assertTrue(plugin.contains("RoseStacker"), "RoseStacker must be declared as a soft dependency");
        assertTrue(plugin.contains("AdvancedSpawners"), "AdvancedSpawners must be declared as a soft dependency");
        assertTrue(plugin.contains("Plan"), "Plan must be declared as a soft dependency");
        assertTrue(plugin.contains("SuperVanish"), "Vanish hooks must be declared as soft dependencies");
        assertTrue(plugin.contains("PremiumVanish"), "Vanish hooks must be declared as soft dependencies");
        assertTrue(plugin.contains("SlimeWorldManager"), "SlimeWorldManager hooks must be declared as soft dependencies");
        Set<String> softDependencies = Arrays.stream(plugin.substring(plugin.indexOf("softdepend: [") + "softdepend: [".length(), plugin.indexOf("]", plugin.indexOf("softdepend: ["))).split(","))
            .map(String::trim)
            .collect(Collectors.toSet());
        assertTrue(softDependencies.containsAll(kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy.knownPlugins()), "plugin.yml soft dependencies must cover the shared hook policy");
    }

    @Test
    void islandVisitorStatsAreExposedForOperators() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String catalog = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandCatalog.java"));
        String adminSurface = source + "\n" + catalog;

        assertTrue(adminSurface.contains("ciadmin island visitor-stats <island>"), "Visitor stats command must be listed in help");
        assertTrue(source.contains("coreApiClient.visitorStats().stats"), "Visitor stats command must use the typed Core visitor stats API");
    }

    @Test
    void adminStorageCommandUsesTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminStorage().status"), "Storage command must use the typed Core storage status API");
        assertTrue(source.contains("storageStatusMessage(AdminStorageStatusView"), "Storage command must render a typed storage view");
    }

    @Test
    void adminIslandInfoAndRuntimeUseTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminIslands().info"), "Island info command must use the typed Core admin island API");
        assertTrue(source.contains("coreApiClient.adminIslands().runtime"), "Island runtime command must use the typed Core admin island API");
        assertTrue(source.contains("runtimeInfoMessage(AdminIslandRuntimeView"), "Island runtime command must render a typed runtime view");
    }

    @Test
    void adminUpgradeRulesUseTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.progression().upgradeRules"), "Upgrade rules command must use the typed Core progression API");
        assertTrue(source.contains("upgradeRulesMessage(List<UpgradeRuleView>"), "Upgrade rules command must render typed upgrade rules");
    }

    @Test
    void adminMaintenanceCommandsUseTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminMaintenance().clearCache"), "Cache clear command must use the typed Core maintenance API");
        assertTrue(source.contains("coreApiClient.adminMaintenance().reload"), "Reload commands must use the typed Core maintenance API");
        assertTrue(source.contains("maintenanceMessage(String label, AdminMaintenanceResultView"), "Maintenance commands must render typed maintenance results");
    }

    @Test
    void adminAddonStateSummaryUsesTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminAddonState().summary"), "Addon state command must use the typed Core addon state API");
        assertTrue(source.contains("addonStateSummaryMessage(AdminAddonStateSummaryView"), "Addon state command must render a typed addon state view");
    }

    @Test
    void adminCoreConfigUsesTypedCoreClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminCoreConfig().config"), "Core config commands must use the typed Core config API");
        assertTrue(source.contains("coreConfigMessage(AdminCoreConfigView"), "Core config command must render a typed config view");
        assertTrue(source.contains("addonEndpointMessage(AdminCoreConfigView"), "Addon endpoint command must render a typed config view");
    }

    @Test
    void adminMetricsAndNodeMenuUseTypedCoreClients() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.adminMetrics().summary"), "Metrics command must use the typed Core metrics API");
        assertTrue(source.contains("metricsMessage(AdminMetricsSummaryView"), "Metrics command must render a typed metrics view");
        assertTrue(source.contains("coreApiClient.adminNodes().nodeInfo(nodeId)"), "Node menu must use the typed Core node API");
    }

    @Test
    void adminRouteRuntimeUsesTypedRoutingClient() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("coreApiClient.routingCommands().routeTicketStatus(ticket)"), "Admin route polling must use the typed routing API");
        assertTrue(source.contains("coreApiClient.routingCommands().publishRouteSession(ticket)"), "Admin route publish must use the typed routing API");
        assertTrue(source.contains("coreApiClient.routingCommands().clearRoute(ticket, reason)"), "Admin route cleanup must use the typed routing API");
    }
}
