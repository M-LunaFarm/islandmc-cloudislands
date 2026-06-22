package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;

final class JdkBankCommandClient implements BankCommandClient {
    private final JdkCoreApiClient core;

    JdkBankCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<IslandBankChangeSnapshot> depositSnapshot(UUID islandId, UUID actorUuid, String amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postResultBody("/v1/islands/bank/deposit", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "amount", amount == null ? "" : amount))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreBankJson::mutation);
    }

    @Override
    public CompletableFuture<IslandBankChangeSnapshot> withdrawSnapshot(UUID islandId, UUID actorUuid, String amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postResultBody("/v1/islands/bank/withdraw", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "amount", amount == null ? "" : amount))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreBankJson::mutation);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
