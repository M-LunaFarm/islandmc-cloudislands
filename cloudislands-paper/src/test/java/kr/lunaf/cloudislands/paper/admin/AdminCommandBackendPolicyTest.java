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
}
