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
        return featureEnabledIn(features, key, fallback);
    }

    public boolean configuredFeatureEnabled(String key) {
        return configuredFeatureEnabled(key, true);
    }

    public boolean configuredFeatureEnabled(String key, boolean fallback) {
        return featureEnabledIn(configuredFeatures, key, fallback);
    }

    private boolean featureEnabledIn(Map<String, Boolean> source, String key, boolean fallback) {
        String canonical = canonicalFeature(key);
        String requested = key == null ? "" : key.trim();
        boolean enabled = source.getOrDefault(canonical, source.getOrDefault(requested, fallback));
        if (!canonical.equals(requested) && source.containsKey(requested)) {
            enabled = enabled && source.get(requested);
        }
        for (Map.Entry<String, String> alias : featureAliases().entrySet()) {
            if (alias.getValue().equals(canonical) && source.containsKey(alias.getKey())) {
                enabled = enabled && source.get(alias.getKey());
            }
        }
        for (Map.Entry<String, String> dependency : featureDependencies().entrySet()) {
            if (dependency.getKey().equals(canonical) || dependency.getKey().equals(requested)) {
                enabled = enabled && linkedFeatureEnabledIn(source, dependency.getValue(), fallback);
            }
        }
        return enabled;
    }

    private boolean linkedFeatureEnabledIn(Map<String, Boolean> source, String canonical, boolean fallback) {
        boolean enabled = source.getOrDefault(canonical, fallback);
        for (Map.Entry<String, String> alias : featureAliases().entrySet()) {
            if (alias.getValue().equals(canonical) && source.containsKey(alias.getKey())) {
                enabled = enabled && source.get(alias.getKey());
            }
        }
        return enabled;
    }

    public Map<String, String> featureAliases() {
        return metadataPairs("feature-aliases");
    }

    public Map<String, String> featureDependencies() {
        Map<String, String> aliases = featureAliases();
        Map<String, String> dependencies = new HashMap<>();
        metadataPairs("feature-dependencies").forEach((feature, required) ->
            dependencies.put(feature.trim(), canonicalFeature(required, aliases)));
        return Map.copyOf(dependencies);
    }

    private String canonicalFeature(String key) {
        return canonicalFeature(key, featureAliases());
    }

    private String canonicalFeature(String key, Map<String, String> aliases) {
        if (key == null) {
            return "";
        }
        String requested = key.trim();
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
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
