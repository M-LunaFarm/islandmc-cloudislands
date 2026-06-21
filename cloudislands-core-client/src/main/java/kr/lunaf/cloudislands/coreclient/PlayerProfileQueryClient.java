package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PlayerProfileQueryClient {
    CompletableFuture<PlayerProfileView> profile(UUID playerUuid);

    CompletableFuture<PlayerProfileView> findByName(String lastName);
}
