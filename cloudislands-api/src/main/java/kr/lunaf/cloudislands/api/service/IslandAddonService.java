package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public interface IslandAddonService {
    default CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled) {
        return register(id, displayName, version, enabled, Map.of());
    }
    default CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled, Map<String, Boolean> features) {
        return register(id, displayName, version, enabled, features, Map.of());
    }
    CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled, Map<String, Boolean> features, Map<String, String> metadata);

    default CompletableFuture<CloudIslandsAddonSnapshot> register(CloudIslandsAddon addon) {
        String id = safeAddonId(addon);
        return register(id, safeAddonDisplayName(addon, id), safeAddonVersion(addon), safeAddonEnabledByDefault(addon), safeAddonFeatures(addon), safeAddonMetadata(addon))
            .thenApply(snapshot -> {
                try {
                    addon.onAddonRegistered(snapshot);
                } catch (RuntimeException ignored) {
                    // Addon callbacks must not break the registry path.
                }
                return snapshot;
            });
    }

    private static String safeAddonId(CloudIslandsAddon addon) {
        if (addon == null) {
            return fallbackAddonId(addon);
        }
        try {
            String id = addon.addonId();
            return id == null || id.isBlank() ? addon.getClass().getName() : id;
        } catch (RuntimeException ignored) {
            return fallbackAddonId(addon);
        }
    }

    private static String fallbackAddonId(CloudIslandsAddon addon) {
        return addon == null ? "null-addon" : addon.getClass().getName();
    }

    private static String safeAddonDisplayName(CloudIslandsAddon addon, String id) {
        if (addon == null) {
            return id;
        }
        try {
            String displayName = addon.addonDisplayName();
            return displayName == null || displayName.isBlank() ? id : displayName;
        } catch (RuntimeException ignored) {
            return id;
        }
    }

    private static String safeAddonVersion(CloudIslandsAddon addon) {
        if (addon == null) {
            return "unknown";
        }
        try {
            String version = addon.addonVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static boolean safeAddonEnabledByDefault(CloudIslandsAddon addon) {
        if (addon == null) {
            return false;
        }
        try {
            return addon.enabledByDefault();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Map<String, Boolean> safeAddonFeatures(CloudIslandsAddon addon) {
        if (addon == null) {
            return Map.of();
        }
        try {
            return copyBooleanMap(addon.addonFeatures());
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Map<String, String> safeAddonMetadata(CloudIslandsAddon addon) {
        if (addon == null) {
            return Map.of("metadata-error", "NullAddon");
        }
        try {
            return copyStringMap(addon.addonMetadata());
        } catch (RuntimeException exception) {
            return Map.of("metadata-error", exception.getClass().getSimpleName());
        }
    }

    private static Map<String, Boolean> copyBooleanMap(Map<String, Boolean> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Boolean> copy = new HashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    private static Map<String, String> copyStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new HashMap<>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, value);
            }
        });
        return Map.copyOf(copy);
    }

    CompletableFuture<Void> unregister(String id);
    CompletableFuture<Optional<CloudIslandsAddonSnapshot>> get(String id);
    CompletableFuture<List<CloudIslandsAddonSnapshot>> list();
    CompletableFuture<Boolean> isEnabled(String id);

    default CompletableFuture<Optional<CloudIslandsAddonSnapshot>> refresh(String id) {
        return get(id);
    }

    default CompletableFuture<List<CloudIslandsAddonSnapshot>> refreshAll() {
        return list();
    }

    default CompletableFuture<Optional<CloudIslandsAddonSnapshot>> setEnabled(String id, boolean enabled) {
        return refresh(id);
    }

    default CompletableFuture<Optional<CloudIslandsAddonSnapshot>> setFeature(String id, String feature, boolean enabled) {
        return refresh(id);
    }

    default CompletableFuture<Map<String, Boolean>> features(String id) {
        return get(id).thenApply(addon -> addon.map(CloudIslandsAddonSnapshot::features).orElse(Map.of()));
    }

    default CompletableFuture<Map<String, Boolean>> configuredFeatures(String id) {
        return get(id).thenApply(addon -> addon.map(CloudIslandsAddonSnapshot::configuredFeatures).orElse(Map.of()));
    }

    default CompletableFuture<Map<String, String>> metadata(String id) {
        return get(id).thenApply(addon -> addon.map(CloudIslandsAddonSnapshot::metadata).orElse(Map.of()));
    }

    default CompletableFuture<Map<String, String>> state(String id) {
        return CompletableFuture.completedFuture(Map.of());
    }

    default CompletableFuture<Optional<String>> state(String id, String key) {
        if (key == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return state(id).thenApply(values -> Optional.ofNullable(values.get(key)));
    }

    default CompletableFuture<Map<String, String>> putState(String id, Map<String, String> values) {
        return state(id);
    }

    default CompletableFuture<Map<String, String>> putState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
        Map<String, String> merged = new HashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    merged.put(key.trim(), value);
                }
            });
        }
        merged.putAll(tableStateValues(tables));
        if (merged.isEmpty()) {
            return state(id);
        }
        return putState(id, Map.copyOf(merged));
    }

    default CompletableFuture<Map<String, String>> saveState(String id, Map<String, String> values) {
        return putState(id, values);
    }

    default CompletableFuture<Map<String, String>> saveState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return putState(id, values, tables);
    }

    default CompletableFuture<Map<String, String>> bulkSaveState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return saveState(id, values, tables);
    }

    default CompletableFuture<Map<String, String>> tableKeyValueBulkSaveState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return bulkSaveState(id, values, tables);
    }

    default CompletableFuture<Map<String, String>> bulkSaveTableKeyValueState(String id, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveState(id, values, tables);
    }

    default CompletableFuture<Map<String, String>> putTableState(String id, String table, Map<String, String> values) {
        return putState(id, tableStateValues(table, values));
    }

    default CompletableFuture<Map<String, String>> saveTableState(String id, String table, Map<String, String> values) {
        return putTableState(id, table, values);
    }

    default CompletableFuture<Map<String, String>> replaceTableState(String id, String table, Map<String, String> values) {
        return clearTableState(id, table).thenCompose(_cleared -> putTableState(id, table, values));
    }

    default CompletableFuture<Map<String, String>> clearTableState(String id, String table) {
        return state(id).thenCompose(values -> {
            String prefix = tableStatePrefix(table);
            CompletableFuture<Map<String, String>> chain = CompletableFuture.completedFuture(values);
            for (String key : values.keySet()) {
                if (key != null && key.startsWith(prefix)) {
                    chain = chain.thenCompose(_ignored -> removeState(id, key));
                }
            }
            return chain;
        });
    }

    default CompletableFuture<Optional<String>> putState(String id, String key, String value) {
        if (key == null || value == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return putState(id, Map.of(key, value)).thenApply(values -> Optional.ofNullable(values.get(key)));
    }

    default CompletableFuture<Map<String, String>> removeState(String id, String key) {
        return state(id);
    }

    default CompletableFuture<Void> clearState(String id) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletableFuture<Map<String, String>> islandState(String id, UUID islandId) {
        return CompletableFuture.completedFuture(Map.of());
    }

    default CompletableFuture<Optional<String>> islandState(String id, UUID islandId, String key) {
        if (key == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return islandState(id, islandId).thenApply(values -> Optional.ofNullable(values.get(key)));
    }

    default CompletableFuture<Map<String, String>> putIslandState(String id, UUID islandId, Map<String, String> values) {
        return islandState(id, islandId);
    }

    default CompletableFuture<Map<String, String>> putIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        Map<String, String> merged = new HashMap<>();
        if (values != null) {
            values.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    merged.put(key.trim(), value);
                }
            });
        }
        merged.putAll(tableStateValues(tables));
        if (merged.isEmpty()) {
            return islandState(id, islandId);
        }
        return putIslandState(id, islandId, Map.copyOf(merged));
    }

    default CompletableFuture<Map<String, String>> saveIslandState(String id, UUID islandId, Map<String, String> values) {
        return putIslandState(id, islandId, values);
    }

    default CompletableFuture<Map<String, String>> saveIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return putIslandState(id, islandId, values, tables);
    }

    default CompletableFuture<Map<String, String>> bulkSaveIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return saveIslandState(id, islandId, values, tables);
    }

    default CompletableFuture<Map<String, String>> tableKeyValueBulkSaveIslandState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return bulkSaveIslandState(id, islandId, values, tables);
    }

    default CompletableFuture<Map<String, String>> bulkSaveIslandTableKeyValueState(String id, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveIslandState(id, islandId, values, tables);
    }

    default CompletableFuture<Map<String, String>> putIslandTableState(String id, UUID islandId, String table, Map<String, String> values) {
        return putIslandState(id, islandId, tableStateValues(table, values));
    }

    default CompletableFuture<Map<String, String>> saveIslandTableState(String id, UUID islandId, String table, Map<String, String> values) {
        return putIslandTableState(id, islandId, table, values);
    }

    default CompletableFuture<Map<String, String>> replaceIslandTableState(String id, UUID islandId, String table, Map<String, String> values) {
        return clearIslandTableState(id, islandId, table).thenCompose(_cleared -> putIslandTableState(id, islandId, table, values));
    }

    default CompletableFuture<Map<String, String>> clearIslandTableState(String id, UUID islandId, String table) {
        return islandState(id, islandId).thenCompose(values -> {
            String prefix = tableStatePrefix(table);
            CompletableFuture<Map<String, String>> chain = CompletableFuture.completedFuture(values);
            for (String key : values.keySet()) {
                if (key != null && key.startsWith(prefix)) {
                    chain = chain.thenCompose(_ignored -> removeIslandState(id, islandId, key));
                }
            }
            return chain;
        });
    }

    default CompletableFuture<Optional<String>> putIslandState(String id, UUID islandId, String key, String value) {
        if (key == null || value == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return putIslandState(id, islandId, Map.of(key, value)).thenApply(values -> Optional.ofNullable(values.get(key)));
    }

    default CompletableFuture<Map<String, String>> removeIslandState(String id, UUID islandId, String key) {
        return islandState(id, islandId);
    }

    default CompletableFuture<Void> clearIslandState(String id, UUID islandId) {
        return CompletableFuture.completedFuture(null);
    }

    default CompletableFuture<Boolean> isFeatureEnabled(String id, String feature) {
        return get(id).thenApply(addon -> addon
            .map(snapshot -> snapshot.enabled() && snapshot.featureEnabled(feature))
            .orElse(false));
    }

    private static Map<String, String> tableStateValues(String table, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        String safeTable = safeTableName(table);
        Map<String, String> state = new HashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                state.put(tableStatePrefix(safeTable) + key.trim(), value);
            }
        });
        return Map.copyOf(state);
    }

    private static Map<String, String> tableStateValues(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return Map.of();
        }
        Map<String, String> state = new HashMap<>();
        tables.forEach((table, values) -> state.putAll(tableStateValues(table, values)));
        return Map.copyOf(state);
    }

    private static String tableStatePrefix(String table) {
        return "table/" + safeTableName(table) + "/";
    }

    private static String safeTableName(String table) {
        String value = table == null ? "" : table.trim();
        if (value.startsWith("table/")) {
            value = value.substring("table/".length());
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Addon state table is required");
        }
        return value;
    }
}
