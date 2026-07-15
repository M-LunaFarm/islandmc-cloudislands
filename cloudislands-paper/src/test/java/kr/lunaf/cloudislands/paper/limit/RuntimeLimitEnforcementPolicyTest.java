package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RuntimeLimitEnforcementPolicyTest {
    @Test
    void blockPlacementEnforcesTheExactMaterialLimit() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/limit/IslandLimitListener.java"));

        assertTrue(source.contains("GameplayParityPolicy.blockAmountLimitKey(material.getKey().toString())"));
    }

    @Test
    void entitySpawnsEnforceGlobalAndExactTypeLimits() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/limit/IslandEntityLimitListener.java"));
        String logicalStackSource = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/limit/LogicalEntitySpawnBridge.java"));

        assertTrue(source.contains("withinLimit(location, islandId, \"ENTITY\", addition)"));
        assertTrue(source.contains("withinLimit(location, islandId, IslandEntityLimitKeys.limitKey(entityType), addition)"));
        assertTrue(logicalStackSource.contains("resolveLimit(region.islandId(), IslandEntityLimitKeys.limitKey(entityType))"));
    }
}
