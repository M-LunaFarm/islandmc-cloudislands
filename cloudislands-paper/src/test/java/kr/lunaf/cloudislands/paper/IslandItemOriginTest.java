package kr.lunaf.cloudislands.paper;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandItemOriginTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID OTHER_ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000802");

    @Test
    void originCodecRejectsMissingAndMalformedValues() {
        assertEquals(Optional.of(ISLAND), IslandItemOrigin.decode(ISLAND.toString()));
        assertTrue(IslandItemOrigin.decode(null).isEmpty());
        assertTrue(IslandItemOrigin.decode(" ").isEmpty());
        assertTrue(IslandItemOrigin.decode("not-a-uuid").isEmpty());
    }

    @Test
    void onlyEquivalentOriginsCanMerge() {
        assertTrue(IslandItemOrigin.compatible(Optional.empty(), Optional.empty()));
        assertTrue(IslandItemOrigin.compatible(Optional.of(ISLAND), Optional.of(ISLAND)));
        assertFalse(IslandItemOrigin.compatible(Optional.of(ISLAND), Optional.empty()));
        assertFalse(IslandItemOrigin.compatible(Optional.of(ISLAND), Optional.of(OTHER_ISLAND)));
    }

    @Test
    void protectedItemsCanOnlyEnterTheirOriginIslandInventory() {
        assertTrue(IslandItemOrigin.destinationAllowed(ISLAND, Optional.of(ISLAND)));
        assertFalse(IslandItemOrigin.destinationAllowed(ISLAND, Optional.of(OTHER_ISLAND)));
        assertFalse(IslandItemOrigin.destinationAllowed(ISLAND, Optional.empty()));
    }
}
