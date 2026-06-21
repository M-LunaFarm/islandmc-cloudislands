package kr.lunaf.cloudislands.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import org.junit.jupiter.api.Test;

class IslandEventServiceDeliveryTest {
    @Test
    void globalEventSnapshotExposesStableDeduplicationId() {
        GlobalEventSnapshot explicit = new GlobalEventSnapshot(7L, "ISLAND_CREATED", Map.of("eventId", "route:abc"), Instant.EPOCH);
        GlobalEventSnapshot sequenced = new GlobalEventSnapshot(8L, "ISLAND_CREATED", Map.of(), Instant.EPOCH);
        GlobalEventSnapshot synthetic = new GlobalEventSnapshot("ISLAND_CREATED", Map.of("islandId", "one"), Instant.EPOCH);

        assertEquals("route:abc", explicit.eventId());
        assertEquals("8", sequenced.eventId());
        assertTrue(synthetic.eventId().startsWith("ISLAND_CREATED:1970-01-01T00:00:00Z:"));
    }

    @Test
    void subscriptionFiltersDuplicateEventIdsWithinOneSubscription() throws Exception {
        ScriptedIslandEventService service = new ScriptedIslandEventService(List.of(
            List.of(
                new GlobalEventSnapshot(1L, "ISLAND_CREATED", Map.of("eventId", "evt-1"), Instant.EPOCH),
                new GlobalEventSnapshot(2L, "ISLAND_CREATED", Map.of("eventId", "evt-1"), Instant.EPOCH),
                new GlobalEventSnapshot(3L, "ISLAND_DELETED", Map.of("eventId", "evt-2"), Instant.EPOCH)
            )
        ));
        List<GlobalEventSnapshot> delivered = new CopyOnWriteArrayList<>();

        try (IslandEventService.GlobalEventSubscription subscription = service.subscribeGlobalEvents(0L, 10, 1L, delivered::addAll)) {
            assertTrue(subscription != null);
            Thread.sleep(150L);
        }

        assertEquals(List.of("evt-1", "evt-2"), delivered.stream().map(GlobalEventSnapshot::eventId).toList());
    }

    private static final class ScriptedIslandEventService implements IslandEventService {
        private final List<List<GlobalEventSnapshot>> pages;
        private int calls;

        private ScriptedIslandEventService(List<List<GlobalEventSnapshot>> pages) {
            this.pages = new ArrayList<>(pages);
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents() {
            return listGlobalEventsSince(0L, 64);
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents(int limit) {
            return listGlobalEventsSince(0L, limit);
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEventsSince(long sinceSeq, int limit) {
            int index = Math.min(calls++, pages.size() - 1);
            return CompletableFuture.completedFuture(pages.get(index));
        }
    }
}
