package kr.lunaf.cloudislands.api.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IslandBiomePolicyTest {
    @Test
    void normalizesSupportedMinecraftBiomes() {
        assertEquals("minecraft:plains", IslandBiomePolicy.normalize("plains").orElseThrow());
        assertEquals("minecraft:cherry_grove", IslandBiomePolicy.normalize(" Minecraft:Cherry_Grove ").orElseThrow());
        assertEquals("minecraft:deep_dark", IslandBiomePolicy.normalize("deep_dark").orElseThrow());
        assertEquals("minecraft:warped_forest", IslandBiomePolicy.normalize("warped_forest").orElseThrow());
        assertTrue(IslandBiomePolicy.supportedBiomes().size() > 60);
    }

    @Test
    void rejectsBlankOrUnsupportedBiomes() {
        assertTrue(IslandBiomePolicy.normalize("").isEmpty());
        assertTrue(IslandBiomePolicy.normalize("minecraft:the_void").isEmpty());
        assertFalse(IslandBiomePolicy.supportedBiomes().isEmpty());
    }
}
