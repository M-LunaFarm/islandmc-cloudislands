package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreMemberQueryClient implements MemberQueryClient {
    private final CoreApiClient delegate;

    public CoreMemberQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName) {
        String normalizedPlayerName = requireText(playerName, "playerName");
        return delegate.playerInfoByName(normalizedPlayerName).thenApply(CoreGuiViews::playerProfile);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.InviteView>> pendingInvites(UUID playerUuid) {
        requirePlayer(playerUuid);
        return CoreGuiViews.pendingInvites(delegate, playerUuid);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.BanView>> bans(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandBans(delegate, islandId);
    }

    private static void requirePlayer(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid is required");
        }
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static String requireText(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }
}
