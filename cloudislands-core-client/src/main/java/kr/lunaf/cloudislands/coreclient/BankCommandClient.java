package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public interface BankCommandClient {
    CompletableFuture<IslandBankChangeSnapshot> depositSnapshot(UUID islandId, UUID actorUuid, String amount);

    CompletableFuture<IslandBankChangeSnapshot> withdrawSnapshot(UUID islandId, UUID actorUuid, String amount);

    default CompletableFuture<BankMutationView> deposit(UUID islandId, UUID actorUuid, String amount) {
        return depositSnapshot(islandId, actorUuid, amount).thenApply(BankCommandClient::mutationView);
    }

    default CompletableFuture<BankMutationView> withdraw(UUID islandId, UUID actorUuid, String amount) {
        return withdrawSnapshot(islandId, actorUuid, amount).thenApply(BankCommandClient::mutationView);
    }

    private static BankMutationView mutationView(IslandBankChangeSnapshot change) {
        IslandBankSnapshot bank = change == null ? null : change.bank();
        return new BankMutationView(
            change != null && change.accepted(),
            change == null ? "FAILED" : change.code(),
            bank == null || bank.islandId() == null ? "" : bank.islandId().toString(),
            bank == null ? "0" : bank.balance(),
            bank == null || bank.updatedAt() == null ? "" : bank.updatedAt().toString()
        );
    }
}
