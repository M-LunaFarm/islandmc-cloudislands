package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.UUID;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class EventRoutes implements RouteGroup {
    private final InMemoryGlobalEventPublisher events;

    public EventRoutes(InMemoryGlobalEventPublisher events) {
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/events", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 512), 4096));
            long sinceSeq = Math.max(0L, JsonFields.longValue(body, "sinceSeq", 0L));
            CoreHttpResponses.write(exchange, 200, events.toJson(limit, sinceSeq));
        });
        registry.routePost("/v1/islands/visitors/stats", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 10), 100));
            CoreHttpResponses.write(exchange, 200, events.visitorStatsJson(islandId, limit));
        });
    }
}
