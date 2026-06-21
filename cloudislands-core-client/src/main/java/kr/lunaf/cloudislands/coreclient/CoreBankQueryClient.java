package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreBankQueryClient implements BankQueryClient {
    private final CoreApiClient delegate;

    public CoreBankQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.BankView> islandBank(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandBank(delegate, islandId);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
