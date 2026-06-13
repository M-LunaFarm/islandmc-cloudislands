package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
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
    private final String islandPool;
    private final Duration routeTicketTtl;
    private final Duration routePreparingTicketTtl;
    private final RedisActivationLock activationLock;

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
        NodeLoad node = nodes.find(targetNode).orElse(null);
        if (node == null) {
            return rejectRoute(409, "TARGET_NODE_UNAVAILABLE", "Migration target node is unavailable", playerUuid, islandId, RouteAction.RETURN_AFTER_MIGRATION, targetNode);
        }
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetType", "MIGRATION_RETURN");
        payload.putAll(locationPayload == null ? Map.of() : locationPayload);
        RouteTicket saved = tickets.save(ticket(playerUuid, islandId, RouteAction.RETURN_AFTER_MIGRATION, payload, new RouteTarget(node, "ci_shard_001", RouteTicketState.PREPARING)));
        events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of(
            "ticketId", saved.ticketId().toString(),
            "playerUuid", saved.playerUuid().toString(),
            "islandId", saved.islandId().toString(),
            "action", saved.action().name(),
            "targetNode", saved.targetNode(),
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
            "targetNode", ticket.targetNode()
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
            "targetNode", nodeId == null ? "" : nodeId,
            "reason", reason
        ));
    }

    private String consumeFailureReason(RouteTicket ticket, UUID playerUuid, String nodeId, String nonce) {
        if (ticket == null) {
            return "TICKET_NOT_FOUND";
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
        if (!ticket.nonce().equals(nonce)) {
            return "NONCE_MISMATCH";
        }
        return "CONSUME_CONFLICT";
    }

    private RoutePreparationResult visitAllowed(UUID playerUuid, IslandSnapshot island) {
        return visitAllowed(playerUuid, island, RouteAction.VISIT, visitPayload());
    }

    private RoutePreparationResult visitAllowed(UUID playerUuid, IslandSnapshot island, RouteAction action, Map<String, String> extraPayload) {
        if (metadata.isBanned(island.islandId(), playerUuid)) {
            return rejectRoute(403, "VISITOR_BANNED", "Visitor is banned from this island", playerUuid, island.islandId(), action);
        }
        if (metadata.isLocked(island.islandId()) && !metadata.isMember(island.islandId(), playerUuid)) {
            return rejectRoute(423, "ISLAND_LOCKED", "Island is locked", playerUuid, island.islandId(), action);
        }
        if (!metadata.isPublicAccess(island.islandId()) && !metadata.isMember(island.islandId(), playerUuid)) {
            return rejectRoute(403, "ISLAND_PRIVATE", "Island is private", playerUuid, island.islandId(), action);
        }
        return prepareTicket(playerUuid, island, action, extraPayload);
    }

    private RoutePreparationResult warpAllowed(UUID playerUuid, IslandSnapshot island, String warpName) {
        IslandWarpSnapshot warp = metadata.warp(island.islandId(), normalizeName(warpName)).orElse(null);
        if (warp == null) {
            return rejectRoute(404, "WARP_NOT_FOUND", "Island warp was not found", playerUuid, island.islandId(), RouteAction.WARP);
        }
        if (metadata.isBanned(island.islandId(), playerUuid)) {
            return rejectRoute(403, "VISITOR_BANNED", "Visitor is banned from this island", playerUuid, island.islandId(), RouteAction.WARP);
        }
        boolean member = metadata.isMember(island.islandId(), playerUuid);
        if (metadata.isLocked(island.islandId()) && !member) {
            return rejectRoute(423, "ISLAND_LOCKED", "Island is locked", playerUuid, island.islandId(), RouteAction.WARP);
        }
        if (!member && (!warp.publicAccess() || !islandFlagEnabled(island.islandId(), IslandFlag.PUBLIC_WARPS))) {
            return rejectRoute(403, "WARP_PRIVATE", "Island warp is private", playerUuid, island.islandId(), RouteAction.WARP);
        }
        return prepareTicket(playerUuid, island, RouteAction.WARP, warpPayload(warp));
    }

    private boolean islandFlagEnabled(UUID islandId, IslandFlag flag) {
        String value = metadata.flags(islandId).values().getOrDefault(flag, "false");
        return value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("allow")
            || value.equalsIgnoreCase("allowed")
            || value.equalsIgnoreCase("enabled")
            || value.equalsIgnoreCase("on");
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
            RouteTicket saved = tickets.save(ticket(playerUuid, island.islandId(), action, extraPayload, routeTarget(runtime, templateId, templates.find(templateId).map(kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot::minNodeVersion).orElse(""), visitorRoute)));
            events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of(
                "ticketId", saved.ticketId().toString(),
                "playerUuid", saved.playerUuid().toString(),
                "islandId", saved.islandId().toString(),
                "action", saved.action().name(),
                "targetNode", saved.targetNode(),
                "state", saved.state().name()
            ));
            return RoutePreparationResult.accepted(toJson(saved));
        } catch (IllegalStateException exception) {
            if ("VISITOR_SOFT_FULL".equals(exception.getMessage())) {
                return rejectRoute(429, "VISITOR_SOFT_FULL", "The island node is reserving slots for members", playerUuid, island.islandId(), action);
            }
            if ("ACTIVATION_LOCKED".equals(exception.getMessage())) {
                return rejectRoute(409, "ACTIVATION_LOCKED", "Island activation is already in progress", playerUuid, island.islandId(), action);
            }
            if (exception.getMessage() != null && exception.getMessage().startsWith("ACTIVE_NODE_")) {
                return rejectRoute(409, exception.getMessage(), "The active island node cannot accept this route", playerUuid, island.islandId(), action, runtime == null ? "" : runtime.activeNode());
            }
            if ("NO_READY_NODE".equals(exception.getMessage()) || (exception.getMessage() != null && exception.getMessage().startsWith("NO_READY_NODE_"))) {
                return rejectRoute(409, exception.getMessage(), "No ready island node is available", playerUuid, island.islandId(), action);
            }
            return rejectRoute(409, "NODE_UNAVAILABLE", "No eligible island node is available", playerUuid, island.islandId(), action);
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
            Instant.now().plus(target.state() == RouteTicketState.PREPARING ? routePreparingTicketTtl : routeTicketTtl),
            UUID.randomUUID().toString(),
            Map.copyOf(payload)
        );
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

    private void publishTicketFailure(UUID playerUuid, UUID islandId, RouteAction action, String reason) {
        publishTicketFailure(playerUuid, islandId, action, reason, "");
    }

    private void publishTicketFailure(UUID playerUuid, UUID islandId, RouteAction action, String reason, String targetNode) {
        events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
            "playerUuid", playerUuid == null ? "" : playerUuid.toString(),
            "islandId", islandId == null ? "" : islandId.toString(),
            "action", action == null ? "" : action.name(),
            "targetNode", targetNode == null ? "" : targetNode,
            "reason", reason
        ));
    }

    private RouteTarget routeTarget(IslandRuntimeSnapshot runtime, String templateId, String minNodeVersion, boolean visitorRoute) {
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
            String blockReason = allocator.existingRouteBlockReason(activeNode, Instant.now(), templateId, minNodeVersion, islandPool);
            if (!blockReason.isBlank()) {
                if (activeRouteRecoveryReason(blockReason)) {
                    markActiveRouteRecoveryRequired(runtime, "active_node_" + blockReason.toLowerCase(java.util.Locale.ROOT));
                }
                throw new IllegalStateException("ACTIVE_NODE_" + blockReason);
            }
            if (visitorRoute && activeNode.state() == NodeState.SOFT_FULL) {
                throw new IllegalStateException("VISITOR_SOFT_FULL");
            }
            String worldName = runtime.activeWorld() == null || runtime.activeWorld().isBlank() ? "ci_shard_001" : runtime.activeWorld();
            return new RouteTarget(activeNode, worldName, RouteTicketState.READY);
        }
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        Instant now = Instant.now();
        NodeLoad selected = allocator.selectReadyNode(nodeSnapshot, now, templateId, minNodeVersion, islandPool).orElse(null);
        if (selected == null) {
            String blockReason = allocator.readyNodeBlockReason(nodeSnapshot, now, templateId, minNodeVersion, islandPool);
            throw new IllegalStateException("NO_READY_NODE".equals(blockReason) ? "NO_READY_NODE" : "NO_READY_NODE_" + blockReason);
        }
        RedisActivationLock.Lease lease = null;
        if (activationLock != null) {
            lease = activationLock.acquire(runtime.islandId(), "route").orElseThrow(() -> new IllegalStateException("ACTIVATION_LOCKED"));
        }
        IslandRuntimeSnapshot activating = runtimes.markActivating(runtime.islandId(), selected.nodeId(), "ci_shard_001", 0, 0);
        try {
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
        } catch (RuntimeException exception) {
            if (activationLock != null) {
                activationLock.release(lease);
            }
            throw exception;
        }
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", runtime.islandId().toString(), "state", activating.state().name(), "targetNode", selected.nodeId()));
        return new RouteTarget(selected, activating.activeWorld() == null ? "ci_shard_001" : activating.activeWorld(), RouteTicketState.PREPARING);
    }

    private boolean activeRouteRecoveryReason(String blockReason) {
        return blockReason.equals("NODE_NOT_FOUND")
            || blockReason.equals("HEARTBEAT_MISSING")
            || blockReason.equals("HEARTBEAT_STALE")
            || blockReason.equals("STATE_DOWN");
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
        String normalized = homeName == null || homeName.isBlank() ? "default" : homeName.toLowerCase();
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
