package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandCommunicationUseCase {
    private final CoreApiClient coreApiClient;

    public IslandCommunicationUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> sendChat(UUID islandId, UUID actorUuid, String channel, String message, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        String normalizedChannel = channel == null || channel.isBlank() ? "ISLAND" : channel.trim().toUpperCase(java.util.Locale.ROOT);
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        return runner.mutate("island.chat.send", () -> coreApiClient.sendIslandChat(islandId, actorUuid, normalizedChannel, normalizedMessage));
    }

    public CompletableFuture<String> listLogs(UUID islandId, int limit) {
        requireIsland(islandId);
        return coreApiClient.listIslandLogs(islandId, Math.max(1, Math.min(limit, 30)));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireActor(UUID actorUuid) {
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }
}
