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
import org.junit.jupiter.api.Test;

class InMemoryIslandJobPublisherTest {
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
}
