package kr.lunaf.cloudislands.protocol.route;

import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTicketPolicyTest {
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OTHER_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Test
    void requiresReadyTicketExactPlayerNodeNonceAndLiveTtlBeforeConsume() {
        RouteTicket ticket = ticket(RouteTicketState.READY, NOW.plusSeconds(30), "island-2", "nonce-1");

        assertTrue(RouteTicketPolicy.consumable(ticket, PLAYER, "island-2", "nonce-1", NOW));
        assertFalse(RouteTicketPolicy.consumable(ticket, OTHER_PLAYER, "island-2", "nonce-1", NOW));
        assertFalse(RouteTicketPolicy.consumable(ticket, PLAYER, "island-3", "nonce-1", NOW));
        assertFalse(RouteTicketPolicy.consumable(ticket, PLAYER, "island-2", "bad-nonce", NOW));
        assertFalse(RouteTicketPolicy.consumable(ticket, PLAYER, "island-2", "", NOW));
        assertFalse(RouteTicketPolicy.consumable(ticket, PLAYER, "island-2", "nonce-1", NOW.plusSeconds(31)));
        assertFalse(RouteTicketPolicy.consumable(ticket(RouteTicketState.PREPARING, NOW.plusSeconds(30), "island-2", "nonce-1"), PLAYER, "island-2", "nonce-1", NOW));
        assertFalse(RouteTicketPolicy.consumable(ticket(RouteTicketState.CONSUMED, NOW.plusSeconds(30), "island-2", "nonce-1"), PLAYER, "island-2", "nonce-1", NOW));
    }

    @Test
    void issuesOnlyPreparingTicketsWithTargetNodeNonceAndLiveTtl() {
        assertTrue(RouteTicketPolicy.issuable(ticket(RouteTicketState.PREPARING, NOW.plusSeconds(120), "island-2", "nonce-2"), NOW));
        assertFalse(RouteTicketPolicy.issuable(ticket(RouteTicketState.READY, NOW.plusSeconds(120), "island-2", "nonce-2"), NOW));
        assertFalse(RouteTicketPolicy.issuable(ticket(RouteTicketState.PREPARING, NOW.minusSeconds(1), "island-2", "nonce-2"), NOW));
        assertFalse(RouteTicketPolicy.issuable(ticket(RouteTicketState.PREPARING, NOW.plusSeconds(120), "", "nonce-2"), NOW));
        assertFalse(RouteTicketPolicy.issuable(ticket(RouteTicketState.PREPARING, NOW.plusSeconds(120), "island-2", ""), NOW));
    }

    @Test
    void hidesPhysicalTargetNameFromPlayerFacingPolicy() {
        assertEquals("island", RouteTicketPolicy.playerVisibleTargetName(ticket(RouteTicketState.READY, NOW.plusSeconds(30), "island-2", "nonce-3")));
        assertEquals("island", RouteTicketPolicy.playerVisibleTargetName(null));
    }

    private RouteTicket ticket(RouteTicketState state, Instant expiresAt, String targetNode, String nonce) {
        return new RouteTicket(
            UUID.randomUUID(),
            PLAYER,
            RouteAction.HOME,
            ISLAND,
            targetNode,
            "ci_shard_001",
            state,
            expiresAt,
            nonce,
            Map.of("targetServerName", targetNode)
        );
    }
}
