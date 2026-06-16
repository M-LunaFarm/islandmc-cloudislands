package kr.lunaf.cloudislands.coreservice.ticket;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
        ticket = sanitizeTicket(ticket);
        if (activeTicketState(ticket.state())) {
            tickets.replaceAll((_id, existing) -> {
                if (existing.ticketId().equals(ticket.ticketId())
                        || !existing.playerUuid().equals(ticket.playerUuid())
                        || !activeTicketState(existing.state())) {
                    return existing;
                }
                return new RouteTicket(existing.ticketId(), existing.playerUuid(), existing.action(), existing.islandId(), existing.targetNode(), existing.targetWorld(), RouteTicketState.EXPIRED, existing.expiresAt(), existing.nonce(), existing.payload());
            });
        }
        tickets.put(ticket.ticketId(), ticket);
        return ticket;
    }

    private RouteTicket sanitizeTicket(RouteTicket ticket) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        mergePayload(payload, ticket.payload());
        return new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), ticket.state(), ticket.expiresAt(), ticket.nonce(), Map.copyOf(payload));
    }

    private boolean activeTicketState(RouteTicketState state) {
        return state == RouteTicketState.PREPARING || state == RouteTicketState.READY;
    }

    @Override
    public int markReadyForIsland(UUID islandId, String targetNode, String targetWorld, Instant expiresAt, Map<String, String> payload) {
        int updated = 0;
        Instant now = clock.instant();
        for (RouteTicket ticket : tickets.values()) {
            if (ticket.state() != RouteTicketState.PREPARING || !ticket.islandId().equals(islandId) || !ticket.targetNode().equals(targetNode)) {
                continue;
            }
            if (ticket.expiresAt().isBefore(now)) {
                tickets.put(ticket.ticketId(), new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), RouteTicketState.EXPIRED, ticket.expiresAt(), ticket.nonce(), ticket.payload()));
                continue;
            }
            java.util.LinkedHashMap<String, String> mergedPayload = new java.util.LinkedHashMap<>(ticket.payload());
            mergePayload(mergedPayload, payload);
            RouteTicket ready = new RouteTicket(
                ticket.ticketId(),
                ticket.playerUuid(),
                ticket.action(),
                ticket.islandId(),
                ticket.targetNode(),
                targetWorld == null || targetWorld.isBlank() ? ticket.targetWorld() : targetWorld,
                RouteTicketState.READY,
                expiresAt == null ? ticket.expiresAt() : expiresAt,
                ticket.nonce(),
                Map.copyOf(mergedPayload)
            );
            tickets.put(ticket.ticketId(), ready);
            updated++;
        }
        return updated;
    }

    private void mergePayload(java.util.LinkedHashMap<String, String> target, Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            target.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
    }

    @Override
    public List<RouteTicket> markFailedForIsland(UUID islandId, String targetNode, String reason) {
        List<RouteTicket> failedTickets = new ArrayList<>();
        for (RouteTicket ticket : tickets.values()) {
            if (ticket.state() != RouteTicketState.PREPARING || !ticket.islandId().equals(islandId) || !ticket.targetNode().equals(targetNode)) {
                continue;
            }
            java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>(ticket.payload());
            payload.put("failureReason", reason == null ? "" : reason);
            RouteTicket failed = new RouteTicket(
                ticket.ticketId(),
                ticket.playerUuid(),
                ticket.action(),
                ticket.islandId(),
                ticket.targetNode(),
                ticket.targetWorld(),
                RouteTicketState.FAILED,
                ticket.expiresAt(),
                ticket.nonce(),
                Map.copyOf(payload)
            );
            tickets.put(ticket.ticketId(), failed);
            failedTickets.add(failed);
        }
        return failedTickets;
    }

    @Override
    public List<RouteTicket> markFailedForNode(String targetNode, String reason) {
        List<RouteTicket> failedTickets = new ArrayList<>();
        for (RouteTicket ticket : tickets.values()) {
            if ((ticket.state() != RouteTicketState.PREPARING && ticket.state() != RouteTicketState.READY) || !ticket.targetNode().equals(targetNode)) {
                continue;
            }
            java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>(ticket.payload());
            payload.put("failureReason", reason == null ? "" : reason);
            RouteTicket failed = new RouteTicket(
                ticket.ticketId(),
                ticket.playerUuid(),
                ticket.action(),
                ticket.islandId(),
                ticket.targetNode(),
                ticket.targetWorld(),
                RouteTicketState.FAILED,
                ticket.expiresAt(),
                ticket.nonce(),
                Map.copyOf(payload)
            );
            tickets.put(ticket.ticketId(), failed);
            failedTickets.add(failed);
        }
        return failedTickets;
    }

    public Optional<RouteTicket> consume(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        Instant now = clock.instant();
        java.util.concurrent.atomic.AtomicReference<RouteTicket> consumed = new java.util.concurrent.atomic.AtomicReference<>();
        tickets.computeIfPresent(ticketId, (_id, ticket) -> {
            if (ticket.state() != RouteTicketState.READY) {
                return ticket;
            }
            if (ticket.expiresAt().isBefore(now)) {
                return new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), RouteTicketState.EXPIRED, ticket.expiresAt(), ticket.nonce(), ticket.payload());
            }
            if (!kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.consumable(ticket, playerUuid, nodeId, nonce, now)) {
                return ticket;
            }
            RouteTicket updated = new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), RouteTicketState.CONSUMED, ticket.expiresAt(), ticket.nonce(), ticket.payload());
            consumed.set(updated);
            return updated;
        });
        return Optional.ofNullable(consumed.get());
    }

    public Optional<RouteTicket> find(UUID ticketId) {
        return Optional.ofNullable(tickets.get(ticketId));
    }

    @Override
    public Optional<RouteTicket> findLatestForPlayer(UUID playerUuid) {
        Instant now = clock.instant();
        Optional<RouteTicket> active = tickets.values().stream()
            .filter(ticket -> ticket.playerUuid().equals(playerUuid))
            .filter(ticket -> (ticket.state() == RouteTicketState.READY || ticket.state() == RouteTicketState.PREPARING) && !ticket.expiresAt().isBefore(now))
            .max((left, right) -> left.expiresAt().compareTo(right.expiresAt()));
        if (active.isPresent()) {
            return active;
        }
        return tickets.values().stream()
            .filter(ticket -> ticket.playerUuid().equals(playerUuid))
            .max((left, right) -> left.expiresAt().compareTo(right.expiresAt()));
    }

    @Override
    public Map<String, Long> countsByState() {
        Map<String, Long> counts = new java.util.HashMap<>();
        for (RouteTicketState state : RouteTicketState.values()) {
            counts.put(state.name(), 0L);
        }
        for (RouteTicket ticket : tickets.values()) {
            counts.put(ticket.state().name(), counts.getOrDefault(ticket.state().name(), 0L) + 1L);
        }
        return Map.copyOf(counts);
    }

    @Override
    public List<RouteTicket> expireStale() {
        Instant now = clock.instant();
        List<RouteTicket> expired = new ArrayList<>();
        for (RouteTicket ticket : tickets.values()) {
            if ((ticket.state() != RouteTicketState.READY && ticket.state() != RouteTicketState.PREPARING) || !ticket.expiresAt().isBefore(now)) {
                continue;
            }
            RouteTicket expiredTicket = new RouteTicket(ticket.ticketId(), ticket.playerUuid(), ticket.action(), ticket.islandId(), ticket.targetNode(), ticket.targetWorld(), RouteTicketState.EXPIRED, ticket.expiresAt(), ticket.nonce(), ticket.payload());
            tickets.put(ticket.ticketId(), expiredTicket);
            expired.add(expiredTicket);
        }
        return expired;
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
            builder.append(json(ticket));
        }
        return builder.append("]}").toString();
    }

    private String json(RouteTicket ticket) {
        return "{\"ticketId\":\"" + ticket.ticketId()
            + "\",\"playerUuid\":\"" + ticket.playerUuid()
            + "\",\"action\":\"" + ticket.action()
            + "\",\"islandId\":\"" + ticket.islandId()
            + "\",\"targetNode\":\"" + ticket.targetNode()
            + "\",\"targetWorld\":\"" + ticket.targetWorld()
            + "\",\"targetServerName\":\"" + ticket.payload().getOrDefault("targetServerName", ticket.targetNode())
            + "\",\"state\":\"" + ticket.state()
            + "\",\"expiresAt\":\"" + ticket.expiresAt()
            + "\",\"payload\":" + payloadJson(ticket.payload())
            + "}";
    }

    private String payloadJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append("}").toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
