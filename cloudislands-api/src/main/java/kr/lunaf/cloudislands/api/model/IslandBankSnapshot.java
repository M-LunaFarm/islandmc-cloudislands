package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandBankSnapshot(UUID islandId, String balance, Instant updatedAt) {}
