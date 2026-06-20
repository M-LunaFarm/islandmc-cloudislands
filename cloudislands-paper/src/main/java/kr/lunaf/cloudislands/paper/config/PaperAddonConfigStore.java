package kr.lunaf.cloudislands.paper.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public final class PaperAddonConfigStore {
    private final Plugin plugin;

    public PaperAddonConfigStore(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean addonEnabled(String id, List<String> parentAliases) {
        boolean enabled = true;
        String addonPath = "addons." + id + ".enabled";
        if (plugin.getConfig().contains(addonPath)) {
            enabled = plugin.getConfig().getBoolean(addonPath, true);
        }
        for (String alias : safeAliases(parentAliases)) {
            String aliasPath = alias + ".enabled";
            if (plugin.getConfig().contains(aliasPath)) {
                enabled = enabled && plugin.getConfig().getBoolean(aliasPath, true);
            }
        }
        return enabled;
    }

    public Map<String, Boolean> addonFeatures(String id, List<String> parentAliases, Map<String, Boolean> defaults) {
        Map<String, Boolean> effective = new HashMap<>(defaults == null ? Map.of() : defaults);
        for (String alias : safeAliases(parentAliases)) {
            applyFeatureSection(effective, plugin.getConfig().getConfigurationSection(alias + ".features"));
        }
        applyFeatureSection(effective, plugin.getConfig().getConfigurationSection("addons." + id + ".features"));
        return effective;
    }

    public List<String> configuredParentPaths(String id, List<String> parentAliases) {
        List<String> parentPaths = new ArrayList<>();
        if (plugin.getConfig().contains("addons." + id)) {
            parentPaths.add("addons." + id);
        }
        for (String alias : safeAliases(parentAliases)) {
            if (plugin.getConfig().contains(alias)) {
                parentPaths.add(alias);
            }
        }
        return List.copyOf(parentPaths);
    }

    public void setEnabled(String id, List<String> parentAliases, boolean enabled) {
        plugin.getConfig().set("addons." + id + ".enabled", enabled);
        for (String alias : safeAliases(parentAliases)) {
            plugin.getConfig().set(alias + ".enabled", enabled);
        }
        saveAndReload();
    }

    public void setFeature(String id, List<String> parentAliases, String feature, boolean enabled) {
        plugin.getConfig().set("addons." + id + ".features." + feature, enabled);
        for (String alias : safeAliases(parentAliases)) {
            plugin.getConfig().set(alias + ".features." + feature, enabled);
        }
    }

    public void clearFeatureAliases(String id, List<String> parentAliases, List<String> featureAliases) {
        for (String alias : safeAliases(featureAliases)) {
            plugin.getConfig().set("addons." + id + ".features." + alias, null);
            for (String parentAlias : safeAliases(parentAliases)) {
                plugin.getConfig().set(parentAlias + ".features." + alias, null);
            }
        }
    }

    public void saveAndReload() {
        plugin.saveConfig();
        plugin.reloadConfig();
    }

    private void applyFeatureSection(Map<String, Boolean> effective, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (section.isBoolean(key)) {
                effective.put(key, effective.getOrDefault(key, true) && section.getBoolean(key, true));
            }
        }
    }

    private List<String> safeAliases(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return List.of();
        }
        List<String> safe = new ArrayList<>();
        for (String alias : aliases) {
            if (alias != null && !alias.isBlank()) {
                safe.add(alias);
            }
        }
        return safe;
    }
}
