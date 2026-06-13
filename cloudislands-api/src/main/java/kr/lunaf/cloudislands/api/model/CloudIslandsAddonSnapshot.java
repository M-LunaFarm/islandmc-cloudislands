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
        id = id == null || id.isBlank() ? "unknown-addon" : id;
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
        version = version == null || version.isBlank() ? "unknown" : version;
        registeredAt = registeredAt == null ? Instant.EPOCH : registeredAt;
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
        String canonical = canonicalFeature(key);
        boolean enabled = features.getOrDefault(canonical, features.getOrDefault(key, fallback));
        if (!canonical.equals(key) && features.containsKey(key)) {
            enabled = enabled && features.get(key);
        }
        for (String pair : metadata.getOrDefault("feature-aliases", "").split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2 && parts[1].equals(canonical) && features.containsKey(parts[0])) {
                enabled = enabled && features.get(parts[0]);
            }
        }
        return enabled;
    }

    private String canonicalFeature(String key) {
        if (key == null) {
            return "";
        }
        for (String pair : metadata.getOrDefault("feature-aliases", "").split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length == 2 && parts[0].equals(key)) {
                return parts[1];
            }
        }
        return key;
    }
}
