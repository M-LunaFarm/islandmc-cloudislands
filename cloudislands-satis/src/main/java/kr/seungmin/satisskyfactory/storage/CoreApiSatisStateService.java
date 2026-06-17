package kr.seungmin.satisskyfactory.storage;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.model.PowerNetwork;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import org.bukkit.block.BlockFace;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Logger;

public final class CoreApiSatisStateService {
    private static final int MAX_PENDING_BULK_RETRIES = 64;
    private static final String GLOBAL_TABLE_BULK_ENDPOINT = "/v1/addons/state/table/bulk";
    private static final String GLOBAL_TABLE_KEY_VALUE_BULK_ENDPOINT = "/v1/addons/state/table/key-value/bulk-save";
    private static final String GLOBAL_TABLE_KEY_VALUE_BULK_LOAD_ENDPOINT = "/v1/addons/state/table/key-value/bulk-load";
    private static final String GLOBAL_FLATTENED_FALLBACK_ENDPOINT = "/v1/addons/state/bulk";
    private static final String ISLAND_TABLE_BULK_ENDPOINT = "/v1/addons/islands/state/table/bulk";
    private static final String ISLAND_TABLE_KEY_VALUE_BULK_ENDPOINT = "/v1/addons/islands/state/table/key-value/bulk-save";
    private static final String ISLAND_TABLE_KEY_VALUE_BULK_LOAD_ENDPOINT = "/v1/addons/islands/state/table/key-value/bulk-load";
    private static final String ISLAND_FLATTENED_FALLBACK_ENDPOINT = "/v1/addons/islands/state/bulk";
    private static final String ISLAND_TABLE_REPLACE_FALLBACK_ENDPOINT = "/v1/addons/islands/state/table/replace";

    private final Logger logger;
    private final CloudIslandsApi cloudIslandsApi;
    private final String addonId;
    private final boolean flattenedFallbackEnabled;
    private final Predicate<String> featureEnabled;
    private final Object pendingRetryLock = new Object();
    private final Deque<PendingIslandBulk> pendingIslandBulkRetries = new ArrayDeque<>();
    private final Deque<PendingGlobalBulk> pendingGlobalBulkRetries = new ArrayDeque<>();
    private volatile String lastBulkStatusFingerprint = "";
    private volatile String lastGlobalBulkStatusFingerprint = "";
    private volatile String lastTableStatusFingerprint = "";
    private volatile String lastGlobalTableStatusFingerprint = "";
    private final AtomicLong islandBulkSuccesses = new AtomicLong();
    private final AtomicLong islandBulkFallbacks = new AtomicLong();
    private final AtomicLong islandBulkFailures = new AtomicLong();
    private final AtomicLong globalBulkSuccesses = new AtomicLong();
    private final AtomicLong globalBulkFallbacks = new AtomicLong();
    private final AtomicLong globalBulkFailures = new AtomicLong();
    private final AtomicLong islandBulkRetriesQueued = new AtomicLong();
    private final AtomicLong globalBulkRetriesQueued = new AtomicLong();
    private final AtomicLong islandBulkRetriesDrained = new AtomicLong();
    private final AtomicLong globalBulkRetriesDrained = new AtomicLong();
    private final AtomicLong islandBulkRetriesDropped = new AtomicLong();
    private final AtomicLong globalBulkRetriesDropped = new AtomicLong();
    private final AtomicLong tableSuccesses = new AtomicLong();
    private final AtomicLong tableFailures = new AtomicLong();
    private final AtomicLong globalTableLoadSuccesses = new AtomicLong();
    private final AtomicLong globalTableLoadFailures = new AtomicLong();
    private final AtomicLong islandTableLoadSuccesses = new AtomicLong();
    private final AtomicLong islandTableLoadFailures = new AtomicLong();
    private final AtomicLong flattenedLoadFallbacks = new AtomicLong();
    private final AtomicLong coreStateFailures = new AtomicLong();
    private volatile String lastFailure = "";
    private volatile String lastFailureAt = "";

    private record PendingIslandBulk(UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
    }

    private record PendingGlobalBulk(Map<String, String> values, Map<String, Map<String, String>> tables) {
    }

    public CoreApiSatisStateService(Logger logger, CloudIslandsApi cloudIslandsApi, String addonId) {
        this(logger, cloudIslandsApi, addonId, true);
    }

    public CoreApiSatisStateService(Logger logger, CloudIslandsApi cloudIslandsApi, String addonId, boolean flattenedFallbackEnabled) {
        this(logger, cloudIslandsApi, addonId, flattenedFallbackEnabled, _feature -> true);
    }

    public CoreApiSatisStateService(Logger logger, CloudIslandsApi cloudIslandsApi, String addonId, boolean flattenedFallbackEnabled, Predicate<String> featureEnabled) {
        this.logger = logger;
        this.cloudIslandsApi = cloudIslandsApi;
        this.addonId = addonId;
        this.flattenedFallbackEnabled = flattenedFallbackEnabled;
        this.featureEnabled = featureEnabled == null ? _feature -> true : featureEnabled;
    }

    public long islandBulkSuccesses() {
        return islandBulkSuccesses.get();
    }

    public long islandBulkFallbacks() {
        return islandBulkFallbacks.get();
    }

    public long islandBulkFailures() {
        return islandBulkFailures.get();
    }

    public long globalBulkSuccesses() {
        return globalBulkSuccesses.get();
    }

    public long globalBulkFallbacks() {
        return globalBulkFallbacks.get();
    }

    public long globalBulkFailures() {
        return globalBulkFailures.get();
    }

    public long islandBulkRetriesQueued() {
        return islandBulkRetriesQueued.get();
    }

    public long globalBulkRetriesQueued() {
        return globalBulkRetriesQueued.get();
    }

    public long islandBulkRetriesDrained() {
        return islandBulkRetriesDrained.get();
    }

    public long globalBulkRetriesDrained() {
        return globalBulkRetriesDrained.get();
    }

    public long islandBulkRetriesDropped() {
        return islandBulkRetriesDropped.get();
    }

    public long globalBulkRetriesDropped() {
        return globalBulkRetriesDropped.get();
    }

    public int pendingIslandBulkRetries() {
        return pendingIslandBulkRetryCount();
    }

    public int pendingGlobalBulkRetries() {
        return pendingGlobalBulkRetryCount();
    }

    public int maxPendingBulkRetries() {
        return MAX_PENDING_BULK_RETRIES;
    }

    public long tableSuccesses() {
        return tableSuccesses.get();
    }

    public long tableFailures() {
        return tableFailures.get();
    }

    public long globalTableLoadSuccesses() {
        return globalTableLoadSuccesses.get();
    }

    public long globalTableLoadFailures() {
        return globalTableLoadFailures.get();
    }

    public long islandTableLoadSuccesses() {
        return islandTableLoadSuccesses.get();
    }

    public long islandTableLoadFailures() {
        return islandTableLoadFailures.get();
    }

    public long flattenedLoadFallbacks() {
        return flattenedLoadFallbacks.get();
    }

    public long coreStateFailures() {
        return coreStateFailures.get();
    }

    public String lastFailure() {
        return lastFailure;
    }

    public String lastFailureAt() {
        return lastFailureAt;
    }

    public boolean flattenedFallbackEnabled() {
        return flattenedFallbackEnabled;
    }

    public String writerTransportMode() {
        return flattenedFallbackEnabled
                ? "table-key-value-bulk-save-primary-with-flattened-state-fallback"
                : "table-key-value-bulk-save-primary-no-flattened-fallback";
    }

    public String writerFallbackPolicy() {
        return flattenedFallbackEnabled
                ? "queue-retry-then-flattened-addon-state"
                : "queue-retry-only";
    }

    public String readerTransportMode() {
        return "table-key-value-bulk-load-primary-with-flattened-state-fallback";
    }

    public String writerReadiness() {
        if (cloudIslandsApi == null) {
            return "cloudislands-api-unavailable";
        }
        if (!stateFeatureEnabled("addon-state")) {
            return "addon-state-feature-disabled";
        }
        int pendingRetries = pendingIslandBulkRetryCount() + pendingGlobalBulkRetryCount();
        if (pendingRetries > 0 && coreStateFailures.get() > 0L) {
            return "degraded-with-pending-retries";
        }
        if (pendingRetries > 0) {
            return "pending-retries";
        }
        if (coreStateFailures.get() > 0L) {
            return "ready-with-prior-failures";
        }
        return "ready";
    }

    public int pendingBulkRetries() {
        return pendingIslandBulkRetryCount() + pendingGlobalBulkRetryCount();
    }

    public void publishDirtyBatch(DirtySaveService.DirtySaveBatch batch) {
        if (cloudIslandsApi == null || batch == null || !stateFeatureEnabled("addon-state")) {
            return;
        }
        Map<UUID, Map<String, String>> valuesByIsland = new LinkedHashMap<>();
        Map<UUID, Map<String, Map<String, String>>> tablesByIsland = new LinkedHashMap<>();
        batch.islands().values().forEach(island -> table(tablesByIsland, island.islandUuid(), "factory_islands")
                .put(island.islandUuid().toString(), islandJson(island)));
        batch.machines().values().forEach(machine -> table(tablesByIsland, machine.islandUuid(), "machines")
                .put(machine.machineId().toString(), machineJson(machine)));
        batch.inventories().values().forEach(inventory -> table(tablesByIsland, inventory.islandUuid(), "virtual_inventories")
                .put(inventory.inventoryId().toString(), inventoryJson(inventory)));
        batch.nodes().values().forEach(node -> table(tablesByIsland, node.islandUuid(), "resource_nodes")
                .put(node.nodeId().toString(), nodeJson(node)));
        tablesByIsland.forEach((islandId, tables) -> {
            Map<String, String> values = state(valuesByIsland, islandId);
            int tableKeys = tables.values().stream().mapToInt(Map::size).sum();
            values.put("core-api-sync-schema", "1");
            values.putAll(SatisStatePortabilityPolicy.coreApiSyncState());
            values.put("core-api-sync-updated-at", Instant.now().toString());
            values.put("core-api-sync-keys", Integer.toString(values.size() + tableKeys));
            values.put("core-api-sync-tables", Integer.toString(tables.size()));
            publishIslandTableKeyValueBulk(islandId, values, tables, "Satis core-api table state for island " + islandId);
        });
    }

    private Map<String, String> table(Map<UUID, Map<String, Map<String, String>>> tablesByIsland, UUID islandId, String table) {
        return tablesByIsland
                .computeIfAbsent(islandId, _ignored -> new LinkedHashMap<>())
                .computeIfAbsent(table, _ignored -> new LinkedHashMap<>());
    }

    public void removeRow(UUID islandId, String key) {
        if (cloudIslandsApi == null || islandId == null || key == null || key.isBlank() || !stateFeatureEnabled("addon-state")) {
            return;
        }
        if (!tableFeatureEnabled(key)) {
            return;
        }
        cloudIslandsApi.addons().removeIslandState(addonId, islandId, key).exceptionally(error -> {
            logger.warning("Failed to remove Satis core-api table state " + key + " for island " + islandId + ": " + error.getMessage());
            recordCoreStateFailure("island-row-remove", error);
            return Map.of();
        });
    }

    public void publishRow(DatabaseService.CoreRowWrite row) {
        if (cloudIslandsApi == null || row == null || row.islandUuid() == null || row.key() == null || row.key().isBlank() || row.value() == null || !stateFeatureEnabled("addon-state")) {
            return;
        }
        if (!tableFeatureEnabled(row.key())) {
            return;
        }
        Map<String, Map<String, String>> tablePayload = tablePayload(row.key(), row.value());
        if (!tablePayload.isEmpty()) {
            publishIslandTableKeyValueBulk(row.islandUuid(), Map.of(), tablePayload, "Satis core-api row " + row.key() + " for island " + row.islandUuid());
            return;
        }
        cloudIslandsApi.addons().putIslandState(addonId, row.islandUuid(), Map.of(row.key(), row.value())).exceptionally(error -> {
            logger.warning("Failed to publish Satis core-api row " + row.key() + " for island " + row.islandUuid() + ": " + error.getMessage());
            recordCoreStateFailure("island-row", error);
            return Map.of();
        });
    }

    public void publishTable(DatabaseService.CoreTableWrite table) {
        if (cloudIslandsApi == null || table == null || table.islandUuid() == null || table.table() == null || table.table().isBlank() || !stateFeatureEnabled("addon-state")) {
            return;
        }
        if (!tableNameFeatureEnabled(table.table())) {
            return;
        }
        if (table.values() == null) {
            return;
        }
        if (table.values().isEmpty()) {
            cloudIslandsApi.addons().clearIslandTableState(addonId, table.islandUuid(), table.table())
                    .thenApply(state -> {
                        publishTableStatus(table, "cleared", "");
                        return state;
                    })
                    .exceptionally(error -> {
                        logger.warning("Failed to clear empty Satis core-api table " + table.table() + " for island " + table.islandUuid() + ": " + error.getMessage());
                        publishTableStatus(table, "failed", error.getMessage());
                        return Map.of();
                    });
            return;
        }
        cloudIslandsApi.addons().replaceIslandTableState(addonId, table.islandUuid(), table.table(), table.values())
                .handle((state, error) -> {
                    if (error == null) {
                        return java.util.concurrent.CompletableFuture.completedFuture(state);
                    }
                    logger.warning("Failed to replace Satis core-api table " + table.table() + " for island " + table.islandUuid() + ", retrying with clear and bulk save: " + error.getMessage());
                    return cloudIslandsApi.addons().clearIslandTableState(addonId, table.islandUuid(), table.table())
                            .thenCompose(_cleared -> cloudIslandsApi.addons().tableBulkIslandState(addonId, table.islandUuid(), Map.of(table.table(), table.values())));
                })
                .thenCompose(result -> result)
                .thenApply(state -> {
                    publishTableStatus(table, "success", "");
                    return state;
                })
                .exceptionally(error -> {
                    logger.warning("Failed to publish Satis core-api table " + table.table() + " for island " + table.islandUuid() + ": " + error.getMessage());
                    publishTableStatus(table, "failed", error.getMessage());
                    return Map.of();
                });
    }

    private void publishTableStatus(DatabaseService.CoreTableWrite table, String status, String error) {
        if (cloudIslandsApi == null || table == null) {
            return;
        }
        String island = table.islandUuid() == null ? "" : table.islandUuid().toString();
        String tableName = table.table() == null ? "" : table.table();
        String keyCount = table.values() == null ? "0" : Integer.toString(table.values().size());
        String safeStatus = status == null || status.isBlank() ? "unknown" : status;
        String safeError = error == null ? "" : error;
        recordTableStatus(safeStatus, safeError);
        String fingerprint = island + "|" + tableName + "|" + keyCount + "|" + safeStatus + "|" + safeError;
        if (fingerprint.equals(lastTableStatusFingerprint)) {
            return;
        }
        lastTableStatusFingerprint = fingerprint;
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-core-table-publish-island", island);
        state.put("last-core-table-publish-table", tableName);
        state.put("last-core-table-publish-keys", keyCount);
        state.put("last-core-table-publish-status", safeStatus);
        state.put("last-core-table-publish-error", safeError);
        state.put("last-core-table-publish-at", Instant.now().toString());
        state.put("last-core-table-publish-authority", "cloudislands-addon-state");
        state.put("last-core-table-publish-primary-endpoint", ISLAND_TABLE_BULK_ENDPOINT);
        state.put("last-core-table-publish-compat-endpoint", ISLAND_TABLE_KEY_VALUE_BULK_ENDPOINT);
        state.put("last-core-table-publish-fallback-endpoint", ISLAND_TABLE_REPLACE_FALLBACK_ENDPOINT);
        state.put("last-core-table-publish-write-path", "replace-table->clear-table-and-table-bulk-on-failure");
        state.put("last-core-table-publish-node-bound", "false");
        state.put("last-core-table-publish-write-fence", "active-island-runtime-owner-only");
        state.put("last-core-table-publish-duplicate-tick-policy", "single-active-runtime-owner");
        cloudIslandsApi.addons().putState(addonId, state).exceptionally(publishError -> {
            logger.warning("Failed to publish Satis core-api table status: " + publishError.getMessage());
            return Map.of();
        });
    }

    public void publishGlobalRow(DatabaseService.CoreGlobalRowWrite row) {
        if (cloudIslandsApi == null || row == null || row.key() == null || row.key().isBlank() || row.value() == null || !stateFeatureEnabled("addon-state")) {
            return;
        }
        if (!tableFeatureEnabled(row.key())) {
            return;
        }
        Map<String, Map<String, String>> tablePayload = tablePayload(row.key(), row.value());
        if (!tablePayload.isEmpty()) {
            publishGlobalTableKeyValueBulk(Map.of(), tablePayload, "Satis core-api global row " + row.key());
            return;
        }
        cloudIslandsApi.addons().putState(addonId, Map.of(row.key(), row.value())).exceptionally(error -> {
            logger.warning("Failed to publish Satis core-api global row " + row.key() + ": " + error.getMessage());
            recordCoreStateFailure("global-row", error);
            return Map.of();
        });
    }

    private Map<String, Map<String, String>> tablePayload(String key, String value) {
        if (key == null || value == null || !key.startsWith(IslandAddonService.TABLE_STATE_KEY_PREFIX)) {
            return Map.of();
        }
        int rowKeyStart = key.indexOf('/', IslandAddonService.TABLE_STATE_KEY_PREFIX.length());
        if (rowKeyStart < 0 || rowKeyStart == key.length() - 1) {
            return Map.of();
        }
        String table = key.substring(IslandAddonService.TABLE_STATE_KEY_PREFIX.length(), rowKeyStart);
        String rowKey = key.substring(rowKeyStart + 1);
        if (table.isBlank() || rowKey.isBlank()) {
            return Map.of();
        }
        return Map.of(table, Map.of(rowKey, value));
    }

    private void publishIslandTableKeyValueBulk(UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables, String description) {
        if (cloudIslandsApi == null || islandId == null || !stateFeatureEnabled("addon-state")) {
            return;
        }
        Map<String, String> mergedValues = copyValues(values);
        Map<String, Map<String, String>> mergedTables = copyTables(enabledTables(tables));
        PendingIslandBulk pending = takePendingIslandBulkRetry(islandId);
        String safeDescription = description;
        if (pending != null) {
            mergedValues = mergeValues(pending.values(), mergedValues);
            mergedTables = mergeTables(pending.tables(), mergedTables);
            safeDescription = description + " with queued retry";
        }
        if (mergedValues.isEmpty() && mergedTables.isEmpty()) {
            return;
        }
        Map<String, String> safeValues = mergedValues;
        Map<String, Map<String, String>> safeTables = mergedTables;
        String finalDescription = safeDescription;
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.islandTables(addonId, islandId, safeTables);
        request = new AddonStateBulkSaveRequest(addonId, islandId, "", safeValues, request.tables());
        cloudIslandsApi.addons().tableKeyValueBulkSaveIslandState(request)
                .handle((state, error) -> {
                    if (error == null) {
                        publishBulkStatus(islandId, safeValues, safeTables, "success", "bulk", "");
                        return java.util.concurrent.CompletableFuture.completedFuture(state);
                    }
                    if (!flattenedFallbackEnabled) {
                        logger.warning("Failed to publish " + finalDescription + " and flattened addon state fallback is disabled: " + error.getMessage());
                        queueIslandBulkRetry(islandId, safeValues, safeTables);
                        publishBulkStatus(islandId, safeValues, safeTables, "failed", "bulk-fallback-disabled", error.getMessage());
                        return java.util.concurrent.CompletableFuture.completedFuture(Map.<String, String>of());
                    }
                    Map<String, String> fallback = flattenedState(safeValues, safeTables);
                    logger.warning("Failed to publish " + finalDescription + ", retrying with flattened addon state: " + error.getMessage());
                    if (fallback.isEmpty()) {
                        queueIslandBulkRetry(islandId, safeValues, safeTables);
                        publishBulkStatus(islandId, safeValues, safeTables, "fallback-empty", "flattened", error.getMessage());
                        return java.util.concurrent.CompletableFuture.completedFuture(Map.<String, String>of());
                    }
                    return cloudIslandsApi.addons().putIslandState(addonId, islandId, fallback)
                            .thenApply(fallbackState -> {
                                publishBulkStatus(islandId, safeValues, safeTables, "fallback", "flattened", error.getMessage());
                                return fallbackState;
                            });
                })
                .thenCompose(result -> result)
                .exceptionally(error -> {
                    logger.warning("Failed to publish " + finalDescription + " with flattened addon state fallback: " + error.getMessage());
                    queueIslandBulkRetry(islandId, safeValues, safeTables);
                    publishBulkStatus(islandId, safeValues, safeTables, "failed", "flattened", error.getMessage());
                    return Map.of();
                });
    }

    private void publishGlobalTableKeyValueBulk(Map<String, String> values, Map<String, Map<String, String>> tables, String description) {
        if (cloudIslandsApi == null || !stateFeatureEnabled("addon-state")) {
            return;
        }
        Map<String, String> mergedValues = copyValues(values);
        Map<String, Map<String, String>> mergedTables = copyTables(enabledTables(tables));
        PendingGlobalBulk pending = takePendingGlobalBulkRetry();
        String safeDescription = description;
        if (pending != null) {
            mergedValues = mergeValues(pending.values(), mergedValues);
            mergedTables = mergeTables(pending.tables(), mergedTables);
            safeDescription = description + " with queued retry";
        }
        if (mergedValues.isEmpty() && mergedTables.isEmpty()) {
            return;
        }
        Map<String, String> safeValues = mergedValues;
        Map<String, Map<String, String>> safeTables = mergedTables;
        String finalDescription = safeDescription;
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.globalTables(addonId, safeTables);
        request = new AddonStateBulkSaveRequest(addonId, null, "", safeValues, request.tables());
        cloudIslandsApi.addons().tableKeyValueBulkSaveState(request)
                .handle((state, error) -> {
                    if (error == null) {
                        publishGlobalBulkStatus(safeValues, safeTables, "success", "bulk", "");
                        return java.util.concurrent.CompletableFuture.completedFuture(state);
                    }
                    if (!flattenedFallbackEnabled) {
                        logger.warning("Failed to publish " + finalDescription + " and flattened addon state fallback is disabled: " + error.getMessage());
                        queueGlobalBulkRetry(safeValues, safeTables);
                        publishGlobalBulkStatus(safeValues, safeTables, "failed", "bulk-fallback-disabled", error.getMessage());
                        return java.util.concurrent.CompletableFuture.completedFuture(Map.<String, String>of());
                    }
                    Map<String, String> fallback = flattenedState(safeValues, safeTables);
                    logger.warning("Failed to publish " + finalDescription + ", retrying with flattened addon state: " + error.getMessage());
                    if (fallback.isEmpty()) {
                        queueGlobalBulkRetry(safeValues, safeTables);
                        publishGlobalBulkStatus(safeValues, safeTables, "fallback-empty", "flattened", error.getMessage());
                        return java.util.concurrent.CompletableFuture.completedFuture(Map.<String, String>of());
                    }
                    return cloudIslandsApi.addons().putState(addonId, fallback)
                            .thenApply(fallbackState -> {
                                publishGlobalBulkStatus(safeValues, safeTables, "fallback", "flattened", error.getMessage());
                                return fallbackState;
                            });
                })
                .thenCompose(result -> result)
                .exceptionally(error -> {
                    logger.warning("Failed to publish " + finalDescription + " with flattened addon state fallback: " + error.getMessage());
                    queueGlobalBulkRetry(safeValues, safeTables);
                    publishGlobalBulkStatus(safeValues, safeTables, "failed", "flattened", error.getMessage());
                    return Map.of();
                });
    }

    private void publishBulkStatus(UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables, String status, String mode, String error) {
        if (cloudIslandsApi == null || islandId == null) {
            return;
        }
        int valueKeys = values == null ? 0 : values.size();
        int tablesCount = tables == null ? 0 : tables.size();
        int tableKeys = tableKeyCount(tables);
        String safeStatus = status == null || status.isBlank() ? "unknown" : status;
        String safeMode = mode == null || mode.isBlank() ? "unknown" : mode;
        String safeError = compactError(error);
        recordIslandBulkStatus(safeStatus, safeError);
        String fingerprint = islandId + "|" + valueKeys + "|" + tablesCount + "|" + tableKeys + "|" + safeStatus + "|" + safeMode + "|" + safeError;
        if (fingerprint.equals(lastBulkStatusFingerprint)) {
            return;
        }
        lastBulkStatusFingerprint = fingerprint;
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-core-bulk-publish-island", islandId.toString());
        state.put("last-core-bulk-publish-status", safeStatus);
        state.put("last-core-bulk-publish-mode", safeMode);
        state.put("last-core-bulk-publish-value-keys", Integer.toString(valueKeys));
        state.put("last-core-bulk-publish-tables", Integer.toString(tablesCount));
        state.put("last-core-bulk-publish-table-keys", Integer.toString(tableKeys));
        state.put("last-core-bulk-publish-error", safeError);
        state.put("last-core-bulk-publish-at", Instant.now().toString());
        state.put("last-core-bulk-publish-authority", "cloudislands-addon-state");
        state.put("last-core-bulk-publish-primary-endpoint", ISLAND_TABLE_KEY_VALUE_BULK_ENDPOINT);
        state.put("last-core-bulk-publish-fallback-endpoint", ISLAND_FLATTENED_FALLBACK_ENDPOINT);
        state.put("last-core-bulk-publish-write-path", bulkWritePath(safeStatus, safeMode, true));
        state.put("last-core-bulk-publish-fallback-policy", flattenedFallbackEnabled ? "flattened-state-retry" : "disabled");
        state.put("last-core-bulk-publish-pending-retries", Integer.toString(pendingIslandBulkRetryCount()));
        state.put("addon-state-sync-bulk-max-pending-retries", Integer.toString(MAX_PENDING_BULK_RETRIES));
        state.put("addon-state-sync-island-bulk-pending-retries", Integer.toString(pendingIslandBulkRetryCount()));
        state.put("addon-state-sync-island-bulk-retries-queued", Long.toString(islandBulkRetriesQueued.get()));
        state.put("addon-state-sync-island-bulk-retries-drained", Long.toString(islandBulkRetriesDrained.get()));
        state.put("addon-state-sync-island-bulk-retries-dropped", Long.toString(islandBulkRetriesDropped.get()));
        cloudIslandsApi.addons().putState(addonId, state).exceptionally(publishError -> {
            logger.warning("Failed to publish Satis core-api bulk status: " + publishError.getMessage());
            recordCoreStateFailure("island-bulk-status", publishError);
            return Map.of();
        });
    }

    private void publishGlobalBulkStatus(Map<String, String> values, Map<String, Map<String, String>> tables, String status, String mode, String error) {
        if (cloudIslandsApi == null) {
            return;
        }
        int valueKeys = values == null ? 0 : values.size();
        int tablesCount = tables == null ? 0 : tables.size();
        int tableKeys = tableKeyCount(tables);
        String safeStatus = status == null || status.isBlank() ? "unknown" : status;
        String safeMode = mode == null || mode.isBlank() ? "unknown" : mode;
        String safeError = compactError(error);
        recordGlobalBulkStatus(safeStatus, safeError);
        String fingerprint = valueKeys + "|" + tablesCount + "|" + tableKeys + "|" + safeStatus + "|" + safeMode + "|" + safeError;
        if (fingerprint.equals(lastGlobalBulkStatusFingerprint)) {
            return;
        }
        lastGlobalBulkStatusFingerprint = fingerprint;
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-core-global-bulk-publish-status", safeStatus);
        state.put("last-core-global-bulk-publish-mode", safeMode);
        state.put("last-core-global-bulk-publish-value-keys", Integer.toString(valueKeys));
        state.put("last-core-global-bulk-publish-tables", Integer.toString(tablesCount));
        state.put("last-core-global-bulk-publish-table-keys", Integer.toString(tableKeys));
        state.put("last-core-global-bulk-publish-error", safeError);
        state.put("last-core-global-bulk-publish-at", Instant.now().toString());
        state.put("last-core-global-bulk-publish-authority", "cloudislands-addon-state");
        state.put("last-core-global-bulk-publish-primary-endpoint", GLOBAL_TABLE_KEY_VALUE_BULK_ENDPOINT);
        state.put("last-core-global-bulk-publish-fallback-endpoint", GLOBAL_FLATTENED_FALLBACK_ENDPOINT);
        state.put("last-core-global-bulk-publish-write-path", bulkWritePath(safeStatus, safeMode, false));
        state.put("last-core-global-bulk-publish-fallback-policy", flattenedFallbackEnabled ? "flattened-state-retry" : "disabled");
        state.put("last-core-global-bulk-publish-pending-retries", Integer.toString(pendingGlobalBulkRetryCount()));
        state.put("addon-state-sync-bulk-max-pending-retries", Integer.toString(MAX_PENDING_BULK_RETRIES));
        state.put("addon-state-sync-global-bulk-pending-retries", Integer.toString(pendingGlobalBulkRetryCount()));
        state.put("addon-state-sync-global-bulk-retries-queued", Long.toString(globalBulkRetriesQueued.get()));
        state.put("addon-state-sync-global-bulk-retries-drained", Long.toString(globalBulkRetriesDrained.get()));
        state.put("addon-state-sync-global-bulk-retries-dropped", Long.toString(globalBulkRetriesDropped.get()));
        cloudIslandsApi.addons().putState(addonId, state).exceptionally(publishError -> {
            logger.warning("Failed to publish Satis core-api global bulk status: " + publishError.getMessage());
            recordCoreStateFailure("global-bulk-status", publishError);
            return Map.of();
        });
    }

    private String bulkWritePath(String status, String mode, boolean islandScoped) {
        String primary = islandScoped ? "island-table-key-value-bulk-save" : "global-table-key-value-bulk-save";
        String fallback = islandScoped ? "island-flattened-bulk" : "global-flattened-bulk";
        if ("success".equals(status) && "bulk".equals(mode)) {
            return primary;
        }
        if ("fallback".equals(status) && "flattened".equals(mode)) {
            return primary + "->" + fallback;
        }
        if ("fallback-empty".equals(status) && "flattened".equals(mode)) {
            return primary + "->" + fallback + "-empty";
        }
        if ("failed".equals(status) && "bulk-fallback-disabled".equals(mode)) {
            return primary + "->fallback-disabled";
        }
        if ("failed".equals(status) && "flattened".equals(mode)) {
            return primary + "->" + fallback + "-failed";
        }
        return primary + "->" + mode;
    }

    private void recordIslandBulkStatus(String status, String error) {
        String value = status == null ? "" : status;
        if ("success".equals(value)) {
            islandBulkSuccesses.incrementAndGet();
            return;
        }
        if ("fallback".equals(value)) {
            islandBulkFallbacks.incrementAndGet();
            return;
        }
        if ("failed".equals(value) || "fallback-empty".equals(value)) {
            islandBulkFailures.incrementAndGet();
            recordCoreStateFailure("island-bulk-" + value, error);
        }
    }

    private void recordGlobalBulkStatus(String status, String error) {
        String value = status == null ? "" : status;
        if ("success".equals(value)) {
            globalBulkSuccesses.incrementAndGet();
            return;
        }
        if ("fallback".equals(value)) {
            globalBulkFallbacks.incrementAndGet();
            return;
        }
        if ("failed".equals(value) || "fallback-empty".equals(value)) {
            globalBulkFailures.incrementAndGet();
            recordCoreStateFailure("global-bulk-" + value, error);
        }
    }

    private void recordTableStatus(String status, String error) {
        String value = status == null ? "" : status;
        if ("success".equals(value) || "cleared".equals(value)) {
            tableSuccesses.incrementAndGet();
            return;
        }
        if ("failed".equals(value)) {
            tableFailures.incrementAndGet();
            recordCoreStateFailure("table-" + value, error);
        }
    }

    private void recordCoreStateFailure(String scope, Throwable error) {
        recordCoreStateFailure(scope, error == null ? "" : error.getMessage());
    }

    private void recordCoreStateFailure(String scope, String error) {
        coreStateFailures.incrementAndGet();
        String safeScope = scope == null || scope.isBlank() ? "unknown" : scope;
        String safeError = compactError(error);
        lastFailure = safeError.isBlank() ? safeScope : safeScope + ": " + safeError;
        lastFailureAt = Instant.now().toString();
    }

    private int tableKeyCount(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        return tables.values().stream()
                .filter(values -> values != null)
                .mapToInt(Map::size)
                .sum();
    }

    private String compactError(String error) {
        if (error == null || error.isBlank()) {
            return "";
        }
        String value = error.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() <= 160 ? value : value.substring(0, 160);
    }

    private void queueIslandBulkRetry(UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (islandId == null || (values.isEmpty() && tables.isEmpty())) {
            return;
        }
        synchronized (pendingRetryLock) {
            while (pendingIslandBulkRetries.size() >= MAX_PENDING_BULK_RETRIES) {
                pendingIslandBulkRetries.removeFirst();
                islandBulkRetriesDropped.incrementAndGet();
            }
            pendingIslandBulkRetries.addLast(new PendingIslandBulk(islandId, copyValues(values), copyTables(tables)));
        }
        islandBulkRetriesQueued.incrementAndGet();
    }

    private PendingIslandBulk takePendingIslandBulkRetry(UUID islandId) {
        synchronized (pendingRetryLock) {
            java.util.Iterator<PendingIslandBulk> iterator = pendingIslandBulkRetries.iterator();
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, Map<String, String>> tables = new LinkedHashMap<>();
            int drained = 0;
            while (iterator.hasNext()) {
                PendingIslandBulk pending = iterator.next();
                if (pending.islandId().equals(islandId)) {
                    iterator.remove();
                    values = mergeValues(values, pending.values());
                    tables = mergeTables(tables, pending.tables());
                    drained++;
                }
            }
            if (drained > 0) {
                islandBulkRetriesDrained.addAndGet(drained);
            }
            return values.isEmpty() && tables.isEmpty() ? null : new PendingIslandBulk(islandId, values, tables);
        }
    }

    private int pendingIslandBulkRetryCount() {
        synchronized (pendingRetryLock) {
            return pendingIslandBulkRetries.size();
        }
    }

    private void queueGlobalBulkRetry(Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (values.isEmpty() && tables.isEmpty()) {
            return;
        }
        synchronized (pendingRetryLock) {
            while (pendingGlobalBulkRetries.size() >= MAX_PENDING_BULK_RETRIES) {
                pendingGlobalBulkRetries.removeFirst();
                globalBulkRetriesDropped.incrementAndGet();
            }
            pendingGlobalBulkRetries.addLast(new PendingGlobalBulk(copyValues(values), copyTables(tables)));
        }
        globalBulkRetriesQueued.incrementAndGet();
    }

    private PendingGlobalBulk takePendingGlobalBulkRetry() {
        synchronized (pendingRetryLock) {
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, Map<String, String>> tables = new LinkedHashMap<>();
            PendingGlobalBulk pending;
            int drained = 0;
            while ((pending = pendingGlobalBulkRetries.pollFirst()) != null) {
                values = mergeValues(values, pending.values());
                tables = mergeTables(tables, pending.tables());
                drained++;
            }
            if (drained > 0) {
                globalBulkRetriesDrained.addAndGet(drained);
            }
            return values.isEmpty() && tables.isEmpty() ? null : new PendingGlobalBulk(values, tables);
        }
    }

    private int pendingGlobalBulkRetryCount() {
        synchronized (pendingRetryLock) {
            return pendingGlobalBulkRetries.size();
        }
    }

    private Map<String, String> copyValues(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                copy.put(key.trim(), value);
            }
        });
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }

    private Map<String, String> mergeValues(Map<String, String> pending, Map<String, String> current) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.putAll(copyValues(pending));
        merged.putAll(copyValues(current));
        return merged.isEmpty() ? Map.of() : Map.copyOf(merged);
    }

    private Map<String, Map<String, String>> copyTables(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        tables.forEach((table, values) -> {
            String safeTable = tableName(table);
            if (safeTable.isBlank()) {
                return;
            }
            Map<String, String> safeValues = copyValues(values);
            if (!safeValues.isEmpty()) {
                copy.put(safeTable, safeValues);
            }
        });
        return copy.isEmpty() ? Map.of() : Map.copyOf(copy);
    }

    private Map<String, Map<String, String>> mergeTables(Map<String, Map<String, String>> pending, Map<String, Map<String, String>> current) {
        Map<String, Map<String, String>> merged = new LinkedHashMap<>();
        putTables(merged, pending);
        putTables(merged, current);
        return copyTables(merged);
    }

    private void putTables(Map<String, Map<String, String>> target, Map<String, Map<String, String>> source) {
        copyTables(source).forEach((table, values) -> {
            Map<String, String> rows = new LinkedHashMap<>(target.getOrDefault(table, Map.of()));
            rows.putAll(values);
            target.put(table, rows);
        });
    }

    private Map<String, String> flattenedState(Map<String, String> values, Map<String, Map<String, String>> tables) {
        Map<String, String> state = new LinkedHashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    state.put(key.trim(), value);
                }
            });
        }
        if (tables != null) {
            tables.forEach((table, tableValues) -> {
                if (tableValues == null) {
                    return;
                }
                String tableName = tableName(table);
                if (tableName.isBlank()) {
                    return;
                }
                tableValues.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        state.put(IslandAddonService.TABLE_STATE_KEY_PREFIX + tableName + "/" + key.trim(), value);
                    }
                });
            });
        }
        return Map.copyOf(state);
    }

    private String tableName(String table) {
        String value = table == null ? "" : table.trim();
        if (value.startsWith(IslandAddonService.TABLE_STATE_KEY_PREFIX)) {
            value = value.substring(IslandAddonService.TABLE_STATE_KEY_PREFIX.length());
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    public void publishGlobalTable(DatabaseService.CoreGlobalTableWrite table) {
        if (cloudIslandsApi == null || table == null || table.table() == null || table.table().isBlank() || table.values() == null) {
            return;
        }
        if (!tableNameFeatureEnabled(table.table())) {
            return;
        }
        if (table.values().isEmpty()) {
            cloudIslandsApi.addons().clearTableState(addonId, table.table())
                    .thenApply(state -> {
                        publishGlobalTableStatus(table, "cleared", "");
                        return state;
                    })
                    .exceptionally(error -> {
                        logger.warning("Failed to clear Satis core-api global table " + table.table() + ": " + error.getMessage());
                        publishGlobalTableStatus(table, "failed", error.getMessage());
                        return Map.of();
                    });
            return;
        }
        cloudIslandsApi.addons().clearTableState(addonId, table.table())
                .thenCompose(_cleared -> cloudIslandsApi.addons().tableBulkState(addonId, Map.of(table.table(), table.values())))
                .handle((state, error) -> {
                    if (error == null) {
                        return java.util.concurrent.CompletableFuture.completedFuture(state);
                    }
                    logger.warning("Failed to bulk save Satis core-api global table " + table.table() + ", retrying with replace: " + error.getMessage());
                    return cloudIslandsApi.addons().replaceTableState(addonId, table.table(), table.values());
                })
                .thenCompose(result -> result)
                .thenApply(state -> {
                    publishGlobalTableStatus(table, "success", "");
                    return state;
                })
                .exceptionally(error -> {
                    logger.warning("Failed to publish Satis core-api global table " + table.table() + ": " + error.getMessage());
                    publishGlobalTableStatus(table, "failed", error.getMessage());
                    return Map.of();
                });
    }

    private void publishGlobalTableStatus(DatabaseService.CoreGlobalTableWrite table, String status, String error) {
        if (cloudIslandsApi == null || table == null) {
            return;
        }
        String tableName = table.table() == null ? "" : table.table();
        String keyCount = table.values() == null ? "0" : Integer.toString(table.values().size());
        String safeStatus = status == null || status.isBlank() ? "unknown" : status;
        String safeError = error == null ? "" : error;
        recordTableStatus(safeStatus, safeError);
        String fingerprint = tableName + "|" + keyCount + "|" + safeStatus + "|" + safeError;
        if (fingerprint.equals(lastGlobalTableStatusFingerprint)) {
            return;
        }
        lastGlobalTableStatusFingerprint = fingerprint;
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-core-global-table-publish-table", tableName);
        state.put("last-core-global-table-publish-keys", keyCount);
        state.put("last-core-global-table-publish-status", safeStatus);
        state.put("last-core-global-table-publish-error", safeError);
        state.put("last-core-global-table-publish-at", Instant.now().toString());
        state.put("last-core-global-table-publish-authority", "cloudislands-addon-state");
        state.put("last-core-global-table-publish-primary-endpoint", GLOBAL_TABLE_BULK_ENDPOINT);
        state.put("last-core-global-table-publish-compat-endpoint", GLOBAL_TABLE_KEY_VALUE_BULK_ENDPOINT);
        state.put("last-core-global-table-publish-node-bound", "false");
        state.put("last-core-global-table-publish-runtime-bound", "false");
        state.put("last-core-global-table-publish-scope", "shared-addon-global-state");
        state.put("last-core-global-table-publish-write-policy", "last-confirmed-state-wins");
        state.put("last-core-global-table-publish-conflict-policy", "global-table-replace-after-bulk-fallback");
        cloudIslandsApi.addons().putState(addonId, state).exceptionally(publishError -> {
            logger.warning("Failed to publish Satis core-api global table status: " + publishError.getMessage());
            recordCoreStateFailure("global-table-status", publishError);
            return Map.of();
        });
    }

    public boolean hydrateGlobal(DatabaseService database) {
        if (cloudIslandsApi == null || database == null) {
            return false;
        }
        if (!stateFeatureEnabled("market")) {
            return false;
        }
        Map<String, String> state = loadGlobalTablesForHydration(List.of("market_daily"));
        if (state.isEmpty()) {
            state = loadGlobalFlattenedStateFallback();
        }
        if (state == null || state.isEmpty()) {
            return false;
        }
        boolean[] restored = {false};
        Map<String, String> hydrationState = state;
        database.withCoreStatePublishingSuspended(() -> {
            for (Map.Entry<String, String> entry : hydrationState.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null || !key.startsWith(IslandAddonService.tableStateKeyPrefix("market_daily"))) {
                    continue;
                }
                try {
                    String itemId = text(value, "itemId", "");
                    String dateKey = text(value, "dateKey", "");
                    if (itemId.isBlank() || dateKey.isBlank()) {
                        continue;
                    }
                    database.saveMarketDailySnapshot(
                            itemId,
                            dateKey,
                            longValue(value, "soldAmount", 0L),
                            decimal(value, "demandFactor", 1.0D)
                    );
                    restored[0] = true;
                } catch (RuntimeException exception) {
                    logger.warning("Failed to hydrate Satis core-api global row " + key + ": " + exception.getMessage());
                }
            }
        });
        return restored[0];
    }

    public boolean hydrateIsland(UUID islandId, DatabaseService database) {
        if (cloudIslandsApi == null || islandId == null || database == null) {
            return false;
        }
        Map<String, String> state = loadIslandTablesForHydration(islandId, List.of(
                "factory_islands",
                "virtual_inventories",
                "machines",
                "resource_nodes",
                "contracts",
                "island_unlocks",
                "market_personal_daily",
                "ledger",
                "item_networks",
                "power_networks"
        ));
        if (state.isEmpty()) {
            state = loadIslandFlattenedStateFallback(islandId);
        }
        if (state == null || state.isEmpty()) {
            return false;
        }
        Map<String, String> hydrationState = state;
        boolean[] restored = {false};
        List<ItemNetwork> itemNetworks = new ArrayList<>();
        List<PowerNetwork> powerNetworks = new ArrayList<>();
        String itemNetworkIndexKey = IslandAddonService.tableStateKey("item_networks", "index");
        String powerNetworkIndexKey = IslandAddonService.tableStateKey("power_networks", "index");
        Set<UUID> itemNetworkIndex = networkIndex(state.get(itemNetworkIndexKey));
        Set<UUID> powerNetworkIndex = networkIndex(state.get(powerNetworkIndexKey));
        database.withCoreStatePublishingSuspended(() -> {
            for (Map.Entry<String, String> entry : hydrationState.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null) {
                    continue;
                }
                try {
                    if (!tableFeatureEnabled(key)) {
                        continue;
                    }
                    if (key.startsWith(IslandAddonService.tableStateKeyPrefix("factory_islands"))) {
                        database.saveIsland(island(value));
                        restored[0] = true;
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("virtual_inventories"))) {
                        database.saveInventory(inventory(value));
                        restored[0] = true;
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("machines"))) {
                        database.saveMachine(machine(value));
                        restored[0] = true;
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("resource_nodes"))) {
                        database.saveNode(node(value));
                        restored[0] = true;
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("contracts"))) {
                        database.saveContract(contract(value));
                        restored[0] = true;
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("island_unlocks"))) {
                        String unlockId = text(value, "unlockId", "");
                        if (!unlockId.isBlank()) {
                            database.saveUnlock(islandId, unlockId);
                            restored[0] = true;
                        }
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("market_personal_daily"))) {
                        String itemId = text(value, "itemId", "");
                        String dateKey = text(value, "dateKey", "");
                        if (itemId.isBlank() || dateKey.isBlank()) {
                            continue;
                        }
                        database.saveMarketPersonalSnapshot(
                                uuid(text(value, "islandUuid", islandId.toString())),
                                itemId,
                                dateKey,
                                longValue(value, "soldAmount", 0L)
                        );
                        restored[0] = true;
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("ledger"))) {
                        database.saveLedgerSnapshot(
                                uuid(text(value, "ledgerId", "")),
                                uuid(text(value, "islandUuid", islandId.toString())),
                                text(value, "type", ""),
                                longValue(value, "amount", 0L),
                                text(value, "reason", ""),
                                longValue(value, "createdAt", System.currentTimeMillis())
                        );
                        restored[0] = true;
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("item_networks")) && !itemNetworkIndexKey.equals(key)) {
                        ItemNetwork network = itemNetwork(value);
                        if (itemNetworkIndex.isEmpty() || itemNetworkIndex.contains(network.networkId())) {
                            itemNetworks.add(network);
                        }
                    } else if (key.startsWith(IslandAddonService.tableStateKeyPrefix("power_networks")) && !powerNetworkIndexKey.equals(key)) {
                        PowerNetwork network = powerNetwork(value);
                        if (powerNetworkIndex.isEmpty() || powerNetworkIndex.contains(network.networkId())) {
                            powerNetworks.add(network);
                        }
                    }
                } catch (RuntimeException exception) {
                    logger.warning("Failed to hydrate Satis core-api row " + key + " for island " + islandId + ": " + exception.getMessage());
                }
            }
            if (!itemNetworks.isEmpty() || !itemNetworkIndex.isEmpty()) {
                database.replaceItemNetworks(islandId, itemNetworks);
                restored[0] = true;
            }
            if (!powerNetworks.isEmpty() || !powerNetworkIndex.isEmpty()) {
                database.replacePowerNetworks(islandId, powerNetworks);
                restored[0] = true;
            }
        });
        return restored[0];
    }

    public Map<String, String> loadGlobalTable(String table) {
        if (cloudIslandsApi == null || table == null || table.isBlank()) {
            return Map.of();
        }
        if (!stateFeatureEnabled("addon-state") || !tableNameFeatureEnabled(table)) {
            return Map.of();
        }
        try {
            Map<String, String> state = safeState(cloudIslandsApi.addons().tableKeyValueBulkLoadState(addonId, table).join());
            globalTableLoadSuccesses.incrementAndGet();
            return state;
        } catch (RuntimeException exception) {
            globalTableLoadFailures.incrementAndGet();
            logger.warning("Failed to bulk-load Satis core-api global table " + table + " through " + GLOBAL_TABLE_KEY_VALUE_BULK_LOAD_ENDPOINT + ": " + exception.getMessage());
            recordCoreStateFailure("global-table-load", exception);
            return Map.of();
        }
    }

    public Map<String, String> loadIslandTable(UUID islandId, String table) {
        if (cloudIslandsApi == null || islandId == null || table == null || table.isBlank()) {
            return Map.of();
        }
        if (!stateFeatureEnabled("addon-state") || !tableNameFeatureEnabled(table)) {
            return Map.of();
        }
        try {
            Map<String, String> state = safeState(cloudIslandsApi.addons().tableKeyValueBulkLoadIslandState(addonId, islandId, table).join());
            islandTableLoadSuccesses.incrementAndGet();
            return state;
        } catch (RuntimeException exception) {
            islandTableLoadFailures.incrementAndGet();
            logger.warning("Failed to bulk-load Satis core-api island table " + table + " for island " + islandId + " through " + ISLAND_TABLE_KEY_VALUE_BULK_LOAD_ENDPOINT + ": " + exception.getMessage());
            recordCoreStateFailure("island-table-load", exception);
            return Map.of();
        }
    }

    private Map<String, String> loadGlobalTablesForHydration(List<String> tables) {
        Map<String, String> state = new LinkedHashMap<>();
        for (String table : tables) {
            state.putAll(flattenTable(table, loadGlobalTable(table)));
        }
        return Map.copyOf(state);
    }

    private Map<String, String> loadIslandTablesForHydration(UUID islandId, List<String> tables) {
        Map<String, String> state = new LinkedHashMap<>();
        for (String table : tables) {
            state.putAll(flattenTable(table, loadIslandTable(islandId, table)));
        }
        return Map.copyOf(state);
    }

    private Map<String, String> flattenTable(String table, Map<String, String> values) {
        if (table == null || table.isBlank() || values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> state = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                state.put(IslandAddonService.tableStateKey(table, key), value);
            }
        });
        return Map.copyOf(state);
    }

    private Map<String, String> loadGlobalFlattenedStateFallback() {
        flattenedLoadFallbacks.incrementAndGet();
        try {
            return safeState(cloudIslandsApi.addons().state(addonId).join());
        } catch (RuntimeException exception) {
            logger.warning("Failed to read Satis core-api global table state: " + exception.getMessage());
            recordCoreStateFailure("global-state-load-fallback", exception);
            return Map.of();
        }
    }

    private Map<String, String> loadIslandFlattenedStateFallback(UUID islandId) {
        flattenedLoadFallbacks.incrementAndGet();
        try {
            return safeState(cloudIslandsApi.addons().islandState(addonId, islandId).join());
        } catch (RuntimeException exception) {
            logger.warning("Failed to read Satis core-api table state for island " + islandId + ": " + exception.getMessage());
            recordCoreStateFailure("island-state-load-fallback", exception);
            return Map.of();
        }
    }

    private Map<String, String> safeState(Map<String, String> state) {
        if (state == null || state.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        state.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    private boolean tableFeatureEnabled(String key) {
        if (key == null) {
            return false;
        }
        if (!key.startsWith(IslandAddonService.TABLE_STATE_KEY_PREFIX)) {
            return true;
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("factory_islands"))) {
            return stateFeatureEnabled("lifecycle");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("virtual_inventories"))) {
            return stateFeatureEnabled("storage");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("machines"))) {
            return stateFeatureEnabled("machines");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("resource_nodes"))) {
            return stateFeatureEnabled("resource-nodes");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("contracts"))) {
            return stateFeatureEnabled("contracts");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("island_unlocks"))) {
            return stateFeatureEnabled("research");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("market_personal_daily"))) {
            return stateFeatureEnabled("market");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("market_daily"))) {
            return stateFeatureEnabled("market");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("ledger"))) {
            return stateFeatureEnabled("storage") || stateFeatureEnabled("market") || stateFeatureEnabled("contracts");
        }
        if (key.startsWith(IslandAddonService.tableStateKeyPrefix("item_networks"))
                || key.startsWith(IslandAddonService.tableStateKeyPrefix("power_networks"))) {
            return stateFeatureEnabled("machines");
        }
        return true;
    }

    private boolean tableNameFeatureEnabled(String table) {
        String tableName = tableName(table);
        if (tableName.isBlank()) {
            return false;
        }
        return tableFeatureEnabled(IslandAddonService.tableStateKey(tableName, "__feature_gate__"));
    }

    private Map<String, Map<String, String>> enabledTables(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> filtered = new LinkedHashMap<>();
        tables.forEach((table, values) -> {
            if (tableNameFeatureEnabled(table) && values != null) {
                filtered.put(table, values);
            }
        });
        return filtered.isEmpty() ? Map.of() : Map.copyOf(filtered);
    }

    private boolean stateFeatureEnabled(String feature) {
        try {
            return featureEnabled.test(feature);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Map<String, String> state(Map<UUID, Map<String, String>> stateByIsland, UUID islandId) {
        return stateByIsland.computeIfAbsent(islandId, _ignored -> new LinkedHashMap<>());
    }

    private String islandJson(FactoryIsland island) {
        return "{"
                + field("islandUuid", island.islandUuid().toString()) + ","
                + field("ownerUuid", island.ownerUuid().toString()) + ","
                + number("tier", island.tier()) + ","
                + number("researchPoints", island.researchPoints()) + ","
                + number("reputation", island.reputation()) + ","
                + number("maintenanceDebt", island.maintenanceDebt()) + ","
                + field("maintenanceStatus", island.maintenanceStatus().name()) + ","
                + number("factoryScore", island.factoryScore()) + ","
                + number("lastMaintenanceAt", island.lastMaintenanceAt()) + ","
                + number("lastTickAt", island.lastTickAt()) + ","
                + number("emergencyContractsUsedToday", island.emergencyContractsUsedToday()) + ","
                + field("activeWorld", island.activeWorld()) + ","
                + number("activeCenterX", island.activeCenterX()) + ","
                + number("activeCenterY", island.activeCenterY()) + ","
                + number("activeCenterZ", island.activeCenterZ()) + ","
                + field("pendingMachineRemapWorld", island.pendingMachineRemapWorld()) + ","
                + number("pendingMachineRemapCenterX", island.pendingMachineRemapCenterX()) + ","
                + number("pendingMachineRemapCenterY", island.pendingMachineRemapCenterY()) + ","
                + number("pendingMachineRemapCenterZ", island.pendingMachineRemapCenterZ()) + ","
                + field("pendingResourceNodeRemapWorld", island.pendingResourceNodeRemapWorld()) + ","
                + number("pendingResourceNodeRemapCenterX", island.pendingResourceNodeRemapCenterX()) + ","
                + number("pendingResourceNodeRemapCenterY", island.pendingResourceNodeRemapCenterY()) + ","
                + number("pendingResourceNodeRemapCenterZ", island.pendingResourceNodeRemapCenterZ()) + ","
                + number("createdAt", island.createdAt()) + ","
                + number("updatedAt", island.updatedAt())
                + "}";
    }

    private FactoryIsland island(String json) {
        FactoryIsland island = new FactoryIsland(uuid(text(json, "islandUuid", "")), uuid(text(json, "ownerUuid", "")));
        island.tier(integer(json, "tier", 1));
        island.researchPoints(longValue(json, "researchPoints", 0L));
        island.reputation(longValue(json, "reputation", 0L));
        island.maintenanceDebt(longValue(json, "maintenanceDebt", 0L));
        island.maintenanceStatus(enumValue(MaintenanceStatus.class, text(json, "maintenanceStatus", "NORMAL"), MaintenanceStatus.NORMAL));
        island.factoryScore(longValue(json, "factoryScore", 0L));
        island.lastMaintenanceAt(longValue(json, "lastMaintenanceAt", 0L));
        island.lastTickAt(longValue(json, "lastTickAt", 0L));
        island.emergencyContractsUsedToday(integer(json, "emergencyContractsUsedToday", 0));
        island.activeWorld(text(json, "activeWorld", ""));
        island.activeCenterX(integer(json, "activeCenterX", 0));
        island.activeCenterY(integer(json, "activeCenterY", 0));
        island.activeCenterZ(integer(json, "activeCenterZ", 0));
        island.pendingMachineRemap(text(json, "pendingMachineRemapWorld", ""), integer(json, "pendingMachineRemapCenterX", 0), integer(json, "pendingMachineRemapCenterY", 0), integer(json, "pendingMachineRemapCenterZ", 0));
        island.pendingResourceNodeRemap(text(json, "pendingResourceNodeRemapWorld", ""), integer(json, "pendingResourceNodeRemapCenterX", 0), integer(json, "pendingResourceNodeRemapCenterY", 0), integer(json, "pendingResourceNodeRemapCenterZ", 0));
        island.createdAt(longValue(json, "createdAt", System.currentTimeMillis()));
        island.updatedAt(longValue(json, "updatedAt", 0L));
        return island;
    }

    private String machineJson(MachineInstance machine) {
        return "{"
                + field("machineId", machine.machineId().toString()) + ","
                + field("islandUuid", machine.islandUuid().toString()) + ","
                + field("ownerUuid", machine.ownerUuid().toString()) + ","
                + field("typeId", machine.typeId()) + ","
                + number("tier", machine.tier()) + ","
                + field("world", machine.world()) + ","
                + number("x", machine.x()) + ","
                + number("y", machine.y()) + ","
                + number("z", machine.z()) + ","
                + field("direction", machine.direction().name()) + ","
                + field("status", machine.status().name()) + ","
                + field("inputInventoryId", string(machine.inputInventoryId())) + ","
                + field("outputInventoryId", string(machine.outputInventoryId())) + ","
                + field("powerNetworkId", string(machine.powerNetworkId())) + ","
                + field("itemNetworkId", string(machine.itemNetworkId())) + ","
                + field("linkedResourceNodeId", string(machine.linkedResourceNodeId())) + ","
                + field("selectedRecipeId", machine.selectedRecipeId()) + ","
                + field("configJson", machine.configJson()) + ","
                + number("lastProcessAt", machine.lastProcessAt()) + ","
                + number("wear", machine.wear()) + ","
                + number("createdAt", machine.createdAt()) + ","
                + number("updatedAt", machine.updatedAt())
                + "}";
    }

    private MachineInstance machine(String json) {
        MachineInstance machine = new MachineInstance(
                uuid(text(json, "machineId", "")),
                uuid(text(json, "islandUuid", "")),
                uuid(text(json, "ownerUuid", "")),
                text(json, "typeId", ""),
                integer(json, "tier", 1),
                new BlockKey(text(json, "world", ""), integer(json, "x", 0), integer(json, "y", 0), integer(json, "z", 0))
        );
        machine.direction(enumValue(BlockFace.class, text(json, "direction", "NORTH"), BlockFace.NORTH));
        machine.status(MachineStatus.fromStoredValue(text(json, "status", "SLEEPING")));
        machine.inputInventoryId(uuidOrNull(text(json, "inputInventoryId", "")));
        machine.outputInventoryId(uuidOrNull(text(json, "outputInventoryId", "")));
        machine.powerNetworkId(uuidOrNull(text(json, "powerNetworkId", "")));
        machine.itemNetworkId(uuidOrNull(text(json, "itemNetworkId", "")));
        machine.linkedResourceNodeId(uuidOrNull(text(json, "linkedResourceNodeId", "")));
        machine.selectedRecipeId(blankToNull(text(json, "selectedRecipeId", "")));
        machine.configJson(text(json, "configJson", "{}"));
        machine.lastProcessAt(longValue(json, "lastProcessAt", 0L));
        machine.wear(decimal(json, "wear", 0.0D));
        machine.createdAt(longValue(json, "createdAt", System.currentTimeMillis()));
        machine.updatedAt(longValue(json, "updatedAt", 0L));
        return machine;
    }

    private String inventoryJson(VirtualInventory inventory) {
        StringBuilder items = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : inventory.items().entrySet()) {
            if (!first) {
                items.append(',');
            }
            first = false;
            items.append('"').append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        items.append('}');
        return "{"
                + field("inventoryId", inventory.inventoryId().toString()) + ","
                + field("islandUuid", inventory.islandUuid().toString()) + ","
                + field("holderType", inventory.holderType()) + ","
                + field("holderId", inventory.holderId()) + ","
                + number("capacity", inventory.capacity()) + ","
                + "\"items\":" + items
                + "}";
    }

    private VirtualInventory inventory(String json) {
        VirtualInventory inventory = new VirtualInventory(
                uuid(text(json, "inventoryId", "")),
                uuid(text(json, "islandUuid", "")),
                text(json, "holderType", ""),
                text(json, "holderId", ""),
                longValue(json, "capacity", 0L)
        );
        itemMap(objectBody(json, "items")).forEach(inventory::set);
        return inventory;
    }

    private String nodeJson(ResourceNode node) {
        return "{"
                + field("nodeId", node.nodeId().toString()) + ","
                + field("islandUuid", node.islandUuid().toString()) + ","
                + field("nodeType", node.nodeType()) + ","
                + field("resourceId", node.resourceId()) + ","
                + number("purity", node.purity()) + ","
                + number("remaining", node.remaining()) + ","
                + number("maxRemaining", node.maxRemaining()) + ","
                + number("regenPerHour", node.regenPerHour()) + ","
                + number("requiredMachineTier", node.requiredMachineTier()) + ","
                + field("world", node.world()) + ","
                + number("x", node.x()) + ","
                + number("y", node.y()) + ","
                + number("z", node.z()) + ","
                + number("createdAt", node.createdAt()) + ","
                + number("updatedAt", node.updatedAt())
                + "}";
    }

    private ResourceNode node(String json) {
        return new ResourceNode(
                uuid(text(json, "nodeId", "")),
                uuid(text(json, "islandUuid", "")),
                text(json, "nodeType", ""),
                text(json, "resourceId", ""),
                decimal(json, "purity", 1.0D),
                longValue(json, "remaining", 0L),
                longValue(json, "maxRemaining", 0L),
                longValue(json, "regenPerHour", 0L),
                integer(json, "requiredMachineTier", 1),
                new BlockKey(text(json, "world", ""), integer(json, "x", 0), integer(json, "y", 0), integer(json, "z", 0)),
                longValue(json, "createdAt", System.currentTimeMillis()),
                longValue(json, "updatedAt", 0L)
        );
    }

    private ItemNetwork itemNetwork(String json) {
        return new ItemNetwork(
                uuid(text(json, "networkId", "")),
                uuid(text(json, "islandUuid", "")),
                integer(json, "throughputPerMinute", 0),
                uuidOrNull(text(json, "bufferInventoryId", "")),
                Boolean.parseBoolean(text(json, "dirty", "false")),
                longValue(json, "updatedAt", System.currentTimeMillis()),
                uuidSet(text(json, "connectedMachineIds", "")),
                routes(text(json, "routes", ""))
        );
    }

    private PowerNetwork powerNetwork(String json) {
        return new PowerNetwork(
                uuid(text(json, "networkId", "")),
                uuid(text(json, "islandUuid", "")),
                decimal(json, "generationPerSecond", 0.0D),
                decimal(json, "consumptionPerSecond", 0.0D),
                decimal(json, "batteryStored", 0.0D),
                decimal(json, "batteryCapacity", 0.0D),
                decimal(json, "powerRatio", 0.0D),
                longValue(json, "updatedAt", System.currentTimeMillis()),
                uuidSet(text(json, "connectedMachineIds", ""))
        );
    }

    private DatabaseService.StoredContract contract(String json) {
        return new DatabaseService.StoredContract(
                uuid(text(json, "contractId", "")),
                uuid(text(json, "islandUuid", "")),
                text(json, "templateId", ""),
                text(json, "contractType", ""),
                integer(json, "tier", 1),
                text(json, "requiredJson", "{}"),
                text(json, "progressJson", "{}"),
                text(json, "rewardsJson", "{}"),
                text(json, "status", "ACTIVE"),
                longValue(json, "expiresAt", 0L)
        );
    }

    private String field(String key, String value) {
        return "\"" + key + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    private String number(String key, long value) {
        return "\"" + key + "\":" + value;
    }

    private String number(String key, double value) {
        return "\"" + key + "\":" + value;
    }

    private String string(UUID value) {
        return value == null ? "" : value.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String text(String json, String field, String fallback) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int index = start + needle.length();
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        while (index < json.length()) {
            char current = json.charAt(index++);
            if (escaped) {
                value.append(current);
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return value.toString();
            }
            value.append(current);
        }
        return fallback;
    }

    private long longValue(String json, String field, long fallback) {
        String value = scalar(json, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int integer(String json, String field, int fallback) {
        return (int) longValue(json, field, fallback);
    }

    private double decimal(String json, String field, double fallback) {
        String value = scalar(json, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String scalar(String json, String field) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int index = start + needle.length();
        int end = index;
        while (end < json.length() && "-0123456789.Ee".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        return end == index ? null : json.substring(index, end);
    }

    private String objectBody(String json, String field) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int objectStart = json.indexOf('{', start + needle.length());
        if (objectStart < 0) {
            return "";
        }
        int depth = 0;
        for (int index = objectStart; index < json.length(); index++) {
            char current = json.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(objectStart + 1, index);
                }
            }
        }
        return "";
    }

    private Map<String, Long> itemMap(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        Map<String, Long> items = new LinkedHashMap<>();
        for (String pair : splitPairs(body)) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon));
            try {
                items.put(key, Long.parseLong(pair.substring(colon + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return items;
    }

    private java.util.List<String> splitPairs(String body) {
        java.util.List<String> pairs = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int index = 0; index < body.length(); index++) {
            char value = body.charAt(index);
            if (escaped) {
                current.append(value);
                escaped = false;
                continue;
            }
            if (value == '\\') {
                current.append(value);
                escaped = true;
                continue;
            }
            if (value == '"') {
                quoted = !quoted;
            }
            if (value == ',' && !quoted) {
                pairs.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(value);
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString());
        }
        return pairs;
    }

    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Set<UUID> networkIndex(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        return uuidSet(text(json, "networkIds", ""));
    }

    private Set<UUID> uuidSet(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<UUID> values = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            if (!value.isBlank()) {
                values.add(uuid(value));
            }
        }
        return Set.copyOf(values);
    }

    private List<ItemNetwork.Route> routes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<ItemNetwork.Route> routes = new ArrayList<>();
        for (String part : csv.split(",")) {
            String value = part.trim();
            int separator = value.indexOf("->");
            if (separator <= 0 || separator >= value.length() - 2) {
                continue;
            }
            routes.add(new ItemNetwork.Route(
                    uuid(value.substring(0, separator)),
                    uuid(value.substring(separator + 2))
            ));
        }
        return List.copyOf(routes);
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
