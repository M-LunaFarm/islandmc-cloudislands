package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;

public final class RoutingOrchestrator {
    private final InMemoryNodeRegistry nodes;
    private final NodeAllocator allocator;
    private final InMemoryRouteTicketStore tickets;
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;

    public RoutingOrchestrator(InMemoryNodeRegistry nodes, NodeAllocator allocator, InMemoryRouteTicketStore tickets, IslandRepository islands, IslandMetadataRepository metadata) {
        this.nodes = nodes;
        this.allocator = allocator;
        this.tickets = tickets;
        this.islands = islands;
        this.metadata = metadata;
    }

    public RoutePreparationResult prepareHomeRoute(UUID playerUuid) {
        return prepareHomeRoute(playerUuid, "default");
    }

    public RoutePreparationResult prepareHomeRoute(UUID playerUuid, String homeName) {
        return islands.findByOwner(playerUuid)
            .map(island -> prepareTicket(playerUuid, island, RouteAction.HOME, homePayload(island.islandId(), homeName)))
            .orElseGet(() -> RoutePreparationResult.rejected(404, ApiResponses.error("ISLAND_NOT_FOUND", "Player does not own an island")));
    }

    public RoutePreparationResult prepareVisitRoute(UUID playerUuid, UUID islandId) {
        return islands.findById(islandId)
            .map(island -> visitAllowed(playerUuid, island))
            .orElseGet(() -> RoutePreparationResult.rejected(404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    public RoutePreparationResult prepareRandomVisitRoute(UUID playerUuid) {
        List<UUID> candidates = new ArrayList<>(metadata.publicIslandIds(64));
        Collections.shuffle(candidates);
        UUID ownedIsland = islands.findByOwner(playerUuid).map(IslandSnapshot::islandId).orElse(null);
        for (UUID islandId : candidates) {
            if (islandId.equals(ownedIsland)) {
                continue;
            }
            IslandSnapshot island = islands.findById(islandId).orElse(null);
            if (island == null || metadata.isBanned(islandId, playerUuid) || metadata.isLocked(islandId)) {
                continue;
            }
            RoutePreparationResult result = prepareTicket(playerUuid, island, RouteAction.VISIT);
            if (result.status() == 202) {
                return result;
            }
        }
        return RoutePreparationResult.rejected(404, ApiResponses.error("PUBLIC_ISLAND_NOT_FOUND", "No public island is available"));
    }

    public RoutePreparationResult prepareWarpRoute(UUID playerUuid, UUID islandId, String warpName) {
        return islands.findById(islandId)
            .map(island -> visitAllowed(playerUuid, island, RouteAction.WARP, Map.of("warpName", warpName)))
            .orElseGet(() -> RoutePreparationResult.rejected(404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    public RoutePreparationResult prepareAdminTeleportRoute(UUID playerUuid, UUID islandId) {
        return islands.findById(islandId)
            .map(island -> prepareTicket(playerUuid, island, RouteAction.ADMIN_TELEPORT, Map.of("admin", "true")))
            .orElseGet(() -> RoutePreparationResult.rejected(404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    public String consumeTicketJson(String body) {
        return tickets.consume(
            JsonFields.uuid(body, "ticketId", new UUID(0L, 0L)),
            JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)),
            JsonFields.text(body, "nodeId", ""),
            JsonFields.text(body, "nonce", "")
        ).map(RoutingOrchestrator::toJson).orElse("");
    }

    private RoutePreparationResult visitAllowed(UUID playerUuid, IslandSnapshot island) {
        return visitAllowed(playerUuid, island, RouteAction.VISIT, Map.of());
    }

    private RoutePreparationResult visitAllowed(UUID playerUuid, IslandSnapshot island, RouteAction action, Map<String, String> extraPayload) {
        if (metadata.isBanned(island.islandId(), playerUuid)) {
            return RoutePreparationResult.rejected(403, ApiResponses.error("VISITOR_BANNED", "Visitor is banned from this island"));
        }
        if (metadata.isLocked(island.islandId()) && !metadata.isMember(island.islandId(), playerUuid)) {
            return RoutePreparationResult.rejected(423, ApiResponses.error("ISLAND_LOCKED", "Island is locked"));
        }
        if (!island.publicAccess() && !metadata.isMember(island.islandId(), playerUuid)) {
            return RoutePreparationResult.rejected(403, ApiResponses.error("ISLAND_PRIVATE", "Island is private"));
        }
        return prepareTicket(playerUuid, island, action, extraPayload);
    }

    private RoutePreparationResult prepareTicket(UUID playerUuid, IslandSnapshot island, RouteAction action) {
        return prepareTicket(playerUuid, island, action, Map.of());
    }

    private RoutePreparationResult prepareTicket(UUID playerUuid, IslandSnapshot island, RouteAction action, Map<String, String> extraPayload) {
        try {
            return RoutePreparationResult.accepted(toJson(tickets.save(ticket(playerUuid, island.islandId(), action, extraPayload))));
        } catch (IllegalStateException exception) {
            return RoutePreparationResult.rejected(409, ApiResponses.error("NODE_UNAVAILABLE", "No eligible island node is available"));
        }
    }

    private RouteTicket ticket(UUID playerUuid, UUID islandId, RouteAction action, Map<String, String> extraPayload) {
        NodeLoad selected = allocator.selectBestNode(nodes.snapshot(), Instant.now())
            .orElseThrow(() -> new IllegalStateException("no eligible island node"));
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetServerName", selected.velocityServerName());
        payload.putAll(extraPayload);
        return new RouteTicket(
            UUID.randomUUID(),
            playerUuid,
            action,
            islandId,
            selected.nodeId(),
            "ci_shard_001",
            RouteTicketState.READY,
            Instant.now().plusSeconds(30),
            UUID.randomUUID().toString(),
            Map.copyOf(payload)
        );
    }

    private Map<String, String> homePayload(UUID islandId, String homeName) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        String normalized = homeName == null || homeName.isBlank() ? "default" : homeName.toLowerCase();
        payload.put("homeName", normalized);
        IslandHomeSnapshot home = metadata.home(islandId, normalized).orElse(null);
        if (home != null) {
            IslandLocation location = home.location();
            payload.put("localX", Double.toString(location.localX()));
            payload.put("localY", Double.toString(location.localY()));
            payload.put("localZ", Double.toString(location.localZ()));
            payload.put("yaw", Float.toString(location.yaw()));
            payload.put("pitch", Float.toString(location.pitch()));
        }
        return Map.copyOf(payload);
    }

    public static String toJson(RouteTicket ticket) {
        StringBuilder builder = new StringBuilder("{")
            .append("\"ticketId\":\"").append(ticket.ticketId()).append("\",")
            .append("\"playerUuid\":\"").append(ticket.playerUuid()).append("\",")
            .append("\"action\":\"").append(ticket.action()).append("\",")
            .append("\"islandId\":\"").append(ticket.islandId()).append("\",")
            .append("\"targetNode\":\"").append(ticket.targetNode()).append("\",")
            .append("\"targetServerName\":\"").append(ticket.payload().getOrDefault("targetServerName", ticket.targetNode())).append("\",")
            .append("\"targetWorld\":\"").append(ticket.targetWorld()).append("\",")
            .append("\"state\":\"").append(ticket.state()).append("\",")
            .append("\"expiresAt\":\"").append(ticket.expiresAt()).append("\",")
            .append("\"nonce\":\"").append(ticket.nonce()).append("\"");
        for (Map.Entry<String, String> entry : ticket.payload().entrySet()) {
            if (!entry.getKey().equals("targetServerName")) {
                builder.append(',').append("\"").append(entry.getKey()).append("\":\"").append(entry.getValue().replace("\"", "'")).append("\"");
            }
        }
        return builder.append("}").toString();
    }
}
