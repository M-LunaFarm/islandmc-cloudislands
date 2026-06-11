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
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class CreateIslandWorkflow {
    private final IslandRepository islands;
    private final IslandMetadataRepository metadata;
    private final InMemoryNodeRegistry nodes;
    private final NodeAllocator allocator;
    private final IslandJobPublisher jobs;
    private final GlobalEventPublisher events;
    private final InMemoryRouteTicketStore tickets;

    public CreateIslandWorkflow(IslandRepository islands, IslandMetadataRepository metadata, InMemoryNodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs, GlobalEventPublisher events, InMemoryRouteTicketStore tickets) {
        this.islands = islands;
        this.metadata = metadata;
        this.nodes = nodes;
        this.allocator = allocator;
        this.jobs = jobs;
        this.events = events;
        this.tickets = tickets;
    }

    public CreateIslandResult create(UUID ownerUuid, String templateId) {
        if (islands.findByOwner(ownerUuid).isPresent()) {
            return new CreateIslandResult(false, "ALREADY_HAS_ISLAND", null, null);
        }
        NodeLoad node = allocator.selectBestNode(nodes.snapshot(), Instant.now()).orElse(null);
        if (node == null) {
            return new CreateIslandResult(false, "NODE_UNAVAILABLE", null, null);
        }
        UUID islandId = UUID.randomUUID();
        IslandSnapshot island = islands.createOwnedIsland(islandId, ownerUuid, templateId, "Island");
        metadata.upsertMember(islandId, ownerUuid, IslandRole.OWNER);
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.CREATE_ISLAND, islandId, node.nodeId(), 0, Map.of("templateId", templateId), Instant.now()));
        events.publish(CloudIslandEventType.ISLAND_CREATED.name(), Map.of("islandId", islandId.toString(), "ownerUuid", ownerUuid.toString(), "targetNode", node.nodeId()));
        RouteTicket ticket = tickets.save(new RouteTicket(UUID.randomUUID(), ownerUuid, RouteAction.HOME, islandId, node.nodeId(), "ci_shard_001", RouteTicketState.READY, Instant.now().plusSeconds(30), UUID.randomUUID().toString(), Map.of(
            "targetServerName", node.velocityServerName(),
            "localX", "0.5",
            "localY", "100.0",
            "localZ", "0.5",
            "yaw", "180.0",
            "pitch", "0.0"
        )));
        events.publish(CloudIslandEventType.ROUTE_TICKET_CREATED.name(), Map.of("ticketId", ticket.ticketId().toString(), "islandId", islandId.toString(), "playerUuid", ownerUuid.toString()));
        return new CreateIslandResult(true, "CREATING", island, ticket);
    }
}
