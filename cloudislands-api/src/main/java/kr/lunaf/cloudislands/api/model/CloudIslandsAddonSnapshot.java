package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;

public record CloudIslandsAddonSnapshot(
    String id,
    String displayName,
    String version,
    boolean enabled,
    Instant registeredAt,
    Instant updatedAt,
    Map<String, Boolean> configuredFeatures,
    Map<String, Boolean> features,
    Map<String, String> metadata
) {
    public CloudIslandsAddonSnapshot {
        updatedAt = updatedAt == null ? registeredAt : updatedAt;
        configuredFeatures = configuredFeatures == null ? Map.of() : Map.copyOf(configuredFeatures);
        features = features == null ? Map.of() : Map.copyOf(features);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public CloudIslandsAddonSnapshot(String id, String displayName, String version, boolean enabled, Instant registeredAt) {
        this(id, displayName, version, enabled, registeredAt, registeredAt, Map.of(), Map.of(), Map.of());
    }

    public CloudIslandsAddonSnapshot(String id, String displayName, String version, boolean enabled, Instant registeredAt, Map<String, Boolean> features) {
        this(id, displayName, version, enabled, registeredAt, registeredAt, features, features, Map.of());
    }

    public CloudIslandsAddonSnapshot(String id, String displayName, String version, boolean enabled, Instant registeredAt, Map<String, Boolean> features, Map<String, String> metadata) {
        this(id, displayName, version, enabled, registeredAt, registeredAt, features, features, metadata);
    }

    public CloudIslandsAddonSnapshot(String id, String displayName, String version, boolean enabled, Instant registeredAt, Instant updatedAt, Map<String, Boolean> features, Map<String, String> metadata) {
        this(id, displayName, version, enabled, registeredAt, updatedAt, features, features, metadata);
    }

    public boolean featureEnabled(String key) {
        return featureEnabled(key, true);
    }

    public boolean featureEnabled(String key, boolean fallback) {
        return features.getOrDefault(key, fallback);
    }
}
