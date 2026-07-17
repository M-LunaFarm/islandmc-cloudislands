package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;

public final class EventRoutes implements RouteGroup {
    private final InMemoryGlobalEventPublisher events;
    private final PlayerProfileRepository playerProfiles;

    public EventRoutes(InMemoryGlobalEventPublisher events) {
        this(events, null);
    }

    public EventRoutes(InMemoryGlobalEventPublisher events, PlayerProfileRepository playerProfiles) {
        this.events = events;
        this.playerProfiles = playerProfiles;
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
            CoreHttpResponses.write(exchange, 200, visitorStatsJson(events.visitorStatsJson(islandId, limit), playerProfiles));
        });
    }

    static String visitorStatsJson(String body, PlayerProfileRepository playerProfiles) {
        if (playerProfiles == null) {
            return body;
        }
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : root.entrySet()) {
            if (!"recentVisitors".equals(String.valueOf(entry.getKey()))) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        List<Object> recentVisitors = new ArrayList<>();
        for (Object entry : SimpleJson.list(root.get("recentVisitors"))) {
            Map<?, ?> visitor = SimpleJson.object(entry);
            LinkedHashMap<String, Object> rendered = new LinkedHashMap<>();
            for (Map.Entry<?, ?> field : visitor.entrySet()) {
                rendered.put(String.valueOf(field.getKey()), field.getValue());
            }
            UUID visitorUuid = uuid(SimpleJson.text(visitor.get("visitorUuid")));
            if (visitorUuid != null) {
                String visitorName = playerProfiles.find(visitorUuid).lastName();
                if (visitorName != null && !visitorName.isBlank()) {
                    rendered.put("visitorName", visitorName.trim());
                }
            }
            recentVisitors.add(rendered);
        }
        values.put("recentVisitors", recentVisitors);
        return SimpleJson.stringify(values);
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
