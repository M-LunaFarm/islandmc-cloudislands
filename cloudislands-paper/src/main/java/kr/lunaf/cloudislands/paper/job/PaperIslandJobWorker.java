package kr.lunaf.cloudislands.paper.job;

import java.util.List;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class PaperIslandJobWorker {
    private final Plugin plugin;
    private final LocalJobSource jobSource;
    private final IslandActivationJobHandler activationHandler;
    private final ActiveIslandRegistry activeIslands;
    private final String nodeId;
    private BukkitTask task;

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, ActiveIslandRegistry activeIslands, String nodeId) {
        this.plugin = plugin;
        this.jobSource = jobSource;
        this.activationHandler = activationHandler;
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
        for (IslandJob job : jobSource.claim(nodeId, List.of(IslandJobType.CREATE_ISLAND, IslandJobType.ACTIVATE_ISLAND), 4)) {
            try {
                IslandActivationJobHandler.ActivationResult result = activationHandler.handle(job);
                if (result.success()) {
                    activeIslands.activated(result);
                    jobSource.complete(nodeId, job.jobId());
                } else {
                    jobSource.fail(nodeId, job.jobId(), result.state());
                }
            } catch (RuntimeException exception) {
                jobSource.fail(nodeId, job.jobId(), exception.getMessage());
            }
        }
    }

    public interface LocalJobSource {
        List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
        void complete(String nodeId, java.util.UUID jobId);
        void fail(String nodeId, java.util.UUID jobId, String errorMessage);
    }
}
