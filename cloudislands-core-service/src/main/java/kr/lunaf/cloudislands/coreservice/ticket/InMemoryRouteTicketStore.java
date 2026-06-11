package kr.lunaf.cloudislands.coreservice.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;

public final class InMemoryRouteTicketStore {
    private final Clock clock;
    private final Map<UUID, RouteTicket> tickets = new ConcurrentHashMap<>();

    public InMemoryRouteTicketStore(Clock clock) {
        this.clock = clock;
    }

    public RouteTicket save(RouteTicket ticket) {
        tickets.put(ticket.ticketId(), ticket);
        return ticket;
    }

    public Optional<RouteTicket> consume(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        RouteTicket ticket = tickets.get(ticketId);
        if (ticket == null || ticket.state() != RouteTicketState.READY) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        if (ticket.expiresAt().isBefore(now) || !ticket.playerUuid().equals(playerUuid) || !ticket.targetNode().equals(nodeId) || !ticket.nonce().equals(nonce)) {
            return Optional.empty();
        }
        RouteTicket consumed = new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), RouteTicketState.CONSUMED, ticket.expiresAt(), ticket.nonce(), ticket.payload());
        tickets.put(ticketId, consumed);
        return Optional.of(consumed);
    }
}
