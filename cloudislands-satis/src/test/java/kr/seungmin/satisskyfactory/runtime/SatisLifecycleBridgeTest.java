package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisLifecycleBridgeTest {
    private final SatisLifecycleBridge bridge = new SatisLifecycleBridge();

    @Test
    void lifecycleBridgeParsesMigrationOperationPlacement() {
        SatisLifecycleBridge.OperationSnapshot snapshot = bridge.operationSnapshot(
                "migrated:node-a->node-b@factory_world#8,12:placement-core");

        assertEquals("node-b", snapshot.activeNode());
        assertEquals("node-a", snapshot.sourceNode());
        assertEquals("node-b", snapshot.targetNode());
        assertEquals("factory_world", snapshot.eventWorld());
        assertEquals("8,12", snapshot.eventCell());
        assertEquals("core", snapshot.placementSource());
    }

    @Test
    void lifecycleBridgeBuildsSuccessStateWithHydrationAndRemapAudit() {
        UUID islandId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Map<String, String> state = bridge.lifecycleState(new SatisLifecycleBridge.LifecycleStateSnapshot(
                islandId,
                "migration-requested:node-a->node-b@factory_world#3,4:placement-core",
                true,
                true,
                "",
                "",
                "1,0,-1",
                true,
                false,
                "event-world-existing-center",
                false,
                true,
                "node-b|factory_world|3,4|core",
                true,
                2
        ));

        assertEquals(islandId.toString(), state.get("last-lifecycle-island"));
        assertEquals("node-a->node-b", state.get("last-lifecycle-node-move"));
        assertEquals("factory_world", state.get("last-lifecycle-active-world"));
        assertEquals("3,4", state.get("last-lifecycle-active-cell"));
        assertEquals("core", state.get("last-lifecycle-placement-source"));
        assertEquals("true", state.get("last-lifecycle-core-hydrate-tracked"));
        assertEquals("2", state.get("core-hydrated-activation-count"));
    }

    @Test
    void lifecycleBridgeBuildsSuspendedRecoveryState() {
        UUID islandId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Map<String, String> state = bridge.suspendedLifecycleState(new SatisLifecycleBridge.SuspendedLifecycleSnapshot(
                islandId,
                "recovery-required:node-c@factory_world#1,2:placement-recovery",
                true,
                false
        ));

        assertEquals("suspended", state.get("last-lifecycle-status"));
        assertEquals("recovery-required-local-cache-evicted", state.get("last-lifecycle-error"));
        assertEquals("factory_world", state.get("last-lifecycle-active-world"));
        assertEquals("1,2", state.get("last-lifecycle-active-cell"));
        assertTrue(state.containsKey("last-lifecycle-recovery-policy"));
    }
}
