package kr.lunaf.cloudislands.paper.event;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerEventAsyncPolicyTest {
    @Test
    void postMutationWorkerEventsAreExplicitlyAsynchronous() {
        UUID islandId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();

        assertTrue(new IslandCreateEvent(islandId, jobId, "node-a", "ci_shard_001").isAsynchronous());
        assertTrue(new IslandDeleteEvent(islandId, jobId, "node-a", 4L).isAsynchronous());
    }
}
