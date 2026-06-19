package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VelocityCoreStatusMessageFormatterTest {
    private final VelocityCoreStatusMessageFormatter formatter = new VelocityCoreStatusMessageFormatter();

    @Test
    void formatsMaintenanceResult() {
        assertEquals(
            "Cache clear: accepted sessions=2 tickets=3",
            formatter.maintenance("Cache clear", "{\"clearedSessions\":2,\"clearedTickets\":3}")
        );
    }

    @Test
    void formatsMaintenanceFailureCode() {
        assertEquals(
            "Core reload: failed code=LOCKED",
            formatter.maintenance("Core reload", "{\"code\":\"LOCKED\"}")
        );
    }

    @Test
    void summarizesCoreMetrics() {
        String body = "# HELP ignored\nmetric_one 1\nmetric_two{label=\"a\"} 2\nmetric_one 3\n";

        assertEquals("Core metrics: samples=3 / metric_one, metric_two", formatter.metrics(body));
    }

    @Test
    void formatsAddonEndpointSummary() {
        String body = "{\"addonStateBulkSaveApi\":true,\"addonStateBulkSaveGlobalEndpoint\":\"/global\",\"addonStateBulkSaveIslandEndpoint\":\"/island\",\"addonStateMaxKeysPerAddon\":25,\"addonStateMaxValueLength\":500}";

        assertEquals(
            "Addon endpoints: bulkSave=true global=/global island=/island tableGlobal= tableIsland= tableGlobalAlias= tableIslandAlias= tableBulkGlobal= tableBulkIsland= tableLoadGlobal= tableLoadIsland= tableMapGlobal= tableMapIsland= payload= loadPayload= api= storage= tablePrefix= maxKeys=25 maxValue=500 globalCacheKey= islandCacheKey= invalidationApi= cacheEventFields= eventTypeKeys= eventRecoveryKeys= eventAddonKeys= fallback= loadFallback=",
            formatter.addonEndpoints(body)
        );
    }
}
