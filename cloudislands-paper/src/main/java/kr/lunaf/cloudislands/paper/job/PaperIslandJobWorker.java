package kr.lunaf.cloudislands.paper.job;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import kr.lunaf.cloudislands.paper.failure.CoreApiFailureLogLimiter;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPayload;
import kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPolicy;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class PaperIslandJobWorker {
    private final Plugin plugin;
    private final LocalJobSource jobSource;
    private final IslandActivationJobHandler activationHandler;
    private final IslandDeactivationHandler deactivationHandler;
    private final ActiveIslandRegistry activeIslands;
    private final PermissionCacheSyncService permissionSync;
    private final String nodeId;
    private final PlatformScheduler scheduler;
    private final PaperJobCompletionReporter completionReporter;
    private final PendingJobCompletionStore pendingCompletions;
    private final ShutdownDrainer shutdownDrainer;
    private final CoreApiFailureLogLimiter coreFailures;
    private final AtomicBoolean acceptingJobs = new AtomicBoolean();
    private final AtomicBoolean polling = new AtomicBoolean();
    private final Object pollingMonitor = new Object();
    private TaskHandle task;
    private volatile int consecutiveFailures;
    private volatile int inFlightJobs;
    private long nextPollAtMillis;

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, ActiveIslandRegistry activeIslands, String nodeId) {
        this(plugin, jobSource, activationHandler, null, activeIslands, null, nodeId);
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, String nodeId) {
        this(plugin, jobSource, activationHandler, deactivationHandler, activeIslands, null, nodeId);
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, PermissionCacheSyncService permissionSync, String nodeId) {
        this(plugin, jobSource, activationHandler, deactivationHandler, activeIslands, permissionSync, nodeId, new BukkitPlatformScheduler(plugin));
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, PermissionCacheSyncService permissionSync, String nodeId, PlatformScheduler scheduler) {
        this(plugin, jobSource, activationHandler, deactivationHandler, activeIslands, permissionSync, nodeId, scheduler, ShutdownDrainer.noop());
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, PermissionCacheSyncService permissionSync, String nodeId, ShutdownDrainer shutdownDrainer) {
        this(plugin, jobSource, activationHandler, deactivationHandler, activeIslands, permissionSync, nodeId, new BukkitPlatformScheduler(plugin), shutdownDrainer);
    }

    public PaperIslandJobWorker(Plugin plugin, LocalJobSource jobSource, IslandActivationJobHandler activationHandler, IslandDeactivationHandler deactivationHandler, ActiveIslandRegistry activeIslands, PermissionCacheSyncService permissionSync, String nodeId, PlatformScheduler scheduler, ShutdownDrainer shutdownDrainer) {
        this.plugin = plugin;
        this.jobSource = jobSource;
        this.activationHandler = activationHandler;
        this.deactivationHandler = deactivationHandler;
        this.activeIslands = activeIslands;
        this.permissionSync = permissionSync;
        this.nodeId = nodeId;
        this.scheduler = scheduler == null ? new BukkitPlatformScheduler(plugin) : scheduler;
        this.shutdownDrainer = shutdownDrainer == null ? ShutdownDrainer.noop() : shutdownDrainer;
        this.coreFailures = CoreApiFailureLogLimiter.forPlugin(plugin);
        this.completionReporter = new PaperJobCompletionReporter(
            this.nodeId,
            this.jobSource::complete,
            message -> this.plugin.getLogger().warning(message)
        );
        try {
            this.pendingCompletions = new PendingJobCompletionStore(plugin.getDataFolder().toPath().resolve("pending-job-completions.bin"));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("failed to open pending job completion journal", exception);
        }
    }

    public void start(long intervalTicks) {
        stop();
        acceptingJobs.set(true);
        Duration interval = Duration.ofMillis(Math.max(1L, intervalTicks) * 50L);
        task = scheduler.repeatAsync(interval, interval, this::poll);
    }

    public void stop() {
        acceptingJobs.set(false);
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean shutdown(Duration timeout) {
        stop();
        Duration bounded = timeout == null || timeout.isZero() || timeout.isNegative() ? Duration.ofSeconds(30) : timeout;
        long deadlineNanos = System.nanoTime() + bounded.toNanos();
        Exception drainFailure = null;
        while (polling.get()) {
            Exception currentDrainFailure = drainShutdownWork();
            if (drainFailure == null && currentDrainFailure != null) {
                drainFailure = currentDrainFailure;
            }
            if (!polling.get()) {
                break;
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                plugin.getLogger().severe("CloudIslands job worker did not drain within the configured shutdown deadline; claimed work remains recoverable through its Core lease");
                return false;
            }
            synchronized (pollingMonitor) {
                if (!polling.get()) {
                    break;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(pollingMonitor, Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(10L)));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    plugin.getLogger().severe("Interrupted while draining the CloudIslands job worker during shutdown");
                    return false;
                }
            }
        }
        Exception finalDrainFailure = drainShutdownWork();
        if (drainFailure == null) {
            drainFailure = finalDrainFailure;
        }
        if (drainFailure != null) {
            plugin.getLogger().severe("Failed to drain Paper global-thread work while stopping the job worker: " + drainFailure.getMessage());
            return false;
        }
        return true;
    }

    private void poll() {
        if (!acceptingJobs.get() || !polling.compareAndSet(false, true)) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            if (now < nextPollAtMillis || !acceptingJobs.get()) {
                return;
            }
            replayPendingCompletions();
            if (!acceptingJobs.get()) {
                return;
            }
            List<IslandJob> claimed = jobSource.claim(nodeId, List.of(IslandJobType.CREATE_ISLAND, IslandJobType.ACTIVATE_ISLAND, IslandJobType.SAVE_ISLAND, IslandJobType.DEACTIVATE_ISLAND, IslandJobType.SNAPSHOT_ISLAND, IslandJobType.DELETE_ISLAND, IslandJobType.MIGRATE_ISLAND, IslandJobType.RESTORE_ISLAND, IslandJobType.RESET_ISLAND), 4);
            inFlightJobs = claimed.size();
            consecutiveFailures = 0;
            nextPollAtMillis = 0L;
            coreFailures.recovered("island-jobs");
            for (IslandJob job : claimed) {
                handle(job);
                inFlightJobs = Math.max(0, inFlightJobs - 1);
            }
        } catch (RuntimeException exception) {
            consecutiveFailures++;
            long backoffMillis = Math.min(60_000L, 1_000L * (1L << Math.min(consecutiveFailures, 6)));
            nextPollAtMillis = now + backoffMillis;
            coreFailures.failed("island-jobs", exception, backoffMillis);
        } finally {
            inFlightJobs = 0;
            polling.set(false);
            synchronized (pollingMonitor) {
                pollingMonitor.notifyAll();
            }
        }
    }

    private Exception drainShutdownWork() {
        try {
            shutdownDrainer.drain();
            return null;
        } catch (Exception error) {
            return error;
        }
    }

    private void handle(IslandJob job) {
        try {
            if (replayPendingCompletion(job)) {
                return;
            }
            if (job.type() == IslandJobType.SAVE_ISLAND || job.type() == IslandJobType.SNAPSHOT_ISLAND) {
                handleSave(job);
                return;
            }
            if (job.type() == IslandJobType.DEACTIVATE_ISLAND || job.type() == IslandJobType.DELETE_ISLAND) {
                handleDeactivation(job);
                return;
            }
            if (job.type() == IslandJobType.CREATE_ISLAND) {
                IslandPreCreateEvent preCreate = new IslandPreCreateEvent(job.islandId(), job.jobId(), nodeId);
                kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(preCreate);
                if (preCreate.isCancelled()) {
                    jobSource.fail(nodeId, job, "CREATE_CANCELLED");
                    return;
                }
            }
            IslandPreActivateEvent preEvent = new IslandPreActivateEvent(job.islandId(), job.jobId(), job.type(), nodeId);
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(preEvent);
            if (preEvent.isCancelled()) {
                jobSource.fail(nodeId, job, "ACTIVATION_CANCELLED");
                return;
            }
            if (!activeIslands.acceptsActivation(job.islandId(), IslandJobCompletionPolicy.fencingToken(job.payload()))) {
                jobSource.fail(nodeId, job, IslandJobCompletionPolicy.STALE_FENCING_TOKEN);
                return;
            }
            IslandActivationJobHandler.ActivationResult result = activationHandler.handle(job);
            if (result.success()) {
                if (!activeIslands.activated(result)) {
                    jobSource.fail(nodeId, job, IslandJobCompletionPolicy.STALE_FENCING_TOKEN);
                    return;
                }
                if (job.type() == IslandJobType.CREATE_ISLAND) {
                    kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new IslandCreateEvent(result.islandId(), job.jobId(), nodeId, result.worldName()));
                }
                kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new IslandActivateEvent(result.islandId(), nodeId, result.worldName(), result.cellX(), result.cellZ(), result.schemaVersion(), result.placementSource()));
                if (permissionSync != null) {
                    permissionSync.sync(job.islandId());
                }
                IslandJobCompletionPayload payload = IslandJobCompletionPayload
                    .activation(result.worldName(), result.cellX(), result.cellZ(), result.schemaVersion(), result.fencingToken(), result.extractedRoot())
                    .with("placementSource", result.placementSource())
                    .withPreMutationSnapshot(result.preMutationSnapshotNo(), result.preMutationReason(), result.preMutationChecksum(), result.preMutationSizeBytes())
                    .withSnapshot(result.creationSnapshotNo(), "CREATED", result.creationSnapshotChecksum(), result.creationSnapshotSizeBytes());
                reportComplete(job, completePayload(job, payload));
            } else {
                jobSource.fail(nodeId, job, result.state());
            }
        } catch (PaperJobCompletionReporter.CompletionReportFailedException ignored) {
            return;
        } catch (RuntimeException exception) {
            jobSource.fail(nodeId, job, exception.getMessage());
        }
    }

    private void handleDeactivation(IslandJob job) {
        if (deactivationHandler == null) {
            jobSource.fail(nodeId, job, "DEACTIVATION_UNAVAILABLE");
            return;
        }
        String reason = job.payload().getOrDefault("reason", defaultSnapshotReason(job.type()));
        IslandDeactivationHandler.DeactivationResult result = deactivationHandler.deactivate(job.islandId(), job.type() == IslandJobType.DELETE_ISLAND, reason);
        if (result.success()) {
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new IslandDeactivateEvent(result.islandId(), nodeId, result.snapshotNo()));
            if (job.type() == IslandJobType.DELETE_ISLAND) {
                kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new IslandDeleteEvent(result.islandId(), job.jobId(), nodeId, result.snapshotNo()));
            }
            IslandJobCompletionPayload payload = IslandJobCompletionPayload.snapshot(result.snapshotNo(), reason, result.checksum(), result.sizeBytes());
            if (job.type() == IslandJobType.DELETE_ISLAND && job.payload().containsKey("ownerUuid")) {
                payload = payload.with("ownerUuid", job.payload().get("ownerUuid"));
            }
            reportComplete(job, completePayload(job, payload));
        } else {
            jobSource.fail(nodeId, job, result.errorMessage());
        }
    }

    private void handleSave(IslandJob job) {
        if (deactivationHandler == null) {
            jobSource.fail(nodeId, job, "SAVE_UNAVAILABLE");
            return;
        }
        String reason = job.payload().getOrDefault("reason", defaultSnapshotReason(job.type()));
        IslandDeactivationHandler.DeactivationResult result = deactivationHandler.saveOnly(job.islandId(), reason);
        if (result.success()) {
            reportComplete(job, completePayload(job, IslandJobCompletionPayload.snapshot(result.snapshotNo(), reason, result.checksum(), result.sizeBytes())));
        } else {
            jobSource.fail(nodeId, job, result.errorMessage());
        }
    }

    private String defaultSnapshotReason(IslandJobType type) {
        if (type == IslandJobType.DEACTIVATE_ISLAND) {
            return "DEACTIVATION";
        }
        if (type == IslandJobType.DELETE_ISLAND) {
            return "BEFORE_DELETE";
        }
        if (type == IslandJobType.SNAPSHOT_ISLAND) {
            return "MANUAL";
        }
        return type == null ? "" : type.name();
    }

    public interface LocalJobSource {
        List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
        void complete(String nodeId, java.util.UUID jobId);
        void complete(String nodeId, java.util.UUID jobId, Map<String, String> payload);
        default void complete(String nodeId, IslandJob job, Map<String, String> payload) {
            complete(nodeId, job.jobId(), payload);
        }
        void fail(String nodeId, java.util.UUID jobId, String errorMessage);
        default void fail(String nodeId, IslandJob job, String errorMessage) {
            fail(nodeId, job.jobId(), errorMessage);
        }
    }

    @FunctionalInterface
    public interface ShutdownDrainer {
        void drain() throws Exception;

        static ShutdownDrainer noop() {
            return () -> {};
        }
    }

    public int activationQueue() {
        return inFlightJobs;
    }

    public int recentFailurePenalty() {
        return Math.min(consecutiveFailures, 20);
    }

    private Map<String, String> completePayload(IslandJob job, IslandJobCompletionPayload payload) {
        return IslandJobCompletionPolicy.carryJobContext(job, payload, nodeId).asMap();
    }

    private void reportComplete(IslandJob job, Map<String, String> payload) {
        try {
            pendingCompletions.put(job, payload);
        } catch (java.io.IOException persistenceFailure) {
            plugin.getLogger().severe("Could not persist local job success before Core completion report: " + job.jobId() + " " + persistenceFailure.getMessage());
        }
        completionReporter.report(job, payload);
        clearPendingCompletion(job.jobId());
    }

    private boolean replayPendingCompletion(IslandJob job) {
        Map<String, String> payload = pendingCompletions.find(job.jobId()).orElse(null);
        if (payload == null) {
            return false;
        }
        completionReporter.report(job, payload);
        clearPendingCompletion(job.jobId());
        return true;
    }

    private void replayPendingCompletions() {
        for (PendingJobCompletionStore.PendingCompletion pending : pendingCompletions.replayable()) {
            IslandJob replay = new IslandJob(
                pending.jobId(),
                null,
                null,
                "",
                0,
                Map.of(),
                java.time.Instant.EPOCH,
                pending.claimLease()
            );
            completionReporter.report(replay, pending.payload());
            clearPendingCompletion(pending.jobId());
        }
    }

    private void clearPendingCompletion(java.util.UUID jobId) {
        try {
            pendingCompletions.remove(jobId);
        } catch (java.io.IOException cleanupFailure) {
            plugin.getLogger().warning("Core accepted job completion but local journal cleanup failed: " + jobId + " " + cleanupFailure.getMessage());
        }
    }
}
