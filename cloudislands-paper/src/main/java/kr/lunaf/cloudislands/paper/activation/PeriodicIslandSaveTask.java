package kr.lunaf.cloudislands.paper.activation;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PeriodicIslandSaveTask {
    private final Plugin plugin;
    private final ActiveIslandRegistry activeIslands;
    private final IslandSaveService saveService;
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private BukkitTask task;

    public PeriodicIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, IslandSaveService saveService) {
        this(plugin, activeIslands, saveService, null, "");
    }

    public PeriodicIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, IslandSaveService saveService, CoreApiClient coreApiClient, String nodeId) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.saveService = saveService;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId == null ? "" : nodeId;
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
                IslandSaveService.SaveResult result = saveService.save(activeIsland.islandId(), activeIsland);
                recordSnapshot(result);
            } catch (java.io.IOException exception) {
                plugin.getLogger().warning("Periodic island save failed for " + activeIsland.islandId() + ": " + exception.getMessage());
            }
        }
    }

    private void recordSnapshot(IslandSaveService.SaveResult result) {
        if (coreApiClient == null || result.snapshotNo() <= 0L) {
            return;
        }
        String storagePath = result.storagePath() == null || result.storagePath().isBlank()
            ? "islands/" + result.islandId() + "/snapshots/" + String.format("%06d", result.snapshotNo()) + "/bundle.tar.zst"
            : result.storagePath();
        coreApiClient.recordIslandSnapshot(result.islandId(), result.snapshotNo(), storagePath, "AUTO", result.checksum(), result.sizeBytes(), nodeId)
            .exceptionally(error -> {
                plugin.getLogger().warning("Periodic island snapshot record failed for " + result.islandId() + ": " + error.getMessage());
                return null;
            });
    }
}
