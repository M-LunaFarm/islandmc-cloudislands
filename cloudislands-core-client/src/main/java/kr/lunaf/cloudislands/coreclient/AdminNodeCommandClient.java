package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminNodeCommandClient {
    CompletableFuture<AdminNodeActionView> drainNode(String nodeId);

    CompletableFuture<AdminNodeActionView> undrainNode(String nodeId);

    CompletableFuture<AdminNodeActionView> sweepNode(String nodeId);

    CompletableFuture<AdminNodeActionView> kickAllNode(String nodeId, String reason);

    CompletableFuture<AdminNodeActionView> shutdownNodeSafely(String nodeId, String reason);
}
