package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.coreclient.AdminRouteClearView;
import kr.lunaf.cloudislands.coreclient.AdminRouteDebugView;
import kr.lunaf.cloudislands.coreclient.AdminRouteSessionView;
import kr.lunaf.cloudislands.coreclient.AdminRouteTicketView;
import org.junit.jupiter.api.Test;

class VelocityRouteMessageFormatterTest {
    @Test
    void summarizesRouteDebugWithoutLeakingHiddenNodeNames() {
        VelocityRouteMessageFormatter formatter = new VelocityRouteMessageFormatter(new VelocityRoutePrivacyFormatter(true));
        AdminRouteDebugView debug = new AdminRouteDebugView(
            List.of(new AdminRouteSessionView("abcdef12-0000-0000-0000-000000000000", "12345678-1234-1234-1234-123456789abc", "island-1", "island-server-1", "nonce", "2026-01-02T03:04:05Z")),
            List.of(new AdminRouteTicketView(
                "87654321-1234-1234-1234-123456789abc",
                "abcdef12-0000-0000-0000-000000000000",
                "00000000-0000-0000-0000-000000000001",
                "VISIT",
                "READY",
                "island-2",
                "world",
                "island-server-2",
                "visit",
                "target",
                "",
                "2026-01-02T03:04:05Z",
                "nonce"
            ))
        );

        String message = formatter.debug(debug);

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
        AdminRouteDebugView debug = new AdminRouteDebugView(
            List.of(new AdminRouteSessionView("abcdef12-0000-0000-0000-000000000000", "12345678-1234-1234-1234-123456789abc", "island-1", "island-server-1", "nonce", "")),
            List.of()
        );

        assertEquals(
            "Routes: sessions=1 [abcdef12 ticket=12345678 node=island-1 server=island-server-1] tickets=0",
            formatter.debug(debug)
        );
    }

    @Test
    void summarizesTicketAndClearResponses() {
        VelocityRouteMessageFormatter formatter = new VelocityRouteMessageFormatter(new VelocityRoutePrivacyFormatter(false));
        AdminRouteTicketView ticket = new AdminRouteTicketView(
            "12345678-1234-1234-1234-123456789abc",
            "abcdef12-0000-0000-0000-000000000000",
            "00000000-0000-0000-0000-000000000001",
            "HOME",
            "READY",
            "island-1",
            "world",
            "island-server-1",
            "home",
            "base",
            "",
            "2026-01-02T03:04:05Z",
            "nonce"
        );

        assertEquals("Route ticket: not found", formatter.ticket(Optional.empty()));
        assertEquals(
            "Route ticket: 12345678 HOME READY 섬=00000000 node=island-1",
            formatter.ticket(Optional.of(ticket))
        );
        assertEquals(
            "Route clear: session=true ticket=false reason=manual",
            formatter.clear(new AdminRouteClearView(true, false, "manual"))
        );
    }

    @Test
    void summarizesTypedRouteResponses() {
        VelocityRouteMessageFormatter formatter = new VelocityRouteMessageFormatter(new VelocityRoutePrivacyFormatter(false));
        AdminRouteTicketView ticket = new AdminRouteTicketView(
            "12345678-1234-1234-1234-123456789abc",
            "abcdef12-0000-0000-0000-000000000000",
            "00000000-0000-0000-0000-000000000001",
            "HOME",
            "READY",
            "island-1",
            "world",
            "island-server-1",
            "home",
            "base",
            "",
            "2026-01-02T03:04:05Z",
            "nonce"
        );
        AdminRouteDebugView debug = new AdminRouteDebugView(
            List.of(new AdminRouteSessionView("abcdef12-0000-0000-0000-000000000000", "12345678-1234-1234-1234-123456789abc", "island-1", "island-server-1", "nonce", "2026-01-02T03:04:05Z")),
            List.of(ticket)
        );

        assertEquals(
            "Routes: sessions=1 [abcdef12 ticket=12345678 node=island-1 server=island-server-1 expires=2026-01-02T03:04:05Z] tickets=1 [12345678 HOME READY 섬=00000000 node=island-1]",
            formatter.debug(debug)
        );
        assertEquals("Route ticket: 12345678 HOME READY 섬=00000000 node=island-1", formatter.ticket(Optional.of(ticket)));
        assertEquals("Route ticket: not found", formatter.ticket(Optional.empty()));
        assertEquals("Route clear: session=true ticket=false reason=manual", formatter.clear(new AdminRouteClearView(true, false, "manual")));
    }
}
