package kr.lunaf.cloudislands.paper.activation;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PeriodicIslandSaveTask {
    private final Plugin plugin;
    private final ActiveIslandRegistry activeIslands;
    private final IslandSaveService saveService;
    private BukkitTask task;

    public PeriodicIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, IslandSaveService saveService) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.saveService = saveService;
    }

    public void start(long intervalSeconds) {
        stop();
        if (intervalSeconds <= 0L) {
            return;
        }
        long intervalTicks = Math.max(20L, intervalSeconds * 20L);
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveAll, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void saveAll() {
        for (ActiveIslandRegistry.ActiveIsland activeIsland : activeIslands.snapshot()) {
            try {
                saveService.save(activeIsland.islandId(), activeIsland);
            } catch (java.io.IOException exception) {
                plugin.getLogger().warning("Periodic island save failed for " + activeIsland.islandId() + ": " + exception.getMessage());
            }
        }
    }
}
