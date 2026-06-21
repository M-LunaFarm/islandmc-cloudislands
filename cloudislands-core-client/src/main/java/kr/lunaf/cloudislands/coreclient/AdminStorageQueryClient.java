package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminStorageQueryClient {
    CompletableFuture<AdminStorageStatusView> status();
}
