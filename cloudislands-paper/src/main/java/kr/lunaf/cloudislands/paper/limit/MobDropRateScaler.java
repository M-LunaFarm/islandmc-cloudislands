package kr.lunaf.cloudislands.paper.limit;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class MobDropRateScaler {
    static final long MAX_PERCENT = 10_000L;
    private static final int MAX_OUTPUT_STACKS = 65_536;

    private MobDropRateScaler() {
    }

    static void scale(List<ItemStack> drops, long requestedPercent, Random random) {
        scale(drops, requestedPercent, bound -> random.nextInt(bound));
    }

    static void scale(List<ItemStack> drops, long requestedPercent, IntUnaryOperator randomRoll) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        long percent = normalizePercent(requestedPercent);
        if (percent == 100L) {
            return;
        }
        if (percent == 0L) {
            drops.clear();
            return;
        }
        int initialCapacity = (int) Math.min((long) drops.size() * 2L, MAX_OUTPUT_STACKS);
        List<ItemStack> scaledDrops = new ArrayList<>(initialCapacity);
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR || scaledDrops.size() >= MAX_OUTPUT_STACKS) {
                continue;
            }
            List<Integer> amounts = splitAmounts(
                drop.getAmount(),
                drop.getMaxStackSize(),
                percent,
                randomRoll.applyAsInt(100),
                MAX_OUTPUT_STACKS - scaledDrops.size()
            );
            appendSplit(scaledDrops, drop, amounts);
        }
        drops.clear();
        drops.addAll(scaledDrops);
    }

    static long normalizePercent(long requestedPercent) {
        return Math.min(Math.max(0L, requestedPercent), MAX_PERCENT);
    }

    static long scaledAmount(int originalAmount, long percent, int roll) {
        long normalizedPercent = normalizePercent(percent);
        long numerator = Math.max(0, originalAmount) * normalizedPercent;
        long amount = numerator / 100L;
        long remainder = numerator % 100L;
        if (remainder > 0L && Math.floorMod(roll, 100) < remainder) {
            amount++;
        }
        return amount;
    }

    static List<Integer> splitAmounts(int originalAmount, int maxStackSize, long percent, int roll, int maxParts) {
        long amount = scaledAmount(originalAmount, percent, roll);
        int boundedMaxStackSize = Math.max(1, maxStackSize);
        List<Integer> parts = new ArrayList<>((int) Math.min(amount / boundedMaxStackSize + 1L, Math.max(0, maxParts)));
        while (amount > 0L && parts.size() < Math.max(0, maxParts)) {
            int partAmount = (int) Math.min(amount, boundedMaxStackSize);
            parts.add(partAmount);
            amount -= partAmount;
        }
        return List.copyOf(parts);
    }

    private static void appendSplit(List<ItemStack> output, ItemStack source, List<Integer> amounts) {
        boolean first = true;
        for (int partAmount : amounts) {
            ItemStack part = first ? source : source.clone();
            part.setAmount(partAmount);
            output.add(part);
            first = false;
        }
    }
}
