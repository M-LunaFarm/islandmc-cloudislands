package kr.lunaf.cloudislands.coreservice.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobCompletionOutboxStoreTest {
    @Test
    void staleDispatchingEventsCanBeClaimedAgain() {
        InMemoryJobCompletionOutboxStore store = new InMemoryJobCompletionOutboxStore();
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000501");
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000502");
        Instant now = Instant.parse("2026-06-23T00:00:00Z");
        store.append(List.of(new JobCompletionEvent(eventId, islandId, 1L, "ISLAND_SNAPSHOT_CREATED", Map.of("islandId", islandId.toString()))));

        List<JobCompletionOutboxEntry> firstClaim = store.claimDue(1, now);
        List<JobCompletionOutboxEntry> earlyClaim = store.claimDue(1, now.plusSeconds(29));
        List<JobCompletionOutboxEntry> reclaimed = store.claimDue(1, now.plusSeconds(31));

        assertEquals(1, firstClaim.size());
        assertTrue(earlyClaim.isEmpty());
        assertEquals(1, reclaimed.size());
        assertEquals(eventId, reclaimed.getFirst().eventId());
        assertEquals(2, reclaimed.getFirst().attempts());
    }
}
