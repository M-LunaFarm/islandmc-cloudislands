package kr.lunaf.cloudislands.coreservice.ticket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public interface RouteTicketStore {
    RouteTicket save(RouteTicket ticket);

    int markReadyForIsland(UUID islandId, String targetNode, String targetWorld, Instant expiresAt, Map<String, String> payload);

    List<RouteTicket> markFailedForIsland(UUID islandId, String targetNode, String reason);

    List<RouteTicket> markFailedForNode(String targetNode, String reason);

    Optional<RouteTicket> consume(UUID ticketId, UUID playerUuid, String nodeId, String nonce);

    Optional<RouteTicket> find(UUID ticketId);

    Optional<RouteTicket> findLatestForPlayer(UUID playerUuid);

    Map<String, Long> countsByState();

    List<RouteTicket> expireStale();

    boolean clear(UUID ticketId);

    int clearAll();

    String toJson();
}
