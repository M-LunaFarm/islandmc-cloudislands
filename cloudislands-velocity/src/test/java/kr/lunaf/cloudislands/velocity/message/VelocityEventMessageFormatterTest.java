package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VelocityEventMessageFormatterTest {
    @Test
    void summarizesEventsWithoutLeakingHiddenNodeNames() {
        VelocityEventMessageFormatter formatter = new VelocityEventMessageFormatter(new VelocityRoutePrivacyFormatter(true));
        String body = """
            {"events":[{"type":"ROUTE_TICKET_READY","occurredAt":"2026-01-02T03:04:05Z","fields":{"islandId":"00000000-0000-0000-0000-000000000001","ticketId":"12345678-1234-1234-1234-123456789abc","playerUuid":"abcdef12-0000-0000-0000-000000000000","requestedNode":"island-1","targetNode":"island-2","clearedSession":"true","clearedTicket":"false"}}]}
            """;

        String message = formatter.events(body);

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
        String body = """
            {"events":[{"type":"NODE_STATE_CHANGED","occurredAt":"2026-01-02T03:04:05Z","fields":{"nodeId":"island-1","requestedNode":"island-2","reason":"drain"}}]}
            """;

        assertEquals(
            "Events: NODE_STATE_CHANGED reason=drain requestedNode=island-2 node=island-1 at=2026-01-02T03:04:05Z",
            formatter.events(body)
        );
    }

    @Test
    void summarizesAuditEntries() {
        VelocityEventMessageFormatter formatter = new VelocityEventMessageFormatter(new VelocityRoutePrivacyFormatter(false));
        String body = """
            {"audit":[{"action":"ISLAND_DELETE","actorType":"PLAYER","targetType":"ISLAND","targetId":"island-1","createdAt":"2026-01-02T03:04:05Z"}]}
            """;

        assertEquals(
            "Audit: ISLAND_DELETE target=ISLAND:island-1 actor=PLAYER at=2026-01-02T03:04:05Z",
            formatter.audit(body)
        );
    }

    @Test
    void handlesEmptyPayloads() {
        VelocityEventMessageFormatter formatter = new VelocityEventMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals("Events: empty", formatter.events("{}"));
        assertEquals("Audit: empty", formatter.audit("{}"));
    }
}
