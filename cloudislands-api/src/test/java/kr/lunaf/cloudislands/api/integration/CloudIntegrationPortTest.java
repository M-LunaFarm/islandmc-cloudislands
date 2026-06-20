package kr.lunaf.cloudislands.api.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class CloudIntegrationPortTest {
    @Test
    void contextAndRequestPayloadsAreImmutableSnapshots() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("world", "island-world");
        CloudIntegrationContext context = new CloudIntegrationContext(
            "CoreProtect",
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "island-node-1",
            7L,
            true,
            "rollback-7",
            attributes
        );
        attributes.put("world", "mutated");

        CloudIntegrationRequest request = new CloudIntegrationRequest("rollback", true, Map.of("radius", "64"));

        assertEquals("island-world", context.attributes().get("world"));
        assertThrows(UnsupportedOperationException.class, () -> context.attributes().put("x", "y"));
        assertTrue(request.coreStateMutation());
        assertThrows(UnsupportedOperationException.class, () -> request.payload().put("radius", "128"));
    }

    @Test
    void portContractCarriesPluginCategoryAndResult() {
        CloudIntegrationPort port = worldEditPort();

        CloudIntegrationResult result = port.apply(
            new CloudIntegrationContext("FastAsyncWorldEdit", UUID.fromString("00000000-0000-0000-0000-000000000002"), "island-node-2", 8L, true, "paste-8", Map.of()),
            new CloudIntegrationRequest("template-paste", true, Map.of())
        ).join();

        assertEquals("FastAsyncWorldEdit", port.pluginName());
        assertEquals(IntegrationCategory.WORLD_EDIT, port.category());
        assertTrue(result.accepted());
        assertEquals("template-paste", result.message());
        assertEquals("FastAsyncWorldEdit", result.metadata().get("plugin"));
    }

    @Test
    void validatedPortRejectsStateChangingHooksWithoutDistributedRuntimeClaims() {
        CloudIntegrationPort port = worldEditPort();

        CloudIntegrationResult result = port.applyValidated(
            new CloudIntegrationContext("FastAsyncWorldEdit", null, "", 0L, false, "", Map.of()),
            new CloudIntegrationRequest("template-paste", true, Map.of())
        ).join();

        assertFalse(result.accepted());
        assertEquals("DISTRIBUTED_CONTEXT_REJECTED", result.code());
        assertTrue(result.metadata().get("violations").contains("island-uuid-missing"));
        assertTrue(result.metadata().get("violations").contains("node-id-missing"));
        assertTrue(result.metadata().get("violations").contains("runtime-fencing-token-missing"));
        assertTrue(result.metadata().get("violations").contains("node-ownership-missing"));
        assertTrue(result.metadata().get("violations").contains("core-idempotency-key-missing"));
    }

    @Test
    void validatedPortAllowsPresenceHooksWithoutCoreAuthorityContext() {
        CloudIntegrationPort port = new CloudIntegrationPort() {
            @Override
            public String pluginName() {
                return "SuperVanish";
            }

            @Override
            public IntegrationCategory category() {
                return IntegrationCategory.PRESENCE;
            }

            @Override
            public CompletableFuture<CloudIntegrationResult> apply(CloudIntegrationContext context, CloudIntegrationRequest request) {
                return CompletableFuture.completedFuture(new CloudIntegrationResult(true, "OBSERVED", request.action(), Map.of("plugin", pluginName())));
            }
        };

        CloudIntegrationResult result = port.applyValidated(
            new CloudIntegrationContext("SuperVanish", null, "", 0L, false, "", Map.of()),
            new CloudIntegrationRequest("presence-hidden", false, Map.of())
        ).join();

        assertTrue(result.accepted());
        assertEquals("OBSERVED", result.code());
    }

    @Test
    void validatedPortRejectsPluginNameMismatchBeforeApplyingHook() {
        CloudIntegrationPort port = worldEditPort();

        CloudIntegrationResult result = port.applyValidated(
            new CloudIntegrationContext("WorldEdit", UUID.randomUUID(), "island-node-2", 8L, true, "paste-8", Map.of()),
            new CloudIntegrationRequest("template-paste", true, Map.of())
        ).join();

        assertFalse(result.accepted());
        assertTrue(result.metadata().get("violations").contains("plugin-name-mismatch"));
    }

    private CloudIntegrationPort worldEditPort() {
        return new CloudIntegrationPort() {
            @Override
            public String pluginName() {
                return "FastAsyncWorldEdit";
            }

            @Override
            public IntegrationCategory category() {
                return IntegrationCategory.WORLD_EDIT;
            }

            @Override
            public CompletableFuture<CloudIntegrationResult> apply(CloudIntegrationContext context, CloudIntegrationRequest request) {
                return CompletableFuture.completedFuture(new CloudIntegrationResult(true, "ACCEPTED", request.action(), Map.of("plugin", pluginName())));
            }
        };
    }
}
