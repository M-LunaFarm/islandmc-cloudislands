package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.BlockValueView;
import org.junit.jupiter.api.Test;

class BlockValueLookupTest {
    @Test
    void resolvesPlainNamesNamespacedKeysAndHeldItemStyleKeys() {
        List<BlockValueView> values = List.of(new BlockValueView("minecraft:diamond_block", "1000", 25, 10));

        assertEquals("1000", BlockValueLookup.find(values, "DIAMOND_BLOCK").orElseThrow().worth());
        assertEquals(25L, BlockValueLookup.find(values, "minecraft:diamond_block").orElseThrow().levelPoints());
        assertEquals("minecraft:diamond_block", BlockValueLookup.normalize("diamond block"));
        assertTrue(BlockValueLookup.find(values, "emerald_block").isEmpty());
    }
}
