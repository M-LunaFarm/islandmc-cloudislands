package kr.lunaf.cloudislands.protocol.job;

import java.time.Instant;
import java.util.UUID;

public record JobClaimLease(
    UUID jobId,
    String streamId,
    String claimedByNode,
    String claimToken,
    long claimEpoch,
    Instant leaseExpiresAt,
    int attempt
) {
    private static final Instant UNCLAIMED_EXPIRES_AT = Instant.EPOCH;

    public JobClaimLease {
        streamId = streamId == null ? "" : streamId.trim();
        claimedByNode = claimedByNode == null ? "" : claimedByNode.trim();
        claimToken = claimToken == null ? "" : claimToken.trim();
        leaseExpiresAt = leaseExpiresAt == null ? UNCLAIMED_EXPIRES_AT : leaseExpiresAt;
        claimEpoch = Math.max(0L, claimEpoch);
        attempt = Math.max(0, attempt);
    }

    public static JobClaimLease unclaimed(UUID jobId) {
        return new JobClaimLease(jobId, "", "", "", 0L, UNCLAIMED_EXPIRES_AT, 0);
    }

    public boolean claimed() {
        return jobId != null && !claimedByNode.isBlank() && !claimToken.isBlank() && claimEpoch > 0L;
    }

    public boolean matches(UUID expectedJobId, String expectedNodeId) {
        String safeNodeId = expectedNodeId == null ? "" : expectedNodeId.trim();
        return claimed()
            && jobId.equals(expectedJobId)
            && claimedByNode.equals(safeNodeId);
    }
}
