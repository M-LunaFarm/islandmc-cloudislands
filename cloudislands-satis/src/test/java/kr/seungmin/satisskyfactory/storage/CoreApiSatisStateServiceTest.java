package kr.seungmin.satisskyfactory.storage;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.lunaf.cloudislands.api.service.IslandAdminService;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
import kr.lunaf.cloudislands.api.service.IslandEventService;
import kr.lunaf.cloudislands.api.service.IslandPermissionService;
import kr.lunaf.cloudislands.api.service.IslandQueryService;
import kr.lunaf.cloudislands.api.service.IslandRoutingService;
import kr.lunaf.cloudislands.api.service.IslandRuntimeService;
import kr.lunaf.cloudislands.api.service.IslandStatusService;
import kr.lunaf.cloudislands.api.service.PlayerIslandService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreApiSatisStateServiceTest {
    @Test
    void reportsUnavailableWhenCloudIslandsApiIsMissing() {
        CoreApiSatisStateService service = new CoreApiSatisStateService(Logger.getLogger("test"), null, "cloudislands-satis");

        assertEquals("cloudislands-api-unavailable", service.writerReadiness());
        assertEquals("table-key-value-bulk-save-primary-with-flattened-state-fallback", service.writerTransportMode());
        assertEquals("queue-retry-then-flattened-addon-state", service.writerFallbackPolicy());
        assertTrue(service.flattenedFallbackEnabled());
        assertEquals(0, service.pendingBulkRetries());
    }

    @Test
    void reportsDisabledAddonStateFeatureBeforeWriting() {
        CoreApiSatisStateService service = new CoreApiSatisStateService(
                Logger.getLogger("test"),
                new NoopCloudIslandsApi(),
                "cloudislands-satis",
                false,
                feature -> !"addon-state".equals(feature)
        );

        assertEquals("addon-state-feature-disabled", service.writerReadiness());
        assertEquals("table-key-value-bulk-save-primary-no-flattened-fallback", service.writerTransportMode());
        assertEquals("queue-retry-only", service.writerFallbackPolicy());
        assertFalse(service.flattenedFallbackEnabled());
    }

    @Test
    void loadsGlobalTableThroughCoreApiBulkLoadEndpoint() {
        CapturingAddonService addons = new CapturingAddonService();
        addons.globalTables.put("market_daily", Map.of("iron_ingot/2026-06-17", "{\"soldAmount\":42}"));
        CoreApiSatisStateService service = new CoreApiSatisStateService(
                Logger.getLogger("test"),
                new AddonOnlyCloudIslandsApi(addons),
                "cloudislands-satis"
        );

        Map<String, String> values = service.loadGlobalTable("market_daily");

        assertEquals(Map.of("iron_ingot/2026-06-17", "{\"soldAmount\":42}"), values);
        assertEquals("market_daily", addons.lastGlobalLoadTable);
        assertEquals("cloudislands-satis", addons.lastGlobalAddonId);
        assertEquals(1L, service.globalTableLoadSuccesses());
        assertEquals(0L, service.globalTableLoadFailures());
        assertEquals("table-key-value-bulk-load-primary-with-flattened-state-fallback", service.readerTransportMode());
    }

    @Test
    void loadsIslandTableThroughCoreApiBulkLoadEndpoint() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000822");
        CapturingAddonService addons = new CapturingAddonService();
        addons.islandTables.put("machines", Map.of("machine/one", "{\"status\":\"RUNNING\"}"));
        CoreApiSatisStateService service = new CoreApiSatisStateService(
                Logger.getLogger("test"),
                new AddonOnlyCloudIslandsApi(addons),
                "cloudislands-satis"
        );

        Map<String, String> values = service.loadIslandTable(islandId, "machines");

        assertEquals(Map.of("machine/one", "{\"status\":\"RUNNING\"}"), values);
        assertEquals("machines", addons.lastIslandLoadTable);
        assertEquals(islandId, addons.lastIslandId);
        assertEquals("cloudislands-satis", addons.lastIslandAddonId);
        assertEquals(1L, service.islandTableLoadSuccesses());
        assertEquals(0L, service.islandTableLoadFailures());
    }

    private record AddonOnlyCloudIslandsApi(IslandAddonService addons) implements CloudIslandsApi {
        @Override
        public IslandQueryService islands() {
            return null;
        }

        @Override
        public PlayerIslandService players() {
            return null;
        }

        @Override
        public IslandRoutingService routing() {
            return null;
        }

        @Override
        public IslandPermissionService permissions() {
            return null;
        }

        @Override
        public IslandRuntimeService runtime() {
            return null;
        }

        @Override
        public IslandStatusService status() {
            return null;
        }

        @Override
        public IslandEventService events() {
            return null;
        }

        @Override
        public IslandAdminService admin() {
            return null;
        }

        @Override
        public IslandCommandService commands() {
            return null;
        }
    }

    private static final class CapturingAddonService implements IslandAddonService {
        private final Map<String, Map<String, String>> globalTables = new java.util.HashMap<>();
        private final Map<String, Map<String, String>> islandTables = new java.util.HashMap<>();
        private String lastGlobalAddonId;
        private String lastGlobalLoadTable;
        private String lastIslandAddonId;
        private UUID lastIslandId;
        private String lastIslandLoadTable;

        @Override
        public CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled, Map<String, Boolean> features, Map<String, String> metadata) {
            return CompletableFuture.completedFuture(new CloudIslandsAddonSnapshot(id, displayName, version, enabled, Instant.EPOCH, Instant.EPOCH, features, features, metadata));
        }

        @Override
        public CompletableFuture<Void> unregister(String id) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Optional<CloudIslandsAddonSnapshot>> get(String id) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletableFuture<List<CloudIslandsAddonSnapshot>> list() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<Boolean> isEnabled(String id) {
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadState(String id, String table) {
            lastGlobalAddonId = id;
            lastGlobalLoadTable = table;
            return CompletableFuture.completedFuture(globalTables.getOrDefault(table, Map.of()));
        }

        @Override
        public CompletableFuture<Map<String, String>> tableKeyValueBulkLoadIslandState(String id, UUID islandId, String table) {
            lastIslandAddonId = id;
            lastIslandId = islandId;
            lastIslandLoadTable = table;
            return CompletableFuture.completedFuture(islandTables.getOrDefault(table, Map.of()));
        }
    }
}
