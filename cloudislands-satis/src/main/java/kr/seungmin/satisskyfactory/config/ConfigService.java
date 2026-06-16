package kr.seungmin.satisskyfactory.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigService {
    private static final List<String> FILES = List.of(
            "config.yml",
            "machines.yml",
            "items.yml",
            "recipes.yml",
            "resource-nodes.yml",
            "market.yml",
            "contracts.yml",
            "research.yml",
            "maintenance.yml",
            "messages.yml"
    );

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        for (String fileName : FILES) {
            plugin.saveResource(fileName, false);
            File file = new File(plugin.getDataFolder(), fileName);
            YamlConfiguration defaults = bundledDefaults(fileName);
            FileConfiguration existing = configs.get(fileName);
            if (existing instanceof YamlConfiguration yaml) {
                try {
                    yaml.load(file);
                    if (mergeMissingDefaults(yaml, defaults)) {
                        yaml.save(file);
                    }
                } catch (IOException | InvalidConfigurationException exception) {
                    throw new IllegalStateException("Failed to load config file: " + fileName, exception);
                }
            } else {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                if (mergeMissingDefaults(yaml, defaults)) {
                    try {
                        yaml.save(file);
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to update config file defaults: " + fileName, exception);
                    }
                }
                configs.put(fileName, yaml);
            }
        }
    }

    private YamlConfiguration bundledDefaults(String fileName) {
        try (InputStream input = plugin.getResource(fileName)) {
            if (input == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read bundled config defaults: " + fileName, exception);
        }
    }

    private boolean mergeMissingDefaults(ConfigurationSection target, ConfigurationSection defaults) {
        if (target == null || defaults == null) {
            return false;
        }
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            ConfigurationSection defaultSection = defaults.getConfigurationSection(key);
            if (!target.contains(key)) {
                if (defaultSection == null) {
                    target.set(key, defaults.get(key));
                } else {
                    ConfigurationSection created = target.createSection(key);
                    mergeMissingDefaults(created, defaultSection);
                }
                changed = true;
                continue;
            }
            ConfigurationSection targetSection = target.getConfigurationSection(key);
            if (defaultSection != null && targetSection != null) {
                changed |= mergeMissingDefaults(targetSection, defaultSection);
            }
        }
        return changed;
    }

    public FileConfiguration main() {
        return file("config.yml");
    }

    public FileConfiguration file(String name) {
        FileConfiguration configuration = configs.get(name);
        if (configuration == null) {
            throw new IllegalArgumentException("Unknown config file: " + name);
        }
        return configuration;
    }
}
