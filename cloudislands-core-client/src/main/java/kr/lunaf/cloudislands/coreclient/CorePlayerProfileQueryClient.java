package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CorePlayerProfileQueryClient implements PlayerProfileQueryClient {
    private final CoreApiClient delegate;

    public CorePlayerProfileQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<PlayerProfileView> profile(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return delegate.playerInfo(playerUuid).thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> findByName(String lastName) {
        String normalized = lastName == null ? "" : lastName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("lastName is required");
        }
        return delegate.playerInfoByName(normalized).thenApply(CorePlayerProfileJson::profile);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
