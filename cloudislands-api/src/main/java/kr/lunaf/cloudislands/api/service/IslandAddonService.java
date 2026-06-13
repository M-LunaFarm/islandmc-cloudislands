package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public interface IslandAddonService {
    default CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled) {
        return register(id, displayName, version, enabled, Map.of());
    }
    default CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled, Map<String, Boolean> features) {
        return register(id, displayName, version, enabled, features, Map.of());
    }
    CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled, Map<String, Boolean> features, Map<String, String> metadata);
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

    default CompletableFuture<Map<String, Boolean>> features(String id) {
        return get(id).thenApply(addon -> addon.map(CloudIslandsAddonSnapshot::features).orElse(Map.of()));
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
