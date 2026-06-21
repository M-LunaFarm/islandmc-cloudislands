package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CorePlayerProfileCommandClient implements PlayerProfileCommandClient {
    private final CoreApiClient delegate;

    public CorePlayerProfileCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName) {
        requireId(playerUuid, "playerUuid");
        return delegate.touchPlayerProfile(playerUuid, lastName == null ? "" : lastName).thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName, String locale) {
        requireId(playerUuid, "playerUuid");
        return delegate.touchPlayerProfile(playerUuid, lastName == null ? "" : lastName, locale == null ? "" : locale).thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> setLocale(UUID playerUuid, String locale) {
        requireId(playerUuid, "playerUuid");
        return delegate.setPlayerLocale(playerUuid, locale == null ? "" : locale).thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> setPrimaryIsland(UUID playerUuid, UUID islandId) {
        requireId(playerUuid, "playerUuid");
        requireId(islandId, "islandId");
        return delegate.setPlayerIsland(playerUuid, islandId).thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> clearPrimaryIsland(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return delegate.clearPlayerIsland(playerUuid).thenApply(CorePlayerProfileJson::profile);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
