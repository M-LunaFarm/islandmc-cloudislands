package kr.lunaf.cloudislands.api.service;

import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class IslandAddonServiceTest {
    @Test
    void defaultRegisterUsesFallbacksWhenAddonCallbacksFail() {
        CapturingAddonService service = new CapturingAddonService();

        CloudIslandsAddonSnapshot snapshot = service.register(new BrokenAddon()).join();

        assertEquals(BrokenAddon.class.getName(), snapshot.id());
        assertEquals(BrokenAddon.class.getName(), snapshot.displayName());
        assertEquals("unknown", snapshot.version());
        assertFalse(snapshot.enabled());
        assertEquals(Map.of(), snapshot.features());
        assertEquals("IllegalStateException", snapshot.metadata().get("metadata-error"));
    }

    @Test
    void defaultRegisterDropsNullFeatureAndMetadataEntries() {
        CapturingAddonService service = new CapturingAddonService();

        CloudIslandsAddonSnapshot snapshot = service.register(new AddonWithNullMapEntries()).join();

        assertEquals(Map.of("machines", true), snapshot.features());
        assertEquals(Map.of("mode", "ADDON"), snapshot.metadata());
    }

    @Test
    void defaultRegisterHandlesNullAddon() {
        CapturingAddonService service = new CapturingAddonService();

        CloudIslandsAddonSnapshot snapshot = service.register((CloudIslandsAddon) null).join();

        assertEquals("null-addon", snapshot.id());
        assertEquals("null-addon", snapshot.displayName());
        assertEquals("unknown", snapshot.version());
        assertFalse(snapshot.enabled());
        assertEquals(Map.of(), snapshot.features());
        assertEquals("NullAddon", snapshot.metadata().get("metadata-error"));
    }

    @Test
    void tableKeyValueBulkSaveFlattensGlobalTablesWithSlashKeys() {
        CapturingAddonService service = new CapturingAddonService();

        Map<String, String> state = service.tableKeyValueBulkSaveState(
                "cloudislands-satis",
                Map.of("runtime-status", "ok"),
                Map.of("machines", Map.of("island/0001/machine/0002", "active"))).join();

        assertEquals("ok", state.get("runtime-status"));
        assertEquals("active", state.get(IslandAddonService.tableStateKey("machines", "island/0001/machine/0002")));
    }

    @Test
    void tableKeyValueBulkLoadProjectsGlobalTableValues() {
        CapturingAddonService service = new CapturingAddonService();

        service.tableKeyValueBulkSaveState(
                "cloudislands-satis",
                Map.of("runtime-status", "ok"),
                Map.of("machines", Map.of("island/0001/machine/0002", "active"))).join();

        assertEquals(Map.of("island/0001/machine/0002", "active"),
                service.tableKeyValueBulkLoadState("cloudislands-satis", "machines").join());
        assertEquals(Map.of("island/0001/machine/0002", "active"),
                service.bulkLoadTableKeyValueState("cloudislands-satis", "machines").join());
    }

    @Test
    void requestObjectAliasesSaveGlobalTableKeyValueState() {
        CapturingAddonService service = new CapturingAddonService();
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.globalTables(
                "cloudislands-satis",
                Map.of("machines", Map.of("island/0001/machine/0002", "active")));

        Map<String, String> state = service.bulkSaveTableKeyValueState(request).join();

        assertEquals("active", state.get(IslandAddonService.tableStateKey("machines", "island/0001/machine/0002")));
        assertEquals(state, service.saveTableKeyValueState(request).join());
        assertEquals(state, service.tableKeyValueBulkState(request).join());
        assertEquals(state, service.tableBulkState(request).join());
    }

    @Test
    void tableKeyValueBulkSaveFlattensIslandTablesWithSlashKeys() {
        CapturingAddonService service = new CapturingAddonService();
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000702");

        Map<String, String> state = service.tableKeyValueBulkSaveIslandState(
                "cloudislands-satis",
                islandId,
                Map.of("active-node", "island-2"),
                Map.of("resource_nodes", Map.of("node/ore/0/0", "12000"))).join();

        assertEquals("island-2", state.get("active-node"));
        assertEquals("12000", state.get(IslandAddonService.tableStateKey("resource_nodes", "node/ore/0/0")));
        assertEquals(state, service.lastIslandValues);
        assertEquals(islandId, service.lastIslandId);
    }

    @Test
    void tableKeyValueBulkLoadProjectsIslandTableValues() {
        CapturingAddonService service = new CapturingAddonService();
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000703");

        service.tableKeyValueBulkSaveIslandState(
                "cloudislands-satis",
                islandId,
                Map.of("active-node", "island-2"),
                Map.of("resource_nodes", Map.of("node/ore/0/0", "12000"))).join();

        assertEquals(Map.of("node/ore/0/0", "12000"),
                service.tableKeyValueBulkLoadIslandState("cloudislands-satis", islandId, "resource_nodes").join());
        assertEquals(Map.of("node/ore/0/0", "12000"),
                service.bulkLoadTableKeyValueIslandState("cloudislands-satis", islandId, "resource_nodes").join());
    }

    @Test
    void requestObjectAliasesSaveIslandTableKeyValueState() {
        CapturingAddonService service = new CapturingAddonService();
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000704");
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.islandTables(
                "cloudislands-satis",
                islandId,
                Map.of("resource_nodes", Map.of("node/ore/0/0", "12000")));

        Map<String, String> state = service.bulkSaveIslandTableKeyValueState(request).join();

        assertEquals(islandId, service.lastIslandId);
        assertEquals("12000", state.get(IslandAddonService.tableStateKey("resource_nodes", "node/ore/0/0")));
        assertEquals(state, service.saveIslandTableKeyValueState(request).join());
        assertEquals(state, service.tableKeyValueBulkIslandState(request).join());
        assertEquals(state, service.tableBulkIslandState(request).join());
    }

    @Test
    void unregisterPreservingStateKeepsStoredAddonState() {
        CapturingAddonService service = new CapturingAddonService();

        service.putState("cloudislands-satis", Map.of("factory", "saved")).join();

        service.unregisterPreservingState("cloudislands-satis").join();

        assertEquals("cloudislands-satis", service.unregisteredId);
        assertFalse(service.clearStateCalled);
        assertEquals("saved", service.lastGlobalValues.get("factory"));
    }

    private static final class BrokenAddon implements CloudIslandsAddon {
        @Override
        public String addonId() {
            throw new IllegalStateException("id unavailable");
        }

        @Override
        public String addonDisplayName() {
            throw new IllegalStateException("display unavailable");
        }

        @Override
        public String addonVersion() {
            throw new IllegalStateException("version unavailable");
        }

        @Override
        public boolean enabledByDefault() {
            throw new IllegalStateException("enabled unavailable");
        }

        @Override
        public Map<String, Boolean> addonFeatures() {
            throw new IllegalStateException("features unavailable");
        }

        @Override
        public Map<String, String> addonMetadata() {
            throw new IllegalStateException("metadata unavailable");
        }

        @Override
        public void onAddonRegistered(CloudIslandsAddonSnapshot snapshot) {
            throw new IllegalStateException("callback unavailable");
        }
    }

    private static final class AddonWithNullMapEntries implements CloudIslandsAddon {
        @Override
        public String addonId() {
            return "null-map-addon";
        }

        @Override
        public String addonDisplayName() {
            return "Null Map Addon";
        }

        @Override
        public String addonVersion() {
            return "1";
        }

        @Override
        public Map<String, Boolean> addonFeatures() {
            Map<String, Boolean> features = new HashMap<>();
            features.put("machines", true);
            features.put("contracts", null);
            features.put(null, false);
            return features;
        }

        @Override
        public Map<String, String> addonMetadata() {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("mode", "ADDON");
            metadata.put("provider", null);
            metadata.put(null, "ignored");
            return metadata;
        }
    }

    private static final class CapturingAddonService implements IslandAddonService {
        private Map<String, String> lastGlobalValues = Map.of();
        private Map<String, String> lastIslandValues = Map.of();
        private UUID lastIslandId;
        private String unregisteredId;
        private boolean clearStateCalled;

        @Override
        public CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled,
                                                                     Map<String, Boolean> features, Map<String, String> metadata) {
            return CompletableFuture.completedFuture(new CloudIslandsAddonSnapshot(
                    id,
                    displayName,
                    version,
                    enabled,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    features,
                    features,
                    metadata
            ));
        }

        @Override
        public CompletableFuture<Void> unregister(String id) {
            unregisteredId = id;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> clearState(String id) {
            clearStateCalled = true;
            lastGlobalValues = Map.of();
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
            return CompletableFuture.completedFuture(false);
        }

        @Override
        public CompletableFuture<Map<String, String>> state(String id) {
            return CompletableFuture.completedFuture(lastGlobalValues);
        }

        @Override
        public CompletableFuture<Map<String, String>> putState(String id, Map<String, String> values) {
            lastGlobalValues = values == null ? Map.of() : Map.copyOf(values);
            return CompletableFuture.completedFuture(lastGlobalValues);
        }

        @Override
        public CompletableFuture<Map<String, String>> islandState(String id, UUID islandId) {
            return CompletableFuture.completedFuture(lastIslandValues);
        }

        @Override
        public CompletableFuture<Map<String, String>> putIslandState(String id, UUID islandId, Map<String, String> values) {
            lastIslandId = islandId;
            lastIslandValues = values == null ? Map.of() : Map.copyOf(values);
            return CompletableFuture.completedFuture(lastIslandValues);
        }
    }
}
