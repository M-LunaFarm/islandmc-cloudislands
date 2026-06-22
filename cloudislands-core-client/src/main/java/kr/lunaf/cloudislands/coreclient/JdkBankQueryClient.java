package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

final class JdkBankQueryClient implements BankQueryClient {
    private final JdkCoreApiClient core;

    JdkBankQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<IslandBankSnapshot> snapshot(UUID islandId) {
        requireId(islandId, "islandId");
        return core.post("/v1/islands/bank", JdkCoreApiClient.jsonObject("islandId", islandId))
            .thenApply(CoreBankJson::snapshot);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
