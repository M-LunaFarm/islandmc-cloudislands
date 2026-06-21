package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

class IslandCommunicationRoutesTest {
    @Test
    void registersIslandCommunicationEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandCommunicationRoutes routes = new IslandCommunicationRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/islands/logs"));
        assertTrue(paths.contains("/v1/islands/chat"));
    }

    @Test
    void rendersIslandLogContract() {
        UUID logId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        LinkedHashMap<String, String> payload = new LinkedHashMap<>();
        payload.put("message", "hi \"there\"");

        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(
            IslandCommunicationRoutes.islandLogsJson(List.of(new IslandLogRecord(logId, islandId, actorUuid, "ISLAND_CHAT", payload, Instant.parse("2026-01-02T03:04:05Z"))))
        ));
        Map<?, ?> log = SimpleJson.object(SimpleJson.list(root.get("logs")).get(0));
        Map<?, ?> renderedPayload = SimpleJson.object(log.get("payload"));
        Map<?, ?> accepted = SimpleJson.object(SimpleJson.parse(IslandCommunicationRoutes.chatAcceptedJson("ISLAND", "hi \"there\"")));

        assertEquals(logId.toString(), SimpleJson.text(log.get("logId")));
        assertEquals(islandId.toString(), SimpleJson.text(log.get("islandId")));
        assertEquals(actorUuid.toString(), SimpleJson.text(log.get("actorUuid")));
        assertEquals("ISLAND_CHAT", SimpleJson.text(log.get("action")));
        assertEquals("hi \"there\"", SimpleJson.text(renderedPayload.get("message")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(log.get("createdAt")));
        assertEquals(true, accepted.get("accepted"));
        assertEquals("ISLAND", SimpleJson.text(accepted.get("channel")));
        assertEquals("hi \"there\"", SimpleJson.text(accepted.get("message")));
    }
}
