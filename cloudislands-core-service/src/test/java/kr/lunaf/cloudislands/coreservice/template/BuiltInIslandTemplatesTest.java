package kr.lunaf.cloudislands.coreservice.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BuiltInIslandTemplatesTest {
    @Test
    void starterTemplateIsPlayableWithoutAnUploadedBundle() {
        IslandTemplateSnapshot starter = BuiltInIslandTemplates.starterTemplate();

        assertEquals("starter", starter.id());
        assertTrue(starter.enabled());
        assertFalse(starter.hasBundle());
        assertTrue(starter.tags().contains("builtin"));
        assertTrue(starter.description().contains("상자"));
    }

    @Test
    void inMemoryRepositoryIncludesTheStarterTemplate() {
        InMemoryIslandTemplateRepository repository = new InMemoryIslandTemplateRepository();

        assertTrue(repository.find("default").isPresent());
        assertTrue(repository.find("starter").isPresent());
        assertEquals(2L, repository.list().stream().filter(IslandTemplateSnapshot::enabled).count());
    }
}
