package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketJson;
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
    private final String islandPool;
    private final Duration routeTicketTtl;
    private final Duration routePreparingTicketTtl;
    private final RedisActivationLock activationLock;
    private final RouteAccessPolicy accessPolicy;
    private final RouteTicketService routeTickets;
    private final RoutingDiagnosticsService diagnostics;

    public RoutingOrchestrator(NodeRegistry nodes, NodeAllocator allocator, RouteTicketStore tickets, IslandRepository islands, IslandMetadataRepository metadata, IslandRuntimeRepository runtimes, IslandTemplateRepository templates, IslandJobPublisher jobs, GlobalEventPublisher events) {
        this(nodes, allocator, tickets, islands, metadata, runtimes, templates, jobs, events, "island");
    }

    public RoutingOrchestrator(NodeRegistry nodes, NodeAllocator allocator, RouteTicketStore tickets, IslandRepository islands, IslandMetadataRepository metadata, IslandRuntimeRepository runtimes, IslandTemplateRepository templates, IslandJobPublisher jobs, GlobalEventPublisher events, String islandPool) {
        this(nodes, allocator, tickets, islands, metadata, runtimes, templates, jobs, events, islandPool, Duration.ofSeconds(30), Duration.ofSeconds(120));
    }

    public RoutingOrchestrator(NodeRegistry nodes, NodeAllocator allocator, RouteTicketStore tickets, IslandRepository islands, IslandMetadataRepository metadata, IslandRuntimeRepository runtimes, IslandTemplateRepository templates, IslandJobPublisher jobs, GlobalEventPublisher events, String islandPool, Duration routeTicketTtl, Duration routePreparingTicketTtl) {
        this(nodes, allocator, tickets, islands, metadata, runtimes, templates, jobs, events, islandPool, routeTicketTtl, routePreparingTicketTtl, null);
    }

    public RoutingOrchestrator(NodeRegistry nodes, NodeAllocator allocator, RouteTicketStore tickets, IslandRepository islands, IslandMetadataRepository metadata, IslandRuntimeRepository runtimes, IslandTemplateRepository templates, IslandJobPublisher jobs, GlobalEventPublisher events, String islandPool, Duration routeTicketTtl, Duration routePreparingTicketTtl, RedisActivationLock activationLock) {
        this.nodes = nodes;
        this.allocator = allocator;
        this.tickets = tickets;
        this.islands = islands;
        this.metadata = metadata;
        this.runtimes = runtimes;
        this.templates = templates;
        this.jobs = jobs;
        this.events = events;
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.routeTicketTtl = routeTicketTtl == null || routeTicketTtl.isNegative() || routeTicketTtl.isZero() ? Duration.ofSeconds(30) : routeTicketTtl;
        this.routePreparingTicketTtl = routePreparingTicketTtl == null || routePreparingTicketTtl.isNegative() || routePreparingTicketTtl.isZero() ? Duration.ofSeconds(120) : routePreparingTicketTtl;
        this.activationLock = activationLock;
        this.accessPolicy = new RouteAccessPolicy(metadata);
        this.routeTickets = new RouteTicketService(nodes, this.routeTicketTtl, this.routePreparingTicketTtl);
        this.diagnostics = new RoutingDiagnosticsService(nodes, allocator, this.islandPool);
    }

    public RoutePreparationResult prepareHomeRoute(UUID playerUuid) {
        return prepareHomeRoute(playerUuid, "default");
    }

    public RoutePreparationResult prepareHomeRoute(UUID playerUuid, String homeName) {
        return islands.findByOwner(playerUuid)
            .map(island -> prepareTicket(playerUuid, island, RouteAction.HOME, homePayload(island.islandId(), homeName)))
            .orElseGet(() -> rejectRoute(404, "ISLAND_NOT_FOUND", "Player does not own an island", playerUuid, null, RouteAction.HOME));
    }

    public RoutePreparationResult prepareVisitRoute(UUID playerUuid, UUID islandId) {
        return islands.findById(islandId)
            .map(island -> visitAllowed(playerUuid, island))
            .orElseGet(() -> rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, islandId, RouteAction.VISIT));
    }

    public RoutePreparationResult prepareVisitRouteByName(UUID playerUuid, String islandName) {
        if (islandName == null || islandName.isBlank()) {
            return rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, null, RouteAction.VISIT);
        }
        return islands.findByName(islandName)
            .map(island -> visitAllowed(playerUuid, island))
            .orElseGet(() -> rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, null, RouteAction.VISIT));
    }

    public RoutePreparationResult prepareVisitRouteByOwner(UUID playerUuid, UUID ownerUuid) {
        return islands.findByOwner(ownerUuid)
            .map(island -> visitAllowed(playerUuid, island))
            .orElseGet(() -> rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, null, RouteAction.VISIT));
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
            RoutePreparationResult result = prepareTicket(playerUuid, island, RouteAction.VISIT, visitPayload());
            if (result.status() == 202) {
                return result;
            }
        }
        return rejectRoute(404, "PUBLIC_ISLAND_NOT_FOUND", "No public island is available", playerUuid, null, RouteAction.VISIT);
    }

    public RoutePreparationResult prepareWarpRoute(UUID playerUuid, UUID islandId, String warpName) {
        return islands.findById(islandId)
            .map(island -> warpAllowed(playerUuid, island, warpName))
            .orElseGet(() -> rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, islandId, RouteAction.WARP));
    }

    public RoutePreparationResult prepareMigrationReturnRoute(UUID playerUuid, UUID islandId, String targetNode, Map<String, String> locationPayload) {
        IslandSnapshot island = islands.findById(islandId).orElse(null);
        if (island == null) {
            return rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, islandId, RouteAction.RETURN_AFTER_MIGRATION, targetNode);
        }
        if (targetNode == null || targetNode.isBlank()) {
            return rejectRoute(409, "TARGET_NODE_UNAVAILABLE", "Migration target node is unavailable", playerUuid, islandId, RouteAction.RETURN_AFTER_MIGRATION, "");
        }
        String templateId = islands.templateId(islandId).orElse("default");
        String minNodeVersion = templates.find(templateId).map(kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot::minNodeVersion).orElse("");
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        Instant now = Instant.now();
        NodeLoad node = allocator.selectTargetNode(nodeSnapshot, now, targetNode, templateId, minNodeVersion, islandPool).orElse(null);
        if (node == null) {
            String reason = allocator.targetNodeBlockReason(nodeSnapshot, now, targetNode, templateId, minNodeVersion, islandPool);
            return rejectRoute(409, targetNodeUnavailableCode(reason), "Migration target node is unavailable", playerUuid, islandId, RouteAction.RETURN_AFTER_MIGRATION, targetNode);
        }
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetType", "MIGRATION_RETURN");
        payload.putAll(locationPayload == null ? Map.of() : locationPayload);
        RouteTicket saved = tickets.save(ticket(playerUuid, islandId, RouteAction.RETURN_AFTER_MIGRATION, payload, RouteTargetResolver.preparing(node, IslandPlacement.worldName(islandId))));
        events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of(
            "ticketId", saved.ticketId().toString(),
            "playerUuid", saved.playerUuid().toString(),
            "islandId", saved.islandId().toString(),
            "action", saved.action().name(),
            "targetNode", saved.targetNode(),
            "targetServerName", saved.payload().getOrDefault("targetServerName", saved.targetNode()),
            "state", saved.state().name()
        ));
        return RoutePreparationResult.accepted(toJson(saved));
    }

    public RoutePreparationResult prepareAdminTeleportRoute(UUID playerUuid, UUID islandId) {
        return islands.findById(islandId)
            .map(island -> prepareTicket(playerUuid, island, RouteAction.ADMIN_TELEPORT, Map.of("targetType", "ADMIN_TELEPORT", "admin", "true")))
            .orElseGet(() -> rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, islandId, RouteAction.ADMIN_TELEPORT));
    }

    public String consumeTicketJson(String body) {
        UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
        String nodeId = JsonFields.text(body, "nodeId", "");
        String nonce = JsonFields.text(body, "nonce", "");
        java.util.Optional<RouteTicket> consumed = tickets.consume(
            ticketId,
            playerUuid,
            nodeId,
            nonce
        );
        consumed.ifPresent(ticket -> events.publish(CloudIslandEventType.ROUTE_TICKET_CONSUMED.name(), Map.of(
            "ticketId", ticket.ticketId().toString(),
            "playerUuid", ticket.playerUuid().toString(),
            "islandId", ticket.islandId().toString(),
            "action", ticket.action().name(),
            "targetNode", ticket.targetNode(),
            "targetServerName", ticket.payload().getOrDefault("targetServerName", ticket.targetNode())
        )));
        consumed.filter(ticket -> ticket.action() == RouteAction.VISIT).ifPresent(ticket -> events.publish(CloudIslandEventType.ISLAND_VISITED.name(), Map.of(
            "ticketId", ticket.ticketId().toString(),
            "visitorUuid", ticket.playerUuid().toString(),
            "islandId", ticket.islandId().toString(),
            "targetNode", ticket.targetNode(),
            "nodeId", ticket.targetNode(),
            "targetWorld", ticket.targetWorld(),
            "placementSource", ticket.payload().getOrDefault("placementSource", "")
        )));
        if (consumed.isEmpty()) {
            publishTicketConsumeFailure(ticketId, playerUuid, nodeId, nonce);
        }
        return consumed.map(RoutingOrchestrator::toJson).orElse("");
    }

    private void publishTicketConsumeFailure(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        RouteTicket ticket = tickets.find(ticketId).orElse(null);
        String reason = consumeFailureReason(ticket, playerUuid, nodeId, nonce);
        events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
            "ticketId", ticketId.toString(),
            "playerUuid", playerUuid.toString(),
            "islandId", ticket == null ? "" : ticket.islandId().toString(),
            "action", ticket == null ? "" : ticket.action().name(),
            "targetNode", ticket == null ? "" : ticket.targetNode(),
            "targetServerName", ticket == null ? "" : ticket.payload().getOrDefault("targetServerName", ticket.targetNode()),
            "requestedNode", nodeId == null ? "" : nodeId,
            "reason", reason
        ));
    }

    private String consumeFailureReason(RouteTicket ticket, UUID playerUuid, String nodeId, String nonce) {
        if (ticket == null) {
            return "TICKET_NOT_FOUND";
        }
        if (ticket.state() == RouteTicketState.CONSUMED) {
            return "TICKET_ALREADY_CONSUMED";
        }
        if (ticket.state() == RouteTicketState.EXPIRED) {
            return "TICKET_EXPIRED";
        }
        if (ticket.state() == RouteTicketState.CANCELLED) {
            return "TICKET_CANCELLED";
        }
        if (ticket.state() == RouteTicketState.FAILED) {
            return "TICKET_FAILED";
        }
        if (ticket.expiresAt().isBefore(Instant.now())) {
            return "TICKET_EXPIRED";
        }
        if (ticket.state() != RouteTicketState.READY) {
            return "TICKET_NOT_READY";
        }
        if (!ticket.playerUuid().equals(playerUuid)) {
            return "PLAYER_MISMATCH";
        }
        if (!ticket.targetNode().equals(nodeId)) {
            return "NODE_MISMATCH";
        }
        if (nonce == null || nonce.isBlank()) {
            return "NONCE_MISSING";
        }
        if (!ticket.nonce().equals(nonce)) {
            return "NONCE_MISMATCH";
        }
        return "CONSUME_CONFLICT";
    }

    private RoutePreparationResult visitAllowed(UUID playerUuid, IslandSnapshot island) {
        return visitAllowed(playerUuid, island, RouteAction.VISIT, visitPayload());
    }

    private RoutePreparationResult visitAllowed(UUID playerUuid, IslandSnapshot island, RouteAction action, Map<String, String> extraPayload) {
        RouteAccessDecision decision = accessPolicy.visitAccess(playerUuid, island);
        if (!decision.allowed()) {
            return rejectRoute(decision.status(), decision.code(), decision.message(), playerUuid, island.islandId(), action);
        }
        return prepareTicket(playerUuid, island, action, extraPayload);
    }

    private RoutePreparationResult warpAllowed(UUID playerUuid, IslandSnapshot island, String warpName) {
        IslandWarpSnapshot warp = metadata.warp(island.islandId(), normalizeName(warpName)).orElse(null);
        if (warp == null) {
            return rejectRoute(404, "WARP_NOT_FOUND", "Island warp was not found", playerUuid, island.islandId(), RouteAction.WARP);
        }
        RouteAccessDecision decision = accessPolicy.warpAccess(playerUuid, island, warp.publicAccess());
        if (!decision.allowed()) {
            return rejectRoute(decision.status(), decision.code(), decision.message(), playerUuid, island.islandId(), RouteAction.WARP);
        }
        return prepareTicket(playerUuid, island, RouteAction.WARP, warpPayload(warp));
    }

    private RoutePreparationResult prepareTicket(UUID playerUuid, IslandSnapshot island, RouteAction action) {
        return prepareTicket(playerUuid, island, action, Map.of());
    }

    private RoutePreparationResult prepareTicket(UUID playerUuid, IslandSnapshot island, RouteAction action, Map<String, String> extraPayload) {
        IslandRuntimeSnapshot runtime = null;
        try {
            runtime = runtimes.find(island.islandId()).orElse(null);
            RoutePreparationResult unavailable = unavailableRuntime(runtime, playerUuid, island.islandId(), action);
            if (unavailable != null) {
                return unavailable;
            }
            String templateId = islands.templateId(island.islandId()).orElse("default");
            boolean visitorRoute = action == RouteAction.VISIT || (action == RouteAction.WARP && !metadata.isMember(island.islandId(), playerUuid));
            if (action == RouteAction.VISIT) {
                events.publish(CloudIslandEventType.ISLAND_PRE_VISIT.name(), Map.of("islandId", island.islandId().toString(), "visitorUuid", playerUuid.toString()));
            }
            RouteTicket saved = tickets.save(ticket(playerUuid, island.islandId(), action, extraPayload, routeTarget(runtime, templateId, templates.find(templateId).map(kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot::minNodeVersion).orElse(""), visitorRoute)));
            events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of(
                "ticketId", saved.ticketId().toString(),
                "playerUuid", saved.playerUuid().toString(),
                "islandId", saved.islandId().toString(),
                "action", saved.action().name(),
                "targetNode", saved.targetNode(),
                "targetServerName", saved.payload().getOrDefault("targetServerName", saved.targetNode()),
                "state", saved.state().name()
            ));
            return RoutePreparationResult.accepted(toJson(saved));
        } catch (RouteFailureException exception) {
            RouteFailureMapper.RouteFailureResponse response = RouteFailureMapper.map(exception.code(), exception.detail(), runtime == null ? "" : runtime.activeNode());
            if (response.includeRoutingDetails()) {
                if (response.targetNode().isBlank()) {
                    return rejectRouteWithRoutingDetails(response.status(), response.publicReason(), response.message(), playerUuid, island.islandId(), action, response.debugReason());
                }
                return rejectRouteWithRoutingDetails(response.status(), response.publicReason(), response.message(), playerUuid, island.islandId(), action, response.targetNode(), response.debugReason());
            }
            return rejectRoute(response.status(), response.publicReason(), response.message(), playerUuid, island.islandId(), action);
        } catch (IllegalStateException exception) {
            return rejectRoute(409, "NODE_UNAVAILABLE", "No eligible island node is available", playerUuid, island.islandId(), action);
        }
    }

    private RouteTicket ticket(UUID playerUuid, UUID islandId, RouteAction action, Map<String, String> extraPayload, RouteTargetSelection target) {
        return routeTickets.ticket(playerUuid, islandId, action, extraPayload, target);
    }

    private RoutePreparationResult unavailableRuntime(IslandRuntimeSnapshot runtime, UUID playerUuid, UUID islandId, RouteAction action) {
        if (runtime == null) {
            return rejectRoute(409, "ISLAND_LOADING_FAILED", "Island runtime is not ready", playerUuid, islandId, action);
        }
        IslandState state = runtime.state();
        if (state == IslandState.ACTIVE || state == IslandState.INACTIVE_READY) {
            return null;
        }
        if (state == IslandState.DELETED || state == IslandState.DELETE_REQUESTED || state == IslandState.BACKUP_BEFORE_DELETE || state == IslandState.DELETING) {
            return rejectRoute(404, "ISLAND_NOT_FOUND", "Island was not found", playerUuid, islandId, action);
        }
        if (state == IslandState.DEACTIVATING) {
            return rejectRoute(409, "ISLAND_MIGRATING", "Island is migrating to another node", playerUuid, islandId, action);
        }
        if (state == IslandState.ACTIVATING) {
            return rejectRoute(409, "ISLAND_PREPARING", "Island activation is already in progress", playerUuid, islandId, action);
        }
        if (state == IslandState.RESTORING) {
            return rejectRoute(409, "ISLAND_RESTORING", "Island restore is already in progress", playerUuid, islandId, action);
        }
        if (state == IslandState.SAVING) {
            return rejectRoute(409, "ISLAND_SAVING", "Island save is already in progress", playerUuid, islandId, action);
        }
        if (state == IslandState.RECOVERY_REQUIRED || state == IslandState.QUARANTINED) {
            return rejectRoute(409, "RECOVERY_REQUIRED", "Island needs recovery before routing", playerUuid, islandId, action);
        }
        return rejectRoute(409, "ISLAND_LOADING_FAILED", "Island is not ready for routing", playerUuid, islandId, action);
    }

    private RoutePreparationResult rejectRoute(int status, String reason, String message, UUID playerUuid, UUID islandId, RouteAction action) {
        publishTicketFailure(playerUuid, islandId, action, reason, "");
        return RoutePreparationResult.rejected(status, ApiResponses.error(reason, message));
    }

    private RoutePreparationResult rejectRoute(int status, String reason, String message, UUID playerUuid, UUID islandId, RouteAction action, String targetNode) {
        publishTicketFailure(playerUuid, islandId, action, reason, targetNode);
        return RoutePreparationResult.rejected(status, ApiResponses.error(reason, message));
    }

    private RoutePreparationResult rejectRoute(int status, String publicReason, String message, UUID playerUuid, UUID islandId, RouteAction action, String targetNode, String debugReason) {
        publishTicketFailure(playerUuid, islandId, action, debugReason == null || debugReason.isBlank() ? publicReason : debugReason, targetNode);
        return RoutePreparationResult.rejected(status, ApiResponses.error(publicReason, message));
    }

    private RoutePreparationResult rejectRouteWithRoutingDetails(int status, String publicReason, String message, UUID playerUuid, UUID islandId, RouteAction action, String debugReason) {
        publishTicketFailure(playerUuid, islandId, action, debugReason == null || debugReason.isBlank() ? publicReason : debugReason, "");
        return RoutePreparationResult.rejected(status, ApiResponses.error(publicReason, message, routingFailureDetails(debugReason)));
    }

    private RoutePreparationResult rejectRouteWithRoutingDetails(int status, String publicReason, String message, UUID playerUuid, UUID islandId, RouteAction action, String targetNode, String debugReason) {
        publishTicketFailure(playerUuid, islandId, action, debugReason == null || debugReason.isBlank() ? publicReason : debugReason, targetNode);
        return RoutePreparationResult.rejected(status, ApiResponses.error(publicReason, message, routingFailureDetails(debugReason)));
    }

    private Map<String, String> routingFailureDetails(String debugReason) {
        return diagnostics.routingFailureDetails(debugReason);
    }

    private void publishTicketFailure(UUID playerUuid, UUID islandId, RouteAction action, String reason) {
        publishTicketFailure(playerUuid, islandId, action, reason, "");
    }

    private void publishTicketFailure(UUID playerUuid, UUID islandId, RouteAction action, String reason, String targetNode) {
        events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
            "playerUuid", playerUuid == null ? "" : playerUuid.toString(),
            "islandId", islandId == null ? "" : islandId.toString(),
            "action", action == null ? "" : action.name(),
            "targetNode", targetNode == null ? "" : targetNode,
            "targetServerName", targetServerName(targetNode),
            "reason", reason
        ));
    }

    private String targetServerName(String targetNode) {
        return routeTickets.targetServerName(targetNode);
    }

    private String targetNodeUnavailableCode(String reason) {
        if (reason == null || reason.isBlank()) {
            return "TARGET_NODE_UNAVAILABLE";
        }
        if ("NODE_NOT_FOUND".equals(reason)) {
            return "TARGET_NODE_UNAVAILABLE";
        }
        return "TARGET_NODE_" + reason;
    }

    private RouteTargetSelection routeTarget(IslandRuntimeSnapshot runtime, String templateId, String minNodeVersion, boolean visitorRoute) {
        if (runtime.state() == IslandState.ACTIVE) {
            if (runtime.activeNode() == null || runtime.activeNode().isBlank()) {
                markActiveRouteRecoveryRequired(runtime, "missing_active_node");
                throw routeFailure(RouteFailureCode.ACTIVE_NODE_UNAVAILABLE, "ACTIVE_NODE_MISSING");
            }
            NodeLoad activeNode = nodes.find(runtime.activeNode()).orElse(null);
            if (activeNode == null) {
                markActiveRouteRecoveryRequired(runtime, "active_node_not_registered");
                throw routeFailure(RouteFailureCode.ACTIVE_NODE_UNAVAILABLE, "ACTIVE_NODE_NOT_REGISTERED");
            }
            if (IslandActivationCoordinator.duplicateVelocityServerName(activeNode, nodes.snapshot())) {
                throw routeFailure(RouteFailureCode.ACTIVE_NODE_UNAVAILABLE, "ACTIVE_NODE_DUPLICATE_VELOCITY_SERVER_NAME");
            }
            String blockReason = allocator.existingRouteBlockReason(activeNode, Instant.now(), templateId, minNodeVersion, islandPool);
            if (!blockReason.isBlank()) {
                if (IslandActivationCoordinator.activeRouteRecoveryReason(blockReason)) {
                    markActiveRouteRecoveryRequired(runtime, "active_node_" + blockReason.toLowerCase(java.util.Locale.ROOT));
                }
                throw routeFailure(RouteFailureCode.ACTIVE_NODE_UNAVAILABLE, "ACTIVE_NODE_" + blockReason);
            }
            if (visitorRoute && activeNode.state() == NodeState.SOFT_FULL) {
                throw routeFailure(RouteFailureCode.VISITOR_SOFT_FULL);
            }
            if (!visitorRoute && activeNode.state() == NodeState.SOFT_FULL && IslandActivationCoordinator.memberReservedSlotsExhausted(activeNode)) {
                throw routeFailure(RouteFailureCode.ACTIVE_NODE_UNAVAILABLE, "ACTIVE_NODE_MEMBER_RESERVED_SLOTS_FULL");
            }
            if (IslandActivationCoordinator.placementMissing(runtime)) {
                markActiveRouteRecoveryRequired(runtime, "missing_placement");
                throw routeFailure(RouteFailureCode.ACTIVE_NODE_UNAVAILABLE, "ACTIVE_PLACEMENT_MISSING");
            }
            return RouteTargetResolver.ready(activeNode, runtime.activeWorld());
        }
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        Instant now = Instant.now();
        NodeLoad selected = allocator.selectReadyNode(nodeSnapshot, now, templateId, minNodeVersion, islandPool).orElse(null);
        if (selected == null) {
            String blockReason = allocator.readyNodeBlockReason(nodeSnapshot, now, templateId, minNodeVersion, islandPool);
            throw routeFailure(RouteFailureCode.NO_READY_NODE, "NO_READY_NODE".equals(blockReason) ? "NO_READY_NODE" : "NO_READY_NODE_" + blockReason);
        }
        RedisActivationLock.Lease lease = null;
        RedisActivationLock.AcquireResult activationLease = RedisActivationLock.AcquireResult.disabled();
        if (activationLock != null) {
            activationLease = activationLock.tryAcquire(runtime.islandId(), "route");
            if (activationLease.locked()) {
                throw routeFailure(RouteFailureCode.ACTIVATION_LOCKED);
            }
            lease = activationLease.lease().orElse(null);
        }
        IslandRuntimeSnapshot activating;
        try {
            activating = IslandPlacement.markActivating(runtime.islandId(), selected.nodeId(), runtimes);
            if (IslandActivationCoordinator.placementMissing(activating)) {
                throw routeFailure(RouteFailureCode.PLACEMENT_MISSING);
            }
            jobs.publish(new IslandJob(
                UUID.randomUUID(),
                IslandJobType.ACTIVATE_ISLAND,
                runtime.islandId(),
                selected.nodeId(),
                0,
                Map.of(
                    "fencingToken", Long.toString(activating.fencingToken()),
                    "worldName", activating.activeWorld(),
                    "cellX", Integer.toString(activating.cellX()),
                    "cellZ", Integer.toString(activating.cellZ()),
                    "activationLockToken", lease == null ? "" : lease.token()
                ),
                Instant.now()
            ));
        } catch (RuntimeException exception) {
            if (activationLock != null) {
                activationLock.release(lease);
            }
            throw exception;
        }
        Map<String, String> event = new LinkedHashMap<>();
        event.put("islandId", runtime.islandId().toString());
        event.put("state", activating.state().name());
        event.put("targetNode", selected.nodeId());
        if (activationLease.fallback()) {
            event.put("lockFallback", activationLease.source());
        }
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), event);
        return RouteTargetResolver.preparing(selected, activating.activeWorld());
    }

    private void markActiveRouteRecoveryRequired(IslandRuntimeSnapshot runtime, String reason) {
        runtimes.setState(runtime.islandId(), IslandState.RECOVERY_REQUIRED);
        islands.setState(runtime.islandId(), IslandState.RECOVERY_REQUIRED);
        events.publish(CloudIslandEventType.ISLAND_RECOVERY_REQUIRED.name(), Map.of(
            "islandId", runtime.islandId().toString(),
            "activeNode", runtime.activeNode() == null ? "" : runtime.activeNode(),
            "previousState", runtime.state().name(),
            "reason", reason
        ));
    }

    private Map<String, String> homePayload(UUID islandId, String homeName) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        String normalized = homeName == null || homeName.isBlank() ? "default" : homeName.toLowerCase(java.util.Locale.ROOT);
        payload.put("targetType", "ISLAND_HOME");
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

    private Map<String, String> visitPayload() {
        return Map.of(
            "targetType", "VISITOR_SPAWN",
            "localX", "0.5",
            "localY", "100.0",
            "localZ", "2.5",
            "yaw", "180.0",
            "pitch", "0.0"
        );
    }

    private Map<String, String> warpPayload(IslandWarpSnapshot warp) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetType", "ISLAND_WARP");
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
        return name == null || name.isBlank() ? "default" : name.toLowerCase(java.util.Locale.ROOT);
    }

    private RouteFailureException routeFailure(RouteFailureCode code) {
        return routeFailure(code, code.name());
    }

    private RouteFailureException routeFailure(RouteFailureCode code, String detail) {
        return new RouteFailureException(code, detail);
    }

    private static final class RouteFailureException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final RouteFailureCode code;
        private final String detail;

        private RouteFailureException(RouteFailureCode code, String detail) {
            super(detail);
            this.code = code == null ? RouteFailureCode.NO_READY_NODE : code;
            this.detail = detail == null || detail.isBlank() ? this.code.name() : detail;
        }

        private RouteFailureCode code() {
            return code;
        }

        private String detail() {
            return detail;
        }
    }

    public static String toJson(RouteTicket ticket) {
        return RouteTicketJson.routeResponse(ticket);
    }
}
