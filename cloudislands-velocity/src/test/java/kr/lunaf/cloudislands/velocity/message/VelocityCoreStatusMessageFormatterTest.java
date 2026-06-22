package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminCoreConfigView;
import kr.lunaf.cloudislands.coreclient.AdminMaintenanceResultView;
import kr.lunaf.cloudislands.coreclient.AdminMetricsSummaryView;
import org.junit.jupiter.api.Test;

class VelocityCoreStatusMessageFormatterTest {
    private final VelocityCoreStatusMessageFormatter formatter = new VelocityCoreStatusMessageFormatter();

    @Test
    void formatsMaintenanceResult() {
        assertEquals(
            "Cache clear: accepted sessions=2 tickets=3",
            formatter.maintenance("Cache clear", new AdminMaintenanceResultView(false, 2L, 3L, 0L, ""))
        );
    }

    @Test
    void formatsMaintenanceFailureCode() {
        assertEquals(
            "Core reload: failed code=LOCKED",
            formatter.maintenance("Core reload", new AdminMaintenanceResultView(false, 0L, 0L, 0L, "LOCKED"))
        );
    }

    @Test
    void summarizesCoreMetrics() {
        assertEquals("Core metrics: samples=3 / metric_one, metric_two", formatter.metrics(new AdminMetricsSummaryView(3L, List.of("metric_one", "metric_two"))));
    }

    @Test
    void formatsAddonEndpointSummary() {
        assertEquals(
            "Addon endpoints: bulkSave=true global=/global island=/island tableGlobal= tableIsland= tableGlobalAlias= tableIslandAlias= tableBulkGlobal= tableBulkIsland= tableLoadGlobal= tableLoadIsland= tableMapGlobal= tableMapIsland= payload= loadPayload= api= storage= tablePrefix= maxKeys=25 maxValue=500 globalCacheKey= islandCacheKey= invalidationApi= cacheEventFields= eventTypeKeys= eventRecoveryKeys= eventAddonKeys= fallback= loadFallback=",
            formatter.addonEndpoints(new AdminCoreConfigView(Map.of(
                "addonStateBulkSaveApi", true,
                "addonStateBulkSaveGlobalEndpoint", "/global",
                "addonStateBulkSaveIslandEndpoint", "/island",
                "addonStateMaxKeysPerAddon", 25,
                "addonStateMaxValueLength", 500
            ), ""))
        );
    }
}
