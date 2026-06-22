package kr.lunaf.cloudislands.coreclient;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkCommunicationCommandClient implements CommunicationCommandClient {
    private final JdkCoreApiClient core;

    JdkCommunicationCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
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
        return core.postBody("/v1/islands/chat", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "channel", normalizedChannel, "message", normalizedMessage))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> CoreCommunicationJson.chatAction(body, "CHAT_SENT"));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
