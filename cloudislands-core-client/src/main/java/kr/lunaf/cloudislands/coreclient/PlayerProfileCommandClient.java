package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileCommandClient {
    CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName);

    CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName, String locale);

    CompletableFuture<PlayerProfileView> setLocale(UUID playerUuid, String locale);

    CompletableFuture<PlayerProfileView> setPrimaryIsland(UUID playerUuid, UUID islandId);

    CompletableFuture<PlayerProfileView> clearPrimaryIsland(UUID playerUuid);
}
