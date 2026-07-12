package kr.lunaf.cloudislands.coreservice.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CachingIslandTemplateRepositoryTest {
    @Test
    void delegatesReorderAndDeleteWhenRedisCacheIsEnabled() {
        InMemoryIslandTemplateRepository delegate = new InMemoryIslandTemplateRepository();
        delegate.upsert("classic", "Classic", true, "1.21");
        CachingIslandTemplateRepository repository = new CachingIslandTemplateRepository(delegate, URI.create("redis://127.0.0.1:1"));

        assertTrue(repository.reorder("classic", 17));
        assertEquals(17, delegate.find("classic").orElseThrow().sortOrder());

        assertTrue(repository.delete("classic"));
        assertFalse(delegate.find("classic").isPresent());
    }

    @Test
    void preservesTemplateTimestampsAcrossRedisCacheEncoding() {
        Instant createdAt = Instant.parse("2026-07-01T01:02:03Z");
        Instant updatedAt = Instant.parse("2026-07-12T04:05:06Z");
        IslandTemplateSnapshot template = new IslandTemplateSnapshot(
            "classic", "Classic", "Starter island", "starter", true, "1.21", "",
            "GRASS_BLOCK", 0, "", "templates/classic.zip", "abc123", 42L, 3, 300,
            0.5D, 100.0D, 0.5D, 180.0F, 0.0F, "default", "normal",
            "minecraft:plains", "BLUE", "0", "100", 5, List.of("starter"),
            createdAt, updatedAt
        );

        IslandTemplateSnapshot cached = CachingIslandTemplateRepository.parse(
            CachingIslandTemplateRepository.encode(List.of(template))
        ).getFirst();

        assertEquals(createdAt, cached.createdAt());
        assertEquals(updatedAt, cached.updatedAt());
    }

    @Test
    void continuesReadingLegacyTemplateCacheRowsWithoutTimestamps() {
        IslandTemplateSnapshot template = new IslandTemplateSnapshot("classic", "Classic", true, "1.21");
        String[] currentFields = CachingIslandTemplateRepository.encode(List.of(template)).strip().split("\\|", -1);
        String legacyRow = String.join("|", java.util.Arrays.copyOf(currentFields, 28));

        IslandTemplateSnapshot cached = CachingIslandTemplateRepository.parse(legacyRow).getFirst();

        assertEquals(Instant.EPOCH, cached.createdAt());
        assertEquals(Instant.EPOCH, cached.updatedAt());
    }

    @Test
    void treatsFullyCorruptNonEmptyCacheAsMiss() {
        assertTrue(CachingIslandTemplateRepository.decodeCached("not-a-template-row").isEmpty());

        IslandTemplateSnapshot template = new IslandTemplateSnapshot("classic", "Classic", true, "1.21");
        String encoded = CachingIslandTemplateRepository.encode(List.of(template));
        assertTrue(CachingIslandTemplateRepository.decodeCached(encoded + "corrupt-row\n").isEmpty());
        assertEquals(
            List.of(template),
            CachingIslandTemplateRepository.decodeCached(encoded).orElseThrow()
        );
    }
}
