package kr.lunaf.cloudislands.migration;

import java.util.List;
import java.util.UUID;

public record MigrationManifest(UUID islandId, UUID ownerUuid, List<UUID> members, int size, long level, String worth) {}
