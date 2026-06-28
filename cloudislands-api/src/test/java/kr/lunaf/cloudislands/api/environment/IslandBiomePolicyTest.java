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
    }

    @Test
    void rejectsBlankOrUnsupportedBiomes() {
        assertTrue(IslandBiomePolicy.normalize("").isEmpty());
        assertTrue(IslandBiomePolicy.normalize("minecraft:the_void").isEmpty());
        assertFalse(IslandBiomePolicy.supportedBiomes().isEmpty());
    }
}
