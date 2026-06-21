package kr.lunaf.cloudislands.coreservice.ticket;

import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRouteTicketStoreTest {
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Test
    void consumesReadyTicketOnlyOnceWithMatchingPlayerNodeAndNonce() {
        InMemoryRouteTicketStore store = new InMemoryRouteTicketStore(CLOCK);
        RouteTicket ticket = store.save(ticket(RouteTicketState.READY, NOW.plusSeconds(30), "island-2", "nonce-1"));

        assertFalse(store.consume(ticket.ticketId(), PLAYER, "island-2", "bad-nonce").isPresent());
        assertFalse(store.consume(ticket.ticketId(), PLAYER, "island-3", "nonce-1").isPresent());
        assertFalse(store.consume(ticket.ticketId(), UUID.fromString("00000000-0000-0000-0000-000000000102"), "island-2", "nonce-1").isPresent());

        RouteTicket consumed = store.consume(ticket.ticketId(), PLAYER, "island-2", "nonce-1").orElseThrow();

        assertEquals(RouteTicketState.CONSUMED, consumed.state());
        assertTrue(store.find(ticket.ticketId()).orElseThrow().consumed());
        assertFalse(store.consume(ticket.ticketId(), PLAYER, "island-2", "nonce-1").isPresent());
    }

    @Test
    void expiresReadyTicketBeforeConsumeWhenTtlPassed() {
        InMemoryRouteTicketStore store = new InMemoryRouteTicketStore(CLOCK);
        RouteTicket ticket = store.save(ticket(RouteTicketState.READY, NOW.minusSeconds(1), "island-2", "nonce-2"));

        assertFalse(store.consume(ticket.ticketId(), PLAYER, "island-2", "nonce-2").isPresent());

        RouteTicket expired = store.find(ticket.ticketId()).orElseThrow();
        assertEquals(RouteTicketState.EXPIRED, expired.state());
    }

    @Test
    void savingNewActiveTicketExpiresOlderActiveTicketsForSamePlayer() {
        InMemoryRouteTicketStore store = new InMemoryRouteTicketStore(CLOCK);
        RouteTicket first = store.save(ticket(RouteTicketState.PREPARING, NOW.plusSeconds(120), "island-2", "nonce-3"));
        RouteTicket second = store.save(ticket(RouteTicketState.READY, NOW.plusSeconds(30), "island-3", "nonce-4"));

        assertEquals(RouteTicketState.EXPIRED, store.find(first.ticketId()).orElseThrow().state());
        assertEquals(RouteTicketState.READY, store.find(second.ticketId()).orElseThrow().state());
        assertEquals(second.ticketId(), store.findLatestForPlayer(PLAYER).orElseThrow().ticketId());
    }

    @Test
    void markReadySkipsExpiredPreparingTickets() {
        InMemoryRouteTicketStore store = new InMemoryRouteTicketStore(CLOCK);
        RouteTicket ticket = store.save(ticket(RouteTicketState.PREPARING, NOW.minusSeconds(1), "island-2", "nonce-5"));

        int updated = store.markReadyForIsland(ISLAND, "island-2", "ci_shard_002", NOW.plusSeconds(30), Map.of("targetServerName", "Island-2"));

        assertEquals(0, updated);
        assertEquals(RouteTicketState.EXPIRED, store.find(ticket.ticketId()).orElseThrow().state());
    }

    @Test
    void nodeFailureInvalidatesReadyTicketBeforePlayerCanConsumeIt() {
        InMemoryRouteTicketStore store = new InMemoryRouteTicketStore(CLOCK);
        RouteTicket ticket = store.save(ticket(RouteTicketState.READY, NOW.plusSeconds(30), "island-2", "nonce-6"));

        java.util.List<RouteTicket> failed = store.markFailedForNode("island-2", "NODE_DOWN_AFTER_READY");

        assertEquals(1, failed.size());
        assertEquals(RouteTicketState.FAILED, failed.getFirst().state());
        assertEquals("NODE_DOWN_AFTER_READY", failed.getFirst().payload().get("failureReason"));
        assertEquals(RouteTicketState.FAILED, store.find(ticket.ticketId()).orElseThrow().state());
        assertFalse(store.consume(ticket.ticketId(), PLAYER, "island-2", "nonce-6").isPresent());
    }

    @Test
    void rendersTicketSnapshotWithJsonParserSafePayloadEscaping() {
        InMemoryRouteTicketStore store = new InMemoryRouteTicketStore(CLOCK);
        RouteTicket ticket = store.save(new RouteTicket(
                UUID.randomUUID(),
                PLAYER,
                RouteAction.HOME,
                ISLAND,
                "island-2",
                "ci_shard_001",
                RouteTicketState.READY,
                NOW.plusSeconds(30),
                "nonce\"7",
                Map.of(
                        "targetServerName", "island-2",
                        "homeName", "farm, \"north\"",
                        "targetType", "ISLAND_HOME"
                )
        ));

        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(store.toJson()));
        Map<?, ?> rendered = SimpleJson.object(List.class.cast(root.get("tickets")).getFirst());
        Map<?, ?> payload = SimpleJson.object(rendered.get("payload"));

        assertEquals(ticket.ticketId().toString(), SimpleJson.text(rendered.get("ticketId")));
        assertEquals("nonce\"7", SimpleJson.text(rendered.get("nonce")));
        assertEquals("farm, \"north\"", SimpleJson.text(payload.get("homeName")));
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
