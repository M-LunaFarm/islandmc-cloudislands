package kr.seungmin.satisskyfactory.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public final class SatisFeatureGateResolver {
    private static final List<String> FEATURE_KEYS = List.of(
            "commands",
            "machines",
            "storage",
            "factories",
            "generators",
            "upgrades",
            "missions",
            "menus",
            "gui",
            "lifecycle",
            "resource-nodes",
            "market",
            "contracts",
            "research",
            "maintenance",
            "placeholders",
            "migration",
            "addon-state",
            "route-events",
            "members",
            "permissions",
            "level-values",
            "warps",
            "biomes",
            "chat",
            "templates"
    );
    private static final List<String> FEATURE_ROOTS = List.of(
            "satis.features.",
            "addons.cloudislands-satis.features.",
            "features."
    );
    private static final List<String> ROOT_GATES = List.of(
            "satis.enabled",
            "integration.enabled",
            "addons.cloudislands-satis.enabled",
            "setup.satis.mode|addons.cloudislands-satis.integration.mode|integration.mode!=DISABLED"
    );
    private static final List<String> INTEGRATION_MODE_PATHS = List.of(
            "setup.satis.mode",
            "addons.cloudislands-satis.integration.mode",
            "integration.mode"
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
            Map.entry("gui", List.of("machines")),
            Map.entry("placeholders", List.of("machines")),
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

    public static List<String> featureKeys() {
        return FEATURE_KEYS;
    }

    public static String featureKeysMetadata() {
        return String.join(",", FEATURE_KEYS);
    }

    public static List<String> featureRoots() {
        return FEATURE_ROOTS;
    }

    public static String featureRootMetadata() {
        return String.join(",", FEATURE_ROOTS);
    }

    public static List<String> rootGates() {
        return ROOT_GATES;
    }

    public static List<String> integrationModePaths() {
        return INTEGRATION_MODE_PATHS;
    }

    public static String rootGateMetadata() {
        return String.join("&&", ROOT_GATES);
    }

    public static String dependencyMetadata() {
        java.util.List<String> values = new java.util.ArrayList<>();
        DEPENDENCIES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.add(entry.getKey() + ":" + String.join("+", entry.getValue())));
        return String.join(",", values);
    }

    public static String aliasMetadata() {
        java.util.List<String> values = new java.util.ArrayList<>();
        ALIASES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.add(entry.getKey() + "->" + entry.getValue()));
        return String.join(",", values);
    }

    public static String disablePolicy() {
        return "disabled-features-hide-commands-skip-runtime-components-preserve-data-and-block-writes";
    }

    public static String configGatePolicy() {
        return "root-gates-disable-all-satis-runtime-and-feature-roots-disable-their-runtime-components";
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
        return !"DISABLED".equals(integrationMode(config, "EXTERNAL_ADDON"));
    }

    public static String integrationMode(ConfigurationSection config, String fallback) {
        String raw = integrationModeConfiguredValue(config, fallback);
        String normalized = raw.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DISABLE", "DISABLED", "OFF" -> "DISABLED";
            case "BUILTIN", "BUILT_IN", "BUILT_IN_ADDON", "BUILTIN_ADDON", "BUILT_IN_COMPATIBLE" -> "BUILT_IN_COMPATIBLE";
            case "EXTERNAL", "EXTERNAL_PLUGIN", "PLUGIN", "ADDON", "EXTERNAL_ADDON" -> "EXTERNAL_ADDON";
            default -> "EXTERNAL_ADDON";
        };
    }

    public static String integrationModeConfiguredValue(ConfigurationSection config, String fallback) {
        String raw = fallback == null || fallback.isBlank() ? "EXTERNAL_ADDON" : fallback;
        if (config != null) {
            for (String path : INTEGRATION_MODE_PATHS) {
                String configured = config.getString(path, "");
                if (configured != null && !configured.isBlank()) {
                    return configured;
                }
            }
        }
        return raw;
    }

    public static String integrationModeSource(ConfigurationSection config) {
        if (config == null) {
            return "default";
        }
        for (String path : INTEGRATION_MODE_PATHS) {
            String configured = config.getString(path, "");
            if (configured != null && !configured.isBlank()) {
                return path;
            }
        }
        return "default";
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

    public static String rootBlockReason(ConfigurationSection config) {
        if (config == null) {
            return "config-unavailable";
        }
        if (!config.getBoolean("satis.enabled", true)) {
            return "satis-root-disabled";
        }
        if (!config.getBoolean("integration.enabled", true)) {
            return "integration-disabled";
        }
        if (!config.getBoolean("addons.cloudislands-satis.enabled", true)) {
            return "addon-root-disabled";
        }
        if ("DISABLED".equals(integrationMode(config, "EXTERNAL_ADDON"))) {
            return "integration-mode-disabled";
        }
        return "none";
    }

    public static String dependencyBlockSummary(ConfigurationSection config) {
        if (!rootEnabled(config)) {
            return "all:" + rootBlockReason(config);
        }
        java.util.List<String> blocked = new java.util.ArrayList<>();
        for (Map.Entry<String, List<String>> entry : DEPENDENCIES.entrySet()) {
            String feature = entry.getKey();
            if (!directFeatureEnabled(config, feature)) {
                continue;
            }
            java.util.List<String> missing = entry.getValue().stream()
                    .filter(dependency -> !featureEnabled(config, dependency))
                    .toList();
            if (!missing.isEmpty()) {
                blocked.add(feature + ":requires-" + String.join("+", missing));
            }
        }
        return blocked.isEmpty() ? "none" : String.join(",", blocked);
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
