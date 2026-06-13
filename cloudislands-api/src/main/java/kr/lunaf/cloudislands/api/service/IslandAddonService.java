package kr.lunaf.cloudislands.api.service;

import java.util.List;
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
        try {
            String id = addon.addonId();
            return id == null || id.isBlank() ? addon.getClass().getName() : id;
        } catch (RuntimeException ignored) {
            return addon.getClass().getName();
        }
    }

    private static String safeAddonDisplayName(CloudIslandsAddon addon, String id) {
        try {
            String displayName = addon.addonDisplayName();
            return displayName == null || displayName.isBlank() ? id : displayName;
        } catch (RuntimeException ignored) {
            return id;
        }
    }

    private static String safeAddonVersion(CloudIslandsAddon addon) {
        try {
            String version = addon.addonVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static boolean safeAddonEnabledByDefault(CloudIslandsAddon addon) {
        try {
            return addon.enabledByDefault();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Map<String, Boolean> safeAddonFeatures(CloudIslandsAddon addon) {
        try {
            return Map.copyOf(addon.addonFeatures());
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private static Map<String, String> safeAddonMetadata(CloudIslandsAddon addon) {
        try {
            return Map.copyOf(addon.addonMetadata());
        } catch (RuntimeException exception) {
            return Map.of("metadata-error", exception.getClass().getSimpleName());
        }
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

    default CompletableFuture<Boolean> isFeatureEnabled(String id, String feature) {
        return get(id).thenApply(addon -> addon
            .map(snapshot -> snapshot.enabled() && snapshot.featureEnabled(feature))
            .orElse(false));
    }
}
