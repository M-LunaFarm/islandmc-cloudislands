package kr.lunaf.cloudislands.coreservice.repository;

import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachingIslandMetadataRepositoryTest {
    @Test
    void cachedTemporaryTrustMembersExpireAtReadTime() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Instant now = Instant.parse("2026-01-02T03:04:05Z");

        assertTrue(CachingIslandMetadataRepository.activeMember(
            new IslandMemberSnapshot(islandId, playerUuid, IslandRole.TRUSTED, now.minusSeconds(60), null),
            now
        ));
        assertTrue(CachingIslandMetadataRepository.activeMember(
            new IslandMemberSnapshot(islandId, playerUuid, IslandRole.TRUSTED, now.minusSeconds(60), now.plusSeconds(1)),
            now
        ));
        assertFalse(CachingIslandMetadataRepository.activeMember(
            new IslandMemberSnapshot(islandId, playerUuid, IslandRole.TRUSTED, now.minusSeconds(60), now),
            now
        ));
        assertFalse(CachingIslandMetadataRepository.activeMember(
            new IslandMemberSnapshot(islandId, playerUuid, IslandRole.TRUSTED, now.minusSeconds(60), now.minusSeconds(1)),
            now
        ));
    }
}
