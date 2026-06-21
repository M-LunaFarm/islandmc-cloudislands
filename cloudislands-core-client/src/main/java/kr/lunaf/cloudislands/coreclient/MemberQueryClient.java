package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MemberQueryClient {
    CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName);

    CompletableFuture<List<CoreGuiViews.InviteView>> pendingInvites(UUID playerUuid);

    CompletableFuture<List<CoreGuiViews.BanView>> bans(UUID islandId);
}
