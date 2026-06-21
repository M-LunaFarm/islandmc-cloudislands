package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CommunicationCommandClient {
    CompletableFuture<ChatActionView> sendChat(UUID islandId, UUID actorUuid, String channel, String message);
}
