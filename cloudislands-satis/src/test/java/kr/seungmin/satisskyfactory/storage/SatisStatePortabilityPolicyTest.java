package kr.seungmin.satisskyfactory.storage;

import org.junit.jupiter.api.Test;

import java.util.List;
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
        assertEquals("islandUuid", state.get("core-api-sync-state-owner-key"));
        assertEquals("islandUuid+activeWorld+activeCenter", state.get("core-api-sync-remap-audit-key"));
        assertEquals("islandUuid+activeWorld+activeCenter", state.get("core-api-sync-remap-key"));
        assertEquals("last-confirmed-state-wins", state.get("core-api-sync-write-policy"));
        assertEquals("active-island-runtime-owner-only", state.get("core-api-sync-write-fence"));
        assertEquals("single-active-runtime-owner", state.get("core-api-sync-duplicate-tick-policy"));
        assertEquals("save-on-source-restore-on-target-by-island-uuid", state.get("core-api-sync-node-handoff-policy"));
        assertEquals("preserve-cloudislands-island-and-addon-state-by-island-uuid", state.get("core-api-sync-addon-removal-policy"));
        assertEquals("preflush-satis-state-stop-runtime-keep-cloudislands-lifecycle", state.get("core-api-sync-addon-disable-policy"));
        assertEquals("when-feature-disabled-store-original-center-and-apply-remap-when-reenabled", state.get("core-api-sync-deferred-remap-policy"));
        assertEquals("pendingMachineRemap,pendingResourceNodeRemap", state.get("core-api-sync-deferred-remap-key"));
        assertEquals("env-explicit-type-setup-core-api-marker-auto-single-backend-database-default", state.get("core-api-sync-setup-selection-policy"));
        assertEquals("CLOUDISLANDS_SATIS_DATABASE_TYPE,setup.database.type,addons.cloudislands-satis.database.type,setup.database.core-api.enabled,jdbc-url,setup.database.<backend>,database.type", state.get("core-api-sync-setup-backend-priority"));
        assertEquals("shared-backend-before-local-sqlite-or-warn", state.get("core-api-sync-fallback-chain-policy"));
        assertEquals("use-only-explicitly-configured-shared-targets-then-explicit-local-sqlite", state.get("core-api-sync-fallback-readiness-policy"));
        assertEquals("report-ready-fallback-targets-before-using-local-sqlite", state.get("core-api-sync-fallback-ready-chain-policy"));
        assertEquals("retry-table-key-value-bulk-save-as-flattened-addon-state", state.get("core-api-sync-write-fallback-policy"));
        assertEquals("POSTGRESQL,MYSQL,MARIADB,CORE_API-before-SQLITE", state.get("core-api-sync-production-safe-fallback-policy"));
        assertEquals("POSTGRESQL,MYSQL,MARIADB,CORE_API", state.get("core-api-sync-shared-backends"));
        assertEquals("SQLITE", state.get("core-api-sync-local-backends"));
    }

    @Test
    void coreApiSyncStateIsImmutable() {
        Map<String, String> state = SatisStatePortabilityPolicy.coreApiSyncState();

        assertThrows(UnsupportedOperationException.class, () -> state.put("core-api-sync-node-bound", "true"));
    }

    @Test
    void classifiesSharedAndLocalFallbackBackends() {
        assertEquals(List.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API", "SQLITE"),
                SatisStatePortabilityPolicy.recommendedFallbackOrder());
        assertEquals(true, SatisStatePortabilityPolicy.sharedBackend("postgresql"));
        assertEquals(true, SatisStatePortabilityPolicy.sharedBackend("MYSQL"));
        assertEquals(true, SatisStatePortabilityPolicy.sharedBackend("core_api"));
        assertEquals(false, SatisStatePortabilityPolicy.sharedBackend("sqlite"));
        assertEquals(true, SatisStatePortabilityPolicy.localBackend("sqlite"));
        assertEquals(false, SatisStatePortabilityPolicy.localBackend("postgresql"));
    }
}
