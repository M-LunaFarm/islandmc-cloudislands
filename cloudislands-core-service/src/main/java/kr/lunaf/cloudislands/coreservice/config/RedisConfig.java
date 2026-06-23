package kr.lunaf.cloudislands.coreservice.config;

import java.net.URI;

public record RedisConfig(
    URI uri,
    String jobQueueMode,
    String eventBusMode
) {}
