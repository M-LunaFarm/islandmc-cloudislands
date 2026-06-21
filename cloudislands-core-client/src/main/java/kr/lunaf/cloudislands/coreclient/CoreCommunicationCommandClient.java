package kr.lunaf.cloudislands.coreclient;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreCommunicationCommandClient implements CommunicationCommandClient {
    private final CoreApiClient delegate;

    public CoreCommunicationCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<ChatActionView> sendChat(UUID islandId, UUID actorUuid, String channel, String message) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedChannel = channel == null || channel.isBlank() ? "ISLAND" : channel.trim().toUpperCase(Locale.ROOT);
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        return delegate.sendIslandChat(islandId, actorUuid, normalizedChannel, normalizedMessage)
            .thenApply(body -> chatAction(body, "CHAT_SENT"));
    }

    private static ChatActionView chatAction(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new ChatActionView(accepted, code, SimpleJson.text(root.get("channel")), SimpleJson.text(root.get("message")));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
