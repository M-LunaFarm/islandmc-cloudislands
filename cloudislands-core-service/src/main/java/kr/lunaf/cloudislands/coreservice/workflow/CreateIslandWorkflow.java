package kr.lunaf.cloudislands.coreservice.workflow;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.RedisPlayerCreationLock;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class CreateIslandWorkflow {
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;
    private final PlayerProfileRepository playerProfiles;
    private final IslandTemplateRepository templates;
    private final NodeRegistry nodes;
    private final NodeAllocator allocator;
    private final IslandJobPublisher jobs;
    private final GlobalEventPublisher events;
    private final RouteTicketStore tickets;
    private final String islandPool;
    private final Duration routePreparingTicketTtl;
    private final RedisPlayerCreationLock playerCreationLock;

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, jobs, events, tickets, "island");
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, jobs, events, tickets, islandPool, Duration.ofSeconds(120));
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool, Duration routePreparingTicketTtl) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, jobs, events, tickets, islandPool, routePreparingTicketTtl, null);
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool, Duration routePreparingTicketTtl, RedisPlayerCreationLock playerCreationLock) {
        this.islands = islands;
        this.metadata = metadata;
        this.playerProfiles = playerProfiles;
        this.templates = templates;
        this.nodes = nodes;
        this.allocator = allocator;
        this.jobs = jobs;
        this.events = events;
        this.tickets = tickets;
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.routePreparingTicketTtl = routePreparingTicketTtl == null || routePreparingTicketTtl.isNegative() || routePreparingTicketTtl.isZero() ? Duration.ofSeconds(120) : routePreparingTicketTtl;
        this.playerCreationLock = playerCreationLock;
    }

    public CreateIslandResult create(UUID ownerUuid, String templateId) {
        String normalizedTemplate = templateId == null || templateId.isBlank() ? "default" : templateId;
        events.publish(CloudIslandEventType.ISLAND_PRE_CREATE.name(), Map.of("ownerUuid", ownerUuid.toString(), "templateId", normalizedTemplate));
        if (isMigrationInputOnlyTemplate(normalizedTemplate)) {
            publishTicketFailure(ownerUuid, null, "TEMPLATE_MIGRATION_INPUT_ONLY");
            return new CreateIslandResult(false, "TEMPLATE_MIGRATION_INPUT_ONLY", null, null);
        }
        IslandTemplateSnapshot template = templates.find(normalizedTemplate).orElse(null);
        if (template == null || !template.enabled()) {
            publishTicketFailure(ownerUuid, null, "TEMPLATE_UNAVAILABLE");
            return new CreateIslandResult(false, "TEMPLATE_UNAVAILABLE", null, null);
        }
        RedisPlayerCreationLock.Lease lease = acquireCreationLock(ownerUuid);
        if (playerCreationLock != null && lease == null) {
            publishTicketFailure(ownerUuid, null, "CREATE_LOCKED");
            return new CreateIslandResult(false, "CREATE_LOCKED", null, null);
        }
        try {
        if (islands.findByOwner(ownerUuid).isPresent()) {
            releaseCreationLock(lease);
            publishTicketFailure(ownerUuid, null, "ALREADY_HAS_ISLAND");
            return new CreateIslandResult(false, "ALREADY_HAS_ISLAND", null, null);
        }
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        NodeLoad node = allocator.selectReadyNode(nodeSnapshot, Instant.now(), normalizedTemplate, template.minNodeVersion(), islandPool).orElse(null);
        if (node == null) {
            releaseCreationLock(lease);
            String code = readyNodeUnavailableCode(nodeSnapshot, normalizedTemplate, template.minNodeVersion());
            publishTicketFailure(ownerUuid, null, code);
            return new CreateIslandResult(false, code, null, null);
        }
        UUID islandId = UUID.randomUUID();
        IslandSnapshot island = islands.createOwnedIsland(islandId, ownerUuid, normalizedTemplate, "Island");
        metadata.upsertMember(islandId, ownerUuid, IslandRole.OWNER);
        playerProfiles.setPrimaryIsland(ownerUuid, islandId);
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime;
        try {
            runtime = kr.lunaf.cloudislands.coreservice.IslandPlacement.markActivating(islandId, node.nodeId(), runtimes);
        } catch (RuntimeException exception) {
            releaseCreationLock(lease);
            islands.setState(islandId, IslandState.ERROR_CREATING);
            runtimes.setState(islandId, IslandState.ERROR_CREATING);
            publishTicketFailure(ownerUuid, islandId, "PLACEMENT_UNAVAILABLE");
            events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", IslandState.ERROR_CREATING.name(), "reason", "PLACEMENT_UNAVAILABLE", "targetNode", node.nodeId()));
            return new CreateIslandResult(false, "PLACEMENT_UNAVAILABLE", islands.findById(islandId).orElse(island), null);
        }
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.CREATE_ISLAND, islandId, node.nodeId(), 0, Map.of("templateId", normalizedTemplate, "ownerUuid", ownerUuid.toString(), "islandSize", Integer.toString(island.size()), "worldName", runtime.activeWorld() == null ? kr.lunaf.cloudislands.coreservice.IslandPlacement.worldName(islandId) : runtime.activeWorld(), "cellX", runtime.cellX() == null ? "0" : Integer.toString(runtime.cellX()), "cellZ", runtime.cellZ() == null ? "0" : Integer.toString(runtime.cellZ())), Instant.now()));
        } catch (RuntimeException exception) {
            releaseCreationLock(lease);
            islands.setState(islandId, IslandState.ERROR_CREATING);
            runtimes.setState(islandId, IslandState.ERROR_CREATING);
            publishTicketFailure(ownerUuid, islandId, "JOB_QUEUE_UNAVAILABLE");
            events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", IslandState.ERROR_CREATING.name(), "reason", "JOB_QUEUE_UNAVAILABLE", "targetNode", node.nodeId()));
            return new CreateIslandResult(false, "JOB_QUEUE_UNAVAILABLE", islands.findById(islandId).orElse(island), null);
        }
        events.publish(CloudIslandEventType.ISLAND_CREATED.name(), Map.of("islandId", islandId.toString(), "ownerUuid", ownerUuid.toString(), "targetNode", node.nodeId()));
        RouteTicket ticket = tickets.save(new RouteTicket(UUID.randomUUID(), ownerUuid, RouteAction.HOME, islandId, node.nodeId(), runtime.activeWorld() == null ? kr.lunaf.cloudislands.coreservice.IslandPlacement.worldName(islandId) : runtime.activeWorld(), RouteTicketState.PREPARING, Instant.now().plus(routePreparingTicketTtl), UUID.randomUUID().toString(), Map.of(
            "targetServerName", node.velocityServerName(),
            "targetType", "ISLAND_HOME",
            "homeName", "default",
            "localX", "0.5",
            "localY", "100.0",
            "localZ", "0.5",
            "yaw", "180.0",
            "pitch", "0.0"
        )));
        events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of("ticketId", ticket.ticketId().toString(), "islandId", islandId.toString(), "playerUuid", ownerUuid.toString(), "action", ticket.action().name(), "targetNode", ticket.targetNode(), "targetServerName", ticket.payload().getOrDefault("targetServerName", ticket.targetNode()), "state", ticket.state().name()));
        releaseCreationLock(lease);
        return new CreateIslandResult(true, "CREATING", island, ticket);
        } catch (RuntimeException exception) {
            releaseCreationLock(lease);
            throw exception;
        }
    }

    private void publishTicketFailure(UUID playerUuid, UUID islandId, String reason) {
        events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
            "playerUuid", playerUuid.toString(),
            "islandId", islandId == null ? "" : islandId.toString(),
            "action", RouteAction.HOME.name(),
            "reason", reason
        ));
    }

    private static boolean isMigrationInputOnlyTemplate(String templateId) {
        return "superiorskyblock2".equalsIgnoreCase(templateId == null ? "" : templateId.trim());
    }

    private RedisPlayerCreationLock.Lease acquireCreationLock(UUID playerUuid) {
        return playerCreationLock == null ? null : playerCreationLock.acquire(playerUuid).orElse(null);
    }

    private void releaseCreationLock(RedisPlayerCreationLock.Lease lease) {
        if (playerCreationLock != null && lease != null) {
            playerCreationLock.release(lease);
        }
    }

    private String readyNodeUnavailableCode(List<NodeLoad> nodeSnapshot, String templateId, String minNodeVersion) {
        String reason = allocator.readyNodeBlockReason(nodeSnapshot, Instant.now(), templateId, minNodeVersion, islandPool);
        return "NO_READY_NODE".equals(reason) ? "NO_READY_NODE" : "NO_READY_NODE_" + reason;
    }
}
