package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;

public record CloudIslandsAddonSnapshot(
    String id,
    String displayName,
    String version,
    boolean enabled,
    Instant registeredAt,
    Map<String, Boolean> features,
    Map<String, String> metadata
) {
    public CloudIslandsAddonSnapshot {
        features = features == null ? Map.of() : Map.copyOf(features);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public CloudIslandsAddonSnapshot(String id, String displayName, String version, boolean enabled, Instant registeredAt) {
        this(id, displayName, version, enabled, registeredAt, Map.of(), Map.of());
    }

    public CloudIslandsAddonSnapshot(String id, String displayName, String version, boolean enabled, Instant registeredAt, Map<String, Boolean> features) {
        this(id, displayName, version, enabled, registeredAt, features, Map.of());
    }

    public boolean featureEnabled(String key) {
        return featureEnabled(key, true);
    }

    public boolean featureEnabled(String key, boolean fallback) {
        return features.getOrDefault(key, fallback);
    }
}
