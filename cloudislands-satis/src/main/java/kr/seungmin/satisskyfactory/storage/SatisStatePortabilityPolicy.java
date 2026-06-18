package kr.seungmin.satisskyfactory.storage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public static final String NODE_HANDOFF_PREFLUSH_POLICY = "flush-dirty-satis-state-before-deactivate-disable-or-node-drain";
    public static final String NODE_HANDOFF_RESTORE_POLICY = "target-node-loads-core-api-table-state-before-first-satis-runtime-tick";
    public static final String NODE_HANDOFF_AUDIT_KEY = "islandUuid+sourceNode+targetNode+fencingToken+lastConfirmedStateAt";
    public static final String ADDON_REMOVAL_POLICY = "preserve-cloudislands-island-and-addon-state-by-island-uuid";
    public static final String ADDON_DISABLE_POLICY = "preflush-satis-state-stop-runtime-keep-cloudislands-lifecycle";
    public static final String DEFERRED_REMAP_POLICY = "when-feature-disabled-store-original-center-and-apply-remap-when-reenabled";
    public static final String DEFERRED_REMAP_KEY = "pendingMachineRemap,pendingResourceNodeRemap";
    public static final String TARGET_TICK_START_POLICY = "start-machine-resource-contract-tickers-only-after-addon-state-hydration";
    public static final String CRASH_REPLAY_POLICY = "replay-last-confirmed-state-only-after-runtime-owner-fence-check";
    public static final String STATE_OWNER_POLICY = "cloudislands-island-uuid-not-node-server-world-or-player";
    public static final String LOCAL_FALLBACK_RISK = "local-fallback-can-split-state-without-shared-backend";
    public static final String SETUP_SELECTION_POLICY = "env-explicit-type-setup-core-api-marker-auto-single-backend-database-default";
    public static final String SETUP_BACKEND_PRIORITY = "CLOUDISLANDS_SATIS_DATABASE_TYPE,setup.database.type,addons.cloudislands-satis.database.type,setup.database.core-api.enabled,jdbc-url,setup.database.<backend>,database.type";
    public static final String FALLBACK_CHAIN_POLICY = "shared-backend-before-local-sqlite-or-warn";
    public static final String FALLBACK_READINESS_POLICY = "use-only-explicitly-configured-shared-targets-then-explicit-local-sqlite";
    public static final String FALLBACK_READY_CHAIN_POLICY = "report-ready-fallback-targets-before-using-local-sqlite";
    public static final String CORE_API_WRITE_FALLBACK_POLICY = "retry-table-key-value-bulk-save-as-flattened-addon-state";
    public static final String PRODUCTION_SAFE_FALLBACK_POLICY = "POSTGRESQL,MYSQL,MARIADB,CORE_API-before-SQLITE";

    private static final Set<String> SHARED_BACKENDS = Set.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API");
    private static final Set<String> LOCAL_BACKENDS = Set.of("SQLITE");
    private static final List<String> RECOMMENDED_FALLBACK_ORDER = List.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API", "SQLITE");

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
        values.putAll(nodeMoveSafetyState());
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
        values.put("core-api-sync-production-safe-fallback-policy", PRODUCTION_SAFE_FALLBACK_POLICY);
        values.put("core-api-sync-shared-backends", String.join(",", RECOMMENDED_FALLBACK_ORDER.stream().filter(SatisStatePortabilityPolicy::sharedBackend).toList()));
        values.put("core-api-sync-local-backends", String.join(",", LOCAL_BACKENDS));
        return Map.copyOf(values);
    }

    public static Map<String, String> nodeMoveSafetyState() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("core-api-sync-node-handoff-preflush-policy", NODE_HANDOFF_PREFLUSH_POLICY);
        values.put("core-api-sync-node-handoff-restore-policy", NODE_HANDOFF_RESTORE_POLICY);
        values.put("core-api-sync-node-handoff-audit-key", NODE_HANDOFF_AUDIT_KEY);
        values.put("core-api-sync-target-tick-start-policy", TARGET_TICK_START_POLICY);
        values.put("core-api-sync-crash-replay-policy", CRASH_REPLAY_POLICY);
        values.put("core-api-sync-state-owner-policy", STATE_OWNER_POLICY);
        return Map.copyOf(values);
    }

    public static List<String> recommendedFallbackOrder() {
        return RECOMMENDED_FALLBACK_ORDER;
    }

    public static boolean sharedBackend(String backend) {
        return backend != null && SHARED_BACKENDS.contains(backend.trim().toUpperCase(java.util.Locale.ROOT));
    }

    public static boolean localBackend(String backend) {
        return backend != null && LOCAL_BACKENDS.contains(backend.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
