package kr.lunaf.cloudislands.coreservice.ticket;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteTicket;

public interface RouteTicketStore {
    RouteTicket save(RouteTicket ticket);

    int markReadyForIsland(UUID islandId, String targetNode, String targetWorld, Map<String, String> payload);

    Optional<RouteTicket> consume(UUID ticketId, UUID playerUuid, String nodeId, String nonce);

    Optional<RouteTicket> find(UUID ticketId);

    Optional<RouteTicket> findLatestForPlayer(UUID playerUuid);

    boolean clear(UUID ticketId);

    int clearAll();

    String toJson();
}
