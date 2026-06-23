package kr.lunaf.cloudislands.coreservice.config;

import java.time.Duration;

public record JobConfig(
    String queueMode,
    Duration leaseDuration
) {}
