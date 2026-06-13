package kr.lunaf.cloudislands.api.addon;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;

public final class CloudIslandsAddonBootstrap {
    private CloudIslandsAddonBootstrap() {}

    public static Optional<CloudIslandsApi> findApi() {
        return CloudIslandsProvider.get();
    }

    public static CompletableFuture<Optional<CloudIslandsAddonSnapshot>> registerIfAvailable(CloudIslandsAddon addon) {
        if (addon == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            return findApi()
                .map(api -> addon.register(api).thenApply(Optional::of).exceptionally(_error -> Optional.empty()))
                .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    public static CompletableFuture<Boolean> unregisterIfAvailable(CloudIslandsAddon addon) {
        if (addon == null) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            return findApi()
                .map(api -> addon.unregister(api).thenApply(ignored -> true).exceptionally(_error -> false))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(false);
        }
    }
}
