package kr.lunaf.cloudislands.protocol.job;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record IslandJob(UUID jobId, IslandJobType type, UUID islandId, String targetNode, int priority, Map<String, String> payload, Instant createdAt, JobClaimLease claimLease) {
    public IslandJob(UUID jobId, IslandJobType type, UUID islandId, String targetNode, int priority, Map<String, String> payload, Instant createdAt) {
        this(jobId, type, islandId, targetNode, priority, payload, createdAt, JobClaimLease.unclaimed(jobId));
    }

    public IslandJob {
        if (payload == null || payload.isEmpty()) {
            payload = Map.of();
        } else {
            Map<String, String> safePayload = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : payload.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.isBlank()) {
                    continue;
                }
                safePayload.put(key, entry.getValue() == null ? "" : entry.getValue());
            }
            payload = safePayload.isEmpty() ? Map.of() : Map.copyOf(safePayload);
        }
        claimLease = claimLease == null ? JobClaimLease.unclaimed(jobId) : claimLease;
    }

    public IslandJob withClaimLease(JobClaimLease claimLease) {
        return new IslandJob(jobId, type, islandId, targetNode, priority, payload, createdAt, claimLease);
    }
}
