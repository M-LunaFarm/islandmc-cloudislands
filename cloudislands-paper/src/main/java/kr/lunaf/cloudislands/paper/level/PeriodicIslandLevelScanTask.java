package kr.lunaf.cloudislands.paper.level;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PeriodicIslandLevelScanTask {
    private final Plugin plugin;
    private final ActiveIslandRegistry activeIslands;
    private final IslandLevelScanService scanService;
    private final AtomicInteger cursor = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private BukkitTask task;
    private volatile UUID lastScannedIslandId;
    private volatile long lastScanStartedAt;
    private volatile long lastScanFinishedAt;
    private volatile long lastScanFailedAt;

    public PeriodicIslandLevelScanTask(Plugin plugin, ActiveIslandRegistry activeIslands, IslandLevelScanService scanService) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.scanService = scanService;
    }

    public void start(long intervalSeconds) {
        long ticks = Math.max(60L, intervalSeconds * 20L);
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::scanNext, ticks, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void scanNext() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        List<ActiveIslandRegistry.ActiveIsland> active = activeIslands.snapshot();
        if (active.isEmpty()) {
            running.set(false);
            return;
        }
        ActiveIslandRegistry.ActiveIsland island = active.get(Math.floorMod(cursor.getAndIncrement(), active.size()));
        UUID islandId = island.islandId();
        lastScannedIslandId = islandId;
        lastScanStartedAt = System.currentTimeMillis();
        scanService.rescanIsland(islandId)
            .thenRun(() -> lastScanFinishedAt = System.currentTimeMillis())
            .exceptionally(error -> {
                lastScanFailedAt = System.currentTimeMillis();
                plugin.getLogger().log(Level.WARNING, "Failed to rescan island level counts: " + islandId, error);
                return null;
            })
            .whenComplete((ignored, error) -> running.set(false));
    }

    public boolean running() {
        return running.get();
    }

    public UUID lastScannedIslandId() {
        return lastScannedIslandId;
    }

    public long lastScanStartedAt() {
        return lastScanStartedAt;
    }

    public long lastScanFinishedAt() {
        return lastScanFinishedAt;
    }

    public long lastScanFailedAt() {
        return lastScanFailedAt;
    }
}
