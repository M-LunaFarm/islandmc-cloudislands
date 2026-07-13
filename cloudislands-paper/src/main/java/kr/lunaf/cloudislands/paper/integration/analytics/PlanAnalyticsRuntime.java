package kr.lunaf.cloudislands.paper.integration.analytics;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.Caller;
import com.djrapitops.plan.extension.ExtensionService;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.bootstrap.RuntimeComponent;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.scheduler.BukkitTask;

public final class PlanAnalyticsRuntime implements RuntimeComponent {
    private static final long REFRESH_INTERVAL_TICKS = 1_200L;

    private final CloudIslandsPaperPlugin plugin;
    private final CoreApiClient client;
    private final ExtensionService extensionService;
    private final CloudIslandsPlanExtension extension;
    private final Caller caller;
    private final AtomicReference<PlanMetricSnapshot> snapshot;
    private final AtomicBoolean refreshInFlight = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicLong consecutiveFailures = new AtomicLong();
    private BukkitTask refreshTask;

    private PlanAnalyticsRuntime(
        CloudIslandsPaperPlugin plugin,
        CoreApiClient client,
        ExtensionService extensionService,
        CloudIslandsPlanExtension extension,
        Caller caller,
        AtomicReference<PlanMetricSnapshot> snapshot
    ) {
        this.plugin = plugin;
        this.client = client;
        this.extensionService = extensionService;
        this.extension = extension;
        this.caller = caller;
        this.snapshot = snapshot;
    }

    public static PlanAnalyticsRuntime start(CloudIslandsPaperPlugin plugin, CoreApiClient client) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(client, "client");
        if (!CapabilityService.getInstance().hasCapability("DATA_EXTENSION_VALUES")) {
            throw new IllegalStateException("Plan does not expose the DATA_EXTENSION_VALUES capability");
        }
        ExtensionService extensionService = ExtensionService.getInstance();
        AtomicReference<PlanMetricSnapshot> initialSnapshot = new AtomicReference<>(PlanMetricSnapshot.empty());
        CloudIslandsPlanExtension extension = new CloudIslandsPlanExtension(initialSnapshot::get);
        Optional<Caller> registration = extensionService.register(extension);
        Caller caller = registration.orElseThrow(() -> new IllegalStateException("Plan rejected the CloudIslands data extension"));
        PlanAnalyticsRuntime runtime = new PlanAnalyticsRuntime(plugin, client, extensionService, extension, caller, initialSnapshot);
        try {
            runtime.refreshTask = PaperSchedulers.runTimer(plugin, runtime::refresh, 1L, REFRESH_INTERVAL_TICKS);
        } catch (RuntimeException error) {
            extensionService.unregister(extension);
            throw error;
        }
        return runtime;
    }

    public PlanMetricSnapshot snapshot() {
        return snapshot.get();
    }

    private void refresh() {
        if (stopped.get() || !refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        int localActiveIslands = plugin.activeIslands() == null ? 0 : plugin.activeIslands().size();
        int localOnlinePlayers = plugin.getServer().getOnlinePlayers().size();
        try {
            client.adminMetrics().summary().whenComplete((metrics, error) -> {
                try {
                    if (stopped.get()) {
                        return;
                    }
                    if (error != null) {
                        reportRefreshFailure(error);
                        return;
                    }
                    snapshot.set(PlanMetricSnapshot.from(metrics, localActiveIslands, localOnlinePlayers, Instant.now()));
                    consecutiveFailures.set(0L);
                    caller.updateServerData();
                } catch (RuntimeException refreshError) {
                    reportRefreshFailure(refreshError);
                } finally {
                    refreshInFlight.set(false);
                }
            });
        } catch (RuntimeException error) {
            refreshInFlight.set(false);
            reportRefreshFailure(error);
        }
    }

    private void reportRefreshFailure(Throwable error) {
        long failures = consecutiveFailures.incrementAndGet();
        if (failures == 1L || failures % 10L == 0L) {
            plugin.getLogger().warning(
                "Plan analytics refresh failed " + failures + " consecutive time(s); retaining the last good snapshot: " + error.getMessage()
            );
        }
    }

    @Override
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            return;
        }
        BukkitTask task = refreshTask;
        refreshTask = null;
        if (task != null) {
            task.cancel();
        }
        extensionService.unregister(extension);
    }
}
