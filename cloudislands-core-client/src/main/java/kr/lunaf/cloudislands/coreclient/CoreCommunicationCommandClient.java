package kr.lunaf.cloudislands.coreclient;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        return CoreCommunicationJson.chatAction(body, successCode);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
