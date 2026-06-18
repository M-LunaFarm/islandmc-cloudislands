package kr.lunaf.cloudislands.common.feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PlayerFacingIslandSurface {
    private static final Set<String> EXTRA_HIDDEN_TOPOLOGY_KEYS = Set.of(
            "active-node",
            "source-node",
            "target-node",
            "node-id",
            "active-world",
            "world",
            "cell-x",
            "cell-z",
            "route-ticket-id",
            "backend-storage-key"
    );

    private PlayerFacingIslandSurface() {
    }

    public static Map<String, String> sanitize(Map<String, String> values) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (!isHiddenTopologyKey(key)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    public static boolean isHiddenTopologyKey(String key) {
        String normalized = normalize(key);
        String compact = compact(normalized);
        for (String hidden : hiddenTopologyKeys()) {
            String normalizedHidden = normalize(hidden);
            String compactHidden = compact(normalizedHidden);
            if (normalized.equals(normalizedHidden)
                    || compact.equals(compactHidden)
                    || normalized.endsWith("-" + normalizedHidden)
                    || compact.endsWith(compactHidden)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isLogicalSurface(String surface) {
        String normalized = normalize(surface);
        return SatisIntegrationPolicy.logicalPlayerSurfaces().stream()
                .map(PlayerFacingIslandSurface::normalize)
                .anyMatch(normalized::equals);
    }

    public static List<String> logicalSurfaces() {
        return SatisIntegrationPolicy.logicalPlayerSurfaces();
    }

    public static Set<String> hiddenTopologyKeys() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(SatisIntegrationPolicy.playerHiddenTopologyFields());
        keys.addAll(EXTRA_HIDDEN_TOPOLOGY_KEYS);
        return Set.copyOf(keys);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace('_', '-')
                .replace('.', '-')
                .replace(' ', '-');
    }

    private static String compact(String value) {
        return value.replace("-", "");
    }
}
