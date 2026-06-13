package kr.lunaf.cloudislands.paper.api;

import java.util.List;
import java.util.Map;

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
            String feature = normalize(metadata, parts[0].trim());
            String required = normalize(metadata, parts[1].trim());
            if (!feature.isBlank() && !required.isBlank()) {
                values.put(feature, required);
            }
        }
        return Map.copyOf(values);
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
