package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;

public record CloudIslandsAddonSnapshot(
    String id,
    String displayName,
    String version,
    boolean enabled,
    Instant registeredAt,
    Map<String, Boolean> features
) {
    public CloudIslandsAddonSnapshot {
        features = features == null ? Map.of() : Map.copyOf(features);
    }

    public CloudIslandsAddonSnapshot(String id, String displayName, String version, boolean enabled, Instant registeredAt) {
        this(id, displayName, version, enabled, registeredAt, Map.of());
    }

    public boolean featureEnabled(String key) {
        return featureEnabled(key, true);
    }

    public boolean featureEnabled(String key, boolean fallback) {
        return features.getOrDefault(key, fallback);
    }
}
