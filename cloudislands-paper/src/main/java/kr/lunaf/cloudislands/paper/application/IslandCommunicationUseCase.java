package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.ChatActionView;
import kr.lunaf.cloudislands.coreclient.CommunicationCommandClient;
import kr.lunaf.cloudislands.coreclient.CommunicationQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LogEntryView;

public final class IslandCommunicationUseCase {
    private final CoreApiClient coreApiClient;
    private final CommunicationQueryClient communicationQueries;
    private final CommunicationCommandClient communicationCommands;

    public IslandCommunicationUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.communicationQueries = coreApiClient.communication();
        this.communicationCommands = coreApiClient.communicationCommands();
    }

    IslandCommunicationUseCase(CoreApiClient coreApiClient, CommunicationQueryClient communicationQueries) {
        this(coreApiClient, communicationQueries, coreApiClient.communicationCommands());
    }

    IslandCommunicationUseCase(CoreApiClient coreApiClient, CommunicationQueryClient communicationQueries, CommunicationCommandClient communicationCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (communicationQueries == null) {
            throw new IllegalArgumentException("communicationQueries is required");
        }
        if (communicationCommands == null) {
            throw new IllegalArgumentException("communicationCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.communicationQueries = communicationQueries;
        this.communicationCommands = communicationCommands;
    }

    private CompletableFuture<ChatActionView> sendChatBody(UUID islandId, UUID actorUuid, String channel, String message, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        String normalizedChannel = channel == null || channel.isBlank() ? "ISLAND" : channel.trim().toUpperCase(java.util.Locale.ROOT);
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        return runner.mutate("island.chat.send", () -> communicationCommands.sendChat(islandId, actorUuid, normalizedChannel, normalizedMessage));
    }

    public CompletableFuture<ChatActionResult> sendChatAction(UUID islandId, UUID actorUuid, String channel, String message, MutationRunner runner) {
        return sendChatBody(islandId, actorUuid, channel, message, runner)
            .thenApply(IslandCommunicationUseCase::chatAction);
    }

    public CompletableFuture<List<LogEntryView>> logViews(UUID islandId, int limit) {
        requireIsland(islandId);
        return communicationQueries.listLogs(islandId, Math.max(1, Math.min(limit, 30)));
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

    private static ChatActionResult chatAction(ChatActionView view) {
        return new ChatActionResult(view.accepted(), view.code());
    }

    @FunctionalInterface
    public interface MutationRunner {
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record ChatActionResult(boolean accepted, String code) {
        public ChatActionResult {
            code = code == null ? "" : code;
        }
    }
}
