package kr.seungmin.satisskyfactory.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisConfigValidatorTest {
    private final SatisConfigValidator validator = new SatisConfigValidator();

    @Test
    void bundledSatisConfigsHaveNoValidationErrors() {
        SatisConfigValidator.ValidationReport report = validator.validate(defaultFiles());

        assertFalse(report.hasErrors(), report.errorSummary());
    }

    @Test
    void rejectsInvalidMaterialsAndUnknownReferencesBeforeRuntimeStart() {
        Map<String, YamlConfiguration> files = defaultFiles();

        files.get("items.yml").set("items.wheat.material", "NOT_A_MATERIAL");
        files.get("machines.yml").set("machines.grinder_t1.allowed-recipes", java.util.List.of("missing_recipe"));
        files.get("recipes.yml").set("recipes.flour_from_wheat.machines", java.util.List.of("missing_machine"));
        files.get("recipes.yml").set("recipes.flour_from_wheat.inputs.missing_item", 1);
        files.get("resource-nodes.yml").set("nodes.bad.resource-id", "missing_ore");
        files.get("market.yml").set("market.items.missing_item.base-price", 5);
        files.get("research.yml").set("research.unlocks.tier_2.unlocks", java.util.List.of("missing_machine"));
        files.get("maintenance.yml").set("maintenance.repair-cost.missing_item", 1);

        SatisConfigValidator.ValidationReport report = validator.validate(files);

        assertTrue(report.hasErrors());
        String errors = report.errorSummary();
        assertTrue(errors.contains("invalid-material:NOT_A_MATERIAL"), errors);
        assertTrue(errors.contains("unknown-recipe:missing_recipe"), errors);
        assertTrue(errors.contains("unknown-machine:missing_machine"), errors);
        assertTrue(errors.contains("unknown-item:missing_item"), errors);
        assertTrue(errors.contains("unknown-item:missing_ore"), errors);
        assertTrue(errors.contains("unknown-unlock-target:missing_machine"), errors);
        assertEquals("ERROR", report.status());
    }

    @Test
    void reportsConflictingAliasPathsAsDoctorVisibleWarnings() {
        Map<String, YamlConfiguration> files = defaultFiles();
        files.get("config.yml").set("satis.features.market", true);
        files.get("config.yml").set("addons.cloudislands-satis.features.market", false);

        SatisConfigValidator.ValidationReport report = validator.validate(files);

        assertFalse(report.hasErrors(), report.errorSummary());
        assertEquals("WARN", report.status());
        assertTrue(report.warningSummary().contains("alias-conflict:satis.features.market!=addons.cloudislands-satis.features.market"));
    }

    private Map<String, YamlConfiguration> defaultFiles() {
        Map<String, YamlConfiguration> files = new LinkedHashMap<>();
        for (String file : java.util.List.of(
                "config.yml",
                "machines.yml",
                "items.yml",
                "recipes.yml",
                "resource-nodes.yml",
                "market.yml",
                "research.yml",
                "maintenance.yml"
        )) {
            files.put(file, YamlConfiguration.loadConfiguration(Path.of("src/main/resources", file).toFile()));
        }
        return files;
    }
}
