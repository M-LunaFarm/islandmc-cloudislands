package kr.lunaf.cloudislands.coreservice;

import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;

public final class CoreRoutingComponents {
    private CoreRoutingComponents() {
    }

    public static RoutingOrchestrator routing(
        CoreServiceConfig config,
        NodeRegistry nodes,
        NodeAllocator allocator,
        RouteTicketStore tickets,
        IslandRepository islands,
        IslandMetadataRepository metadata,
        IslandRuntimeRepository runtimes,
        IslandTemplateRepository templates,
        IslandJobPublisher jobs,
        GlobalEventPublisher events,
        RedisActivationLock activationLock
    ) {
        return new RoutingOrchestrator(
            nodes,
            allocator,
            tickets,
            islands,
            metadata,
            runtimes,
            templates,
            jobs,
            events,
            config.islandPool(),
            config.routeTicketTtl(),
            config.routePreparingTicketTtl(),
            activationLock
        );
    }
}
