package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileCommandClient {
    CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName);

    CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName, String locale);

    CompletableFuture<PlayerProfileView> setLocale(UUID playerUuid, String locale);

    CompletableFuture<PlayerProfileView> setIslandFlyEnabled(UUID playerUuid, boolean enabled);

    CompletableFuture<PlayerProfileView> setWorldBorderEnabled(UUID playerUuid, boolean enabled);

    CompletableFuture<PlayerProfileView> setBlocksStackerEnabled(UUID playerUuid, boolean enabled);

    CompletableFuture<PlayerProfileView> setBorderColor(UUID playerUuid, String color);

    CompletableFuture<Long> reservePrimaryIslandSelection(UUID playerUuid);

    CompletableFuture<PlayerProfileView> setPrimaryIsland(UUID playerUuid, UUID islandId);

    CompletableFuture<PlayerProfileView> selectPrimaryIsland(UUID playerUuid, UUID islandId);

    CompletableFuture<PlayerProfileView> selectPrimaryIsland(UUID playerUuid, UUID islandId, long selectionRevision);

    CompletableFuture<PlayerProfileView> clearPrimaryIsland(UUID playerUuid);

    CompletableFuture<PlayerProfileView> setDisbandsRemaining(UUID playerUuid, int value);

    CompletableFuture<PlayerProfileView> addDisbandsRemaining(UUID playerUuid, int delta);
}
