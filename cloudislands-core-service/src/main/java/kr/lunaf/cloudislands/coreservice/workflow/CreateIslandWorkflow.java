package kr.lunaf.cloudislands.coreservice.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class CreateIslandWorkflow {
    private final IslandRepository islands;
    private final InMemoryNodeRegistry nodes;
    private final NodeAllocator allocator;
    private final IslandJobPublisher jobs;

    public CreateIslandWorkflow(IslandRepository islands, InMemoryNodeRegistry nodes, NodeAllocator allocator, IslandJobPublisher jobs) {
        this.islands = islands;
        this.nodes = nodes;
        this.allocator = allocator;
        this.jobs = jobs;
    }

    public CreateIslandResult create(UUID ownerUuid, String templateId) {
        if (islands.findByOwner(ownerUuid).isPresent()) {
            return new CreateIslandResult(false, "ALREADY_HAS_ISLAND", null, null);
        }
        NodeLoad node = allocator.selectBestNode(nodes.snapshot(), Instant.now())
            .orElse(null);
        if (node == null) {
            return new CreateIslandResult(false, "NODE_UNAVAILABLE", null, null);
        }
        UUID islandId = UUID.randomUUID();
        IslandSnapshot island = islands.createOwnedIsland(islandId, ownerUuid, templateId, "Island");
        jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.CREATE_ISLAND, islandId, node.nodeId(), 0, Map.of("templateId", templateId), Instant.now()));
        RouteTicket ticket = new RouteTicket(UUID.randomUUID(), ownerUuid, RouteAction.HOME, islandId, node.nodeId(), "ci_shard_001", RouteTicketState.PREPARING, Instant.now().plusSeconds(30), UUID.randomUUID().toString(), Map.of("targetServerName", node.velocityServerName()));
        return new CreateIslandResult(true, "CREATING", island, ticket);
    }
}
