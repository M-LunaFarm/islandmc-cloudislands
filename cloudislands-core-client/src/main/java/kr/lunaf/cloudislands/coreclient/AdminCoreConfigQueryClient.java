package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminCoreConfigQueryClient {
    CompletableFuture<AdminCoreConfigView> config();
}
