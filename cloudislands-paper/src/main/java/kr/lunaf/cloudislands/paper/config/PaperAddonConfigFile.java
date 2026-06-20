package kr.lunaf.cloudislands.paper.config;

import org.bukkit.plugin.Plugin;

public final class PaperAddonConfigFile {
    private final Plugin plugin;

    private PaperAddonConfigFile(Plugin plugin) {
        this.plugin = plugin;
    }

    public static PaperAddonConfigFile fromPlugin(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin is required");
        }
        return new PaperAddonConfigFile(plugin);
    }

    PaperAddonConfigSnapshot snapshot() {
        return PaperAddonConfigSnapshot.from(plugin.getConfig());
    }

    void set(String path, Object value) {
        plugin.getConfig().set(path, value);
    }

    void saveAndReload() {
        plugin.saveConfig();
        plugin.reloadConfig();
    }
}
