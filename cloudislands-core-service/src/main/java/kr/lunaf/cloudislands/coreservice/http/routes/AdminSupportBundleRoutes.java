package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.cache.RedisCacheAdmin;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionStore;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class AdminSupportBundleRoutes implements RouteGroup {
    private static final int RECENT_FAILURE_LIMIT = 20;

    private final CoreServiceConfig config;
    private final NodeRegistry nodes;
    private final IslandJobQueue jobs;
    private final RouteTicketStore tickets;
    private final RouteSessionStore sessions;
    private final InMemoryGlobalEventPublisher events;
    private final RedisCacheAdmin redisCacheAdmin;
    private final DataSource dataSource;
    private final IslandStorage storage;

    public AdminSupportBundleRoutes(
        CoreServiceConfig config,
        NodeRegistry nodes,
        IslandJobQueue jobs,
        RouteTicketStore tickets,
        RouteSessionStore sessions,
        InMemoryGlobalEventPublisher events,
        RedisCacheAdmin redisCacheAdmin,
        DataSource dataSource,
        IslandStorage storage
    ) {
        this.config = config;
        this.nodes = nodes;
        this.jobs = jobs;
        this.tickets = tickets;
        this.sessions = sessions;
        this.events = events;
        this.redisCacheAdmin = redisCacheAdmin;
        this.dataSource = dataSource;
        this.storage = storage;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/admin/support-bundle", exchange -> CoreHttpResponses.write(exchange, 200, supportBundleJson()));
    }

    String supportBundleJson() {
        return supportBundleJson(config, nodes, jobs, tickets, sessions, events, redisCacheAdmin, dataSource, storage);
    }

    static String supportBundleJson(
        CoreServiceConfig config,
        NodeRegistry nodes,
        IslandJobQueue jobs,
        RouteTicketStore tickets,
        RouteSessionStore sessions,
        InMemoryGlobalEventPublisher events,
        RedisCacheAdmin redisCacheAdmin,
        DataSource dataSource,
        IslandStorage storage
    ) {
        LinkedHashMap<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("bundleId", "support-" + Instant.now().toEpochMilli());
        bundle.put("generatedAt", Instant.now().toString());
        bundle.put("version", version(config));
        bundle.put("nodeState", nodeState(config, nodes));
        bundle.put("coreRedisDbStorage", coreRedisDbStorage(config, redisCacheAdmin, dataSource, storage));
        bundle.put("routeTicketState", routeTicketState(tickets, sessions));
        bundle.put("jobState", jobState(jobs));
        bundle.put("configRedaction", configRedaction(config));
        bundle.put("recentFailures", recentFailures(events));
        return SimpleJson.stringify(bundle);
    }

    static String maskRouteNonces(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        return json.replaceAll("(\"nonce\"\\s*:\\s*\")([^\"]*)(\")", "$1hidden$3");
    }

    private static Map<String, Object> version(CoreServiceConfig config) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        Package pkg = AdminSupportBundleRoutes.class.getPackage();
        String implementationVersion = pkg == null ? "" : pkg.getImplementationVersion();
        values.put("runtimeVersion", implementationVersion == null || implementationVersion.isBlank() ? "local-dev" : implementationVersion);
        values.put("javaVersion", System.getProperty("java.version", "unknown"));
        values.put("runtimeMode", config == null ? "unknown" : config.runtimeMode());
        values.put("repositoryMode", config == null ? "unknown" : config.repositoryMode());
        values.put("jobQueueMode", config == null ? "unknown" : config.jobQueueMode());
        values.put("eventBusMode", config == null ? "unknown" : config.eventBusMode());
        return values;
    }

    private static Map<String, Object> nodeState(CoreServiceConfig config, NodeRegistry nodes) {
        String json = nodes == null ? "{}" : nodes.toJson(config == null ? Duration.ofSeconds(5) : config.heartbeatTimeout());
        return stringKeyMap(SimpleJson.object(SimpleJson.parse(json)));
    }

    private static Map<String, Object> coreRedisDbStorage(
        CoreServiceConfig config,
        RedisCacheAdmin redisCacheAdmin,
        DataSource dataSource,
        IslandStorage storage
    ) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("core", Map.of(
            "adminApiEnabled", config != null && config.adminApiEnabled(),
            "adminListenerEnabled", config != null && config.adminListenerEnabled()
        ));
        values.put("redis", redisState(config, redisCacheAdmin));
        values.put("database", databaseState(config, dataSource));
        values.put("storage", storageState(config, storage));
        return values;
    }

    private static Map<String, Object> redisState(CoreServiceConfig config, RedisCacheAdmin redisCacheAdmin) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("uriConfigured", config != null && config.redisUri() != null);
        values.put("eventBusMode", config == null ? "unknown" : config.eventBusMode());
        values.put("jobQueueMode", config == null ? "unknown" : config.jobQueueMode());
        values.put("cacheFailuresTotal", redisCacheAdmin == null ? 0L : redisCacheAdmin.failuresTotal());
        if (config == null || config.redisUri() == null) {
            values.put("ready", false);
            values.put("detail", "not-configured");
            return values;
        }
        try (RedisRespConnection redis = new RedisRespConnection(config.redisUri())) {
            String response = redis.command("PING");
            values.put("ready", response != null && response.toUpperCase(Locale.ROOT).contains("PONG"));
            values.put("detail", "ping");
        } catch (IOException | RuntimeException exception) {
            values.put("ready", false);
            values.put("detail", exception.getClass().getSimpleName());
        }
        return values;
    }

    private static Map<String, Object> databaseState(CoreServiceConfig config, DataSource dataSource) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("backend", config == null ? "unknown" : config.setupDatabaseEffectiveBackend());
        values.put("authority", config == null ? "unknown" : config.setupDatabaseEffectiveAuthority());
        values.put("productionDurable", config != null && config.setupDatabaseProductionDurable());
        values.put("poolSize", config == null ? 0 : config.databasePoolSize());
        if (dataSource == null) {
            values.put("ready", false);
            values.put("detail", "not-configured");
            return values;
        }
        try (Connection connection = dataSource.getConnection()) {
            values.put("ready", connection.isValid(2));
            values.put("detail", "connection-valid");
        } catch (Exception exception) {
            values.put("ready", false);
            values.put("detail", exception.getClass().getSimpleName());
        }
        return values;
    }

    private static Map<String, Object> storageState(CoreServiceConfig config, IslandStorage storage) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("type", config == null ? "unknown" : config.storageType());
        values.put("bucketConfigured", config != null && !config.storageBucket().isBlank());
        values.put("sharedBackend", config != null && "S3".equalsIgnoreCase(config.storageType()));
        if (storage == null) {
            values.put("ready", false);
            values.put("detail", "not-configured");
            return values;
        }
        try {
            values.put("ready", storage.available());
            values.put("detail", "available-probe");
        } catch (IOException | RuntimeException exception) {
            values.put("ready", false);
            values.put("detail", exception.getClass().getSimpleName());
        }
        return values;
    }

    private static Map<String, Object> routeTicketState(RouteTicketStore tickets, RouteSessionStore sessions) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("ticketCounts", tickets == null ? Map.of() : new LinkedHashMap<>(tickets.countsByState()));
        values.put("sessionStoreAvailable", sessions != null);
        values.put("ticketSnapshot", tickets == null ? Map.of() : SimpleJson.object(SimpleJson.parse(maskRouteNonces(tickets.toJson()))));
        return values;
    }

    private static Map<String, Object> jobState(IslandJobQueue jobs) {
        if (jobs == null) {
            return Map.of("available", false);
        }
        return stringKeyMap(SimpleJson.object(SimpleJson.parse(JobRoutes.jobsJson(jobs))));
    }

    private static Map<String, Object> configRedaction(CoreServiceConfig config) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("secretsRedacted", true);
        values.put("redactedKeys", List.of(
            "databasePassword",
            "storageAccessKey",
            "storageSecretKey",
            "storageBearerToken",
            "coreToken",
            "nodeCredentials",
            "adminToken"
        ));
        values.put("databasePasswordConfigured", config != null && !config.databasePassword().isBlank());
        values.put("storageAccessKeyConfigured", config != null && !config.storageAccessKey().isBlank());
        values.put("storageSecretKeyConfigured", config != null && !config.storageSecretKey().isBlank());
        values.put("storageBearerTokenConfigured", config != null && !config.storageBearerToken().isBlank());
        values.put("coreTokenConfigured", config != null && !config.coreToken().isBlank());
        values.put("nodeCredentialsConfigured", config != null && !config.nodeCredentials().isBlank());
        values.put("adminTokenConfigured", config != null && !config.adminToken().isBlank());
        values.put("redactionMarker", "<redacted>");
        return values;
    }

    private static List<Map<String, Object>> recentFailures(InMemoryGlobalEventPublisher events) {
        if (events == null) {
            return List.of();
        }
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(events.toJson(100)));
        List<?> rawEvents = SimpleJson.list(root.get("events"));
        List<Map<String, Object>> failures = new ArrayList<>();
        for (Object rawEvent : rawEvents) {
            Map<?, ?> event = SimpleJson.object(rawEvent);
            String type = SimpleJson.text(event.get("type"));
            Map<?, ?> fields = SimpleJson.object(event.get("fields"));
            if (!failureEvent(type, fields)) {
                continue;
            }
            LinkedHashMap<String, Object> rendered = new LinkedHashMap<>();
            rendered.put("seq", event.get("seq"));
            rendered.put("type", type);
            rendered.put("reason", SimpleJson.text(fields.get("reason")));
            rendered.put("targetNode", SimpleJson.text(fields.get("targetNode")));
            rendered.put("occurredAt", SimpleJson.text(event.get("occurredAt")));
            failures.add(rendered);
            if (failures.size() >= RECENT_FAILURE_LIMIT) {
                break;
            }
        }
        return List.copyOf(failures);
    }

    private static boolean failureEvent(String type, Map<?, ?> fields) {
        String safeType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (safeType.contains("FAILED") || safeType.contains("FAILURE") || safeType.endsWith("_DOWN")) {
            return true;
        }
        return fields != null && !SimpleJson.text(fields.get("reason")).isBlank();
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            values.put(SimpleJson.text(entry.getKey()), entry.getValue());
        }
        return values;
    }
}
