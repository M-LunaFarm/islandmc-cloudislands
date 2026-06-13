package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    default CompletableFuture<Boolean> isFeatureEnabled(String id, String feature) {
        return get(id).thenApply(addon -> addon
            .map(snapshot -> snapshot.enabled() && snapshot.featureEnabled(feature))
            .orElse(false));
    }
}
