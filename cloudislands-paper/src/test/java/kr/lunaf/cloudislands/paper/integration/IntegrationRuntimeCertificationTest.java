package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationSupportState;
import org.junit.jupiter.api.Test;

class IntegrationRuntimeCertificationTest {
    @Test
    void priorityRuntimeCertificationCoversFirstExternalPluginWave() {
        assertIterableEquals(
            List.of("Vault", "LuckPerms", "PlaceholderAPI", "WorldEdit", "CoreProtect"),
            IntegrationRuntimeCertification.priorityPlugins()
        );
    }

    @Test
    void priorityRuntimeCertificationRunsOperationSmokeForEveryPriorityPlugin() {
        List<String> calls = new ArrayList<>();
        List<IntegrationRuntimeCertification.CertificationResult> results = IntegrationRuntimeCertification.certifyPriorityPlugins(
            (pluginName, category, operation, context, plan) -> {
                calls.add(pluginName + ":" + category + ":" + operation + ":" + plan.externalApi());
                return IntegrationResult.success("runtime fixture passed", Map.of(
                    "roundTripVerified", "true",
                    "stateArtifactKey", "fixture/" + pluginName + "/" + operation + ".json"
                ));
            }
        );

        assertEquals(5, results.size());
        assertTrue(results.stream().allMatch(IntegrationRuntimeCertification.CertificationResult::certified));
        assertTrue(results.stream().allMatch(result -> result.operationState() == IntegrationSupportState.OPERATION_SUCCEEDED));
        assertTrue(results.stream().allMatch(result -> result.requiredRuntimeClaims().contains("island-uuid")));
        assertTrue(results.stream().allMatch(result -> result.requiredRuntimeClaims().contains("runtime-fencing-token")));
        assertTrue(results.stream().filter(IntegrationRuntimeCertification.CertificationResult::runtimeAuthorityRequired).count() >= 4);

        assertEquals(List.of(
            "Vault:economy:economy-transaction-smoke:Vault Economy#withdrawPlayer+depositPlayer+getBalance",
            "LuckPerms:permission:permission-context-export:LuckPerms#userManager+trackManager#saveContextState",
            "PlaceholderAPI:placeholder:placeholder-render-smoke:PlaceholderAPI#setPlaceholders",
            "WorldEdit:world-edit:schematic-export:ClipboardWriter#write",
            "CoreProtect:audit-rollback:audit-export:CoreProtectAPI#performLookup"
        ), calls);
    }

    @Test
    void priorityRuntimeCertificationRejectsProbeOnlySuccessWithoutOperationEvidence() {
        List<IntegrationRuntimeCertification.CertificationResult> results = IntegrationRuntimeCertification.certifyPriorityPlugins(
            (pluginName, category, operation, context, plan) ->
                IntegrationResult.success("probe only", Map.of("apiProbe", "true"))
        );

        assertEquals(5, results.size());
        assertTrue(results.stream().allMatch(result -> result.operationState() == IntegrationSupportState.OPERATION_FAILED));
        assertFalse(results.stream().anyMatch(IntegrationRuntimeCertification.CertificationResult::certified));
        assertTrue(results.stream().allMatch(result -> "state-artifact-or-round-trip".equals(result.details().get("external.evidenceRequired"))));
    }
}
