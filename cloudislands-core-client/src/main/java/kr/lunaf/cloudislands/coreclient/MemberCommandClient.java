package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;

public interface MemberCommandClient {
    CompletableFuture<MemberActionView> removeMember(UUID islandId, UUID actorUuid, UUID targetUuid);

    CompletableFuture<CoreGuiViews.InviteView> createInvite(UUID islandId, UUID actorUuid, UUID targetUuid);

    CompletableFuture<IslandInviteActionResult> acceptInvite(UUID inviteId, UUID playerUuid);

    CompletableFuture<IslandInviteActionResult> declineInvite(UUID inviteId, UUID playerUuid);

    CompletableFuture<MemberActionView> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey);

    CompletableFuture<MemberActionView> trustTemporarily(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds);

    CompletableFuture<MemberActionView> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid);

    CompletableFuture<MemberActionView> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason);

    CompletableFuture<MemberActionView> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid);

    CompletableFuture<MemberActionView> kickVisitor(UUID islandId, UUID actorUuid, UUID targetUuid);
}
