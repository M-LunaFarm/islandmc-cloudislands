package kr.lunaf.cloudislands.protocol.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRouteMessagePolicyTest {
    @Test
    void hidesPhysicalTopologyFromPlayerFacingRouteMessages() {
        String raw = "targetNode=island-2 targetServerName=Island-2 activeWorld=ci_shard_002 cell=5,3 raw=NODE_DOWN";

        String sanitized = PlayerRouteMessagePolicy.sanitize(raw);

        assertFalse(sanitized.contains("island-2"));
        assertFalse(sanitized.contains("Island-2"));
        assertFalse(sanitized.contains("ci_shard_002"));
        assertFalse(sanitized.contains("5,3"));
        assertTrue(sanitized.contains("targetNode=hidden"));
        assertTrue(sanitized.contains("targetServerName=hidden"));
        assertTrue(sanitized.contains("activeWorld=hidden"));
        assertTrue(sanitized.contains("cell=hidden"));
    }

    @Test
    void keepsLogicalPlayerMessageReadable() {
        assertEquals("Island is preparing. Please try again.", PlayerRouteMessagePolicy.sanitize("Island is preparing. Please try again."));
        assertEquals(PlayerRouteMessagePolicy.FALLBACK_MESSAGE, PlayerRouteMessagePolicy.sanitize(""));
        assertFalse(PlayerRouteMessagePolicy.containsPhysicalTopology("Island is preparing. Please try again."));
        assertTrue(PlayerRouteMessagePolicy.containsPhysicalTopology("server=Island-1"));
    }
}
