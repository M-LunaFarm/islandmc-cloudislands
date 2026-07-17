package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminEventView;
import org.junit.jupiter.api.Test;

class AdminEventMenuTest {
    @Test
    void newestEventsRenderFirst() {
        List<AdminEventView> events = List.of(
            event(7L, "OLDER", Map.of()),
            event(9L, "NEWEST", Map.of()),
            event(8L, "MIDDLE", Map.of())
        );

        assertEquals(List.of(9L, 8L, 7L), AdminEventMenu.newestFirst(events).stream().map(AdminEventView::seq).toList());
    }

    @Test
    void eventLoreSanitizesAndBoundsCoreFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("islandId", "island-a");
        fields.put("nodeId", "node-a");
        fields.put("playerUuid", "player-a");
        fields.put("reason", "timeout\nsecond-line");
        fields.put("requestedNode", "node-b");
        fields.put("ticketId", "ticket-a");
        fields.put("extra", "hidden-after-limit");
        AdminEventView event = event(42L, "ROUTE_FAILED", fields);

        List<String> lore = AdminEventMenu.eventLore(event, null);

        assertTrue(lore.stream().noneMatch(line -> line.contains("\n")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("시퀀스: 42")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("reason = timeout second-line")));
        assertTrue(lore.stream().anyMatch(line -> line.contains("추가 필드: 1")));
    }

    private static AdminEventView event(long seq, String type, Map<String, String> fields) {
        return new AdminEventView(seq, type, fields, "2026-07-17T12:00:00Z");
    }
}
