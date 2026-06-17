package kr.seungmin.satisskyfactory.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class SatisFeatureGateResolver {
    private static final List<String> FEATURE_ROOTS = List.of(
            "satis.features.",
            "addons.cloudislands-satis.features.",
            "features."
    );
    private static final Map<String, String> ALIASES = Map.of(
            "factories", "machines",
            "generators", "resource-nodes",
            "upgrades", "research",
            "missions", "contracts",
            "menus", "gui"
    );
    private static final Map<String, List<String>> DEPENDENCIES = Map.ofEntries(
            Map.entry("resource-nodes", List.of("machines")),
            Map.entry("market", List.of("storage")),
            Map.entry("contracts", List.of("storage")),
            Map.entry("missions", List.of("contracts", "storage")),
            Map.entry("upgrades", List.of("research")),
            Map.entry("menus", List.of("gui")),
            Map.entry("route-events", List.of("addon-state")),
            Map.entry("members", List.of("lifecycle")),
            Map.entry("permissions", List.of("lifecycle")),
            Map.entry("level-values", List.of("lifecycle")),
            Map.entry("warps", List.of("lifecycle")),
            Map.entry("biomes", List.of("lifecycle")),
            Map.entry("chat", List.of("lifecycle")),
            Map.entry("templates", List.of("lifecycle"))
    );

    private SatisFeatureGateResolver() {
    }

    public static boolean rootEnabled(ConfigurationSection config) {
        if (config == null) {
            return false;
        }
        if (!config.getBoolean("satis.enabled", true)) {
            return false;
        }
        if (!config.getBoolean("integration.enabled", true)) {
            return false;
        }
        if (!config.getBoolean("addons.cloudislands-satis.enabled", true)) {
            return false;
        }
        return !"DISABLED".equals(config.getString("integration.mode", "EXTERNAL_ADDON").toUpperCase(Locale.ROOT));
    }

    public static boolean featureEnabled(ConfigurationSection config, String feature) {
        if (!rootEnabled(config)) {
            return false;
        }
        String canonical = canonical(feature);
        if (!directFeatureEnabled(config, canonical)) {
            return false;
        }
        List<String> dependencies = DEPENDENCIES.get(canonical);
        return dependencies == null || dependencies.stream().allMatch(dependency -> featureEnabled(config, dependency));
    }

    public static String canonical(String feature) {
        if (feature == null) {
            return "";
        }
        String normalized = feature.toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(normalized, normalized);
    }

    private static boolean directFeatureEnabled(ConfigurationSection config, String canonical) {
        for (String root : FEATURE_ROOTS) {
            if (config.contains(root + canonical) && !config.getBoolean(root + canonical)) {
                return false;
            }
            for (Map.Entry<String, String> alias : ALIASES.entrySet()) {
                if (alias.getValue().equals(canonical)
                        && config.contains(root + alias.getKey())
                        && !config.getBoolean(root + alias.getKey())) {
                    return false;
                }
            }
        }
        return true;
    }
}
