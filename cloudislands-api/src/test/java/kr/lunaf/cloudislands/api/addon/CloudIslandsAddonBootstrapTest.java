package kr.lunaf.cloudislands.api.addon;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
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

import static org.junit.jupiter.api.Assertions.assertFalse;

class CloudIslandsAddonBootstrapTest {
    @Test
    void nullAddonRegistrationIsIgnored() {
        assertFalse(CloudIslandsAddonBootstrap.registerIfAvailable(null).join().isPresent());
    }

    @Test
    void nullAddonUnregistrationIsIgnored() {
        assertFalse(CloudIslandsAddonBootstrap.unregisterIfAvailable(null).join());
    }

    @Test
    void addonRegistrationFailureIsIgnored() {
        CloudIslandsApi api = apiWithThrowingAddons();
        CloudIslandsProvider.set(api);
        try {
            assertFalse(CloudIslandsAddonBootstrap.registerIfAvailable(new MinimalAddon()).join().isPresent());
        } finally {
            CloudIslandsProvider.clear(api);
        }
    }

    @Test
    void addonUnregistrationFailureIsIgnored() {
        CloudIslandsApi api = apiWithThrowingAddons();
        CloudIslandsProvider.set(api);
        try {
            assertFalse(CloudIslandsAddonBootstrap.unregisterIfAvailable(new MinimalAddon()).join());
        } finally {
            CloudIslandsProvider.clear(api);
        }
    }

    private CloudIslandsApi apiWithThrowingAddons() {
        return new CloudIslandsApi() {
            @Override
            public IslandAddonService addons() {
                return new IslandAddonService() {
                    @Override
                    public CompletableFuture<CloudIslandsAddonSnapshot> register(String id, String displayName, String version, boolean enabled, Map<String, Boolean> features, Map<String, String> metadata) {
                        throw new IllegalStateException("registry unavailable");
                    }

                    @Override
                    public CompletableFuture<Void> unregister(String id) {
                        throw new IllegalStateException("registry unavailable");
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
                };
            }

            @Override public IslandQueryService islands() { throw new UnsupportedOperationException(); }
            @Override public PlayerIslandService players() { throw new UnsupportedOperationException(); }
            @Override public IslandRoutingService routing() { throw new UnsupportedOperationException(); }
            @Override public IslandPermissionService permissions() { throw new UnsupportedOperationException(); }
            @Override public IslandRuntimeService runtime() { throw new UnsupportedOperationException(); }
            @Override public IslandStatusService status() { throw new UnsupportedOperationException(); }
            @Override public IslandEventService events() { throw new UnsupportedOperationException(); }
            @Override public IslandAdminService admin() { throw new UnsupportedOperationException(); }
            @Override public IslandCommandService commands() { throw new UnsupportedOperationException(); }
        };
    }

    private static final class MinimalAddon implements CloudIslandsAddon {
        @Override
        public String addonId() {
            return "minimal-addon";
        }

        @Override
        public String addonDisplayName() {
            return "Minimal Addon";
        }

        @Override
        public String addonVersion() {
            return "1";
        }
    }
}
