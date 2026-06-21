package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminEventQueryClient {
    CompletableFuture<AdminEventStreamView> list(int limit);

    CompletableFuture<AdminEventStreamView> listSince(long sinceSeq, int limit);
}
