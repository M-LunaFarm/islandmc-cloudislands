package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import org.junit.jupiter.api.Test;

class EventRoutesTest {
    @Test
    void registersEventEndpointAsRouteGroup() {
        List<String> paths = new ArrayList<>();
        EventRoutes routes = new EventRoutes(new InMemoryGlobalEventPublisher());

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(List.of("/v1/events", "/v1/islands/visitors/stats"), paths);
    }

    @Test
    void visitorStatsCountsUniqueVisitorsFromVisitedEvents() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID otherIslandId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        events.publish(CloudIslandEventType.ISLAND_VISITED.name(), Map.of("islandId", islandId.toString(), "visitorUuid", "a"));
        events.publish(CloudIslandEventType.ISLAND_VISITED.name(), Map.of("islandId", islandId.toString(), "visitorUuid", "a"));
        events.publish(CloudIslandEventType.ISLAND_VISITED.name(), Map.of("islandId", islandId.toString(), "visitorUuid", "b"));
        events.publish(CloudIslandEventType.ISLAND_VISITED.name(), Map.of("islandId", otherIslandId.toString(), "visitorUuid", "c"));

        String json = events.visitorStatsJson(islandId, 10);

        assertTrue(json.contains("\"totalVisits\":3"));
        assertTrue(json.contains("\"uniqueVisitors\":2"));
        assertTrue(json.contains("\"visitorUuid\":\"a\""));
        assertTrue(json.contains("\"visitorUuid\":\"b\""));
    }
}
