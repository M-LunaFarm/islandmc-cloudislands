package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkPlayerProfileQueryClient implements PlayerProfileQueryClient {
    private final JdkCoreApiClient core;

    JdkPlayerProfileQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<PlayerProfileView> profile(UUID playerUuid) {
        requireId(playerUuid, "playerUuid");
        return core.postWithResultBody("/v1/admin/players/info", JdkCoreApiClient.jsonObject("playerUuid", playerUuid))
            .thenApply(CorePlayerProfileJson::profile);
    }

    @Override
    public CompletableFuture<PlayerProfileView> findByName(String lastName) {
        String normalized = lastName == null ? "" : lastName.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("lastName is required");
        }
        return core.post("/v1/players/info", JdkCoreApiClient.jsonObject("lastName", normalized))
            .thenApply(CorePlayerProfileJson::profile);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
