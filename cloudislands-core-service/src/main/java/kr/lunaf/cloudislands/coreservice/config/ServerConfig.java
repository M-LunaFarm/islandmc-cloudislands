package kr.lunaf.cloudislands.coreservice.config;

import java.time.Duration;

public record ServerConfig(
    String bind,
    int port,
    String adminBind,
    int adminPort,
    boolean adminListenerEnabled,
    boolean allowInsecurePublicHttp,
    int httpWorkerThreads,
    int httpQueueCapacity,
    Duration httpKeepAlive,
    Duration httpShutdownGrace
) {}
