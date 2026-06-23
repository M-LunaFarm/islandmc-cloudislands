package kr.lunaf.cloudislands.coreservice.config;

import java.time.Duration;

public record ObservabilityConfig(
    int rateLimitRequests,
    Duration rateLimitWindow
) {}
