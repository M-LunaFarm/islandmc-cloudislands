package kr.lunaf.cloudislands.migration.rollback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MigrationRollbackPlan(UUID runId, List<UUID> importedIslandIds, Instant createdAt) {
    public MigrationRollbackPlan {
        importedIslandIds = importedIslandIds == null ? List.of() : List.copyOf(importedIslandIds);
        createdAt = createdAt == null ? Instant.EPOCH : createdAt;
    }
}
