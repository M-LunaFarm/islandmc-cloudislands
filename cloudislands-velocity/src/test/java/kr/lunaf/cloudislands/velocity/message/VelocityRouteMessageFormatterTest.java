package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VelocityRouteMessageFormatterTest {
    @Test
    void summarizesRouteDebugWithoutLeakingHiddenNodeNames() {
        VelocityRouteMessageFormatter formatter = new VelocityRouteMessageFormatter(new VelocityRoutePrivacyFormatter(true));
        String body = """
            {"sessions":[{"playerUuid":"abcdef12-0000-0000-0000-000000000000","ticketId":"12345678-1234-1234-1234-123456789abc","targetNode":"island-1","targetServerName":"island-server-1","expiresAt":"2026-01-02T03:04:05Z"}],"tickets":[{"ticketId":"87654321-1234-1234-1234-123456789abc","action":"VISIT","state":"READY","islandId":"00000000-0000-0000-0000-000000000001","targetNode":"island-2"}]}
            """;

        String message = formatter.debug(body);

        assertTrue(message.contains("Routes: sessions=1"));
        assertTrue(message.contains("abcdef12 ticket=12345678"));
        assertTrue(message.contains("87654321 VISIT READY 섬=00000000"));
        assertFalse(message.contains("island-1"));
        assertFalse(message.contains("island-2"));
        assertFalse(message.contains("island-server-1"));
    }

    @Test
    void summarizesRouteDebugWithVisibleNodeNames() {
        VelocityRouteMessageFormatter formatter = new VelocityRouteMessageFormatter(new VelocityRoutePrivacyFormatter(false));
        String body = """
            {"sessions":[{"playerUuid":"abcdef12-0000-0000-0000-000000000000","ticketId":"12345678-1234-1234-1234-123456789abc","targetNode":"island-1","targetServerName":"island-server-1"}],"tickets":[]}
            """;

        assertEquals(
            "Routes: sessions=1 [abcdef12 ticket=12345678 node=island-1 server=island-server-1] tickets=0",
            formatter.debug(body)
        );
    }

    @Test
    void summarizesTicketAndClearResponses() {
        VelocityRouteMessageFormatter formatter = new VelocityRouteMessageFormatter(new VelocityRoutePrivacyFormatter(false));

        assertEquals("Route ticket: not found", formatter.ticket(""));
        assertEquals("Route ticket: failed code=NOT_FOUND", formatter.ticket("{\"code\":\"NOT_FOUND\"}"));
        assertEquals(
            "Route ticket: 12345678 HOME READY 섬=00000000 node=island-1",
            formatter.ticket("{\"ticketId\":\"12345678-1234-1234-1234-123456789abc\",\"action\":\"HOME\",\"state\":\"READY\",\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"targetNode\":\"island-1\"}")
        );
        assertEquals(
            "Route clear: session=true ticket=false reason=manual",
            formatter.clear("{\"clearedSession\":true,\"clearedTicket\":false,\"reason\":\"manual\"}")
        );
    }
}
