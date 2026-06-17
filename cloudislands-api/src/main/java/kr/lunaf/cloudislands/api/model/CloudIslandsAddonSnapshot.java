package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        return featureEnabledIn(features, key, fallback, true);
    }

    public boolean configuredFeatureEnabled(String key) {
        return configuredFeatureEnabled(key, true);
    }

    public boolean configuredFeatureEnabled(String key, boolean fallback) {
        return featureEnabledIn(configuredFeatures, key, fallback, false);
    }

    public boolean acceptsRuntimeFeature(String key) {
        return acceptsRuntimeFeature(key, true);
    }

    public boolean acceptsRuntimeFeature(String key, boolean fallback) {
        return enabled && featureEnabled(key, fallback);
    }

    public boolean runtimeFeatureEnabled(String key) {
        return acceptsRuntimeFeature(key);
    }

    public boolean runtimeFeatureEnabled(String key, boolean fallback) {
        return acceptsRuntimeFeature(key, fallback);
    }

    public Map<String, Boolean> resolvedConfiguredFeatures() {
        return resolvedFeatureStates(configuredFeatures, false);
    }

    public Map<String, Boolean> resolvedRuntimeFeatures() {
        return resolvedFeatureStates(features, true);
    }

    public boolean commandsEnabled() {
        return acceptsRuntimeFeature("commands", true);
    }

    public boolean guiEnabled() {
        return acceptsRuntimeFeature("gui", true);
    }

    public boolean placeholdersEnabled() {
        return acceptsRuntimeFeature("placeholders", true);
    }

    public boolean lifecycleEventsEnabled() {
        return acceptsRuntimeFeature("lifecycle", true);
    }

    public boolean addonStateWritesEnabled() {
        return acceptsRuntimeFeature("addon-state", true);
    }

    public boolean routeEventsEnabled() {
        return acceptsRuntimeFeature("route-events", true);
    }

    private boolean featureEnabledIn(Map<String, Boolean> source, String key, boolean fallback, boolean includeDependencies) {
        String canonical = canonicalFeature(key);
        String requested = key == null ? "" : key.trim();
        boolean enabled = source.getOrDefault(canonical, source.getOrDefault(requested, fallback));
        if (!canonical.equals(requested) && source.containsKey(requested)) {
            enabled = enabled && source.get(requested);
        }
        for (Map.Entry<String, String> alias : featureAliases().entrySet()) {
            if (canonical.equals(alias.getValue()) && source.containsKey(alias.getKey())) {
                enabled = enabled && source.get(alias.getKey());
            }
        }
        if (!includeDependencies) {
            return enabled;
        }
        for (Map.Entry<String, String> dependency : featureDependencies().entrySet()) {
            if (dependency.getKey().equals(canonical) || dependency.getKey().equals(requested)) {
                enabled = enabled && linkedDependenciesEnabledIn(source, dependency.getValue(), fallback, new java.util.HashSet<>());
            }
        }
        return enabled;
    }

    private boolean linkedDependenciesEnabledIn(Map<String, Boolean> source, String required, boolean fallback, java.util.Set<String> visited) {
        for (String dependency : dependencyParts(required)) {
            if (!linkedFeatureEnabledIn(source, dependency, fallback, visited)) {
                return false;
            }
        }
        return true;
    }

    private boolean linkedFeatureEnabledIn(Map<String, Boolean> source, String required, boolean fallback, java.util.Set<String> visited) {
        Map<String, String> aliases = featureAliases();
        String canonical = canonicalFeature(required, aliases);
        String requested = required == null ? "" : required.trim();
        if (canonical.isBlank() || !visited.add(canonical)) {
            return true;
        }
        boolean enabled = source.getOrDefault(canonical, source.getOrDefault(requested, fallback));
        for (Map.Entry<String, String> alias : aliases.entrySet()) {
            if (canonical.equals(alias.getValue()) && source.containsKey(alias.getKey())) {
                enabled = enabled && source.get(alias.getKey());
            }
        }
        for (Map.Entry<String, String> dependency : featureDependencies().entrySet()) {
            if (dependency.getKey().equals(canonical) || dependency.getKey().equals(requested)) {
                enabled = enabled && linkedDependenciesEnabledIn(source, dependency.getValue(), fallback, visited);
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
        metadataPairs("feature-dependencies").forEach((feature, required) -> {
            String canonicalFeature = canonicalFeature(feature, aliases);
            String canonicalRequired = canonicalDependencyExpression(required, aliases);
            if (!canonicalFeature.isBlank() && !canonicalRequired.isBlank() && !dependencyParts(canonicalRequired).contains(canonicalFeature)) {
                dependencies.putIfAbsent(canonicalFeature, canonicalRequired);
            }
        });
        return Map.copyOf(dependencies);
    }

    public String canonicalFeatureKey(String key) {
        return canonicalFeature(key);
    }

    private Map<String, Boolean> resolvedFeatureStates(Map<String, Boolean> source, boolean runtime) {
        Map<String, String> aliases = featureAliases();
        Map<String, String> dependencies = featureDependencies();
        Map<String, Boolean> resolved = new LinkedHashMap<>();
        source.keySet().forEach(key -> putResolvedFeatureState(resolved, source, key, runtime));
        aliases.forEach((alias, canonical) -> {
            putResolvedFeatureState(resolved, source, alias, runtime);
            putResolvedFeatureState(resolved, source, canonical, runtime);
        });
        dependencies.forEach((feature, required) -> {
            putResolvedFeatureState(resolved, source, feature, runtime);
            dependencyParts(required).forEach(dependency -> putResolvedFeatureState(resolved, source, dependency, runtime));
        });
        return Map.copyOf(resolved);
    }

    private void putResolvedFeatureState(Map<String, Boolean> resolved, Map<String, Boolean> source, String key, boolean runtime) {
        String requested = key == null ? "" : key.trim();
        String canonical = canonicalFeature(requested);
        if (!canonical.isBlank()) {
            boolean value = featureEnabledIn(source, canonical, true, runtime);
            resolved.put(canonical, runtime ? enabled && value : value);
        }
        if (!requested.isBlank() && !requested.equals(canonical)) {
            boolean value = featureEnabledIn(source, requested, true, runtime);
            resolved.put(requested, runtime ? enabled && value : value);
        }
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

    private String canonicalDependencyExpression(String required, Map<String, String> aliases) {
        return dependencyParts(required).stream()
            .map(part -> canonicalFeature(part, aliases))
            .filter(part -> !part.isBlank())
            .distinct()
            .collect(java.util.stream.Collectors.joining("+"));
    }

    private java.util.List<String> dependencyParts(String required) {
        if (required == null || required.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(required.split("\\+"))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .toList();
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
