package kr.lunaf.cloudislands.api.service;

import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final class CapturingAddonService implements IslandAddonService {
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
    }
}
