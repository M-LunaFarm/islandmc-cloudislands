package kr.lunaf.cloudislands.paper.generator;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public final class ConfigGeneratorRules {
    private ConfigGeneratorRules() {}

    public static GeneratorRegistry load(Plugin plugin) {
        YamlConfiguration config = loadBundled(plugin);
        java.io.File override = new java.io.File(plugin.getDataFolder(), "rules/generators.yaml");
        if (override.isFile()) {
            config = YamlConfiguration.loadConfiguration(override);
        }
        GeneratorRegistry registry = fromConfig(config);
        return registry == null ? DefaultGeneratorRules.create() : registry;
    }

    private static YamlConfiguration loadBundled(Plugin plugin) {
        try (InputStream input = plugin.getResource("rules/generators.yaml")) {
            if (input == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (java.io.IOException exception) {
            return new YamlConfiguration();
        }
    }

    private static GeneratorRegistry fromConfig(YamlConfiguration config) {
        ConfigurationSection generators = config.getConfigurationSection("generators");
        if (generators == null) {
            return null;
        }
        GeneratorRegistry registry = new GeneratorRegistry();
        int loadedRules = 0;
        for (String generatorKey : generators.getKeys(false)) {
            ConfigurationSection levels = generators.getConfigurationSection(generatorKey + ".levels");
            if (levels == null) {
                continue;
            }
            for (String levelKey : levels.getKeys(false)) {
                int level = parseLevel(levelKey);
                if (level < 1) {
                    continue;
                }
                ConfigurationSection materials = levels.getConfigurationSection(levelKey);
                if (materials == null) {
                    continue;
                }
                GeneratorRule rule = new GeneratorRule();
                for (String materialKey : materials.getKeys(false)) {
                    rule.add(materialKey, materials.getInt(materialKey, 0));
                }
                registry.put(generatorKey, level, rule);
                loadedRules++;
            }
        }
        return loadedRules == 0 ? null : registry;
    }

    private static int parseLevel(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}
