package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CommunicationQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreCommunicationQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LogEntryView;

public final class IslandCommunicationUseCase {
    private final CoreApiClient coreApiClient;
    private final CommunicationQueryClient communicationQueries;

    public IslandCommunicationUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.communicationQueries = new CoreCommunicationQueryClient(coreApiClient);
    }

    IslandCommunicationUseCase(CoreApiClient coreApiClient, CommunicationQueryClient communicationQueries) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (communicationQueries == null) {
            throw new IllegalArgumentException("communicationQueries is required");
        }
        this.coreApiClient = coreApiClient;
        this.communicationQueries = communicationQueries;
    }

    private CompletableFuture<String> sendChatBody(UUID islandId, UUID actorUuid, String channel, String message, MutationRunner runner) {
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

    public CompletableFuture<ChatActionResult> sendChatAction(UUID islandId, UUID actorUuid, String channel, String message, MutationRunner runner) {
        return sendChatBody(islandId, actorUuid, channel, message, runner)
            .thenApply(body -> chatAction(body, "CHAT_SENT"));
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

    private static ChatActionResult chatAction(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new ChatActionResult(accepted, code);
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public record ChatActionResult(boolean accepted, String code) {
        public ChatActionResult {
            code = code == null ? "" : code;
        }
    }
}
