package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface CloudIslandEvent permits IslandCreatedEvent, IslandActivatedEvent, IslandMigratedEvent, RouteTicketConsumedEvent {
    UUID islandId();
    Instant occurredAt();
}
