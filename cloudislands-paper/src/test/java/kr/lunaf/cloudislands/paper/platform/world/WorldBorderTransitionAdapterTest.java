package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WorldBorderTransitionAdapterTest {
    @Test
    void convertsTickDurationsForLegacyPaperWithoutOverflow() {
        assertEquals(0L, WorldBorderTransitionAdapter.legacySeconds(0L));
        assertEquals(1L, WorldBorderTransitionAdapter.legacySeconds(1L));
        assertEquals(1L, WorldBorderTransitionAdapter.legacySeconds(20L));
        assertEquals(107_374_182L, WorldBorderTransitionAdapter.legacySeconds(2_147_483_647L));
    }
}
