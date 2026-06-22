package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkPlayerProfileCommandClient implements PlayerProfileCommandClient {
    private final JdkCoreApiClient core;

    JdkPlayerProfileCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName) {
        requireId(playerUuid, "playerUuid");
        return core.postResultBody("/v1/players/touch", CoreJsonPayload.object("playerUuid", playerUuid, "lastName", lastName == null ? "" : lastName))
            .thenApply(CoreResponseBody::value)
            .thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName, String locale) {
        requireId(playerUuid, "playerUuid");
        return core.postResultBody("/v1/players/touch", CoreJsonPayload.object("playerUuid", playerUuid, "lastName", lastName == null ? "" : lastName, "locale", locale == null ? "" : locale))
            .thenApply(CoreResponseBody::value)
            .thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> setLocale(UUID playerUuid, String locale) {
        requireId(playerUuid, "playerUuid");
        return core.postResultBody("/v1/players/locale", CoreJsonPayload.object("playerUuid", playerUuid, "locale", locale == null ? "" : locale))
            .thenApply(CoreResponseBody::value)
            .thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> setPrimaryIsland(UUID playerUuid, UUID islandId) {
        requireId(playerUuid, "playerUuid");
        requireId(islandId, "islandId");
        return core.postResultBody("/v1/admin/players/setisland", CoreJsonPayload.object("playerUuid", playerUuid, "islandId", islandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> clearPrimaryIsland(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return core.postResultBody("/v1/admin/players/clearisland", CoreJsonPayload.object("playerUuid", playerUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(CorePlayerProfileJson::profile);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
