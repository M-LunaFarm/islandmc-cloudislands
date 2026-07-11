package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationSupportState;
import org.junit.jupiter.api.Test;

class IntegrationRuntimeCertificationTest {
    @Test
    void probeOnlyRegistryDoesNotAdvertiseOperationCertification() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        assertEquals(List.of(), IntegrationRuntimeCertification.priorityPlugins());
        assertEquals(List.of(), IntegrationRuntimeCertification.certifyPriorityPlugins(
            (_plugin, _category, _operation, _context, _plan) -> {
                invoked.set(true);
                throw new AssertionError("diagnostic adapters must not execute operation certification");
            }
        ));
        assertFalse(invoked.get());
    }

    @Test
    void certificationReportPublishesDiagnosticStateWithoutOperationClaims() {
        PaperIntegrationRegistry.IntegrationStatus vault = diagnosticStatus("Vault", "economy");
        PaperIntegrationRegistry.IntegrationStatus placeholder = diagnosticStatus("PlaceholderAPI", "placeholder");

        IntegrationRuntimeCertification.CertificationReport report = IntegrationRuntimeCertification.report(
            List.of(vault, placeholder),
            List.of(),
            Map.of("Vault", "1.7.3", "PlaceholderAPI", "2.11.6")
        );

        assertTrue(report.summaryLine().contains("certified=0"));
        assertTrue(report.summaryLine().contains("failed=0"));
        assertTrue(report.toJson().contains("\"pluginName\":\"Vault\""));
        assertTrue(report.toJson().contains("\"adapterState\":\"DIAGNOSTIC_ONLY\""));
        assertTrue(report.toJson().contains("\"operation\":\"\""));
        assertTrue(report.toJson().contains("\"certified\":false"));
        assertTrue(report.toMarkdown().contains("DIAGNOSTIC_ONLY"));
        assertTrue(report.failedOperations().isEmpty());
    }

    @Test
    void missingPluginsRemainExplicitlyNotInstalled() {
        IntegrationRuntimeCertification.CertificationReport report = IntegrationRuntimeCertification.report(
            List.of(),
            List.of(),
            Map.of()
        );

        assertTrue(report.toJson().contains("\"state\":\"NOT_INSTALLED\""));
        assertTrue(report.toMarkdown().contains("NOT_INSTALLED"));
    }

    private PaperIntegrationRegistry.IntegrationStatus diagnosticStatus(String pluginName, String category) {
        return new PaperIntegrationRegistry.IntegrationStatus(
            pluginName,
            category,
            true,
            IntegrationSupportState.DIAGNOSTIC_ONLY,
            IntegrationSupportState.DETECTED,
            IntegrationSupportState.API_COMPATIBLE,
            IntegrationSupportState.DIAGNOSTIC_ONLY,
            null,
            false,
            List.of(),
            Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION)
        );
    }
}
