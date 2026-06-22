package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;

public final class JdkAddonStateClient implements AddonStateClient {
    private final JdkCoreApiClient core;

    public JdkAddonStateClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<Map<String, String>> state(String addonId) {
        return core.post("/v1/addons/state", CoreJsonPayload.object("addonId", safeAddonId(addonId)))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putState(String addonId, Map<String, String> values) {
        return core.postWithResultBody("/v1/addons/state/bulk", CoreJsonPayload.object(
                "addonId", safeAddonId(addonId),
                "values", CoreJsonPayload.stringMap(safeValues(values))
            ))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> saveState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return core.postWithResultBody("/v1/addons/state/save", statePayload(addonId, values, tables))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return core.postWithResultBody(AddonStateBulkSaveRequest.GLOBAL_ENDPOINT, statePayload(addonId, values, tables))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableBulkState(String addonId, Map<String, Map<String, String>> tables) {
        return core.postWithResultBody("/v1/addons/state/table/bulk", CoreJsonPayload.object(
                "addonId", safeAddonId(addonId),
                "tables", CoreJsonPayload.tableMap(safeTables(tables))
            ))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadState(String addonId, String table) {
        return core.post(AddonStateBulkLoadRequest.GLOBAL_ENDPOINT, CoreJsonPayload.object("addonId", safeAddonId(addonId), "table", safeTable(table)))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putTableState(String addonId, String table, Map<String, String> values) {
        return core.postWithResultBody("/v1/addons/state/table/bulk", tablePayload(addonId, table, values))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> replaceTableState(String addonId, String table, Map<String, String> values) {
        return core.postWithResultBody("/v1/addons/state/table/replace", tablePayload(addonId, table, values))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> clearTableState(String addonId, String table) {
        return core.postWithResultBody("/v1/addons/state/table/clear", CoreJsonPayload.object("addonId", safeAddonId(addonId), "table", safeTable(table)))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> removeState(String addonId, String key) {
        return core.postWithResultBody("/v1/addons/state/remove", CoreJsonPayload.object("addonId", safeAddonId(addonId), "key", safeKey(key)))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Void> clearState(String addonId) {
        return core.postWithResultBody("/v1/addons/state/clear", CoreJsonPayload.object("addonId", safeAddonId(addonId)))
            .thenApply(_body -> (Void) null);
    }

    @Override
    public CompletableFuture<Map<String, String>> islandState(String addonId, UUID islandId) {
        return core.post("/v1/addons/islands/state", islandPayload(addonId, islandId))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putIslandState(String addonId, UUID islandId, Map<String, String> values) {
        return core.postWithResultBody("/v1/addons/islands/state/bulk", CoreJsonPayload.object(
                "addonId", safeAddonId(addonId),
                "islandId", requireIslandId(islandId),
                "values", CoreJsonPayload.stringMap(safeValues(values))
            ))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> saveIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return core.postWithResultBody("/v1/addons/islands/state/save", islandStatePayload(addonId, islandId, values, tables))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkSaveIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return core.postWithResultBody(AddonStateBulkSaveRequest.ISLAND_ENDPOINT, islandStatePayload(addonId, islandId, values, tables))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableBulkIslandState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return core.postWithResultBody("/v1/addons/islands/state/table/bulk", CoreJsonPayload.object(
                "addonId", safeAddonId(addonId),
                "islandId", requireIslandId(islandId),
                "tables", CoreJsonPayload.tableMap(safeTables(tables))
            ))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadIslandState(String addonId, UUID islandId, String table) {
        return core.post(AddonStateBulkLoadRequest.ISLAND_ENDPOINT, CoreJsonPayload.object(
                "addonId", safeAddonId(addonId),
                "islandId", requireIslandId(islandId),
                "table", safeTable(table)
            ))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> putIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return core.postWithResultBody("/v1/addons/islands/state/table/bulk", islandTablePayload(addonId, islandId, table, values))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> replaceIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return core.postWithResultBody("/v1/addons/islands/state/table/replace", islandTablePayload(addonId, islandId, table, values))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> clearIslandTableState(String addonId, UUID islandId, String table) {
        return core.postWithResultBody("/v1/addons/islands/state/table/clear", CoreJsonPayload.object(
                "addonId", safeAddonId(addonId),
                "islandId", requireIslandId(islandId),
                "table", safeTable(table)
            ))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Map<String, String>> removeIslandState(String addonId, UUID islandId, String key) {
        return core.postWithResultBody("/v1/addons/islands/state/remove", CoreJsonPayload.object(
                "addonId", safeAddonId(addonId),
                "islandId", requireIslandId(islandId),
                "key", safeKey(key)
            ))
            .thenApply(CoreAddonStateJson::values);
    }

    @Override
    public CompletableFuture<Void> clearIslandState(String addonId, UUID islandId) {
        return core.postWithResultBody("/v1/addons/islands/state/clear", islandPayload(addonId, islandId))
            .thenApply(_body -> (Void) null);
    }

    private String statePayload(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return CoreJsonPayload.object(
            "addonId", safeAddonId(addonId),
            "values", CoreJsonPayload.stringMap(safeValues(values)),
            "tables", CoreJsonPayload.tableMap(safeTables(tables))
        );
    }

    private String islandStatePayload(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return CoreJsonPayload.object(
            "addonId", safeAddonId(addonId),
            "islandId", requireIslandId(islandId),
            "values", CoreJsonPayload.stringMap(safeValues(values)),
            "tables", CoreJsonPayload.tableMap(safeTables(tables))
        );
    }

    private String tablePayload(String addonId, String table, Map<String, String> values) {
        return CoreJsonPayload.object(
            "addonId", safeAddonId(addonId),
            "table", safeTable(table),
            "values", CoreJsonPayload.stringMap(safeValues(values))
        );
    }

    private String islandPayload(String addonId, UUID islandId) {
        return CoreJsonPayload.object("addonId", safeAddonId(addonId), "islandId", requireIslandId(islandId));
    }

    private String islandTablePayload(String addonId, UUID islandId, String table, Map<String, String> values) {
        return CoreJsonPayload.object(
            "addonId", safeAddonId(addonId),
            "islandId", requireIslandId(islandId),
            "table", safeTable(table),
            "values", CoreJsonPayload.stringMap(safeValues(values))
        );
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
        if (islandId == null || islandId.equals(new UUID(0L, 0L))) {
            throw new IllegalArgumentException("islandId is required");
        }
        return islandId;
    }

    private static Map<String, String> safeValues(Map<String, String> values) {
        return values == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, Map<String, String>> safeTables(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return Map.of();
        }
        return tables.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
            .collect(Collectors.toUnmodifiableMap(
                entry -> entry.getKey().trim(),
                entry -> safeValues(entry.getValue())
            ));
    }
}
