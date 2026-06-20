package kr.lunaf.cloudislands.paper.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.plugin.Plugin;

public final class PaperAddonConfigStore {
    private final PaperAddonConfigFile configFile;
    private PaperAddonConfigSnapshot snapshot;

    public PaperAddonConfigStore(Plugin plugin) {
        this(PaperAddonConfigFile.fromPlugin(plugin));
    }

    public PaperAddonConfigStore(PaperAddonConfigFile configFile) {
        if (configFile == null) {
            throw new IllegalArgumentException("configFile is required");
        }
        this.configFile = configFile;
        this.snapshot = configFile.snapshot();
    }

    public boolean addonEnabled(String id, List<String> parentAliases) {
        boolean enabled = true;
        String addonPath = "addons." + id + ".enabled";
        enabled = snapshot.enabled(parentPath(addonPath), enabled);
        for (String alias : safeAliases(parentAliases)) {
            enabled = enabled && snapshot.enabled(parentPath(alias + ".enabled"), true);
        }
        return enabled;
    }

    public Map<String, Boolean> addonFeatures(String id, List<String> parentAliases, Map<String, Boolean> defaults) {
        Map<String, Boolean> effective = new HashMap<>(defaults == null ? Map.of() : defaults);
        for (String alias : safeAliases(parentAliases)) {
            applyFeatures(effective, snapshot.features(alias));
        }
        applyFeatures(effective, snapshot.features("addons." + id));
        return effective;
    }

    public List<String> configuredParentPaths(String id, List<String> parentAliases) {
        List<String> parentPaths = new ArrayList<>();
        if (snapshot.contains("addons." + id)) {
            parentPaths.add("addons." + id);
        }
        for (String alias : safeAliases(parentAliases)) {
            if (snapshot.contains(alias)) {
                parentPaths.add(alias);
            }
        }
        return List.copyOf(parentPaths);
    }

    public void setEnabled(String id, List<String> parentAliases, boolean enabled) {
        configFile.set("addons." + id + ".enabled", enabled);
        for (String alias : safeAliases(parentAliases)) {
            configFile.set(alias + ".enabled", enabled);
        }
        saveAndReload();
    }

    public void setFeature(String id, List<String> parentAliases, String feature, boolean enabled) {
        configFile.set("addons." + id + ".features." + feature, enabled);
        for (String alias : safeAliases(parentAliases)) {
            configFile.set(alias + ".features." + feature, enabled);
        }
    }

    public void clearFeatureAliases(String id, List<String> parentAliases, List<String> featureAliases) {
        for (String alias : safeAliases(featureAliases)) {
            configFile.set("addons." + id + ".features." + alias, null);
            for (String parentAlias : safeAliases(parentAliases)) {
                configFile.set(parentAlias + ".features." + alias, null);
            }
        }
    }

    public void saveAndReload() {
        configFile.saveAndReload();
        snapshot = configFile.snapshot();
    }

    private void applyFeatures(Map<String, Boolean> effective, Map<String, Boolean> features) {
        for (Map.Entry<String, Boolean> entry : features.entrySet()) {
            effective.put(entry.getKey(), effective.getOrDefault(entry.getKey(), true) && entry.getValue());
        }
    }

    private String parentPath(String settingPath) {
        int lastDot = settingPath.lastIndexOf('.');
        return lastDot < 0 ? settingPath : settingPath.substring(0, lastDot);
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
