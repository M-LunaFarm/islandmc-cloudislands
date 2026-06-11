package kr.lunaf.cloudislands.coreservice.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
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

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets) {
        this(islands, metadata, playerProfiles, templates, nodes, allocator, jobs, events, tickets, "island");
    }

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandTemplateRepository templates, NodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, RouteTicketStore tickets, String islandPool) {
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
    }

    public CreateIslandResult create(UUID ownerUuid, String templateId) {
        String normalizedTemplate = templateId == null || templateId.isBlank() ? "default" : templateId;
        IslandTemplateSnapshot template = templates.find(normalizedTemplate).orElse(null);
        if (template == null || !template.enabled()) {
            publishTicketFailure(ownerUuid, null, "TEMPLATE_UNAVAILABLE");
            return new CreateIslandResult(false, "TEMPLATE_UNAVAILABLE", null, null);
        }
        if (islands.findByOwner(ownerUuid).isPresent()) {
            publishTicketFailure(ownerUuid, null, "ALREADY_HAS_ISLAND");
            return new CreateIslandResult(false, "ALREADY_HAS_ISLAND", null, null);
        }
        NodeLoad node = allocator.selectBestNode(nodes.snapshot(), Instant.now(), normalizedTemplate, template.minNodeVersion(), islandPool).orElse(null);
        if (node == null) {
            publishTicketFailure(ownerUuid, null, "NODE_UNAVAILABLE");
            return new CreateIslandResult(false, "NODE_UNAVAILABLE", null, null);
        }
        UUID islandId = UUID.randomUUID();
        IslandSnapshot island = islands.createOwnedIsland(islandId, ownerUuid, normalizedTemplate, "Island");
        metadata.upsertMember(islandId, ownerUuid, IslandRole.OWNER);
        playerProfiles.setPrimaryIsland(ownerUuid, islandId);
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.CREATE_ISLAND, islandId, node.nodeId(), 0, Map.of("templateId", normalizedTemplate), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_CREATED.name(), Map.of("islandId", islandId.toString(), "ownerUuid", ownerUuid.toString(), "targetNode", node.nodeId()));
        RouteTicket ticket = tickets.save(new RouteTicket(UUID.randomUUID(), ownerUuid, RouteAction.HOME, islandId, node.nodeId(), "ci_shard_001", RouteTicketState.PREPARING, Instant.now().plusSeconds(120), UUID.randomUUID().toString(), Map.of(
            "targetServerName", node.velocityServerName(),
            "localX", "0.5",
            "localY", "100.0",
            "localZ", "0.5",
            "yaw", "180.0",
            "pitch", "0.0"
        )));
        events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of("ticketId", ticket.ticketId().toString(), "islandId", islandId.toString(), "playerUuid", ownerUuid.toString(), "action", ticket.action().name(), "targetNode", ticket.targetNode(), "state", ticket.state().name()));
        return new CreateIslandResult(true, "CREATING", island, ticket);
    }

    private void publishTicketFailure(UUID playerUuid, UUID islandId, String reason) {
        events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
            "playerUuid", playerUuid.toString(),
            "islandId", islandId == null ? "" : islandId.toString(),
            "action", RouteAction.HOME.name(),
            "reason", reason
        ));
    }
}
