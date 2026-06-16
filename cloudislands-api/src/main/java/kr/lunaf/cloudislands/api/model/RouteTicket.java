package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record RouteTicket(
    UUID ticketId,
    UUID playerUuid,
    RouteAction action,
    UUID islandId,
    String targetNode,
    String targetWorld,
    RouteTicketState state,
    Instant expiresAt,
    String nonce,
    Map<String, String> payload
) {
    public RouteTicket {
        LinkedHashMap<String, String> safePayload = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<String, String> entry : payload.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                safePayload.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
        }
        payload = Map.copyOf(safePayload);
    }

    public boolean preparing() {
        return state == RouteTicketState.PREPARING;
    }

    public boolean ready() {
        return state == RouteTicketState.READY;
    }

    public boolean consumed() {
        return state == RouteTicketState.CONSUMED;
    }

    public boolean terminal() {
        return state == RouteTicketState.CONSUMED
            || state == RouteTicketState.EXPIRED
            || state == RouteTicketState.CANCELLED
            || state == RouteTicketState.FAILED;
    }

    public boolean expiredAt(Instant now) {
        return expiresAt != null && now != null && expiresAt.isBefore(now);
    }

    public boolean consumableAt(Instant now) {
        return ready() && !expiredAt(now);
    }
}
