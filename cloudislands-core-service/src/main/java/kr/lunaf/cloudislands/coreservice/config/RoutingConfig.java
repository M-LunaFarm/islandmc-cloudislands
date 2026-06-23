package kr.lunaf.cloudislands.coreservice.config;

import java.time.Duration;

public record RoutingConfig(
    String islandPool,
    String softFullPolicy,
    String hardFullPolicy,
    String migrationPolicy,
    Duration routeTicketTtl,
    Duration routePreparingTicketTtl,
    Duration heartbeatTimeout
) {}
