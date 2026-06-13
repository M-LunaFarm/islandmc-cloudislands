package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.HashMap;
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
        configuredFeatures = copyBooleanMap(configuredFeatures);
        features = copyBooleanMap(features);
        metadata = copyStringMap(metadata);
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
        String requested = key == null ? "" : key.trim();
        boolean enabled = features.getOrDefault(canonical, features.getOrDefault(requested, fallback));
        if (!canonical.equals(requested) && features.containsKey(requested)) {
            enabled = enabled && features.get(requested);
        }
        for (Map.Entry<String, String> alias : featureAliases().entrySet()) {
            if (alias.getValue().equals(canonical) && features.containsKey(alias.getKey())) {
                enabled = enabled && features.get(alias.getKey());
            }
        }
        return enabled;
    }

    public Map<String, String> featureAliases() {
        return metadataPairs("feature-aliases");
    }

    public Map<String, String> featureDependencies() {
        Map<String, String> dependencies = new HashMap<>();
        metadataPairs("feature-dependencies").forEach((feature, required) ->
            dependencies.put(canonicalFeature(feature), canonicalFeature(required)));
        return Map.copyOf(dependencies);
    }

    private String canonicalFeature(String key) {
        if (key == null) {
            return "";
        }
        String requested = key.trim();
        for (Map.Entry<String, String> alias : featureAliases().entrySet()) {
            if (alias.getKey().equals(requested)) {
                return alias.getValue();
            }
        }
        return requested;
    }

    private Map<String, String> metadataPairs(String key) {
        String source = metadata.getOrDefault(key, "");
        if (source.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        for (String pair : source.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String left = parts[0].trim();
            String right = parts[1].trim();
            if (!left.isBlank() && !right.isBlank()) {
                values.put(left, right);
            }
        }
        return Map.copyOf(values);
    }
}
