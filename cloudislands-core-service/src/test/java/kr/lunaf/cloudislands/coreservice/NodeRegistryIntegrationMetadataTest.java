package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import org.junit.jupiter.api.Test;

class NodeRegistryIntegrationMetadataTest {
    @Test
    void rendersIntegrationMetadataFromHeartbeat() {
        NodeLoad node = new NodeLoad(
            "island-1",
            "island",
            "Island-1",
            "1.0.0",
            NodeState.READY,
            1,
            90,
            110,
            20,
            2,
            600,
            19.5D,
            0,
            20,
            0.0D,
            512,
            2048,
            0,
            Instant.now(),
            true,
            "*;integrationsDetected=Vault,CoreProtect;integrationsMissing=WorldEdit;integrationPolicy=distributed-aware-paper-hooks"
        );

        String json = NodeRegistry.toJson(node);

        assertTrue(json.contains("\"integrations\""));
        assertTrue(json.contains("\"detected\":\"Vault,CoreProtect\""));
        assertTrue(json.contains("\"missing\":\"WorldEdit\""));
        assertTrue(json.contains("\"policy\":\"distributed-aware-paper-hooks\""));
    }
}
