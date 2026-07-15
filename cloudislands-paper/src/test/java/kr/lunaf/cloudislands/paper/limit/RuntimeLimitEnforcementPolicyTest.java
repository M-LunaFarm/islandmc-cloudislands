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

    @Test
    void pluginEntityRemovalsUpdateAggregateAndExactTypeCounts() throws Exception {
        String limitListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/limit/IslandEntityLimitListener.java"));
        String protectionListener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));
        String removalPolicy = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/limit/EntityRemovalAccountingPolicy.java"));

        assertTrue(removalPolicy.contains("EntityRemoveEvent.Cause.PLUGIN"));
        assertTrue(removalPolicy.contains("EntityRemoveEvent.Cause.DISCARD"));
        assertTrue(limitListener.contains("EntityRemovalAccountingPolicy.records(event.getCause())"));
        assertTrue(limitListener.contains("recordAcceptedDelta(event.getEntity().getLocation(), -stackAmounts.entityAmount(event.getEntity()))"));
        assertTrue(protectionListener.contains("EntityRemovalAccountingPolicy.records(event.getCause())"));
        assertTrue(protectionListener.contains("blockDeltas.entityRemoved(islandId, event.getEntity(), stackAmounts.entityAmount(event.getEntity()))"));
    }
}
