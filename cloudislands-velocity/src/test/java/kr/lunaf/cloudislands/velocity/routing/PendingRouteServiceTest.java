package kr.lunaf.cloudislands.velocity.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;
import org.junit.jupiter.api.Test;

class PendingRouteServiceTest {
    @Test
    void prefersTargetServerNameOverTargetNode() {
        PlayerRouteSession session = new PlayerRouteSession(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "node-1",
            "Island-1",
            "nonce",
            Instant.parse("2026-01-02T03:04:05Z")
        );

        assertEquals("Island-1", PendingRouteService.targetServerName(session));
    }

    @Test
    void fallsBackToTargetNodeWhenServerNameMissing() {
        PlayerRouteSession session = new PlayerRouteSession(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "node-1",
            "",
            "nonce",
            Instant.parse("2026-01-02T03:04:05Z")
        );

        assertEquals("node-1", PendingRouteService.targetServerName(session));
    }
}
