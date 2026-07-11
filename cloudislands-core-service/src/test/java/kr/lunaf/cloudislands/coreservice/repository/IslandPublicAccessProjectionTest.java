package kr.lunaf.cloudislands.coreservice.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IslandPublicAccessProjectionTest {
    @Test
    void inMemoryRepositoryUpdatesSnapshotVisibility() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        UUID islandId = UUID.randomUUID();
        islands.createOwnedIsland(islandId, UUID.randomUUID(), "default", "Visibility Test");

        assertFalse(islands.findById(islandId).orElseThrow().publicAccess());
        islands.setPublicAccess(islandId, true);
        assertTrue(islands.findById(islandId).orElseThrow().publicAccess());
    }

    @Test
    void cachingRepositoryRefreshesSummaryAfterVisibilityMutation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/CachingIslandRepository.java"));

        assertTrue(source.contains("delegate.setPublicAccess(islandId, publicAccess);"));
        assertTrue(source.contains("delegate.findById(islandId).ifPresent(this::cache);"));
    }
}
