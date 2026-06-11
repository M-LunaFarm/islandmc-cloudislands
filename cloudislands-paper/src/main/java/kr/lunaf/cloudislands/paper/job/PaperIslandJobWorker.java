package kr.lunaf.cloudislands.paper.job;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.paper.activation.IslandDeactivationHandler;
import kr.lunaf.cloudislands.paper.cache.PermissionCacheSyncService;
import kr.lunaf.cloudislands.paper.event.IslandActivateEvent;
import kr.lunaf.cloudislands.paper.event.IslandCreateEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeactivateEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeleteEvent;
import kr.lunaf.cloudislands.paper.event.IslandPreActivateEvent;
import kr.lunaf.cloudislands.paper.event.IslandPreCreateEvent;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperIslandJobWorker {
    private final Plugin plugin;
    private final LocalJobSource jobSource;
    private final IslandActivationJobHandler activationHandler;
    private final IslandDeactivationHandler deactivationHandler;
    private final ActiveIslandRegistry activeIslands;
    private final PermissionCacheSyncService permissionSync;
    private final String nodeId;
    private BukkitTask task;
    private int consecutiveFailures;
    private long nextPollAtMillis;

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, ActiveIslandRegistry activeIslands, String nodeId) {
        this(plugin, jobSource, activationHandler, null, activeIslands, null, nodeId);
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, String nodeId) {
        this(plugin, jobSource, activationHandler, deactivationHandler, activeIslands, null, nodeId);
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, PermissionCacheSyncService permissionSync, String nodeId) {
        this.plugin = plugin;
        this.jobSource = jobSource;
        this.activationHandler = activationHandler;
        this.deactivationHandler = deactivationHandler;
        this.activeIslands = activeIslands;
        this.permissionSync = permissionSync;
        this.nodeId = nodeId;
    }

    public void start(long intervalTicks) {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::poll, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void poll() {
        long now = System.currentTimeMillis();
        if (now < nextPollAtMillis) {
            return;
        }
        try {
            List<IslandJob> claimed = jobSource.claim(nodeId, List.of(IslandJobType.CREATE_ISLAND, IslandJobType.ACTIVATE_ISLAND, IslandJobType.DEACTIVATE_ISLAND, IslandJobType.SNAPSHOT_ISLAND, IslandJobType.DELETE_ISLAND, IslandJobType.MIGRATE_ISLAND, IslandJobType.RESTORE_ISLAND, IslandJobType.RESET_ISLAND), 4);
            consecutiveFailures = 0;
            for (IslandJob job : claimed) {
                handle(job);
            }
        } catch (RuntimeException exception) {
            consecutiveFailures++;
            long backoffMillis = Math.min(30_000L, 1_000L * (1L << Math.min(consecutiveFailures, 5)));
            nextPollAtMillis = now + backoffMillis;
            plugin.getLogger().warning("CloudIslands job poll failed; backing off for " + backoffMillis + "ms: " + exception.getMessage());
        }
    }

    private void handle(IslandJob job) {
        try {
            if (job.type() == IslandJobType.DEACTIVATE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND || job.type() == IslandJobType.DELETE_ISLAND) {
                handleDeactivation(job);
                return;
            }
            if (job.type() == IslandJobType.CREATE_ISLAND) {
                IslandPreCreateEvent preCreate = new IslandPreCreateEvent(job.islandId(), job.jobId(), nodeId);
                Bukkit.getPluginManager().callEvent(preCreate);
                if (preCreate.isCancelled()) {
                    jobSource.fail(nodeId, job.jobId(), "CREATE_CANCELLED");
                    return;
                }
            }
            IslandPreActivateEvent preEvent = new IslandPreActivateEvent(job.islandId(), job.jobId(), job.type(), nodeId);
            Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) {
                jobSource.fail(nodeId, job.jobId(), "ACTIVATION_CANCELLED");
                return;
            }
            IslandActivationJobHandler.ActivationResult result = activationHandler.handle(job);
            if (result.success()) {
                activeIslands.activated(result);
                if (job.type() == IslandJobType.CREATE_ISLAND) {
                    Bukkit.getPluginManager().callEvent(new IslandCreateEvent(result.islandId(), job.jobId(), nodeId, result.worldName()));
                }
                Bukkit.getPluginManager().callEvent(new IslandActivateEvent(result.islandId(), nodeId, result.worldName(), result.cellX(), result.cellZ(), result.schemaVersion()));
                if (permissionSync != null) {
                    permissionSync.sync(job.islandId());
                }
                java.util.Map<String, String> payload = new java.util.HashMap<>();
                payload.put("worldName", result.worldName() == null ? "" : result.worldName());
                payload.put("cellX", Integer.toString(result.cellX()));
                payload.put("cellZ", Integer.toString(result.cellZ()));
                payload.put("schemaVersion", Long.toString(result.schemaVersion()));
                payload.put("fencingToken", Long.toString(result.fencingToken()));
                payload.put("extractedRoot", result.extractedRoot() == null ? "" : result.extractedRoot());
                if (result.preMutationSnapshotNo() > 0L) {
                    payload.put("preMutationSnapshotNo", Long.toString(result.preMutationSnapshotNo()));
                    payload.put("preMutationChecksum", result.preMutationChecksum());
                    payload.put("preMutationSizeBytes", Long.toString(result.preMutationSizeBytes()));
                    payload.put("preMutationReason", result.preMutationReason());
                }
                jobSource.complete(nodeId, job.jobId(), payload);
            } else {
                jobSource.fail(nodeId, job.jobId(), result.state());
            }
        } catch (RuntimeException exception) {
            jobSource.fail(nodeId, job.jobId(), exception.getMessage());
        }
    }

    private void handleDeactivation(IslandJob job) {
        if (deactivationHandler == null) {
            jobSource.fail(nodeId, job.jobId(), "DEACTIVATION_UNAVAILABLE");
            return;
        }
        IslandDeactivationHandler.DeactivationResult result = deactivationHandler.deactivate(job.islandId(), job.type() == IslandJobType.DELETE_ISLAND);
        if (result.success()) {
            Bukkit.getPluginManager().callEvent(new IslandDeactivateEvent(result.islandId(), nodeId, result.snapshotNo()));
            if (job.type() == IslandJobType.DELETE_ISLAND) {
                Bukkit.getPluginManager().callEvent(new IslandDeleteEvent(result.islandId(), job.jobId(), nodeId, result.snapshotNo()));
            }
            jobSource.complete(nodeId, job.jobId(), Map.of(
                "snapshotNo", Long.toString(result.snapshotNo()),
                "reason", job.payload().getOrDefault("reason", job.type().name()),
                "checksum", result.checksum(),
                "sizeBytes", Long.toString(result.sizeBytes())
            ));
        } else {
            jobSource.fail(nodeId, job.jobId(), result.errorMessage());
        }
    }

    public interface LocalJobSource {
        List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
        void complete(String nodeId, java.util.UUID jobId);
        void complete(String nodeId, java.util.UUID jobId, Map<String, String> payload);
        void fail(String nodeId, java.util.UUID jobId, String errorMessage);
    }
}
