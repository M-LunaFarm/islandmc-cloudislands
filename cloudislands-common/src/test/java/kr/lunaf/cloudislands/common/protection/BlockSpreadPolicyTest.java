package kr.lunaf.cloudislands.common.protection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockSpreadPolicyTest {
    @Test
    void onlyFireAndSoulFireUseTheFireSpreadFlag() {
        assertTrue(BlockSpreadPolicy.fireSpread("FIRE", "FIRE"));
        assertTrue(BlockSpreadPolicy.fireSpread("SOUL_FIRE", "SOUL_FIRE"));
        assertTrue(BlockSpreadPolicy.fireSpread("netherrack", "fire"));
        assertFalse(BlockSpreadPolicy.fireSpread("VINE", "VINE"));
        assertFalse(BlockSpreadPolicy.fireSpread("GRASS_BLOCK", "GRASS_BLOCK"));
        assertFalse(BlockSpreadPolicy.fireSpread("SCULK", "SCULK"));
        assertFalse(BlockSpreadPolicy.fireSpread(null, null));
    }
}
