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
}
