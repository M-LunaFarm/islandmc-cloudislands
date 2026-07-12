package kr.lunaf.cloudislands.coreservice.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
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
}
