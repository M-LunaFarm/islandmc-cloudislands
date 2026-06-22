package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreAddonStateClient implements AddonStateClient {
    private final CoreApiClient delegate;

    public CoreAddonStateClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<Map<String, String>> state(String addonId) {
        return delegate.addonState(safeAddonId(addonId)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putState(String addonId, Map<String, String> values) {
        return delegate.putAddonState(safeAddonId(addonId), safeValues(values)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> saveState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return delegate.saveAddonState(safeAddonId(addonId), safeValues(values), safeTables(tables)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return delegate.tableKeyValueBulkSaveAddonState(safeAddonId(addonId), safeValues(values), safeTables(tables)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableBulkState(String addonId, Map<String, Map<String, String>> tables) {
        return delegate.tableBulkAddonState(safeAddonId(addonId), safeTables(tables)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadState(String addonId, String table) {
        return delegate.tableKeyValueBulkLoadAddonState(safeAddonId(addonId), safeTable(table)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putTableState(String addonId, String table, Map<String, String> values) {
        return delegate.putAddonTableState(safeAddonId(addonId), safeTable(table), safeValues(values)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> replaceTableState(String addonId, String table, Map<String, String> values) {
        return delegate.replaceAddonTableState(safeAddonId(addonId), safeTable(table), safeValues(values)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> clearTableState(String addonId, String table) {
        return delegate.clearAddonTableState(safeAddonId(addonId), safeTable(table)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> removeState(String addonId, String key) {
        return delegate.removeAddonState(safeAddonId(addonId), safeKey(key)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Void> clearState(String addonId) {
        return delegate.clearAddonState(safeAddonId(addonId)).thenApply(_body -> (Void) null);
    }

    @Override
    public CompletableFuture<Map<String, String>> islandState(String addonId, UUID islandId) {
        return delegate.addonIslandState(safeAddonId(addonId), requireIslandId(islandId)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putIslandState(String addonId, UUID islandId, Map<String, String> values) {
        return delegate.putAddonIslandState(safeAddonId(addonId), requireIslandId(islandId), safeValues(values)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> saveIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return delegate.saveAddonIslandState(safeAddonId(addonId), requireIslandId(islandId), safeValues(values), safeTables(tables)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return delegate.tableKeyValueBulkSaveAddonIslandState(safeAddonId(addonId), requireIslandId(islandId), safeValues(values), safeTables(tables)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableBulkIslandState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return delegate.tableBulkAddonIslandState(safeAddonId(addonId), requireIslandId(islandId), safeTables(tables)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadIslandState(String addonId, UUID islandId, String table) {
        return delegate.tableKeyValueBulkLoadAddonIslandState(safeAddonId(addonId), requireIslandId(islandId), safeTable(table)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return delegate.putAddonIslandTableState(safeAddonId(addonId), requireIslandId(islandId), safeTable(table), safeValues(values)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> replaceIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return delegate.replaceAddonIslandTableState(safeAddonId(addonId), requireIslandId(islandId), safeTable(table), safeValues(values)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> clearIslandTableState(String addonId, UUID islandId, String table) {
        return delegate.clearAddonIslandTableState(safeAddonId(addonId), requireIslandId(islandId), safeTable(table)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> removeIslandState(String addonId, UUID islandId, String key) {
        return delegate.removeAddonIslandState(safeAddonId(addonId), requireIslandId(islandId), safeKey(key)).thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Void> clearIslandState(String addonId, UUID islandId) {
        return delegate.clearAddonIslandState(safeAddonId(addonId), requireIslandId(islandId)).thenApply(_body -> (Void) null);
    }

    private static String safeAddonId(String addonId) {
        if (addonId == null || addonId.isBlank()) {
            throw new IllegalArgumentException("addonId is required");
        }
        return addonId.trim();
    }

    private static String safeTable(String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        return table.trim();
    }

    private static String safeKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        return key.trim();
    }

    private static UUID requireIslandId(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return islandId;
    }

    private static Map<String, String> safeValues(Map<String, String> values) {
        return values == null ? Map.of() : Map.copyOf(values);
    }

    private static Map<String, Map<String, String>> safeTables(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return Map.of();
        }
        return tables.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                entry -> entry.getKey().trim(),
                entry -> safeValues(entry.getValue())
            ));
    }
}
