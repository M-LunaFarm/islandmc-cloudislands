package kr.lunaf.cloudislands.paper.integration.analytics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PlanAnalyticsRuntimePolicyTest {
    @Test
    void runtimeRegistersRefreshesAndUnregistersAPlanDataExtension() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/main/java/kr/lunaf/cloudislands/paper/integration/analytics/PlanAnalyticsRuntime.java"
        ));
        String services = Files.readString(Path.of(
            "src/main/java/kr/lunaf/cloudislands/paper/bootstrap/PaperRuntimeServices.java"
        ));

        assertTrue(runtime.contains("extensionService.register(extension)"));
        assertTrue(runtime.contains("PaperSchedulers.runTimer(plugin, runtime::refresh"));
        assertTrue(runtime.contains("client.adminMetrics().summary().whenComplete"));
        assertTrue(runtime.contains("AtomicReference<PlanMetricSnapshot>"));
        assertTrue(runtime.contains("caller.updateServerData()"));
        assertTrue(runtime.contains("extensionService.unregister(extension)"));
        assertTrue(services.contains("PlanAnalyticsRuntime.start(plugin, client)"));
        assertTrue(services.contains("clearRuntimeService(\"Plan\")"));
    }
}
