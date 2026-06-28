package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.generator.DefaultGeneratorRules;
import kr.lunaf.cloudislands.coreservice.generator.IslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpException;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;

public final class AdminGeneratorRoutes implements RouteGroup {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final IslandGeneratorRepository generators;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public AdminGeneratorRoutes(IslandGeneratorRepository generators, AuditLogger audit, GlobalEventPublisher events) {
        this.generators = generators;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/admin/generators/reload", this::reload);
        registry.routePost("/v1/admin/generators/set", this::set);
    }

    private void reload(HttpExchange exchange) throws IOException {
        Map<String, List<GeneratorRuleSnapshot>> grouped = DefaultGeneratorRules.all().stream()
            .collect(Collectors.groupingBy(GeneratorRuleSnapshot::generatorKey, LinkedHashMap::new, Collectors.toList()));
        int ruleCount = 0;
        for (Map.Entry<String, List<GeneratorRuleSnapshot>> entry : grouped.entrySet()) {
            ruleCount += generators.setRules(entry.getKey(), entry.getValue()).size();
        }
        Result result = new Result(true, grouped.size(), ruleCount);
        audit.log(SYSTEM_ACTOR, "ADMIN", "GENERATOR_RULES_RELOAD", "CORE", "generator-rules", result.auditFields());
        events.publish(CloudIslandEventType.CORE_CACHE_CLEARED.name(), result.eventFields());
        CoreHttpResponses.write(exchange, 202, result.json());
    }

    private void set(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String generatorKey = safeGeneratorKey(JsonFields.text(body, "generatorKey", "default"));
        List<GeneratorRuleSnapshot> rules = rules(body, generatorKey);
        validateChanceSum(rules);
        List<GeneratorRuleSnapshot> stored = generators.setRules(generatorKey, rules);
        Result result = new Result(false, 1, stored.size());
        audit.log(SYSTEM_ACTOR, "ADMIN", "GENERATOR_RULES_SET", "CORE", generatorKey, result.auditFields());
        events.publish(CloudIslandEventType.CORE_CACHE_CLEARED.name(), result.eventFields());
        CoreHttpResponses.write(exchange, 202, setJson(generatorKey, stored));
    }

    static List<GeneratorRuleSnapshot> rules(String body, String fallbackGeneratorKey) {
        Map<?, ?> object = SimpleJson.object(SimpleJson.parse(body));
        return SimpleJson.list(object.get("rules")).stream()
            .map(SimpleJson::object)
            .map(rule -> new GeneratorRuleSnapshot(
                safeGeneratorKey(text(rule.get("generatorKey"), fallbackGeneratorKey)),
                text(rule.get("materialKey"), "minecraft:cobblestone"),
                decimal(rule.get("chance"), 0.0D),
                integer(rule.get("minIslandLevel"), 0),
                integer(rule.get("minUpgradeLevel"), 0),
                text(rule.get("biomeKey"), "*"),
                bool(rule.get("enabled"), true)
            ))
            .toList();
    }

    static void validateChanceSum(List<GeneratorRuleSnapshot> rules) {
        double sum = rules.stream()
            .filter(GeneratorRuleSnapshot::enabled)
            .mapToDouble(GeneratorRuleSnapshot::chance)
            .sum();
        if (rules.isEmpty() || sum <= 0.0D || sum > 100.0D) {
            throw new CoreHttpException(400, "INVALID_GENERATOR_RULES", "Enabled generator rule chances must sum to more than 0 and at most 100");
        }
    }

    static String setJson(String generatorKey, List<GeneratorRuleSnapshot> rules) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("generatorKey", safeGeneratorKey(generatorKey));
        values.put("ruleCount", rules == null ? 0 : rules.size());
        values.put("rules", (rules == null ? List.<GeneratorRuleSnapshot>of() : rules).stream()
            .<Object>map(AdminGeneratorRoutes::ruleMap)
            .toList());
        return SimpleJson.stringify(values);
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

    private static String text(Object value, String fallback) {
        return value == null ? fallback : SimpleJson.text(value);
    }

    private static int integer(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        long number = SimpleJson.number(value);
        try {
            return Math.toIntExact(number);
        } catch (ArithmeticException exception) {
            throw new CoreHttpException(400, "INVALID_REQUEST", "Generator rule integer field is outside the integer range");
        }
    }

    private static double decimal(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number number)) {
            throw new CoreHttpException(400, "INVALID_REQUEST", "Generator rule chance must be a number");
        }
        double decimal = number.doubleValue();
        if (!Double.isFinite(decimal)) {
            throw new CoreHttpException(400, "INVALID_REQUEST", "Generator rule chance must be finite");
        }
        return decimal;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new CoreHttpException(400, "INVALID_REQUEST", "Generator rule enabled must be a boolean");
    }

    private static String safeGeneratorKey(String generatorKey) {
        return generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey.trim().toLowerCase();
    }

    record Result(boolean reloaded, int generatorKeys, int ruleCount) {
        Map<String, String> auditFields() {
            return Map.of(
                "generatorKeys", Integer.toString(generatorKeys),
                "ruleCount", Integer.toString(ruleCount)
            );
        }

        Map<String, String> eventFields() {
            return Map.of(
                "cacheTargets", "GENERATOR_RULES",
                "generatorKeys", Integer.toString(generatorKeys),
                "ruleCount", Integer.toString(ruleCount)
            );
        }

        String json() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("reloaded", reloaded);
            values.put("generatorKeys", generatorKeys);
            values.put("ruleCount", ruleCount);
            return SimpleJson.stringify(values);
        }
    }
}
