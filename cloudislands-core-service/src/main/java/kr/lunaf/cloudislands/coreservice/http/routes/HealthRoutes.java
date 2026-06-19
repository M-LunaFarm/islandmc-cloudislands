package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class HealthRoutes implements RouteGroup {
    private static final String METRICS_CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    private final CoreServiceConfig config;
    private final Supplier<String> metrics;

    public HealthRoutes(CoreServiceConfig config, Supplier<String> metrics) {
        this.config = config;
        this.metrics = metrics;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/live", exchange -> CoreHttpResponses.write(exchange, 200, "{\"status\":\"UP\"}"));
        registry.route("/ready", this::readiness);
        registry.route("/health", this::readiness);
        registry.route("/metrics", exchange -> CoreHttpResponses.write(exchange, 200, metrics.get(), METRICS_CONTENT_TYPE));
    }

    private void readiness(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        boolean ready = config.setupDatabaseReady();
        CoreHttpResponses.write(exchange, ready ? 200 : 503, readinessJson(config, ready));
    }

    static String readinessJson(CoreServiceConfig config, boolean ready) {
        return "{\"status\":\"" + (ready ? "UP" : "DOWN") + "\""
            + ",\"databaseReady\":" + ready
            + ",\"databaseDurable\":" + config.setupDatabaseProductionDurable()
            + ",\"databaseReadiness\":\"" + escape(config.setupDatabaseFallbackReadiness()) + "\""
            + ",\"databaseEffectiveBackend\":\"" + escape(config.setupDatabaseEffectiveBackend()) + "\""
            + ",\"databaseEffectiveAuthority\":\"" + escape(config.setupDatabaseEffectiveAuthority()) + "\""
            + "}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
