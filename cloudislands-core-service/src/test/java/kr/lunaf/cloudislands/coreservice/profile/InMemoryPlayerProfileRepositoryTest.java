package kr.lunaf.cloudislands.coreservice.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryPlayerProfileRepositoryTest {
    @Test
    void preservesPersonalFlightPreferenceAcrossOtherProfileMutations() {
        InMemoryPlayerProfileRepository repository = new InMemoryPlayerProfileRepository();
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000099");

        assertTrue(repository.setIslandFlyEnabled(playerUuid, true).islandFlyEnabled());
        assertTrue(repository.touch(playerUuid, "Flyer", "en_us").islandFlyEnabled());
        assertTrue(repository.setDisbandsRemaining(playerUuid, 3).islandFlyEnabled());
        assertTrue(repository.setPrimaryIsland(playerUuid, UUID.randomUUID()).islandFlyEnabled());
        assertFalse(repository.setIslandFlyEnabled(playerUuid, false).islandFlyEnabled());
    }

    @Test
    void preservesIndependentVisualPreferencesAcrossOtherProfileMutations() {
        InMemoryPlayerProfileRepository repository = new InMemoryPlayerProfileRepository();
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-00000000009a");

        assertFalse(repository.setWorldBorderEnabled(playerUuid, false).worldBorderEnabled());
        assertFalse(repository.setBlocksStackerEnabled(playerUuid, false).blocksStackerEnabled());
        var touched = repository.touch(playerUuid, "Viewer", "en_us");
        assertFalse(touched.worldBorderEnabled());
        assertFalse(touched.blocksStackerEnabled());
        assertTrue(repository.setWorldBorderEnabled(playerUuid, true).worldBorderEnabled());
        assertFalse(repository.find(playerUuid).blocksStackerEnabled());
        assertEquals("red", repository.setBorderColor(playerUuid, "RED").borderColor());
        assertEquals("red", repository.touch(playerUuid, "Viewer", "ko_kr").borderColor());
        assertEquals("blue", repository.setBorderColor(playerUuid, "purple").borderColor());
    }

    @Test
    void partialUpdatesPreserveAtomicSaturatingDisbandQuota() throws Exception {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        InMemoryPlayerProfileRepository repository = new InMemoryPlayerProfileRepository();

        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 1_000; index++) {
                int sequence = index;
                workers.submit(() -> repository.addDisbandsRemaining(playerUuid, 1));
                workers.submit(() -> repository.touch(playerUuid, "Player" + sequence, sequence % 2 == 0 ? "ko_kr" : "en_us"));
            }
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(1_000, repository.find(playerUuid).disbandsRemaining());
        assertEquals(Integer.MAX_VALUE, repository.addDisbandsRemaining(playerUuid, Integer.MAX_VALUE).disbandsRemaining());
        assertEquals(Integer.MAX_VALUE, repository.addDisbandsRemaining(playerUuid, 1).disbandsRemaining());
        assertEquals(0, repository.addDisbandsRemaining(playerUuid, Integer.MIN_VALUE).disbandsRemaining());
        assertEquals(0, repository.addDisbandsRemaining(playerUuid, -1).disbandsRemaining());
    }

    @Test
    void onlyNewestReservedPrimaryIslandSelectionCanApply() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        UUID firstIsland = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
        UUID secondIsland = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
        InMemoryPlayerProfileRepository repository = new InMemoryPlayerProfileRepository();

        long firstRevision = repository.reservePrimaryIslandSelection(playerUuid);
        long secondRevision = repository.reservePrimaryIslandSelection(playerUuid);

        assertTrue(repository.setPrimaryIslandIfSelectionCurrent(playerUuid, secondIsland, secondRevision).isPresent());
        assertTrue(repository.setPrimaryIslandIfSelectionCurrent(playerUuid, firstIsland, firstRevision).isEmpty());
        assertEquals(secondIsland, repository.find(playerUuid).primaryIslandId().orElseThrow());
    }

    @Test
    void administrativePrimaryIslandMutationFencesPendingPlayerSelection() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID pendingIsland = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        UUID adminIsland = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        InMemoryPlayerProfileRepository repository = new InMemoryPlayerProfileRepository();

        long pendingRevision = repository.reservePrimaryIslandSelection(playerUuid);
        repository.setPrimaryIsland(playerUuid, adminIsland);

        assertTrue(repository.setPrimaryIslandIfSelectionCurrent(playerUuid, pendingIsland, pendingRevision).isEmpty());
        assertEquals(adminIsland, repository.find(playerUuid).primaryIslandId().orElseThrow());
    }

    @Test
    void onlyNewestReservedFlightPreferenceCanApply() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
        InMemoryPlayerProfileRepository repository = new InMemoryPlayerProfileRepository();

        long firstRevision = repository.reservePreferenceMutation(playerUuid, "island-fly");
        long secondRevision = repository.reservePreferenceMutation(playerUuid, "island-fly");

        assertTrue(repository.setIslandFlyEnabledIfPreferenceCurrent(playerUuid, true, "island-fly", secondRevision).isPresent());
        assertTrue(repository.setIslandFlyEnabledIfPreferenceCurrent(playerUuid, false, "island-fly", firstRevision).isEmpty());
        assertTrue(repository.find(playerUuid).islandFlyEnabled());
    }
}
