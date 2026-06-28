package kr.lunaf.cloudislands.coreservice.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import org.junit.jupiter.api.Test;

class InMemoryIslandJobPublisherTest {
    @Test
    void duplicateJobIdPublishIsIdempotent() {
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        IslandJob first = new IslandJob(jobId, IslandJobType.CREATE_ISLAND, islandId, "island-node-1", 0, Map.of("request", "first"), Instant.EPOCH);
        IslandJob duplicate = new IslandJob(jobId, IslandJobType.CREATE_ISLAND, islandId, "island-node-1", 0, Map.of("request", "duplicate"), Instant.EPOCH.plusSeconds(1));

        jobs.publish(first);
        jobs.publish(duplicate);

        assertEquals(1, jobs.snapshot().size());
        assertEquals("first", jobs.snapshot().getFirst().payload().get("request"));
    }

    @Test
    void claimReturnsLeaseIdentityForWorkerCompletion() {
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        UUID jobId = UUID.randomUUID();
        jobs.publish(new IslandJob(jobId, IslandJobType.SAVE_ISLAND, UUID.randomUUID(), "island-node-1", 0, Map.of(), Instant.EPOCH));

        IslandJob claimed = jobs.claim("island-node-1", List.of(IslandJobType.SAVE_ISLAND), 1).getFirst();

        assertEquals(jobId, claimed.claimLease().jobId());
        assertEquals("island-node-1", claimed.claimLease().claimedByNode());
        assertFalse(claimed.claimLease().claimToken().isBlank());
        assertEquals(1L, claimed.claimLease().claimEpoch());
        assertEquals(1, claimed.claimLease().attempt());
        assertTrue(claimed.claimLease().matches(jobId, "island-node-1"));
    }

    @Test
    void completionRequiresMatchingClaimLease() {
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        UUID jobId = UUID.randomUUID();
        jobs.publish(new IslandJob(jobId, IslandJobType.SAVE_ISLAND, UUID.randomUUID(), "island-node-1", 0, Map.of(), Instant.EPOCH));
        JobClaimLease lease = jobs.claim("island-node-1", List.of(IslandJobType.SAVE_ISLAND), 1).getFirst().claimLease();
        JobClaimLease forged = new JobClaimLease(
            lease.jobId(),
            lease.streamId(),
            lease.claimedByNode(),
            lease.claimToken() + "-forged",
            lease.claimEpoch(),
            lease.leaseExpiresAt(),
            lease.attempt()
        );

        assertFalse(jobs.complete("island-node-1", jobId, forged));
        assertTrue(jobs.findClaimed(jobId, lease).isPresent());
        assertTrue(jobs.complete("island-node-1", jobId, lease));
        assertEquals(0L, jobs.countsByState().get("CLAIMED"));
    }
}
