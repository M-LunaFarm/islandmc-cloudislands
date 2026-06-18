package kr.lunaf.cloudislands.paper.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

final class AddonFeatureAliases {
    private AddonFeatureAliases() {
    }

    static String normalize(Map<String, String> metadata, String feature) {
        if (feature == null) {
            return "";
        }
        String requested = feature.trim();
        for (Alias alias : aliases(metadata)) {
            if (requested.equals(alias.alias())) {
                return alias.canonical();
            }
        }
        return requested;
    }

    static List<String> aliasesFor(Map<String, String> metadata, String canonicalFeature) {
        return aliases(metadata).stream()
                .filter(alias -> alias.canonical().equals(canonicalFeature))
                .map(Alias::alias)
                .toList();
    }

    static Map<String, String> dependencies(Map<String, String> metadata) {
        Map<String, String> values = new java.util.HashMap<>();
        String source = metadata == null ? "" : metadata.getOrDefault("feature-dependencies", "");
        for (String pair : source.split(",")) {
            String[] parts = pair.split(":", 2);
            if (parts.length != 2) {
                continue;
            }
            String feature = parts[0].trim();
            String required = normalize(metadata, parts[1].trim());
            if (!feature.isBlank() && !required.isBlank()) {
                String canonicalFeature = normalize(metadata, feature);
                if (!canonicalFeature.equals(required)) {
                    values.putIfAbsent(canonicalFeature, required);
                }
            }
        }
        return Map.copyOf(values);
    }

    static boolean featureEnabled(Map<String, String> metadata, Map<String, Boolean> features, String feature) {
        return featureEnabled(metadata, features == null ? Map.of() : features, feature, new java.util.HashSet<>());
    }

    private static boolean featureEnabled(Map<String, String> metadata, Map<String, Boolean> features, String feature, Set<String> visited) {
        String canonical = normalize(metadata, feature);
        if (canonical.isBlank() || !visited.add(canonical)) {
            return true;
        }
        boolean enabled = features.getOrDefault(canonical, true);
        String requested = feature == null ? "" : feature.trim();
        if (!requested.isBlank() && features.containsKey(requested)) {
            enabled = enabled && features.get(requested);
        }
        for (String alias : aliasesFor(metadata, canonical)) {
            if (features.containsKey(alias)) {
                enabled = enabled && features.get(alias);
            }
        }
        String required = dependencies(metadata).get(canonical);
        if (required == null || required.isBlank()) {
            return enabled;
        }
        for (String dependency : required.split("[+&]")) {
            String safeDependency = dependency.trim();
            if (!safeDependency.isBlank() && !featureEnabled(metadata, features, safeDependency, visited)) {
                return false;
            }
        }
        return enabled;
    }

    private static List<Alias> aliases(Map<String, String> metadata) {
        String source = metadata == null ? "" : metadata.getOrDefault("feature-aliases", "");
        return java.util.Arrays.stream(source.split(","))
                .map(pair -> pair.split(":", 2))
                .filter(parts -> parts.length == 2)
                .map(parts -> new String[] {parts[0].trim(), parts[1].trim()})
                .filter(parts -> !parts[0].isBlank() && !parts[1].isBlank())
                .map(parts -> new Alias(parts[0], parts[1]))
                .toList();
    }

    private record Alias(String alias, String canonical) {
    }
}
