package kr.lunaf.cloudislands.paper.integration.analytics;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.djrapitops.plan.extension.extractor.ExtensionExtractor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import kr.lunaf.cloudislands.coreclient.AdminMetricsSummaryView;
import org.junit.jupiter.api.Test;

class CloudIslandsPlanExtensionTest {
    @Test
    void extensionAnnotationsAreValidAndExposeCachedDistributedMetrics() {
        AtomicReference<PlanMetricSnapshot> snapshot = new AtomicReference<>(new PlanMetricSnapshot(
            73L, 14L, 29L, 3L, 5L, 2L, 4L, 7L, 1_700_000_000L
        ));
        CloudIslandsPlanExtension extension = new CloudIslandsPlanExtension(snapshot::get);

        assertDoesNotThrow(() -> new ExtensionExtractor(extension).validateAnnotations());
        assertEquals(14L, extension.clusterActiveIslands());
        assertEquals(29L, extension.clusterPlayers());
        assertEquals(3L, extension.clusterOnlineNodes());
        assertEquals(5L, extension.pendingJobs());
        assertEquals(2L, extension.failedJobs());
        assertEquals(4L, extension.localActiveIslands());
        assertEquals(7L, extension.localOnlinePlayers());
    }

    @Test
    void snapshotMapsCoreMetricsAndClampsInvalidOrNegativeValues() {
        AdminMetricsSummaryView metrics = new AdminMetricsSummaryView(42L, List.of(), Map.of(
            "cloudislands_cluster_active_islands", 9.0D,
            "cloudislands_cluster_players", 17.0D,
            "cloudislands_cluster_nodes_online", 2.0D,
            "cloudislands_jobs_pending", -4.0D,
            "cloudislands_jobs_failed_total", Double.NaN
        ));

        PlanMetricSnapshot snapshot = PlanMetricSnapshot.from(metrics, 3, 6, Instant.ofEpochSecond(1234L));

        assertEquals(42L, snapshot.coreMetricSamples());
        assertEquals(9L, snapshot.clusterActiveIslands());
        assertEquals(17L, snapshot.clusterPlayers());
        assertEquals(2L, snapshot.clusterOnlineNodes());
        assertEquals(0L, snapshot.pendingJobs());
        assertEquals(0L, snapshot.failedJobs());
        assertEquals(3L, snapshot.localActiveIslands());
        assertEquals(6L, snapshot.localOnlinePlayers());
        assertEquals(1234L, snapshot.refreshedAtEpochSeconds());
    }
}
