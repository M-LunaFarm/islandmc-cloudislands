package kr.lunaf.cloudislands.paper.event;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void localRouteConsumptionPublishesItsAsyncEventOffTheGlobalThread() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/RouteTicketConsumer.java"));
        int event = source.indexOf("RouteTicketConsumedEvent consumedEvent");
        int async = source.indexOf("PaperSchedulers.runAsync(plugin", event);
        int publish = source.indexOf("PaperEvents.call(consumedEvent)", async);

        assertTrue(event >= 0 && async > event && publish > async,
            "the explicitly asynchronous route event must not be fired from the Paper global thread");
    }
}
