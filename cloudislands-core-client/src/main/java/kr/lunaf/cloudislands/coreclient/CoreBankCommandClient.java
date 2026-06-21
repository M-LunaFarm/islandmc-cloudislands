package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;

public final class CoreBankCommandClient implements BankCommandClient {
    private final CoreApiClient delegate;

    public CoreBankCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<IslandBankChangeSnapshot> depositSnapshot(UUID islandId, UUID actorUuid, String amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.depositIslandBank(islandId, actorUuid, amount == null ? "" : amount)
            .thenApply(CoreBankJson::mutation);
    }

    @Override
    public CompletableFuture<IslandBankChangeSnapshot> withdrawSnapshot(UUID islandId, UUID actorUuid, String amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.withdrawIslandBank(islandId, actorUuid, amount == null ? "" : amount)
            .thenApply(CoreBankJson::mutation);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
