package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreMemberCommandClient;
import kr.lunaf.cloudislands.coreclient.CoreMemberQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreIslandQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.BanView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.InviteView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.MemberView;
import kr.lunaf.cloudislands.coreclient.IslandQueryClient;
import kr.lunaf.cloudislands.coreclient.MemberActionView;
import kr.lunaf.cloudislands.coreclient.MemberCommandClient;
import kr.lunaf.cloudislands.coreclient.MemberQueryClient;

public final class MemberManagementUseCase {
    private final CoreApiClient coreApiClient;
    private final IslandQueryClient islandQueries;
    private final MemberQueryClient memberQueries;
    private final MemberCommandClient memberCommands;

    public MemberManagementUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.islandQueries = new CoreIslandQueryClient(coreApiClient);
        this.memberQueries = new CoreMemberQueryClient(coreApiClient);
        this.memberCommands = new CoreMemberCommandClient(coreApiClient);
    }

    MemberManagementUseCase(CoreApiClient coreApiClient, IslandQueryClient islandQueries) {
        this(coreApiClient, islandQueries, new CoreMemberQueryClient(coreApiClient), new CoreMemberCommandClient(coreApiClient));
    }

    MemberManagementUseCase(CoreApiClient coreApiClient, IslandQueryClient islandQueries, MemberQueryClient memberQueries) {
        this(coreApiClient, islandQueries, memberQueries, new CoreMemberCommandClient(coreApiClient));
    }

    MemberManagementUseCase(CoreApiClient coreApiClient, IslandQueryClient islandQueries, MemberQueryClient memberQueries, MemberCommandClient memberCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (islandQueries == null) {
            throw new IllegalArgumentException("islandQueries is required");
        }
        if (memberQueries == null) {
            throw new IllegalArgumentException("memberQueries is required");
        }
        if (memberCommands == null) {
            throw new IllegalArgumentException("memberCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.islandQueries = islandQueries;
        this.memberQueries = memberQueries;
        this.memberCommands = memberCommands;
    }

    public CompletableFuture<List<MemberView>> listMemberViews(UUID islandId) {
        requireIslandId(islandId);
        return islandQueries.listMembers(islandId);
    }

    private CompletableFuture<MemberActionView> removeMemberBody(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return memberCommands.removeMember(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> removeMemberAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return removeMemberBody(islandId, actorUuid, targetUuid).thenApply(MemberManagementUseCase::memberAction);
    }

    public CompletableFuture<UUID> playerUuidByName(String playerName) {
        return memberQueries.playerProfileByName(playerName).thenApply(profile -> uuid(profile.playerUuid()));
    }

    private CompletableFuture<InviteView> createInviteBody(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return memberCommands.createInvite(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<InviteView> createInviteView(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return createInviteBody(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<List<InviteView>> listPendingInviteViews(UUID playerUuid) {
        requirePlayerId(playerUuid);
        return memberQueries.pendingInvites(playerUuid);
    }

    public CompletableFuture<UUID> resolveInviteIdOrDirectId(UUID playerUuid, UUID inviteOrTargetUuid) {
        requirePlayerId(playerUuid);
        if (inviteOrTargetUuid == null) {
            throw new IllegalArgumentException("inviteOrTargetUuid is required");
        }
        return memberQueries.pendingInvites(playerUuid).thenApply(invites -> {
            UUID inviteId = findPendingInviteId(invites, inviteOrTargetUuid);
            return inviteId == null ? inviteOrTargetUuid : inviteId;
        });
    }

    public CompletableFuture<UUID> resolveInviteByPlayerUuid(UUID playerUuid, UUID targetUuid) {
        requireIdsForInviteLookup(playerUuid, targetUuid);
        return memberQueries.pendingInvites(playerUuid).thenApply(invites -> findPendingInviteId(invites, targetUuid));
    }

    public CompletableFuture<UUID> resolveInviteByPlayerNameOrIslandName(UUID playerUuid, String target) {
        requirePlayerId(playerUuid);
        String normalizedTarget = target == null ? "" : target.trim();
        if (normalizedTarget.isBlank()) {
            throw new IllegalArgumentException("target is required");
        }
        return playerUuidByName(normalizedTarget)
            .handle((targetUuid, error) -> error == null ? targetUuid : null)
            .thenCompose(targetUuid -> {
                if (targetUuid == null) {
                    return resolveInviteByIslandName(playerUuid, normalizedTarget);
                }
                return resolveInviteByPlayerUuid(playerUuid, targetUuid)
                    .thenCompose(inviteId -> inviteId == null ? resolveInviteByIslandName(playerUuid, normalizedTarget) : CompletableFuture.completedFuture(inviteId));
            });
    }

    public CompletableFuture<UUID> resolveInviteByIslandName(UUID playerUuid, String islandName) {
        requirePlayerId(playerUuid);
        String normalizedIslandName = islandName == null ? "" : islandName.trim();
        if (normalizedIslandName.isBlank()) {
            throw new IllegalArgumentException("islandName is required");
        }
        return islandQueries.findIslandByName(normalizedIslandName)
            .thenCompose(island -> {
                UUID islandId = uuid(island.islandId());
                return islandId == null ? CompletableFuture.completedFuture(null) : resolveInviteByPlayerUuid(playerUuid, islandId);
            });
    }

    private CompletableFuture<IslandInviteActionResult> acceptInviteBody(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return memberCommands.acceptInvite(inviteId, playerUuid);
    }

    public CompletableFuture<IslandInviteActionResult> acceptInviteAction(UUID inviteId, UUID playerUuid) {
        return acceptInviteBody(inviteId, playerUuid);
    }

    private CompletableFuture<IslandInviteActionResult> declineInviteBody(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return memberCommands.declineInvite(inviteId, playerUuid);
    }

    public CompletableFuture<IslandInviteActionResult> declineInviteAction(UUID inviteId, UUID playerUuid) {
        return declineInviteBody(inviteId, playerUuid);
    }

    private CompletableFuture<MemberActionView> setRoleBody(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        requireIds(islandId, actorUuid, targetUuid);
        String normalizedRoleKey = roleKey == null ? "" : roleKey.trim();
        if (normalizedRoleKey.isBlank()) {
            throw new IllegalArgumentException("roleKey is required");
        }
        return memberCommands.setRole(islandId, actorUuid, targetUuid, normalizedRoleKey);
    }

    public CompletableFuture<MemberActionResult> setRoleAction(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        return setRoleBody(islandId, actorUuid, targetUuid, roleKey).thenApply(MemberManagementUseCase::memberAction);
    }

    private CompletableFuture<MemberActionView> trustTemporarilyBody(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
        requireIds(islandId, actorUuid, targetUuid);
        if (durationSeconds <= 0L) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        return memberCommands.trustTemporarily(islandId, actorUuid, targetUuid, durationSeconds);
    }

    public CompletableFuture<MemberActionResult> trustTemporarilyAction(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
        return trustTemporarilyBody(islandId, actorUuid, targetUuid, durationSeconds).thenApply(MemberManagementUseCase::memberAction);
    }

    private CompletableFuture<MemberActionView> transferOwnershipBody(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return memberCommands.transferOwnership(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> transferOwnershipAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return transferOwnershipBody(islandId, actorUuid, targetUuid).thenApply(MemberManagementUseCase::memberAction);
    }

    private CompletableFuture<MemberActionView> banVisitorBody(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
        requireIds(islandId, actorUuid, targetUuid);
        return memberCommands.banVisitor(islandId, actorUuid, targetUuid, reason == null ? "" : reason);
    }

    public CompletableFuture<MemberActionResult> banVisitorAction(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
        return banVisitorBody(islandId, actorUuid, targetUuid, reason).thenApply(MemberManagementUseCase::memberAction);
    }

    private CompletableFuture<MemberActionView> pardonVisitorBody(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return memberCommands.pardonVisitor(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> pardonVisitorAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return pardonVisitorBody(islandId, actorUuid, targetUuid).thenApply(MemberManagementUseCase::memberAction);
    }

    private CompletableFuture<MemberActionView> kickVisitorBody(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return memberCommands.kickVisitor(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> kickVisitorAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return kickVisitorBody(islandId, actorUuid, targetUuid).thenApply(MemberManagementUseCase::memberAction);
    }

    public CompletableFuture<List<BanView>> listBanViews(UUID islandId) {
        requireIslandId(islandId);
        return memberQueries.bans(islandId);
    }

    private static void requireIslandId(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requirePlayerId(UUID playerUuid) {
        if (playerUuid == null) {
            throw new IllegalArgumentException("playerUuid is required");
        }
    }

    private static void requireInviteAndPlayer(UUID inviteId, UUID playerUuid) {
        if (inviteId == null) {
            throw new IllegalArgumentException("inviteId is required");
        }
        requirePlayerId(playerUuid);
    }

    private static void requireIdsForInviteLookup(UUID playerUuid, UUID targetUuid) {
        requirePlayerId(playerUuid);
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
    }

    private static void requireIds(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIslandId(islandId);
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
    }

    static UUID findPendingInviteId(List<InviteView> invites, UUID targetUuid) {
        if (invites == null || targetUuid == null) {
            return null;
        }
        for (InviteView invite : invites) {
            UUID inviteId = uuid(invite.inviteId());
            if (targetUuid.equals(inviteId)
                || targetUuid.equals(uuid(invite.islandId()))
                || targetUuid.equals(uuid(invite.inviterUuid()))) {
                return inviteId;
            }
        }
        return null;
    }

    private static MemberActionResult memberAction(MemberActionView view) {
        return new MemberActionResult(view.accepted(), view.code(), view.expiresAt());
    }

    private static UUID uuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record MemberActionResult(boolean accepted, String code, String expiresAt) {
        public MemberActionResult {
            code = code == null ? "" : code;
            expiresAt = expiresAt == null ? "" : expiresAt;
        }
    }
}
