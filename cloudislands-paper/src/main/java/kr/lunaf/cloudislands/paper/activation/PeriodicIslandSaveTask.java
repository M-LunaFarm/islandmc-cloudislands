package kr.lunaf.cloudislands.paper.activation;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final PendingSnapshotRecords pendingSnapshotRecords;
    private final AtomicBoolean running = new AtomicBoolean();
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
        this.pendingSnapshotRecords = coreApiClient == null
            ? new PendingSnapshotRecords()
            : new PendingSnapshotRecords(plugin.getDataFolder().toPath().resolve("pending-periodic-snapshots.tsv"));
        if (!this.pendingSnapshotRecords.lastPersistenceError().isBlank()) {
            plugin.getLogger().warning("Failed to load periodic snapshot retry journal: " + this.pendingSnapshotRecords.lastPersistenceError());
        }
        if (this.pendingSnapshotRecords.discardedJournalRecords() > 0) {
            plugin.getLogger().warning("Discarded " + this.pendingSnapshotRecords.discardedJournalRecords() + " invalid periodic snapshot retry journal records");
        }
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
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            pendingSnapshotRecords.claimAll().forEach(this::recordSnapshot);
            for (ActiveIslandRegistry.ActiveIsland activeIsland : activeIslands.snapshot()) {
                if (pendingSnapshotRecords.contains(activeIsland.islandId())) {
                    continue;
                }
                try {
                    IslandSaveService.SaveResult result = saveService.save(activeIsland.islandId(), activeIsland);
                    retryQueue.remove(activeIsland.islandId());
                    PendingSnapshotRecords.PendingSnapshotRecord record = pendingRecord(result, activeIsland.fencingToken());
                    if (record != null) {
                        if (!pendingSnapshotRecords.enqueue(record)) {
                            recordJournalFailure("enqueue", record);
                        }
                        pendingSnapshotRecords.claim(record.islandId()).forEach(this::recordSnapshot);
                    }
                } catch (java.io.IOException exception) {
                    failuresTotal.incrementAndGet();
                    int attempts = retryQueue.merge(activeIsland.islandId(), 1, Integer::sum);
                    plugin.getLogger().warning("Periodic island save failed for " + activeIsland.islandId() + " retry=" + attempts + " queued=" + retryQueueSize() + " policy=" + StorageOutagePolicy.SAVE_RETRY_POLICY + ": " + exception.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    public int retryQueueSize() {
        return retryQueue.size() + pendingSnapshotRecords.size();
    }

    public long failuresTotal() {
        return failuresTotal.get();
    }

    private PendingSnapshotRecords.PendingSnapshotRecord pendingRecord(IslandSaveService.SaveResult result, long fencingToken) {
        if (coreApiClient == null || result.snapshotNo() <= 0L) {
            return null;
        }
        String storagePath = result.storagePath() == null || result.storagePath().isBlank()
            ? "islands/" + result.islandId() + "/snapshots/" + String.format("%06d", result.snapshotNo()) + "/bundle.tar.zst"
            : result.storagePath();
        return new PendingSnapshotRecords.PendingSnapshotRecord(result.islandId(), result.snapshotNo(), storagePath, PERIODIC_SNAPSHOT_REASON, result.checksum(), result.sizeBytes(), nodeId, fencingToken);
    }

    private void recordSnapshot(PendingSnapshotRecords.PendingSnapshotRecord record) {
        try {
            coreApiClient.snapshotCommands().recordSnapshot(record.islandId(), record.snapshotNo(), record.storagePath(), record.reason(), record.checksum(), record.sizeBytes(), record.nodeId(), record.fencingToken())
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        if (!pendingSnapshotRecords.completed(record)) {
                            recordJournalFailure("complete", record);
                        }
                        return;
                    }
                    failuresTotal.incrementAndGet();
                    pendingSnapshotRecords.failed(record);
                    plugin.getLogger().warning("Periodic island snapshot record failed for " + record.islandId() + ": " + error.getMessage());
                });
        } catch (RuntimeException error) {
            failuresTotal.incrementAndGet();
            pendingSnapshotRecords.failed(record);
            plugin.getLogger().warning("Periodic island snapshot record failed for " + record.islandId() + ": " + error.getMessage());
        }
    }

    private void recordJournalFailure(String operation, PendingSnapshotRecords.PendingSnapshotRecord record) {
        failuresTotal.incrementAndGet();
        plugin.getLogger().warning("Periodic island snapshot retry journal " + operation + " failed for " + record.islandId() + ": " + pendingSnapshotRecords.lastPersistenceError());
    }
}
