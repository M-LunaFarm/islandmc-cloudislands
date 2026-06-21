package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public interface BankQueryClient {
    CompletableFuture<IslandBankSnapshot> snapshot(UUID islandId);

    default CompletableFuture<CoreGuiViews.BankView> islandBank(UUID islandId) {
        return snapshot(islandId).thenApply(snapshot -> new CoreGuiViews.BankView(
            snapshot == null ? "0" : snapshot.balance(),
            snapshot == null || snapshot.updatedAt() == null ? "" : snapshot.updatedAt().toString()
        ));
    }
}
