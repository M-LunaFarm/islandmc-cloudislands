package kr.seungmin.satisskyfactory.storage;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SatisStatePortabilityPolicy {
    public static final String AUTHORITY = "cloudislands-addon-state";
    public static final String NODE_BOUND = "false";
    public static final String PORTABILITY = "portable-across-island-nodes";
    public static final String RUNTIME_SOURCE = "CloudIslands IslandRuntime";
    public static final String REMAP_POLICY = "island-uuid-stable-active-world-and-center-volatile";
    public static final String REMAP_KEY = "islandUuid+activeWorld+activeCenter";
    public static final String WRITE_POLICY = "last-confirmed-state-wins";
    public static final String WRITE_FENCE = "active-island-runtime-owner-only";
    public static final String DUPLICATE_TICK_POLICY = "single-active-runtime-owner";
    public static final String NODE_HANDOFF_POLICY = "save-on-source-restore-on-target-by-island-uuid";
    public static final String ADDON_REMOVAL_POLICY = "preserve-cloudislands-island-and-addon-state-by-island-uuid";
    public static final String ADDON_DISABLE_POLICY = "preflush-satis-state-stop-runtime-keep-cloudislands-lifecycle";
    public static final String DEFERRED_REMAP_POLICY = "when-feature-disabled-store-original-center-and-apply-remap-when-reenabled";
    public static final String DEFERRED_REMAP_KEY = "pendingMachineRemap,pendingResourceNodeRemap";
    public static final String LOCAL_FALLBACK_RISK = "local-fallback-can-split-state-without-shared-backend";
    public static final String SETUP_SELECTION_POLICY = "env-explicit-type-setup-core-api-marker-auto-single-backend-database-default";
    public static final String SETUP_BACKEND_PRIORITY = "CLOUDISLANDS_SATIS_DATABASE_TYPE,setup.database.type,addons.cloudislands-satis.database.type,setup.database.core-api.enabled,jdbc-url,setup.database.<backend>,database.type";
    public static final String FALLBACK_CHAIN_POLICY = "shared-backend-before-local-sqlite-or-warn";
    public static final String FALLBACK_READINESS_POLICY = "use-only-explicitly-configured-shared-targets-then-explicit-local-sqlite";
    public static final String FALLBACK_READY_CHAIN_POLICY = "report-ready-fallback-targets-before-using-local-sqlite";
    public static final String CORE_API_WRITE_FALLBACK_POLICY = "retry-table-key-value-bulk-save-as-flattened-addon-state";

    private SatisStatePortabilityPolicy() {
    }

    public static Map<String, String> coreApiSyncState() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("core-api-sync-authority", AUTHORITY);
        values.put("core-api-sync-node-bound", NODE_BOUND);
        values.put("core-api-sync-portability", PORTABILITY);
        values.put("core-api-sync-runtime-source", RUNTIME_SOURCE);
        values.put("core-api-sync-remap-policy", REMAP_POLICY);
        values.put("core-api-sync-remap-key", REMAP_KEY);
        values.put("core-api-sync-write-policy", WRITE_POLICY);
        values.put("core-api-sync-write-fence", WRITE_FENCE);
        values.put("core-api-sync-duplicate-tick-policy", DUPLICATE_TICK_POLICY);
        values.put("core-api-sync-node-handoff-policy", NODE_HANDOFF_POLICY);
        values.put("core-api-sync-addon-removal-policy", ADDON_REMOVAL_POLICY);
        values.put("core-api-sync-addon-disable-policy", ADDON_DISABLE_POLICY);
        values.put("core-api-sync-deferred-remap-policy", DEFERRED_REMAP_POLICY);
        values.put("core-api-sync-deferred-remap-key", DEFERRED_REMAP_KEY);
        values.put("core-api-sync-setup-selection-policy", SETUP_SELECTION_POLICY);
        values.put("core-api-sync-setup-backend-priority", SETUP_BACKEND_PRIORITY);
        values.put("core-api-sync-fallback-chain-policy", FALLBACK_CHAIN_POLICY);
        values.put("core-api-sync-fallback-readiness-policy", FALLBACK_READINESS_POLICY);
        values.put("core-api-sync-fallback-ready-chain-policy", FALLBACK_READY_CHAIN_POLICY);
        values.put("core-api-sync-write-fallback-policy", CORE_API_WRITE_FALLBACK_POLICY);
        return Map.copyOf(values);
    }
}
