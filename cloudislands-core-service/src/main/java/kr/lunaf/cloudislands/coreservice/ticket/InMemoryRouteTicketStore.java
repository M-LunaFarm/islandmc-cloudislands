package kr.lunaf.cloudislands.coreservice.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;

public final class InMemoryRouteTicketStore implements RouteTicketStore {
    private final Clock clock;
    private final Map<UUID, RouteTicket> tickets = new ConcurrentHashMap<>();

    public InMemoryRouteTicketStore(Clock clock) {
        this.clock = clock;
    }

    public RouteTicket save(RouteTicket ticket) {
        tickets.put(ticket.ticketId(), ticket);
        return ticket;
    }

    @Override
    public int markReadyForIsland(UUID islandId, String targetNode, String targetWorld, Map<String, String> payload) {
        int updated = 0;
        for (RouteTicket ticket : tickets.values()) {
            if (ticket.state() != RouteTicketState.PREPARING || !ticket.islandId().equals(islandId) || !ticket.targetNode().equals(targetNode)) {
                continue;
            }
            java.util.LinkedHashMap<String, String> mergedPayload = new java.util.LinkedHashMap<>(ticket.payload());
            mergedPayload.putAll(payload);
            RouteTicket ready = new RouteTicket(
                ticket.ticketId(),
                ticket.playerUuid(),
                ticket.action(),
                ticket.islandId(),
                ticket.targetNode(),
                targetWorld == null || targetWorld.isBlank() ? ticket.targetWorld() : targetWorld,
                RouteTicketState.READY,
                ticket.expiresAt(),
                ticket.nonce(),
                Map.copyOf(mergedPayload)
            );
            tickets.put(ticket.ticketId(), ready);
            updated++;
        }
        return updated;
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

    public Optional<RouteTicket> find(UUID ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }

    public boolean clear(UUID ticketId) {
        return tickets.remove(ticketId) != null;
    }

    public int clearAll() {
        int cleared = tickets.size();
        tickets.clear();
        return cleared;
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder("{\"tickets\":[");
        boolean first = true;
        for (RouteTicket ticket : tickets.values()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"ticketId\":\"").append(ticket.ticketId())
                .append("\",\"playerUuid\":\"").append(ticket.playerUuid())
                .append("\",\"action\":\"").append(ticket.action())
                .append("\",\"islandId\":\"").append(ticket.islandId())
                .append("\",\"targetNode\":\"").append(ticket.targetNode())
                .append("\",\"state\":\"").append(ticket.state())
                .append("\",\"expiresAt\":\"").append(ticket.expiresAt())
                .append("\"}");
        }
        return builder.append("]}").toString();
    }
}
