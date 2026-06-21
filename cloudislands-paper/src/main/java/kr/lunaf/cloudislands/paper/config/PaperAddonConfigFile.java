package kr.lunaf.cloudislands.paper.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PaperAddonConfigFile {
    private final Path configPath;

    private PaperAddonConfigFile(Plugin plugin) {
        this.configPath = plugin.getDataFolder().toPath().resolve("config-v2").resolve("addons.yml");
    }

    public static PaperAddonConfigFile fromPlugin(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("plugin is required");
        }
        return new PaperAddonConfigFile(plugin);
    }

    PaperAddonConfigSnapshot snapshot() {
        return PaperAddonConfigSnapshot.from(load());
    }

    void set(String path, Object value) {
        YamlConfiguration config = load();
        config.set(path, value);
        save(config);
    }

    void saveAndReload() {
        snapshot();
    }

    private YamlConfiguration load() {
        YamlConfiguration config = new YamlConfiguration();
        if (!Files.isRegularFile(configPath)) {
            return config;
        }
        try {
            config.load(configPath.toFile());
            return config;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read Paper addon config-v2 file " + configPath, exception);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid Paper addon config-v2 file " + configPath, exception);
        }
    }

    private void save(YamlConfiguration config) {
        try {
            Files.createDirectories(configPath.getParent());
            config.save(configPath.toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write Paper addon config-v2 file " + configPath, exception);
        }
    }
}
