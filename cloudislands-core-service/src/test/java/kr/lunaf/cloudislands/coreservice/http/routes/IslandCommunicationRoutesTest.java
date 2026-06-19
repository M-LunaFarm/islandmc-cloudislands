package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
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

        assertEquals(
            "{\"logs\":[{\"logId\":\"00000000-0000-0000-0000-000000000001\",\"islandId\":\"00000000-0000-0000-0000-000000000002\",\"actorUuid\":\"00000000-0000-0000-0000-000000000003\",\"action\":\"ISLAND_CHAT\",\"payload\":{\"message\":\"hi \\\"there\\\"\"},\"createdAt\":\"2026-01-02T03:04:05Z\"}]}",
            IslandCommunicationRoutes.islandLogsJson(List.of(new IslandLogRecord(logId, islandId, actorUuid, "ISLAND_CHAT", payload, Instant.parse("2026-01-02T03:04:05Z"))))
        );
    }
}
