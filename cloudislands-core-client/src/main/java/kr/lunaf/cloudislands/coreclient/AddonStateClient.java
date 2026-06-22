package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AddonStateClient {
    CompletableFuture<Map<String, String>> state(String addonId);
    CompletableFuture<Map<String, String>> putState(String addonId, Map<String, String> values);
    CompletableFuture<Map<String, String>> saveState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables);
    CompletableFuture<Map<String, String>> tableKeyValueBulkSaveState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables);
    CompletableFuture<Map<String, String>> tableBulkState(String addonId, Map<String, Map<String, String>> tables);
    CompletableFuture<Map<String, String>> tableKeyValueBulkLoadState(String addonId, String table);
    CompletableFuture<Map<String, String>> putTableState(String addonId, String table, Map<String, String> values);
    CompletableFuture<Map<String, String>> replaceTableState(String addonId, String table, Map<String, String> values);
    CompletableFuture<Map<String, String>> clearTableState(String addonId, String table);
    CompletableFuture<Map<String, String>> removeState(String addonId, String key);
    CompletableFuture<Void> clearState(String addonId);
    CompletableFuture<Map<String, String>> islandState(String addonId, UUID islandId);
    CompletableFuture<Map<String, String>> putIslandState(String addonId, UUID islandId, Map<String, String> values);
    CompletableFuture<Map<String, String>> saveIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables);
    CompletableFuture<Map<String, String>> tableKeyValueBulkSaveIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables);
    CompletableFuture<Map<String, String>> tableBulkIslandState(String addonId, UUID islandId, Map<String, Map<String, String>> tables);
    CompletableFuture<Map<String, String>> tableKeyValueBulkLoadIslandState(String addonId, UUID islandId, String table);
    CompletableFuture<Map<String, String>> putIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values);
    CompletableFuture<Map<String, String>> replaceIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values);
    CompletableFuture<Map<String, String>> clearIslandTableState(String addonId, UUID islandId, String table);
    CompletableFuture<Map<String, String>> removeIslandState(String addonId, UUID islandId, String key);
    CompletableFuture<Void> clearIslandState(String addonId, UUID islandId);
}
