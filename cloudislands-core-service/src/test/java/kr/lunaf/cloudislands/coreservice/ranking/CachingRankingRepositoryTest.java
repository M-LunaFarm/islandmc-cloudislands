package kr.lunaf.cloudislands.coreservice.ranking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CachingRankingRepositoryTest {
    @Test
    void delegatesIgnoredStateWhenRedisCacheIsEnabled() {
        InMemoryRankingRepository delegate = new InMemoryRankingRepository();
        CachingRankingRepository repository = new CachingRankingRepository(delegate, URI.create("redis://127.0.0.1:1"));
        UUID islandId = UUID.randomUUID();

        repository.setIgnored(islandId, true);

        assertTrue(delegate.isIgnored(islandId));
        assertTrue(repository.isIgnored(islandId));

        repository.setIgnored(islandId, false);

        assertFalse(delegate.isIgnored(islandId));
        assertFalse(repository.isIgnored(islandId));
    }
}
