package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.cache.RedisCacheAdmin;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionStore;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;

public final class AdminRuntimeRoutes implements RouteGroup {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final RouteSessionStore sessions;
    private final RouteTicketStore tickets;
    private final RedisCacheAdmin redisCacheAdmin;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public AdminRuntimeRoutes(RouteSessionStore sessions, RouteTicketStore tickets, RedisCacheAdmin redisCacheAdmin, AuditLogger audit, GlobalEventPublisher events) {
        this.sessions = sessions;
        this.tickets = tickets;
        this.redisCacheAdmin = redisCacheAdmin;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/admin/cache/clear", this::clearCache);
        registry.route("/v1/admin/reload", this::reload);
    }

    private void clearCache(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        ClearResult result = clearRuntimeCaches();
        audit.log(SYSTEM_ACTOR, "ADMIN", "CACHE_CLEAR", "CORE", "application-cache", result.auditFields());
        events.publish(CloudIslandEventType.CORE_CACHE_CLEARED.name(), result.cacheClearEventFields());
        CoreHttpResponses.write(exchange, 202, result.json(false));
    }

    private void reload(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        ClearResult result = clearRuntimeCaches();
        audit.log(SYSTEM_ACTOR, "ADMIN", "CORE_RELOAD", "CORE", "runtime", result.reloadAuditFields());
        events.publish(CloudIslandEventType.CORE_RELOADED.name(), result.reloadEventFields());
        CoreHttpResponses.write(exchange, 202, result.json(true));
    }

    private ClearResult clearRuntimeCaches() {
        int clearedSessions = sessions.clearAll();
        int clearedTickets = tickets.clearAll();
        int clearedRedisKeys = redisCacheAdmin == null ? 0 : redisCacheAdmin.clearApplicationCaches();
        return new ClearResult(clearedSessions, clearedTickets, clearedRedisKeys);
    }

    record ClearResult(int clearedSessions, int clearedTickets, int clearedRedisKeys) {
        Map<String, String> auditFields() {
            return Map.of(
                "sessions", Integer.toString(clearedSessions),
                "tickets", Integer.toString(clearedTickets),
                "redisKeys", Integer.toString(clearedRedisKeys)
            );
        }

        Map<String, String> reloadAuditFields() {
            return Map.of(
                "clearedSessions", Integer.toString(clearedSessions),
                "clearedTickets", Integer.toString(clearedTickets),
                "clearedRedisKeys", Integer.toString(clearedRedisKeys)
            );
        }

        Map<String, String> cacheClearEventFields() {
            return Map.of(
                "scope", "application-cache",
                "sessions", Integer.toString(clearedSessions),
                "tickets", Integer.toString(clearedTickets),
                "redisKeys", Integer.toString(clearedRedisKeys)
            );
        }

        Map<String, String> reloadEventFields() {
            return reloadAuditFields();
        }

        String json(boolean reloaded) {
            return (reloaded ? "{\"reloaded\":true," : "{")
                + "\"clearedSessions\":" + clearedSessions
                + ",\"clearedTickets\":" + clearedTickets
                + ",\"clearedRedisKeys\":" + clearedRedisKeys
                + "}";
        }
    }
}
