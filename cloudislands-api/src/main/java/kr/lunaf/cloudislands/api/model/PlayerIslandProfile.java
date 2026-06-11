package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PlayerIslandProfile(UUID playerUuid, String lastName, Optional<UUID> primaryIslandId, Instant lastSeenAt) {}
