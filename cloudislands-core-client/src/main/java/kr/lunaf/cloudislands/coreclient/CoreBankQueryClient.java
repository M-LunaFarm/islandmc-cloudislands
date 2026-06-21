package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public final class CoreBankQueryClient implements BankQueryClient {
    private final CoreApiClient delegate;

    public CoreBankQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<IslandBankSnapshot> snapshot(UUID islandId) {
        requireIsland(islandId);
        return delegate.islandBank(islandId).thenApply(CoreBankJson::snapshot);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
