package kr.lunaf.cloudislands.paper.activation;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.storage.StorageOutagePolicy;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import org.bukkit.plugin.Plugin;

public final class PeriodicIslandSaveTask {
    private static final String PERIODIC_SNAPSHOT_REASON = "PERIODIC";

    private final Plugin plugin;
    private final ActiveIslandRegistry activeIslands;
    private final IslandSaveService saveService;
    private final CoreApiClient coreApiClient;
    private final String nodeId;
    private final PlatformScheduler scheduler;
    private final Map<UUID, Integer> retryQueue = new ConcurrentHashMap<>();
    private final AtomicLong failuresTotal = new AtomicLong();
    private TaskHandle task;

    public PeriodicIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, IslandSaveService saveService) {
        this(plugin, activeIslands, saveService, null, "");
    }

    public PeriodicIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, IslandSaveService saveService, CoreApiClient coreApiClient, String nodeId) {
        this(plugin, activeIslands, saveService, coreApiClient, nodeId, new BukkitPlatformScheduler(plugin));
    }

    public PeriodicIslandSaveTask(Plugin plugin, ActiveIslandRegistry activeIslands, IslandSaveService saveService, CoreApiClient coreApiClient, String nodeId, PlatformScheduler scheduler) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.saveService = saveService;
        this.coreApiClient = coreApiClient;
        this.nodeId = nodeId == null ? "" : nodeId;
        this.scheduler = scheduler == null ? new BukkitPlatformScheduler(plugin) : scheduler;
    }

    public void start(long intervalSeconds) {
        stop();
        if (intervalSeconds <= 0L) {
            return;
        }
        Duration interval = Duration.ofSeconds(Math.max(1L, intervalSeconds));
        task = scheduler.repeatAsync(interval, interval, this::saveAll);
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
                retryQueue.remove(activeIsland.islandId());
                recordSnapshot(result, activeIsland.fencingToken());
            } catch (java.io.IOException exception) {
                failuresTotal.incrementAndGet();
                int attempts = retryQueue.merge(activeIsland.islandId(), 1, Integer::sum);
                plugin.getLogger().warning("Periodic island save failed for " + activeIsland.islandId() + " retry=" + attempts + " queued=" + retryQueue.size() + " policy=" + StorageOutagePolicy.SAVE_RETRY_POLICY + ": " + exception.getMessage());
            }
        }
    }

    public int retryQueueSize() {
        return retryQueue.size();
    }

    public long failuresTotal() {
        return failuresTotal.get();
    }

    private void recordSnapshot(IslandSaveService.SaveResult result, long fencingToken) {
        if (coreApiClient == null || result.snapshotNo() <= 0L) {
            return;
        }
        String storagePath = result.storagePath() == null || result.storagePath().isBlank()
            ? "islands/" + result.islandId() + "/snapshots/" + String.format("%06d", result.snapshotNo()) + "/bundle.tar.zst"
            : result.storagePath();
        coreApiClient.snapshotCommands().recordSnapshot(result.islandId(), result.snapshotNo(), storagePath, PERIODIC_SNAPSHOT_REASON, result.checksum(), result.sizeBytes(), nodeId, fencingToken)
            .exceptionally(error -> {
                plugin.getLogger().warning("Periodic island snapshot record failed for " + result.islandId() + ": " + error.getMessage());
                return null;
            });
    }
}
