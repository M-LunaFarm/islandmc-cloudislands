package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface BankQueryClient {
    CompletableFuture<CoreGuiViews.BankView> islandBank(UUID islandId);
}
