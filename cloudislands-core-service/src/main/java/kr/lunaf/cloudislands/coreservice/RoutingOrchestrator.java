package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class RoutingOrchestrator {
    private final NodeRegistry nodes;
    private final NodeAllocator allocator;
    private final RouteTicketStore tickets;
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;
    private final IslandRuntimeRepository runtimes;
    private final IslandTemplateRepository templates;
    private final IslandJobPublisher jobs;
    private final GlobalEventPublisher events;

    public RoutingOrchestrator(NodeRegistry nodes, NodeAllocator allocator, RouteTicketStore tickets, IslandRepository islands, IslandMetadataRepository metadata, IslandRuntimeRepository runtimes, IslandTemplateRepository templates, IslandJobPublisher jobs, GlobalEventPublisher events) {
        this.nodes = nodes;
        this.allocator = allocator;
        this.tickets = tickets;
        this.islands = islands;
        this.metadata = metadata;
        this.runtimes = runtimes;
        this.templates = templates;
        this.jobs = jobs;
        this.events = events;
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
            .map(island -> warpAllowed(playerUuid, island, warpName))
            .orElseGet(() -> RoutePreparationResult.rejected(404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    public RoutePreparationResult prepareAdminTeleportRoute(UUID playerUuid, UUID islandId) {
        return islands.findById(islandId)
            .map(island -> prepareTicket(playerUuid, island, RouteAction.ADMIN_TELEPORT, Map.of("admin", "true")))
            .orElseGet(() -> RoutePreparationResult.rejected(404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    public String consumeTicketJson(String body) {
        java.util.Optional<RouteTicket> consumed = tickets.consume(
            JsonFields.uuid(body, "ticketId", new UUID(0L, 0L)),
            JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)),
            JsonFields.text(body, "nodeId", ""),
            JsonFields.text(body, "nonce", "")
        );
        consumed.ifPresent(ticket -> events.publish("ROUTE_TICKET_CONSUMED", Map.of(
            "ticketId", ticket.ticketId().toString(),
            "playerUuid", ticket.playerUuid().toString(),
            "islandId", ticket.islandId().toString(),
            "action", ticket.action().name(),
            "targetNode", ticket.targetNode()
        )));
        return consumed.map(RoutingOrchestrator::toJson).orElse("");
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
        if (!metadata.isPublicAccess(island.islandId()) && !metadata.isMember(island.islandId(), playerUuid)) {
            return RoutePreparationResult.rejected(403, ApiResponses.error("ISLAND_PRIVATE", "Island is private"));
        }
        return prepareTicket(playerUuid, island, action, extraPayload);
    }

    private RoutePreparationResult warpAllowed(UUID playerUuid, IslandSnapshot island, String warpName) {
        IslandWarpSnapshot warp = metadata.warp(island.islandId(), normalizeName(warpName)).orElse(null);
        if (warp == null) {
            return RoutePreparationResult.rejected(404, ApiResponses.error("WARP_NOT_FOUND", "Island warp was not found"));
        }
        if (metadata.isBanned(island.islandId(), playerUuid)) {
            return RoutePreparationResult.rejected(403, ApiResponses.error("VISITOR_BANNED", "Visitor is banned from this island"));
        }
        boolean member = metadata.isMember(island.islandId(), playerUuid);
        if (metadata.isLocked(island.islandId()) && !member) {
            return RoutePreparationResult.rejected(423, ApiResponses.error("ISLAND_LOCKED", "Island is locked"));
        }
        if (!warp.publicAccess() && !member) {
            return RoutePreparationResult.rejected(403, ApiResponses.error("WARP_PRIVATE", "Island warp is private"));
        }
        return prepareTicket(playerUuid, island, RouteAction.WARP, warpPayload(warp));
    }

    private RoutePreparationResult prepareTicket(UUID playerUuid, IslandSnapshot island, RouteAction action) {
        return prepareTicket(playerUuid, island, action, Map.of());
    }

    private RoutePreparationResult prepareTicket(UUID playerUuid, IslandSnapshot island, RouteAction action, Map<String, String> extraPayload) {
        try {
            IslandRuntimeSnapshot runtime = runtimes.find(island.islandId()).orElse(null);
            RoutePreparationResult unavailable = unavailableRuntime(runtime);
            if (unavailable != null) {
                return unavailable;
            }
            String templateId = islands.templateId(island.islandId()).orElse("default");
            RouteTicket saved = tickets.save(ticket(playerUuid, island.islandId(), action, extraPayload, routeTarget(runtime, templateId, templates.find(templateId).map(kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot::minNodeVersion).orElse(""), action)));
            events.publish("ROUTE_TICKET_CREATED", Map.of(
                "ticketId", saved.ticketId().toString(),
                "playerUuid", saved.playerUuid().toString(),
                "islandId", saved.islandId().toString(),
                "action", saved.action().name(),
                "targetNode", saved.targetNode(),
                "state", saved.state().name()
            ));
            return RoutePreparationResult.accepted(toJson(saved));
        } catch (IllegalStateException exception) {
            events.publish("ROUTE_TICKET_FAILED", Map.of(
                "playerUuid", playerUuid.toString(),
                "islandId", island.islandId().toString(),
                "action", action.name(),
                "reason", "NODE_UNAVAILABLE"
            ));
            return RoutePreparationResult.rejected(409, ApiResponses.error("NODE_UNAVAILABLE", "No eligible island node is available"));
        }
    }

    private RouteTicket ticket(UUID playerUuid, UUID islandId, RouteAction action, Map<String, String> extraPayload, RouteTarget target) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetServerName", target.node().velocityServerName());
        payload.putAll(extraPayload);
        return new RouteTicket(
            UUID.randomUUID(),
            playerUuid,
            action,
            islandId,
            target.node().nodeId(),
            target.worldName(),
            target.state(),
            Instant.now().plusSeconds(target.state() == RouteTicketState.PREPARING ? 120 : 30),
            UUID.randomUUID().toString(),
            Map.copyOf(payload)
        );
    }

    private RoutePreparationResult unavailableRuntime(IslandRuntimeSnapshot runtime) {
        if (runtime == null) {
            return RoutePreparationResult.rejected(409, ApiResponses.error("ISLAND_LOADING_FAILED", "Island runtime is not ready"));
        }
        IslandState state = runtime.state();
        if (state == IslandState.ACTIVE || state == IslandState.INACTIVE_READY) {
            return null;
        }
        if (state == IslandState.DELETED || state == IslandState.DELETE_REQUESTED || state == IslandState.DELETING) {
            return RoutePreparationResult.rejected(404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
        }
        return RoutePreparationResult.rejected(409, ApiResponses.error("ISLAND_LOADING_FAILED", "Island is not ready for routing"));
    }

    private RouteTarget routeTarget(IslandRuntimeSnapshot runtime, String templateId, String minNodeVersion, RouteAction action) {
        if (runtime.state() == IslandState.ACTIVE) {
            if (runtime.activeNode() == null || runtime.activeNode().isBlank()) {
                markActiveRouteRecoveryRequired(runtime, "missing_active_node");
                throw new IllegalStateException("active node is unavailable");
            }
            NodeLoad activeNode = nodes.find(runtime.activeNode()).orElse(null);
            if (activeNode == null) {
                markActiveRouteRecoveryRequired(runtime, "active_node_not_registered");
                throw new IllegalStateException("active node is unavailable");
            }
            if (!allocator.acceptsExistingRoute(activeNode, Instant.now(), templateId, minNodeVersion)) {
                markActiveRouteRecoveryRequired(runtime, "active_node_unhealthy");
                throw new IllegalStateException("active node is unavailable");
            }
            if (action == RouteAction.VISIT && activeNode.state() == NodeState.SOFT_FULL) {
                throw new IllegalStateException("visitor route denied on soft-full active node");
            }
            String worldName = runtime.activeWorld() == null || runtime.activeWorld().isBlank() ? "ci_shard_001" : runtime.activeWorld();
            return new RouteTarget(activeNode, worldName, RouteTicketState.READY);
        }
        NodeLoad selected = allocator.selectBestNode(nodes.snapshot(), Instant.now(), templateId, minNodeVersion)
            .orElseThrow(() -> new IllegalStateException("no eligible island node"));
        IslandRuntimeSnapshot activating = runtimes.markActivating(runtime.islandId(), selected.nodeId(), "ci_shard_001", 0, 0);
        jobs.publish(new IslandJob(
            UUID.randomUUID(),
            IslandJobType.ACTIVATE_ISLAND,
            runtime.islandId(),
            selected.nodeId(),
            0,
            Map.of(
                "fencingToken", Long.toString(activating.fencingToken()),
                "worldName", activating.activeWorld() == null ? "ci_shard_001" : activating.activeWorld(),
                "cellX", activating.cellX() == null ? "0" : Integer.toString(activating.cellX()),
                "cellZ", activating.cellZ() == null ? "0" : Integer.toString(activating.cellZ())
            ),
            Instant.now()
        ));
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", runtime.islandId().toString(), "state", activating.state().name(), "targetNode", selected.nodeId()));
        return new RouteTarget(selected, activating.activeWorld() == null ? "ci_shard_001" : activating.activeWorld(), RouteTicketState.PREPARING);
    }

    private void markActiveRouteRecoveryRequired(IslandRuntimeSnapshot runtime, String reason) {
        runtimes.setState(runtime.islandId(), IslandState.RECOVERY_REQUIRED);
        events.publish("ISLAND_RECOVERY_REQUIRED", Map.of(
            "islandId", runtime.islandId().toString(),
            "activeNode", runtime.activeNode() == null ? "" : runtime.activeNode(),
            "reason", reason
        ));
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

    private Map<String, String> warpPayload(IslandWarpSnapshot warp) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("warpName", warp.name());
        IslandLocation location = warp.location();
        payload.put("localX", Double.toString(location.localX()));
        payload.put("localY", Double.toString(location.localY()));
        payload.put("localZ", Double.toString(location.localZ()));
        payload.put("yaw", Float.toString(location.yaw()));
        payload.put("pitch", Float.toString(location.pitch()));
        return Map.copyOf(payload);
    }

    private String normalizeName(String name) {
        return name == null || name.isBlank() ? "default" : name.toLowerCase();
    }

    private record RouteTarget(NodeLoad node, String worldName, RouteTicketState state) {}

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
