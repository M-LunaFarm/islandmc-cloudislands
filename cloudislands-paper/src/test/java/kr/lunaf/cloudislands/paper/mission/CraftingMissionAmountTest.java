package kr.lunaf.cloudislands.paper.mission;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CraftingMissionAmountTest {
    @Test
    void shiftClickCountsEveryCraftAllowedByTheSmallestIngredientStack() {
        assertEquals(16L, CraftingMissionAmount.shiftCraftedAmount(
            4,
            new int[]{8, 4, 12},
            new long[]{64, 64}
        ));
    }

    @Test
    void shiftClickStopsAtActualInventoryCapacity() {
        assertEquals(8L, CraftingMissionAmount.shiftCraftedAmount(
            4,
            new int[]{64, 64},
            new long[]{3, 5, 0}
        ));
    }

    @Test
    void incompleteOutputSpaceDoesNotCreditAnImpossibleCraft() {
        assertEquals(0L, CraftingMissionAmount.shiftCraftedAmount(
            4,
            new int[]{64},
            new long[]{3}
        ));
    }

    @Test
    void emptyMatrixOrStorageCannotProduceMissionProgress() {
        assertEquals(0L, CraftingMissionAmount.shiftCraftedAmount(1, new int[0], new long[]{64}));
        assertEquals(0L, CraftingMissionAmount.shiftCraftedAmount(1, new int[]{64}, new long[0]));
        assertEquals(0L, CraftingMissionAmount.shiftCraftedAmount(0, new int[]{64}, new long[]{64}));
    }

    @Test
    void arithmeticUsesLongInsteadOfWrappingAtIntegerBounds() {
        assertEquals((long) Integer.MAX_VALUE * Integer.MAX_VALUE, CraftingMissionAmount.shiftCraftedAmount(
            Integer.MAX_VALUE,
            new int[]{Integer.MAX_VALUE},
            new long[]{Long.MAX_VALUE}
        ));
    }
}
