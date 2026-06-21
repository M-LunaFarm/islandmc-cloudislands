package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminAddonStateQueryClient {
    CompletableFuture<AdminAddonStateSummaryView> summary();
}
