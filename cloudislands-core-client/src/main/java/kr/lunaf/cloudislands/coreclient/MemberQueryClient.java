package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;

public interface MemberQueryClient {
    CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName);

    CompletableFuture<List<IslandInviteSnapshot>> inviteSnapshots(UUID playerUuid);

    CompletableFuture<List<IslandBanSnapshot>> banSnapshots(UUID islandId);

    default CompletableFuture<List<CoreGuiViews.InviteView>> pendingInvites(UUID playerUuid) {
        return inviteSnapshots(playerUuid).thenApply(invites -> invites.stream()
            .map(CoreMemberJson::inviteView)
            .toList());
    }

    default CompletableFuture<List<CoreGuiViews.BanView>> bans(UUID islandId) {
        return banSnapshots(islandId).thenApply(bans -> bans.stream()
            .map(CoreMemberJson::banView)
            .toList());
    }
}
