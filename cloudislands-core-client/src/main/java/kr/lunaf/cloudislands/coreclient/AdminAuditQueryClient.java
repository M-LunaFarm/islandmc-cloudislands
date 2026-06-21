package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AdminAuditQueryClient {
    CompletableFuture<List<AdminAuditEntryView>> list(int limit);
}
