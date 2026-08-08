package kr.seungmin.satisskyfactory.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SatisConfigValidator {
    private static final List<String> FILES = List.of(
            "config.yml",
            "machines.yml",
            "items.yml",
            "recipes.yml",
            "resource-nodes.yml",
            "market.yml",
            "research.yml",
            "maintenance.yml"
    );

    public ValidationReport validate(ConfigService configs) {
        Map<String, FileConfiguration> files = new LinkedHashMap<>();
        for (String file : FILES) {
            files.put(file, configs.file(file));
        }
        return validate(files);
    }

    public ValidationReport validate(Map<String, ? extends FileConfiguration> files) {
        ValidationBuilder builder = new ValidationBuilder();
        FileConfiguration config = files.get("config.yml");
        FileConfiguration items = files.get("items.yml");
        FileConfiguration machines = files.get("machines.yml");
        FileConfiguration recipes = files.get("recipes.yml");
        FileConfiguration nodes = files.get("resource-nodes.yml");
        FileConfiguration market = files.get("market.yml");
        FileConfiguration research = files.get("research.yml");
        FileConfiguration maintenance = files.get("maintenance.yml");

        Set<String> itemIds = keys(items, "items");
        Set<String> machineIds = keys(machines, "machines");
        Set<String> recipeIds = keys(recipes, "recipes");
        Set<String> researchIds = keys(research, "research.unlocks");
        Set<String> featureIds = new LinkedHashSet<>(SatisFeatureGateResolver.featureKeys());

        validateItems(items, builder);
        validateMachines(machines, recipeIds, itemIds, researchIds, builder);
        validateRecipes(recipes, machineIds, itemIds, researchIds, builder);
        validateResourceNodes(nodes, itemIds, builder);
        validateMarket(market, itemIds, builder);
        validateResearch(research, machineIds, featureIds, researchIds, builder);
        validateMaintenance(maintenance, itemIds, builder);
        validateConfigSchemaAliases(config, builder);

        return builder.build();
    }

    private void validateItems(FileConfiguration config, ValidationBuilder builder) {
        ConfigurationSection section = section(config, "items");
        if (section == null) {
            builder.error("items.yml:items", "missing-items-section");
            return;
        }
        for (String id : section.getKeys(false)) {
            requireMaterial(section.getString(id + ".material", ""), "items.yml:items." + id + ".material", builder);
        }
    }

    private void validateMachines(FileConfiguration config, Set<String> recipeIds, Set<String> itemIds,
                                  Set<String> researchIds, ValidationBuilder builder) {
        ConfigurationSection section = section(config, "machines");
        if (section == null) {
            builder.error("machines.yml:machines", "missing-machines-section");
            return;
        }
        for (String id : section.getKeys(false)) {
            String path = "machines." + id + ".";
            requireMaterial(firstString(config, path + "material", path + "item-material"), "machines.yml:" + path + "material", builder);
            requireMaterial(firstString(config, path + "placed-block", path + "placed-material"), "machines.yml:" + path + "placed-block", builder);
            for (String recipe : config.getStringList(path + "allowed-recipes")) {
                requireKnown(recipeIds, recipe, "machines.yml:" + path + "allowed-recipes", "unknown-recipe", builder);
            }
            for (String unlock : config.getStringList(path + "required-unlocks")) {
                requireKnown(researchIds, unlock, "machines.yml:" + path + "required-unlocks", "unknown-research-unlock", builder);
            }
            ConfigurationSection drops = config.getConfigurationSection(path + "harvest-drops");
            if (drops != null) {
                for (String material : drops.getKeys(false)) {
                    requireMaterial(material, "machines.yml:" + path + "harvest-drops." + material, builder);
                    requireKnown(itemIds, drops.getString(material, ""), "machines.yml:" + path + "harvest-drops." + material, "unknown-item", builder);
                }
            }
            ConfigurationSection planting = config.getConfigurationSection(path + "planting");
            if (planting != null) {
                for (String seed : planting.getKeys(false)) {
                    requireKnown(itemIds, seed, "machines.yml:" + path + "planting." + seed, "unknown-seed-item", builder);
                    requireMaterial(planting.getString(seed + ".crop", ""), "machines.yml:" + path + "planting." + seed + ".crop", builder);
                    requireMaterial(planting.getString(seed + ".soil", "FARMLAND"), "machines.yml:" + path + "planting." + seed + ".soil", builder);
                }
            }
            String fertilizer = config.getString(path + "fertilizer.item", "");
            if (!fertilizer.isBlank()) {
                requireKnown(itemIds, fertilizer, "machines.yml:" + path + "fertilizer.item", "unknown-item", builder);
            }
            String qualityItem = config.getString(path + "fertilizer.quality-item", "");
            if (!qualityItem.isBlank()) {
                requireKnown(itemIds, qualityItem, "machines.yml:" + path + "fertilizer.quality-item", "unknown-item", builder);
            }
        }
    }

    private void validateRecipes(FileConfiguration config, Set<String> machineIds, Set<String> itemIds,
                                 Set<String> researchIds, ValidationBuilder builder) {
        ConfigurationSection section = section(config, "recipes");
        if (section == null) {
            builder.error("recipes.yml:recipes", "missing-recipes-section");
            return;
        }
        for (String id : section.getKeys(false)) {
            String path = "recipes." + id + ".";
            List<String> machines = stringList(config, path + "machines", path + "machine");
            if (machines.isEmpty()) {
                builder.error("recipes.yml:" + path + "machines", "missing-recipe-machine");
            }
            for (String machine : machines) {
                requireKnown(machineIds, machine, "recipes.yml:" + path + "machines", "unknown-machine", builder);
            }
            validateItemAmountSection(config, path, "input", "inputs", itemIds, "recipes.yml", builder);
            validateItemAmountSection(config, path, "output", "outputs", itemIds, "recipes.yml", builder);
            validateItemAmountSection(config, path, "byproducts", "byproducts", itemIds, "recipes.yml", builder);
            for (String unlock : stringList(config, path + "research-required", path + "researchRequired")) {
                requireKnown(researchIds, unlock, "recipes.yml:" + path + "research-required", "unknown-research-unlock", builder);
            }
            String qualityItem = config.getString(path + "quality-item", "");
            if (!qualityItem.isBlank()) {
                requireKnown(itemIds, qualityItem, "recipes.yml:" + path + "quality-item", "unknown-item", builder);
            }
        }
    }

    private void validateResourceNodes(FileConfiguration config, Set<String> itemIds, ValidationBuilder builder) {
        if (config == null) {
            return;
        }
        List<Map<?, ?>> defaultNodes = config.getMapList("resource-nodes.default-new-island-nodes");
        for (int index = 0; index < defaultNodes.size(); index++) {
            Object value = defaultNodes.get(index).get("resource-id");
            requireKnown(itemIds, value == null ? "" : value.toString(), "resource-nodes.yml:resource-nodes.default-new-island-nodes[" + index + "].resource-id", "unknown-item", builder);
        }
        ConfigurationSection legacy = config.getConfigurationSection("nodes");
        if (legacy != null) {
            for (String node : legacy.getKeys(false)) {
                requireKnown(itemIds, legacy.getString(node + ".resource-id", ""), "resource-nodes.yml:nodes." + node + ".resource-id", "unknown-item", builder);
            }
        }
    }

    private void validateMarket(FileConfiguration config, Set<String> itemIds, ValidationBuilder builder) {
        ConfigurationSection section = section(config, "market.items");
        if (section == null) {
            builder.warn("market.yml:market.items", "missing-market-items-section");
            return;
        }
        for (String item : section.getKeys(false)) {
            requireKnown(itemIds, item, "market.yml:market.items." + item, "unknown-item", builder);
        }
    }

    private void validateResearch(FileConfiguration config, Set<String> machineIds, Set<String> featureIds,
                                  Set<String> researchIds, ValidationBuilder builder) {
        ConfigurationSection section = section(config, "research.unlocks");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            for (String required : stringList(section, id + ".required-unlocks", id + ".requires")) {
                requireKnown(researchIds, required, "research.yml:research.unlocks." + id + ".required-unlocks", "unknown-research-unlock", builder);
            }
            for (String grant : section.getStringList(id + ".unlocks")) {
                if (!machineIds.contains(grant) && !featureIds.contains(grant)) {
                    builder.error("research.yml:research.unlocks." + id + ".unlocks", "unknown-unlock-target:" + grant);
                }
            }
        }
    }

    private void validateMaintenance(FileConfiguration config, Set<String> itemIds, ValidationBuilder builder) {
        validateCostSection(config, "maintenance.repair-cost", itemIds, "maintenance.yml", builder);
        validateCostSection(config, "maintenance.broken-repair-cost", itemIds, "maintenance.yml", builder);
    }

    private void validateConfigSchemaAliases(FileConfiguration config, ValidationBuilder builder) {
        if (config == null) {
            return;
        }
        compare(config, "satis.enabled", "integration.enabled", builder);
        compare(config, "satis.enabled", "addons.cloudislands-satis.enabled", builder);
        compare(config, "satis.mode", "setup.satis.mode", builder);
        compare(config, "satis.mode", "addons.cloudislands-satis.integration.mode", builder);
        compare(config, "satis.mode", "integration.mode", builder);
        warnAliasIfCanonicalMissing(config, "satis.mode", "setup.satis.mode", builder);
        warnAliasIfCanonicalMissing(config, "satis.mode", "addons.cloudislands-satis.integration.mode", builder);
        warnAliasIfCanonicalMissing(config, "satis.mode", "integration.mode", builder);

        for (String path : List.of("type", "path", "shared-directory", "sqlite-file")) {
            compare(config, "satis.database." + path, "setup.database." + path, builder);
            compare(config, "satis.database." + path, "addons.cloudislands-satis.database." + path, builder);
            compare(config, "satis.database." + path, "database." + path, builder);
            warnAliasIfCanonicalMissing(config, "satis.database." + path, "setup.database." + path, builder);
            warnAliasIfCanonicalMissing(config, "satis.database." + path, "addons.cloudislands-satis.database." + path, builder);
            warnAliasIfCanonicalMissing(config, "satis.database." + path, "database." + path, builder);
        }
        for (String path : List.of(
                "fallback.enabled",
                "core-api.enabled",
                "core-api.flattened-fallback.enabled",
                "core-api.local-cache-writes.enabled",
                "jdbc.url",
                "jdbc.username",
                "jdbc.password",
                "jdbc.max-pool-size",
                "jdbc.connection-timeout-ms"
        )) {
            if (path.equals("core-api.enabled") && config.contains("satis.database.type")) {
                continue;
            }
            compare(config, "satis.database." + path, "setup.database." + path, builder);
            compare(config, "satis.database." + path, "addons.cloudislands-satis.database." + path, builder);
            compare(config, "satis.database." + path, "database." + path, builder);
            warnAliasIfCanonicalMissing(config, "satis.database." + path, "setup.database." + path, builder);
            warnAliasIfCanonicalMissing(config, "satis.database." + path, "addons.cloudislands-satis.database." + path, builder);
            warnAliasIfCanonicalMissing(config, "satis.database." + path, "database." + path, builder);
        }
        compareList(config, "satis.database.fallback.order", "setup.database.fallback.order", builder);
        compareList(config, "satis.database.fallback.order", "addons.cloudislands-satis.database.fallback.order", builder);
        compareList(config, "satis.database.fallback.order", "database.fallback.order", builder);
        warnAliasIfCanonicalMissing(config, "satis.database.fallback.order", "setup.database.fallback.order", builder);
        warnAliasIfCanonicalMissing(config, "satis.database.fallback.order", "addons.cloudislands-satis.database.fallback.order", builder);
        warnAliasIfCanonicalMissing(config, "satis.database.fallback.order", "database.fallback.order", builder);

        for (String backend : List.of("postgresql", "mysql", "mariadb")) {
            for (String field : List.of("jdbc-url", "url", "host", "port", "name", "database", "options", "username", "password", "max-pool-size", "connection-timeout-ms")) {
                String canonical = "satis.database." + backend + "." + field;
                compare(config, canonical, "setup.database." + backend + "." + field, builder);
                compare(config, canonical, "addons.cloudislands-satis.database." + backend + "." + field, builder);
                compare(config, canonical, "database." + backend + "." + field, builder);
                warnAliasIfCanonicalMissing(config, canonical, "setup.database." + backend + "." + field, builder);
                warnAliasIfCanonicalMissing(config, canonical, "addons.cloudislands-satis.database." + backend + "." + field, builder);
                warnAliasIfCanonicalMissing(config, canonical, "database." + backend + "." + field, builder);
            }
        }
        for (String feature : SatisFeatureGateResolver.featureKeys()) {
            compare(config, "satis.features." + feature, "features." + feature, builder);
            compare(config, "satis.features." + feature, "addons.cloudislands-satis.features." + feature, builder);
            warnAliasIfCanonicalMissing(config, "satis.features." + feature, "features." + feature, builder);
            warnAliasIfCanonicalMissing(config, "satis.features." + feature, "addons.cloudislands-satis.features." + feature, builder);
        }
    }

    private void compare(FileConfiguration config, String firstPath, String secondPath, ValidationBuilder builder) {
        if (!config.contains(firstPath) || !config.contains(secondPath)) {
            return;
        }
        String first = normalizedAliasValue(config.get(firstPath));
        String second = normalizedAliasValue(config.get(secondPath));
        if (!first.isBlank() && !second.isBlank() && !first.equals(second)) {
            builder.warn("config.yml:" + firstPath + "," + secondPath, "alias-conflict:" + firstPath + "!=" + secondPath);
        }
    }

    private void compareList(FileConfiguration config, String firstPath, String secondPath, ValidationBuilder builder) {
        if (!config.contains(firstPath) || !config.contains(secondPath)) {
            return;
        }
        String first = String.join("+", config.getStringList(firstPath)).trim().toLowerCase(Locale.ROOT);
        String second = String.join("+", config.getStringList(secondPath)).trim().toLowerCase(Locale.ROOT);
        if (!first.isBlank() && !second.isBlank() && !first.equals(second)) {
            builder.warn("config.yml:" + firstPath + "," + secondPath, "alias-conflict:" + firstPath + "!=" + secondPath);
        }
    }

    private void warnAliasIfCanonicalMissing(FileConfiguration config, String canonicalPath, String aliasPath, ValidationBuilder builder) {
        if (config.contains(canonicalPath) || !config.contains(aliasPath) || blankAliasValue(config, aliasPath)) {
            return;
        }
        builder.warn("config.yml:" + aliasPath, "deprecated-alias-used:set-" + canonicalPath);
    }

    private boolean blankAliasValue(FileConfiguration config, String path) {
        Object value = config.get(path);
        if (value == null) {
            return true;
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof ConfigurationSection section) {
            return section.getKeys(false).isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() <= 0.0D;
        }
        return value.toString().isBlank();
    }

    private String normalizedAliasValue(Object value) {
        if (value instanceof Number number && number.doubleValue() <= 0.0D) {
            return "";
        }
        return normalized(value);
    }

    private void validateItemAmountSection(FileConfiguration config, String base, String first, String second,
                                           Set<String> itemIds, String file, ValidationBuilder builder) {
        ConfigurationSection section = config.getConfigurationSection(base + first);
        if (section == null && !first.equals(second)) {
            section = config.getConfigurationSection(base + second);
        }
        if (section == null) {
            return;
        }
        for (String item : section.getKeys(false)) {
            requireKnown(itemIds, item, file + ":" + base + first + "." + item, "unknown-item", builder);
        }
    }

    private void validateCostSection(FileConfiguration config, String path, Set<String> itemIds, String file, ValidationBuilder builder) {
        ConfigurationSection section = section(config, path);
        if (section == null) {
            return;
        }
        for (String item : section.getKeys(false)) {
            requireKnown(itemIds, item, file + ":" + path + "." + item, "unknown-item", builder);
        }
    }

    private void requireMaterial(String value, String path, ValidationBuilder builder) {
        if (value == null || value.isBlank()) {
            builder.error(path, "missing-material");
            return;
        }
        if (Material.matchMaterial(value) == null) {
            builder.error(path, "invalid-material:" + value);
        }
    }

    private void requireKnown(Set<String> known, String value, String path, String reason, ValidationBuilder builder) {
        if (value == null || value.isBlank()) {
            builder.error(path, reason + ":blank");
            return;
        }
        if (!known.contains(value)) {
            builder.error(path, reason + ":" + value);
        }
    }

    private Set<String> keys(FileConfiguration config, String path) {
        ConfigurationSection section = section(config, path);
        return section == null ? Set.of() : new LinkedHashSet<>(section.getKeys(false));
    }

    private ConfigurationSection section(FileConfiguration config, String path) {
        return config == null ? null : config.getConfigurationSection(path);
    }

    private String firstString(FileConfiguration config, String firstPath, String secondPath) {
        String first = config.getString(firstPath, "");
        if (first != null && !first.isBlank()) {
            return first;
        }
        return config.getString(secondPath, "");
    }

    private List<String> stringList(FileConfiguration config, String firstPath, String secondPath) {
        List<String> values = new ArrayList<>(config.getStringList(firstPath));
        if (values.isEmpty()) {
            values.addAll(config.getStringList(secondPath));
        }
        String scalar = config.isList(firstPath) || config.isList(secondPath) ? "" : config.getString(firstPath, config.getString(secondPath, ""));
        if (values.isEmpty() && scalar != null && !scalar.isBlank()) {
            values.add(scalar);
        }
        return values;
    }

    private List<String> stringList(ConfigurationSection section, String firstPath, String secondPath) {
        List<String> values = new ArrayList<>(section.getStringList(firstPath));
        if (values.isEmpty()) {
            values.addAll(section.getStringList(secondPath));
        }
        String scalar = section.isList(firstPath) || section.isList(secondPath) ? "" : section.getString(firstPath, section.getString(secondPath, ""));
        if (values.isEmpty() && scalar != null && !scalar.isBlank()) {
            values.add(scalar);
        }
        return values;
    }

    private String normalized(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim().toLowerCase(Locale.ROOT);
    }

    public record ValidationReport(List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        public static ValidationReport empty() {
            return new ValidationReport(List.of(), List.of());
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public String status() {
            if (!errors.isEmpty()) {
                return "ERROR";
            }
            return warnings.isEmpty() ? "OK" : "WARN";
        }

        public String errorSummary() {
            return summarize(errors);
        }

        public String warningSummary() {
            return summarize(warnings);
        }

        private static String summarize(List<ValidationIssue> issues) {
            if (issues.isEmpty()) {
                return "none";
            }
            List<String> values = issues.stream().map(ValidationIssue::toSummary).toList();
            return String.join(",", values);
        }
    }

    public record ValidationIssue(String path, String reason) {
        String toSummary() {
            return path + "=" + reason;
        }
    }

    private static final class ValidationBuilder {
        private final List<ValidationIssue> errors = new ArrayList<>();
        private final List<ValidationIssue> warnings = new ArrayList<>();

        void error(String path, String reason) {
            errors.add(new ValidationIssue(path, reason));
        }

        void warn(String path, String reason) {
            warnings.add(new ValidationIssue(path, reason));
        }

        ValidationReport build() {
            return new ValidationReport(List.copyOf(errors), List.copyOf(warnings));
        }
    }
}
