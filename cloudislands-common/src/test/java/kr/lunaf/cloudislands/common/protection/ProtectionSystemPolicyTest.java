package kr.lunaf.cloudislands.common.protection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionSystemPolicyTest {
    @Test
    void pinsRegionLookupPipelineToGoalOrder() {
        assertEquals(
                List.of(
                        "read-world-and-block-xz",
                        "build-chunk-key",
                        "find-region-candidates-by-world-chunk",
                        "filter-by-bounding-box",
                        "resolve-island-id",
                        "check-local-permission-cache"
                ),
                ProtectionSystemPolicy.regionLookupSteps()
        );
        assertEquals(
                "world-chunk-region-index>bounding-box>island-id>local-permission-cache",
                ProtectionDecisionPolicy.REGION_LOOKUP_ORDER
        );
    }

    @Test
    void regionIndexUsesChunkKeyThenBoundingBox() {
        UUID island = UUID.fromString("00000000-0000-0000-0000-000000000701");
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000000702");
        RegionIndex index = new RegionIndex();
        index.add(new IslandRegion(island, "ci_shard_001", 0, 7, 0, 7, 0, 0));
        index.add(new IslandRegion(other, "ci_shard_001", 8, 15, 8, 15, 1, 0));

        assertEquals(island, index.find("ci_shard_001", 3, 3).orElseThrow().islandId());
        assertEquals(other, index.find("ci_shard_001", 12, 12).orElseThrow().islandId());
        assertTrue(index.find("ci_shard_001", 7, 12).isEmpty());
        assertEquals(ChunkKey.fromBlock("ci_shard_001", 12, 12), new ChunkKey("ci_shard_001", 0, 0));
    }

    @Test
    void pinsProtectedEventSurfaceFromGoal() {
        for (String eventName : ProtectionSystemPolicy.requiredProtectedEvents()) {
            assertTrue(ProtectionDecisionPolicy.protectedEvent(eventName), eventName);
        }
    }

    @Test
    void pinsBorderHandlingByRole() {
        assertEquals("TELEPORT_VISITOR_SPAWN", ProtectionSystemPolicy.borderAction("VISITOR"));
        assertEquals("TELEPORT_VISITOR_SPAWN", ProtectionSystemPolicy.borderAction("BANNED"));
        assertEquals("TELEPORT_ISLAND_SPAWN", ProtectionSystemPolicy.borderAction("MEMBER"));
        assertEquals("TELEPORT_ISLAND_SPAWN", ProtectionSystemPolicy.borderAction("TRUSTED"));
        assertEquals("ALLOW_BYPASS", ProtectionSystemPolicy.borderAction("ADMIN"));
    }
}
