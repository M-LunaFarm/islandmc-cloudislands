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
    private static final Map<String, String> DEPENDENCIES = Map.ofEntries(
            Map.entry("resource-nodes", "machines"),
            Map.entry("market", "storage"),
            Map.entry("contracts", "storage"),
            Map.entry("route-events", "addon-state"),
            Map.entry("members", "lifecycle"),
            Map.entry("permissions", "lifecycle"),
            Map.entry("level-values", "lifecycle"),
            Map.entry("warps", "lifecycle"),
            Map.entry("biomes", "lifecycle"),
            Map.entry("chat", "lifecycle"),
            Map.entry("templates", "lifecycle")
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
        String dependency = DEPENDENCIES.get(canonical);
        return dependency == null || featureEnabled(config, dependency);
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
