package kr.lunaf.cloudislands.paper.generator;

import java.util.Random;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

public final class IslandCropGrowthListener implements Listener {
    private final ProtectionController protection;
    private final CropGrowthLevelCache levels;
    private final IslandLimitCache limits;
    private final Random random = new Random();

    public IslandCropGrowthListener(ProtectionController protection, CropGrowthLevelCache levels) {
        this(protection, levels, null);
    }

    public IslandCropGrowthListener(ProtectionController protection, CropGrowthLevelCache levels, IslandLimitCache limits) {
        this.protection = protection;
        this.levels = levels;
        this.limits = limits;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        protection.islandAt(event.getBlock()).ifPresent(islandId -> {
            long cropGrowthRate = limits == null ? 100L : limits.limit(islandId, "RATE:CROP_GROWTH", 100L);
            if (!applyCropGrowthRate(event, ageable, cropGrowthRate)) {
                return;
            }
            int level = levels.level(islandId);
            if (level <= 1 || ageable.getAge() >= ageable.getMaximumAge()) {
                return;
            }
            int bonus = Math.min(4, level - 1);
            if (random.nextInt(100) >= bonus * 20) {
                return;
            }
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
            event.getNewState().setBlockData(ageable);
        });
    }

    private boolean applyCropGrowthRate(BlockGrowEvent event, Ageable ageable, long cropGrowthRate) {
        long percent = Math.max(0L, cropGrowthRate);
        if (percent <= 0L) {
            event.setCancelled(true);
            return false;
        }
        if (percent < 100L) {
            boolean allowed = random.nextInt(100) < percent;
            event.setCancelled(!allowed);
            return allowed;
        }
        int extraSteps = (int) Math.min(4L, (percent / 100L) - 1L);
        int remainder = (int) (percent % 100L);
        if (remainder > 0 && random.nextInt(100) < remainder) {
            extraSteps++;
        }
        if (extraSteps > 0 && ageable.getAge() < ageable.getMaximumAge()) {
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + extraSteps));
            event.getNewState().setBlockData(ageable);
        }
        return true;
    }
}
