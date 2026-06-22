package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminAuditEntryView;
import kr.lunaf.cloudislands.coreclient.AdminEventStreamView;
import kr.lunaf.cloudislands.coreclient.AdminEventView;
import org.junit.jupiter.api.Test;

class VelocityEventMessageFormatterTest {
    @Test
    void summarizesEventsWithoutLeakingHiddenNodeNames() {
        VelocityEventMessageFormatter formatter = new VelocityEventMessageFormatter(new VelocityRoutePrivacyFormatter(true));

        String message = formatter.events(new AdminEventStreamView(1L, 1L, List.of(new AdminEventView(
            1L,
            "ROUTE_TICKET_READY",
            Map.of(
                "islandId", "00000000-0000-0000-0000-000000000001",
                "ticketId", "12345678-1234-1234-1234-123456789abc",
                "playerUuid", "abcdef12-0000-0000-0000-000000000000",
                "requestedNode", "island-1",
                "targetNode", "island-2",
                "clearedSession", "true",
                "clearedTicket", "false"
            ),
            "2026-01-02T03:04:05Z"
        ))));

        assertTrue(message.contains("ROUTE_TICKET_READY"));
        assertTrue(message.contains("ticket=12345678"));
        assertTrue(message.contains("player=abcdef12"));
        assertTrue(message.contains("session=true"));
        assertFalse(message.contains("island-1"));
        assertFalse(message.contains("island-2"));
    }

    @Test
    void summarizesEventsWithVisibleNodeNames() {
        VelocityEventMessageFormatter formatter = new VelocityEventMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "Events: NODE_STATE_CHANGED reason=drain requestedNode=island-2 node=island-1 at=2026-01-02T03:04:05Z",
            formatter.events(new AdminEventStreamView(1L, 1L, List.of(new AdminEventView(
                1L,
                "NODE_STATE_CHANGED",
                Map.of("nodeId", "island-1", "requestedNode", "island-2", "reason", "drain"),
                "2026-01-02T03:04:05Z"
            ))))
        );
    }

    @Test
    void summarizesAuditEntries() {
        VelocityEventMessageFormatter formatter = new VelocityEventMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals(
            "Audit: ISLAND_DELETE target=ISLAND:island-1 actor=PLAYER at=2026-01-02T03:04:05Z",
            formatter.audit(List.of(new AdminAuditEntryView("", "", "PLAYER", "ISLAND_DELETE", "ISLAND", "island-1", Map.of(), "2026-01-02T03:04:05Z")))
        );
    }

    @Test
    void handlesEmptyPayloads() {
        VelocityEventMessageFormatter formatter = new VelocityEventMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals("Events: empty", formatter.events(new AdminEventStreamView(0L, 0L, List.of())));
        assertEquals("Audit: empty", formatter.audit(List.of()));
    }
}
