package kr.lunaf.cloudislands.api.model;

import java.time.Instant;

public record CloudIslandsStatusSnapshot(
    String platform,
    String role,
    String nodeId,
    String version,
    boolean providerRegistered,
    boolean coreTokenConfigured,
    boolean adminTokenConfigured,
    boolean forwardingRequired,
    boolean forwardingSecretConfigured,
    boolean routeSessionEnforced,
    int onlinePlayers,
    int activeIslands,
    int activationQueue,
    Instant sampledAt,
    String readPolicy,
    String writeAuthority,
    String syncEventPolicy,
    String addonStoragePolicy
) {}
