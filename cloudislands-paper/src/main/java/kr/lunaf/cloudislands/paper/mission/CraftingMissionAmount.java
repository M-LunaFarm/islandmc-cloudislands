package kr.lunaf.cloudislands.paper.mission;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class CraftingMissionAmount {
    private CraftingMissionAmount() {
    }

    static long from(CraftItemEvent event, ItemStack result) {
        if (event == null || result == null || result.getType() == Material.AIR || result.getAmount() <= 0) {
            return 0L;
        }
        if (!event.isShiftClick()) {
            return result.getAmount();
        }
        if (!(event.getInventory() instanceof CraftingInventory craftingInventory)
            || !(event.getWhoClicked().getInventory() instanceof PlayerInventory playerInventory)) {
            return 0L;
        }

        int[] matrixAmounts = matrixAmounts(craftingInventory.getMatrix());
        long[] storageCapacities = storageCapacities(playerInventory.getStorageContents(), result);
        return shiftCraftedAmount(result.getAmount(), matrixAmounts, storageCapacities);
    }

    static long shiftCraftedAmount(int resultAmount, int[] matrixAmounts, long[] storageCapacities) {
        if (resultAmount <= 0 || matrixAmounts == null || matrixAmounts.length == 0
            || storageCapacities == null || storageCapacities.length == 0) {
            return 0L;
        }

        long craftsByIngredients = Long.MAX_VALUE;
        for (int amount : matrixAmounts) {
            if (amount <= 0) {
                return 0L;
            }
            craftsByIngredients = Math.min(craftsByIngredients, amount);
        }

        long availableSpace = 0L;
        for (long capacity : storageCapacities) {
            if (capacity <= 0L) {
                continue;
            }
            availableSpace = saturatedAdd(availableSpace, capacity);
        }
        long craftsBySpace = availableSpace / resultAmount;
        long crafts = Math.min(craftsByIngredients, craftsBySpace);
        return saturatedMultiply(crafts, resultAmount);
    }

    private static int[] matrixAmounts(ItemStack[] matrix) {
        if (matrix == null || matrix.length == 0) {
            return new int[0];
        }
        List<Integer> amounts = new ArrayList<>(matrix.length);
        for (ItemStack ingredient : matrix) {
            if (ingredient != null && ingredient.getType() != Material.AIR && ingredient.getAmount() > 0) {
                amounts.add(ingredient.getAmount());
            }
        }
        int[] result = new int[amounts.size()];
        for (int index = 0; index < amounts.size(); index++) {
            result[index] = amounts.get(index);
        }
        return result;
    }

    private static long[] storageCapacities(ItemStack[] storage, ItemStack result) {
        if (storage == null || storage.length == 0) {
            return new long[0];
        }
        long[] capacities = new long[storage.length];
        int resultMaxStack = Math.max(1, result.getMaxStackSize());
        for (int index = 0; index < storage.length; index++) {
            ItemStack slot = storage[index];
            if (slot == null || slot.getType() == Material.AIR) {
                capacities[index] = resultMaxStack;
            } else if (slot.isSimilar(result)) {
                capacities[index] = Math.max(0, Math.min(resultMaxStack, slot.getMaxStackSize()) - slot.getAmount());
            }
        }
        return capacities;
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }
}
