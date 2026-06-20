package kr.lunaf.cloudislands.paper.config;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;

public final class PaperAddonConfigSnapshot {
    private final Map<String, AddonPath> paths;

    private PaperAddonConfigSnapshot(Map<String, AddonPath> paths) {
        this.paths = Map.copyOf(paths);
    }

    public static PaperAddonConfigSnapshot from(ConfigurationSection root) {
        if (root == null) {
            return new PaperAddonConfigSnapshot(Map.of());
        }
        Map<String, AddonPath> paths = new HashMap<>();
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            if (key.equals("addons")) {
                for (String addonId : section.getKeys(false)) {
                    ConfigurationSection addon = section.getConfigurationSection(addonId);
                    if (addon != null) {
                        paths.put("addons." + addonId, AddonPath.from(addon));
                    }
                }
            } else {
                paths.put(key, AddonPath.from(section));
            }
        }
        return new PaperAddonConfigSnapshot(paths);
    }

    boolean contains(String path) {
        return paths.containsKey(path);
    }

    boolean enabled(String path, boolean fallback) {
        AddonPath config = paths.get(path);
        if (config == null || !config.enabledConfigured()) {
            return fallback;
        }
        return config.enabled();
    }

    Map<String, Boolean> features(String path) {
        AddonPath config = paths.get(path);
        return config == null ? Map.of() : config.features();
    }

    Set<String> paths() {
        return paths.keySet();
    }

    private record AddonPath(boolean enabledConfigured, boolean enabled, Map<String, Boolean> features) {
        private AddonPath {
            features = features == null ? Map.of() : Map.copyOf(features);
        }

        private static AddonPath from(ConfigurationSection section) {
            boolean enabledConfigured = section.contains("enabled");
            Map<String, Boolean> features = new HashMap<>();
            ConfigurationSection featureSection = section.getConfigurationSection("features");
            if (featureSection != null) {
                for (String key : featureSection.getKeys(false)) {
                    if (featureSection.isBoolean(key)) {
                        features.put(key, featureSection.getBoolean(key, true));
                    }
                }
            }
            return new AddonPath(enabledConfigured, section.getBoolean("enabled", true), features);
        }
    }
}
