package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.generator.IslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;

public final class GeneratorRoutes implements RouteGroup {
    private final IslandGeneratorRepository generators;
    private final IslandUpgradeRepository upgrades;

    public GeneratorRoutes(IslandGeneratorRepository generators, IslandUpgradeRepository upgrades) {
        this.generators = generators;
        this.upgrades = upgrades;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/islands/generator", this::generator);
        registry.routePost("/v1/islands/generator/rules", this::rules);
    }

    private void generator(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
        CoreHttpResponses.write(exchange, 200, generatorJson(resolveProfile(islandId)));
    }

    private void rules(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", null);
        String requestedKey = JsonFields.text(body, "generatorKey", "");
        IslandGeneratorSnapshot profile = islandId == null
            ? new IslandGeneratorSnapshot(new UUID(0L, 0L), requestedKey, JsonFields.integer(body, "level", 1), Instant.EPOCH)
            : resolveProfile(islandId);
        String generatorKey = requestedKey.isBlank() ? profile.generatorKey() : requestedKey;
        int level = islandId == null ? profile.level() : Math.max(profile.level(), JsonFields.integer(body, "level", profile.level()));
        List<GeneratorRuleSnapshot> rules = generators.rules(generatorKey).stream()
            .filter(GeneratorRuleSnapshot::enabled)
            .filter(rule -> rule.minUpgradeLevel() <= level)
            .toList();
        CoreHttpResponses.write(exchange, 200, generatorRulesJson(generatorKey, level, rules));
    }

    private IslandGeneratorSnapshot resolveProfile(UUID islandId) {
        IslandGeneratorSnapshot base = generators.profile(islandId);
        IslandUpgradeSnapshot selected = null;
        for (IslandUpgradeSnapshot upgrade : upgrades.list(islandId)) {
            if (upgrade.type() != UpgradeType.GENERATOR_LEVEL) {
                continue;
            }
            if (selected == null || upgrade.level() > selected.level()) {
                selected = upgrade;
            }
        }
        if (selected == null) {
            return base;
        }
        return new IslandGeneratorSnapshot(islandId, generatorKey(base.generatorKey(), selected.upgradeKey()), selected.level(), selected.updatedAt());
    }

    private static String generatorKey(String fallback, String upgradeKey) {
        if (upgradeKey != null && upgradeKey.toLowerCase().startsWith("generator:")) {
            String key = upgradeKey.substring(upgradeKey.indexOf(':') + 1);
            return key.isBlank() ? fallback : key;
        }
        return fallback;
    }

    public static String generatorJson(IslandGeneratorSnapshot snapshot) {
        return SimpleJson.stringify(Map.of("generator", generatorMap(snapshot)));
    }

    public static String generatorRulesJson(String generatorKey, int level, List<GeneratorRuleSnapshot> rules) {
        List<Object> renderedRules = (rules == null ? List.<GeneratorRuleSnapshot>of() : rules).stream()
            .<Object>map(GeneratorRoutes::ruleMap)
            .toList();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("generatorKey", generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey);
        values.put("level", Math.max(1, level));
        values.put("rules", renderedRules);
        return SimpleJson.stringify(values);
    }

    private static Map<String, Object> generatorMap(IslandGeneratorSnapshot snapshot) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", snapshot.islandId());
        values.put("generatorKey", snapshot.generatorKey());
        values.put("level", snapshot.level());
        values.put("updatedAt", snapshot.updatedAt());
        return values;
    }

    private static Map<String, Object> ruleMap(GeneratorRuleSnapshot rule) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("generatorKey", rule.generatorKey());
        values.put("materialKey", rule.materialKey());
        values.put("chance", rule.chance());
        values.put("minIslandLevel", rule.minIslandLevel());
        values.put("minUpgradeLevel", rule.minUpgradeLevel());
        values.put("biomeKey", rule.biomeKey());
        values.put("enabled", rule.enabled());
        return values;
    }
}
