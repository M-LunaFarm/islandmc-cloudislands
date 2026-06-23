package kr.lunaf.cloudislands.protocol.job.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import org.junit.jupiter.api.Test;

class IslandJobJsonTest {
    @Test
    void roundTripsClaimLeaseInClaimResponse() {
        UUID jobId = UUID.randomUUID();
        Instant leaseExpiresAt = Instant.parse("2026-06-23T01:02:03Z");
        IslandJob job = new IslandJob(
            jobId,
            IslandJobType.SAVE_ISLAND,
            UUID.randomUUID(),
            "island-node-1",
            10,
            Map.of("snapshotNo", "7"),
            Instant.EPOCH,
            new JobClaimLease(jobId, "1730000000000-0", "island-node-1", "token-1", 3L, leaseExpiresAt, 2)
        );

        IslandJob parsed = IslandJobJson.readArray(IslandJobJson.writeArray(List.of(job))).getFirst();

        assertEquals(jobId, parsed.claimLease().jobId());
        assertEquals("1730000000000-0", parsed.claimLease().streamId());
        assertEquals("island-node-1", parsed.claimLease().claimedByNode());
        assertEquals("token-1", parsed.claimLease().claimToken());
        assertEquals(3L, parsed.claimLease().claimEpoch());
        assertEquals(leaseExpiresAt, parsed.claimLease().leaseExpiresAt());
        assertEquals(2, parsed.claimLease().attempt());
        assertTrue(parsed.claimLease().claimed());
    }
}
