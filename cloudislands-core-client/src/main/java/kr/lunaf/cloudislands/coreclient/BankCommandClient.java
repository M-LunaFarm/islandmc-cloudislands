package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BankCommandClient {
    CompletableFuture<BankMutationView> deposit(UUID islandId, UUID actorUuid, String amount);

    CompletableFuture<BankMutationView> withdraw(UUID islandId, UUID actorUuid, String amount);
}
