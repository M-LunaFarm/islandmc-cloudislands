package kr.lunaf.cloudislands.paper.job;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.paper.activation.IslandDeactivationHandler;
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
    private final String nodeId;
    private BukkitTask task;
    private int consecutiveFailures;
    private long nextPollAtMillis;

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, ActiveIslandRegistry activeIslands, String nodeId) {
        this(plugin, jobSource, activationHandler, null, activeIslands, nodeId);
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, String nodeId) {
        this.plugin = plugin;
        this.jobSource = jobSource;
        this.activationHandler = activationHandler;
        this.deactivationHandler = deactivationHandler;
        this.activeIslands = activeIslands;
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
            List<IslandJob> claimed = jobSource.claim(nodeId, List.of(IslandJobType.CREATE_ISLAND, IslandJobType.ACTIVATE_ISLAND, IslandJobType.DEACTIVATE_ISLAND, IslandJobType.SNAPSHOT_ISLAND, IslandJobType.RESTORE_ISLAND), 4);
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
            if (job.type() == IslandJobType.DEACTIVATE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND) {
                handleDeactivation(job);
                return;
            }
            IslandActivationJobHandler.ActivationResult result = activationHandler.handle(job);
            if (result.success()) {
                activeIslands.activated(result);
                jobSource.complete(nodeId, job.jobId(), Map.of(
                    "worldName", result.worldName() == null ? "" : result.worldName(),
                    "cellX", Integer.toString(result.cellX()),
                    "cellZ", Integer.toString(result.cellZ()),
                    "schemaVersion", Long.toString(result.schemaVersion()),
                    "fencingToken", Long.toString(result.fencingToken()),
                    "extractedRoot", result.extractedRoot() == null ? "" : result.extractedRoot()
                ));
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
        IslandDeactivationHandler.DeactivationResult result = deactivationHandler.deactivate(job.islandId());
        if (result.success()) {
            jobSource.complete(nodeId, job.jobId(), Map.of("snapshotNo", Long.toString(result.snapshotNo())));
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
