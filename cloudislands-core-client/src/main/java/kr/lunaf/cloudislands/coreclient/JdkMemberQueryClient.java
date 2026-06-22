package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;

public final class JdkMemberQueryClient implements MemberQueryClient {
    private final JdkCoreApiClient core;

    public JdkMemberQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName) {
        String normalizedPlayerName = requireText(playerName, "playerName");
        return core.playerProfiles().findByName(normalizedPlayerName).thenApply(CorePlayerProfileJson::guiProfile);
    }

    @Override
    public CompletableFuture<List<IslandInviteSnapshot>> inviteSnapshots(UUID playerUuid) {
        requirePlayer(playerUuid);
        return core.post("/v1/players/invites", CoreJsonPayload.object("playerUuid", playerUuid))
            .thenApply(CoreMemberJson::invites);
    }

    @Override
    public CompletableFuture<List<IslandBanSnapshot>> banSnapshots(UUID islandId) {
        requireIsland(islandId);
        return core.get("/v1/islands/" + islandId + "/bans")
            .thenApply(body -> CoreMemberJson.bans(islandId, body));
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
