package kr.lunaf.cloudislands.paper.integration.analytics;

import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import java.util.function.Supplier;

@PluginInfo(name = "CloudIslands", iconName = "cloud")
public final class CloudIslandsPlanExtension implements DataExtension {
    private final Supplier<PlanMetricSnapshot> snapshotSupplier;

    public CloudIslandsPlanExtension(Supplier<PlanMetricSnapshot> snapshotSupplier) {
        this.snapshotSupplier = snapshotSupplier == null ? PlanMetricSnapshot::empty : snapshotSupplier;
    }

    @NumberProvider(text = "Cluster Active Islands", description = "Active islands across every CloudIslands Paper node", priority = 100)
    public long clusterActiveIslands() {
        return snapshot().clusterActiveIslands();
    }

    @NumberProvider(text = "Cluster Players", description = "Players reported by all current CloudIslands nodes", priority = 90)
    public long clusterPlayers() {
        return snapshot().clusterPlayers();
    }

    @NumberProvider(text = "Online Island Nodes", description = "Fresh CloudIslands nodes currently visible to Core", priority = 80)
    public long clusterOnlineNodes() {
        return snapshot().clusterOnlineNodes();
    }

    @NumberProvider(text = "Pending Island Jobs", description = "Pending work in the distributed island job queue", priority = 70)
    public long pendingJobs() {
        return snapshot().pendingJobs();
    }

    @NumberProvider(text = "Failed Island Jobs", description = "Total terminal island job failures reported by Core", priority = 60)
    public long failedJobs() {
        return snapshot().failedJobs();
    }

    @NumberProvider(text = "Local Active Islands", description = "Islands active on this Paper node", priority = 50)
    public long localActiveIslands() {
        return snapshot().localActiveIslands();
    }

    @NumberProvider(text = "Local Online Players", description = "Players online on this Paper server", priority = 40)
    public long localOnlinePlayers() {
        return snapshot().localOnlinePlayers();
    }

    @NumberProvider(text = "Core Metric Samples", description = "Prometheus samples observed during the last successful Core refresh", priority = 20)
    public long coreMetricSamples() {
        return snapshot().coreMetricSamples();
    }

    @NumberProvider(text = "Last Core Refresh", description = "Unix timestamp of the last successful Core metric refresh", priority = 10)
    public long refreshedAtEpochSeconds() {
        return snapshot().refreshedAtEpochSeconds();
    }

    private PlanMetricSnapshot snapshot() {
        PlanMetricSnapshot snapshot = snapshotSupplier.get();
        return snapshot == null ? PlanMetricSnapshot.empty() : snapshot;
    }
}
