package kr.lunaf.cloudislands.paper.generator;

import java.util.Random;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.block.data.Ageable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

public final class IslandCropGrowthListener implements Listener {
    private final ProtectionController protection;
    private final CropGrowthLevelCache levels;
    private final Random random = new Random();

    public IslandCropGrowthListener(ProtectionController protection, CropGrowthLevelCache levels) {
        this.protection = protection;
        this.levels = levels;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!(event.getNewState().getBlockData() instanceof Ageable ageable)) {
            return;
        }
        protection.islandAt(event.getBlock()).ifPresent(islandId -> {
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
}
