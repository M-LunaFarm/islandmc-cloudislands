package kr.lunaf.cloudislands.paper.admin;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AdminCommandBackendPolicyTest {
    @Test
    void diagnosticsExportIsAFirstClassAdminCommand() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(source.contains("\"diagnostics\""), "Diagnostics root command must be registered");
        assertTrue(source.contains("ciadmin diagnostics export"), "Diagnostics export must be listed in help");
        assertTrue(source.contains("handleDiagnostics"), "Diagnostics command must have a handler");
        assertTrue(source.contains("cloudislands.admin.\" + root"), "Diagnostics must be covered by admin permission mapping");
        assertTrue(source.contains("redactDiagnostic"), "Diagnostics export must redact secrets");
        assertTrue(source.contains("coreApiClient.storageStatus()"), "Diagnostics export must include storage health");
        assertTrue(source.contains("coreApiClient.metrics()"), "Diagnostics export must include metrics");
        assertTrue(source.contains("coreApiClient.listAuditLogs(25)"), "Diagnostics export must include bounded audit context");
        assertTrue(plugin.contains("cloudislands.admin.diagnostics"), "Diagnostics command must have a plugin permission");
    }

    @Test
    void integrationsCommandCoversMajorHookPlugins() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));
        String plugin = Files.readString(Path.of("src/main/resources/plugin.yml"));
        String policy = Files.readString(Path.of("../cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/integration/CloudIntegrationPolicy.java"));

        assertTrue(source.contains("\"integrations\""), "Integrations root command must be registered");
        assertTrue(source.contains("ciadmin integrations"), "Integrations command must be listed in help");
        assertTrue(source.contains("integrationStatusMessage"), "Integrations command must have a status handler");
        assertTrue(source.contains("isPluginEnabled(pluginName)"), "Integrations command must inspect Bukkit plugin state");
        assertTrue(source.contains("CloudIntegrationPolicy.knownPlugins()"), "Integrations command must use the shared integration policy");
        assertTrue(policy.contains("LuckPerms"), "LuckPerms must be covered by integration status");
        assertTrue(policy.contains("CoreProtect"), "CoreProtect must be covered by integration status");
        assertTrue(policy.contains("FastAsyncWorldEdit"), "FAWE must be covered by integration status");
        assertTrue(policy.contains("DISTRIBUTED_HOOK_POLICY"), "Integrations must publish the distributed hook policy");
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
    }

    @Test
    void islandVisitorStatsAreExposedForOperators() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/admin/AdminCommandBackend.java"));

        assertTrue(source.contains("ciadmin island visitor-stats <island>"), "Visitor stats command must be listed in help");
        assertTrue(source.contains("coreApiClient.islandVisitorStats"), "Visitor stats command must call the Core visitor stats API");
    }
}
