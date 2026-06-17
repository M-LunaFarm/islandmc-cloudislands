package kr.seungmin.satisskyfactory.storage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SatisStatePortabilityPolicyTest {
    @Test
    void coreApiSyncStateKeepsAddonStatePortableAcrossIslandNodes() {
        Map<String, String> state = SatisStatePortabilityPolicy.coreApiSyncState();

        assertEquals("cloudislands-addon-state", state.get("core-api-sync-authority"));
        assertEquals("false", state.get("core-api-sync-node-bound"));
        assertEquals("portable-across-island-nodes", state.get("core-api-sync-portability"));
        assertEquals("CloudIslands IslandRuntime", state.get("core-api-sync-runtime-source"));
        assertEquals("island-uuid-stable-active-world-and-center-volatile", state.get("core-api-sync-remap-policy"));
        assertEquals("islandUuid+activeWorld+activeCenter", state.get("core-api-sync-remap-key"));
        assertEquals("last-confirmed-state-wins", state.get("core-api-sync-write-policy"));
        assertEquals("active-island-runtime-owner-only", state.get("core-api-sync-write-fence"));
        assertEquals("single-active-runtime-owner", state.get("core-api-sync-duplicate-tick-policy"));
        assertEquals("save-on-source-restore-on-target-by-island-uuid", state.get("core-api-sync-node-handoff-policy"));
        assertEquals("preserve-cloudislands-island-and-addon-state-by-island-uuid", state.get("core-api-sync-addon-removal-policy"));
        assertEquals("preflush-satis-state-stop-runtime-keep-cloudislands-lifecycle", state.get("core-api-sync-addon-disable-policy"));
    }

    @Test
    void coreApiSyncStateIsImmutable() {
        Map<String, String> state = SatisStatePortabilityPolicy.coreApiSyncState();

        assertThrows(UnsupportedOperationException.class, () -> state.put("core-api-sync-node-bound", "true"));
    }
}
