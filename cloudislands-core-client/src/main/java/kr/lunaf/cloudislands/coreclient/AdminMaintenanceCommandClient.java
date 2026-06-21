package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminMaintenanceCommandClient {
    CompletableFuture<AdminMaintenanceResultView> clearCache();

    CompletableFuture<AdminMaintenanceResultView> reload();
}
