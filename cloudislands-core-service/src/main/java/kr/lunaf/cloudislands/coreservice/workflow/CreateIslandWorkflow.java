package kr.lunaf.cloudislands.coreservice.workflow;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
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
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
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
    private final IslandRuntimeRepository runtimes;
    private final IslandJobPublisher jobs;
    private final GlobalEventPublisher events;
    private final RouteTicketStore tickets;
    private final String islandPool;
    private final Duration routePreparingTicketTtl;
    private final RedisPlayerCreationLock playerCreationLock;

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, new InMemoryIslandRuntimeRepository(), jobs, events, tickets, "island", Duration.ofSeconds(120), null);
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, new InMemoryIslandRuntimeRepository(), jobs, events, tickets, islandPool, Duration.ofSeconds(120), null);
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool, Duration routePreparingTicketTtl) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, new InMemoryIslandRuntimeRepository(), jobs, events, tickets, islandPool, routePreparingTicketTtl, null);
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool, Duration routePreparingTicketTtl, RedisPlayerCreationLock playerCreationLock) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, new InMemoryIslandRuntimeRepository(), jobs, events, tickets, islandPool, routePreparingTicketTtl, playerCreationLock);
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandRuntimeRepository runtimes, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool, Duration routePreparingTicketTtl, RedisPlayerCreationLock playerCreationLock) {
        this.islands = islands;
        this.metadata = metadata;
        this.playerProfiles = playerProfiles;
        this.templates = templates;
        this.nodes = nodes;
        this.allocator = allocator;
        this.runtimes = runtimes;
        this.jobs = jobs;
        this.events = events;
        this.tickets = tickets;
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.routePreparingTicketTtl = routePreparingTicketTtl == null || routePreparingTicketTtl.isNegative() || routePreparingTicketTtl.isZero() ? Duration.ofSeconds(120) : routePreparingTicketTtl;
        this.playerCreationLock = playerCreationLock;
    }

    public CreateIslandResult create(UUID ownerUuid, String templateId) {
        return create(ownerUuid, templateId, false);
    }

    public CreateIslandResult create(UUID ownerUuid, String templateId, boolean economySettlementManaged) {
        String normalizedTemplate = templateId == null || templateId.isBlank() ? "default" : templateId.trim().toLowerCase(Locale.ROOT);
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
        if (paidTemplate(template) && !economySettlementManaged) {
            publishTicketFailure(ownerUuid, null, "PAID_TEMPLATE_SETTLEMENT_REQUIRED");
            return new CreateIslandResult(false, "PAID_TEMPLATE_SETTLEMENT_REQUIRED", null, null);
        }
        RedisPlayerCreationLock.Lease lease = acquireCreationLock(ownerUuid);
        if (playerCreationLock != null && lease == null) {
            publishTicketFailure(ownerUuid, null, "CREATE_LOCKED");
            return new CreateIslandResult(false, "CREATE_LOCKED", null, null);
        }
        try {
        IslandSnapshot existingIsland = islands.findByOwner(ownerUuid).orElse(null);
        boolean retryingFailedCreate = existingIsland != null
            && existingIsland.state() == IslandState.ERROR_CREATING
            && normalizedTemplate.equals(islands.templateId(existingIsland.islandId()).orElse(""));
        if (existingIsland != null && !retryingFailedCreate) {
            releaseCreationLock(lease);
            String code = existingIsland.state() == IslandState.ERROR_CREATING
                ? "FAILED_CREATE_TEMPLATE_MISMATCH"
                : "ALREADY_HAS_ISLAND";
            publishTicketFailure(ownerUuid, existingIsland.islandId(), code);
            return new CreateIslandResult(false, code, existingIsland, null);
        }
        List<NodeLoad> nodeSnapshot = nodes.snapshot();
        NodeLoad node = allocator.selectReadyNode(nodeSnapshot, Instant.now(), normalizedTemplate, template.minNodeVersion(), islandPool).orElse(null);
        if (node == null) {
            releaseCreationLock(lease);
            String code = readyNodeUnavailableCode(nodeSnapshot, normalizedTemplate, template.minNodeVersion());
            publishTicketFailure(ownerUuid, null, code);
            return new CreateIslandResult(false, code, null, null);
        }
        UUID islandId = retryingFailedCreate ? existingIsland.islandId() : UUID.randomUUID();
        IslandSnapshot island = retryingFailedCreate
            ? existingIsland
            : islands.createOwnedIsland(islandId, ownerUuid, normalizedTemplate, defaultIslandName(islandId));
        if (!retryingFailedCreate) {
            if (template.defaultIslandSize() != island.size()) {
                islands.updateStats(islandId, template.defaultIslandSize(), island.level(), island.worth());
                island = islands.findById(islandId).orElse(island);
            }
            metadata.upsertMemberKey(islandId, ownerUuid, CoreRoleKeys.OWNER);
            playerProfiles.setPrimaryIsland(ownerUuid, islandId);
        }
        kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime;
        try {
            runtime = kr.lunaf.cloudislands.coreservice.IslandPlacement.markActivating(islandId, node.nodeId(), runtimes);
            if (placementMissing(runtime)) {
                releaseCreationLock(lease);
                islands.setState(islandId, IslandState.ERROR_CREATING);
                runtimes.setState(islandId, IslandState.ERROR_CREATING);
                publishTicketFailure(ownerUuid, islandId, "PLACEMENT_UNAVAILABLE");
                events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", IslandState.ERROR_CREATING.name(), "reason", "PLACEMENT_UNAVAILABLE", "targetNode", node.nodeId()));
                return new CreateIslandResult(false, "PLACEMENT_UNAVAILABLE", islands.findById(islandId).orElse(island), null);
            }
            islands.setState(islandId, IslandState.CREATING);
            island = islands.findById(islandId).orElse(island);
        } catch (RuntimeException exception) {
            releaseCreationLock(lease);
            islands.setState(islandId, IslandState.ERROR_CREATING);
            runtimes.setState(islandId, IslandState.ERROR_CREATING);
            failPreparingTickets(islandId, node.nodeId(), "ROUTE_TICKET_UNAVAILABLE");
            publishTicketFailure(ownerUuid, islandId, "PLACEMENT_UNAVAILABLE");
            events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", IslandState.ERROR_CREATING.name(), "reason", "PLACEMENT_UNAVAILABLE", "targetNode", node.nodeId()));
            return new CreateIslandResult(false, "PLACEMENT_UNAVAILABLE", islands.findById(islandId).orElse(island), null);
        }
        String targetServerName = node.velocityServerName() == null || node.velocityServerName().isBlank() ? node.nodeId() : node.velocityServerName();
        RouteTicket ticket;
        try {
            ticket = tickets.save(new RouteTicket(UUID.randomUUID(), ownerUuid, RouteAction.HOME, islandId, node.nodeId(), runtime.activeWorld(), RouteTicketState.PREPARING, Instant.now().plus(routePreparingTicketTtl), UUID.randomUUID().toString(), routePayload(targetServerName, template)));
        } catch (RuntimeException exception) {
            releaseCreationLock(lease);
            islands.setState(islandId, IslandState.ERROR_CREATING);
            runtimes.setState(islandId, IslandState.ERROR_CREATING);
            publishTicketFailure(ownerUuid, islandId, "ROUTE_TICKET_UNAVAILABLE");
            events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", IslandState.ERROR_CREATING.name(), "reason", "ROUTE_TICKET_UNAVAILABLE", "targetNode", node.nodeId()));
            return new CreateIslandResult(false, "ROUTE_TICKET_UNAVAILABLE", islands.findById(islandId).orElse(island), null);
        }
        try {
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.CREATE_ISLAND, islandId, node.nodeId(), 0, createJobPayload(template, ownerUuid, island, runtime), Instant.now()));
        } catch (RuntimeException exception) {
            releaseCreationLock(lease);
            islands.setState(islandId, IslandState.ERROR_CREATING);
            runtimes.setState(islandId, IslandState.ERROR_CREATING);
            failPreparingTickets(islandId, node.nodeId(), "JOB_QUEUE_UNAVAILABLE");
            publishTicketFailure(ownerUuid, islandId, "JOB_QUEUE_UNAVAILABLE");
            events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", IslandState.ERROR_CREATING.name(), "reason", "JOB_QUEUE_UNAVAILABLE", "targetNode", node.nodeId()));
            return new CreateIslandResult(false, "JOB_QUEUE_UNAVAILABLE", islands.findById(islandId).orElse(island), null);
        }
        events.publish(CloudIslandEventType.ISLAND_CREATED.name(), Map.of("islandId", islandId.toString(), "ownerUuid", ownerUuid.toString(), "targetNode", node.nodeId(), "retry", Boolean.toString(retryingFailedCreate)));
        events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of("ticketId", ticket.ticketId().toString(), "islandId", islandId.toString(), "playerUuid", ownerUuid.toString(), "action", ticket.action().name(), "targetNode", ticket.targetNode(), "targetServerName", ticket.payload().getOrDefault("targetServerName", ticket.targetNode()), "state", ticket.state().name()));
        releaseCreationLock(lease);
        return new CreateIslandResult(true, "CREATING", island, ticket);
        } catch (RuntimeException exception) {
            releaseCreationLock(lease);
            throw exception;
        }
    }

    private static Map<String, String> createJobPayload(IslandTemplateSnapshot template, UUID ownerUuid, IslandSnapshot island, kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("templateId", template.id());
        payload.put("ownerUuid", ownerUuid.toString());
        payload.put("islandSize", Integer.toString(island.size()));
        payload.put("worldName", runtime.activeWorld());
        payload.put("cellX", Integer.toString(runtime.cellX()));
        payload.put("cellZ", Integer.toString(runtime.cellZ()));
        payload.put("fencingToken", Long.toString(runtime.fencingToken()));
        payload.put("templateBundlePath", template.bundleStoragePath());
        payload.put("templateBundleChecksum", template.bundleChecksum());
        payload.put("templateBundleSizeBytes", Long.toString(template.bundleSizeBytes()));
        payload.put("templateSchemaVersion", Integer.toString(template.schemaVersion()));
        payload.put("homeName", template.homeName());
        payload.put("localX", Double.toString(template.spawnWorldOffsetX()));
        payload.put("localY", Double.toString(template.spawnWorldOffsetY()));
        payload.put("localZ", Double.toString(template.spawnWorldOffsetZ()));
        payload.put("yaw", Float.toString(template.spawnYaw()));
        payload.put("pitch", Float.toString(template.spawnPitch()));
        payload.put("creationCost", template.creationCost());
        payload.put("requiredPermission", template.requiredPermission());
        payload.put("creationRequestId", UUID.randomUUID().toString());
        return Map.copyOf(payload);
    }

    private static Map<String, String> routePayload(String targetServerName, IslandTemplateSnapshot template) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("targetServerName", targetServerName);
        payload.put("targetType", "ISLAND_HOME");
        payload.put("homeName", template.homeName());
        payload.put("localX", Double.toString(template.spawnWorldOffsetX()));
        payload.put("localY", Double.toString(template.spawnWorldOffsetY()));
        payload.put("localZ", Double.toString(template.spawnWorldOffsetZ()));
        payload.put("yaw", Float.toString(template.spawnYaw()));
        payload.put("pitch", Float.toString(template.spawnPitch()));
        payload.put("templateId", template.id());
        return Map.copyOf(payload);
    }

    private static String defaultIslandName(UUID islandId) {
        String value = islandId == null ? UUID.randomUUID().toString() : islandId.toString();
        return "Island-" + value.substring(0, 8);
    }

    private void publishTicketFailure(UUID playerUuid, UUID islandId, String reason) {
        events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
            "playerUuid", playerUuid.toString(),
            "islandId", islandId == null ? "" : islandId.toString(),
            "action", RouteAction.HOME.name(),
            "reason", reason
        ));
    }

    private void failPreparingTickets(UUID islandId, String targetNode, String reason) {
        try {
            tickets.markFailedForIsland(islandId, targetNode, reason);
        } catch (RuntimeException ignored) {
            // Preserve the authoritative creation failure even if ticket cleanup is unavailable.
        }
    }

    private static boolean isMigrationInputOnlyTemplate(String templateId) {
        return "superiorskyblock2".equalsIgnoreCase(templateId == null ? "" : templateId.trim());
    }

    private static boolean paidTemplate(IslandTemplateSnapshot template) {
        try {
            return new BigDecimal(template.creationCost()).signum() > 0;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static boolean placementMissing(kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime) {
        return runtime == null
            || runtime.activeWorld() == null
            || runtime.activeWorld().isBlank()
            || runtime.cellX() == null
            || runtime.cellZ() == null;
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
