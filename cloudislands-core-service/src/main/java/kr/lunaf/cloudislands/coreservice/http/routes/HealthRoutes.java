package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class HealthRoutes implements RouteGroup {
    private static final String METRICS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final CoreServiceConfig config;
    private final Supplier<String> metrics;
    private final List<ReadinessProbe> probes;

    public HealthRoutes(CoreServiceConfig config, Supplier<String> metrics) {
        this(config, metrics, List.of());
    }

    public HealthRoutes(CoreServiceConfig config, Supplier<String> metrics, List<ReadinessProbe> probes) {
        this.config = config;
        this.metrics = metrics;
        this.probes = probes == null ? List.of() : List.copyOf(probes);
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routeGet("/live", exchange -> CoreHttpResponses.write(exchange, 200, SimpleJson.stringify(Map.of("status", "UP"))));
        registry.routeGet("/ready", this::readiness);
        registry.routeGet("/health", this::readiness);
        registry.routeMethods("/metrics", exchange -> CoreHttpResponses.write(exchange, 200, metrics.get(), METRICS_CONTENT_TYPE), "GET", "POST");
    }

    private void readiness(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        List<ReadinessCheck> checks = readinessChecks(config, probes);
        boolean ready = checks.stream().allMatch(ReadinessCheck::ready);
        CoreHttpResponses.write(exchange, ready ? 200 : 503, readinessJson(config, ready, checks));
    }

    static String readinessJson(CoreServiceConfig config, boolean ready) {
        return readinessJson(config, ready, readinessChecks(config, List.of()));
    }

    static String readinessJson(CoreServiceConfig config, boolean ready, List<ReadinessCheck> checks) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        boolean databaseReady = checks.stream()
            .filter(check -> "database-authority".equals(check.name()))
            .findFirst()
            .map(ReadinessCheck::ready)
            .orElse(config.setupDatabaseReady());
        values.put("status", ready ? "UP" : "DOWN");
        values.put("databaseReady", databaseReady);
        values.put("databaseDurable", config.setupDatabaseProductionDurable());
        values.put("databaseReadiness", config.setupDatabaseFallbackReadiness());
        values.put("databaseEffectiveBackend", config.setupDatabaseEffectiveBackend());
        values.put("databaseEffectiveAuthority", config.setupDatabaseEffectiveAuthority());
        values.put("checks", checks.stream().map(ReadinessCheck::toMap).toList());
        return SimpleJson.stringify(values);
    }

    static List<ReadinessCheck> readinessChecks(CoreServiceConfig config, List<ReadinessProbe> probes) {
        List<ReadinessCheck> checks = new ArrayList<>();
        boolean databaseReady = config.setupDatabaseReady();
        checks.add(new ReadinessCheck("database-authority", databaseReady, config.setupDatabaseFallbackReadiness()));
        List<ReadinessProbe> safeProbes = probes == null ? List.of() : probes;
        for (ReadinessProbe probe : safeProbes) {
            if (probe == null) {
                continue;
            }
            try {
                ProbeResult result = probe.probe().get();
                checks.add(new ReadinessCheck(probe.name(), result.ready(), result.detail()));
            } catch (RuntimeException exception) {
                checks.add(new ReadinessCheck(probe.name(), false, exception.getClass().getSimpleName()));
            }
        }
        return List.copyOf(checks);
    }

    public record ReadinessProbe(String name, Supplier<ProbeResult> probe) {
        public ReadinessProbe {
            name = name == null || name.isBlank() ? "unknown" : name.trim();
            probe = probe == null ? () -> ProbeResult.down("missing-probe") : probe;
        }
    }

    public record ProbeResult(boolean ready, String detail) {
        public ProbeResult {
            detail = detail == null || detail.isBlank() ? (ready ? "ready" : "not-ready") : detail;
        }

        public static ProbeResult up(String detail) {
            return new ProbeResult(true, detail);
        }

        public static ProbeResult down(String detail) {
            return new ProbeResult(false, detail);
        }
    }

    record ReadinessCheck(String name, boolean ready, String detail) {
        private Map<String, Object> toMap() {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            values.put("name", name);
            values.put("ready", ready);
            values.put("detail", detail == null || detail.isBlank() ? (ready ? "ready" : "not-ready") : detail);
            return values;
        }
    }
}
