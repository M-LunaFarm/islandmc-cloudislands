package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.BanView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.InviteView;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.MemberView;

public final class MemberManagementUseCase {
    private final CoreApiClient coreApiClient;

    public MemberManagementUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> removeMember(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.removeIslandMemberResult(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> removeMemberAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return removeMember(islandId, actorUuid, targetUuid).thenApply(body -> memberAction(body, "MEMBER_REMOVED"));
    }

    public CompletableFuture<String> listMembers(UUID islandId) {
        requireIslandId(islandId);
        return coreApiClient.listIslandMembers(islandId);
    }

    public CompletableFuture<List<MemberView>> listMemberViews(UUID islandId) {
        requireIslandId(islandId);
        return CoreGuiViews.islandMembers(coreApiClient, islandId);
    }

    public CompletableFuture<String> playerInfoByName(String playerName) {
        String normalizedPlayerName = playerName == null ? "" : playerName.trim();
        if (normalizedPlayerName.isBlank()) {
            throw new IllegalArgumentException("playerName is required");
        }
        return coreApiClient.playerInfoByName(normalizedPlayerName);
    }

    public CompletableFuture<UUID> playerUuidByName(String playerName) {
        return playerInfoByName(playerName).thenApply(body -> uuid(CoreGuiViews.playerProfile(body).playerUuid()));
    }

    public CompletableFuture<String> islandInfoByName(String islandName) {
        String normalizedIslandName = islandName == null ? "" : islandName.trim();
        if (normalizedIslandName.isBlank()) {
            throw new IllegalArgumentException("islandName is required");
        }
        return coreApiClient.islandInfoByName(normalizedIslandName);
    }

    public CompletableFuture<String> createInvite(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.createIslandInvite(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<InviteView> createInviteView(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return createInvite(islandId, actorUuid, targetUuid).thenApply(CoreGuiViews::inviteView);
    }

    public CompletableFuture<String> listPendingInvites(UUID playerUuid) {
        requirePlayerId(playerUuid);
        return coreApiClient.listPendingInvites(playerUuid);
    }

    public CompletableFuture<List<InviteView>> listPendingInviteViews(UUID playerUuid) {
        requirePlayerId(playerUuid);
        return CoreGuiViews.pendingInvites(coreApiClient, playerUuid);
    }

    public CompletableFuture<UUID> resolveInviteIdOrDirectId(UUID playerUuid, UUID inviteOrTargetUuid) {
        requirePlayerId(playerUuid);
        if (inviteOrTargetUuid == null) {
            throw new IllegalArgumentException("inviteOrTargetUuid is required");
        }
        return listPendingInvites(playerUuid).thenApply(body -> {
            UUID inviteId = findPendingInviteId(body, inviteOrTargetUuid);
            return inviteId == null ? inviteOrTargetUuid : inviteId;
        });
    }

    public CompletableFuture<UUID> resolveInviteByPlayerUuid(UUID playerUuid, UUID targetUuid) {
        requireIdsForInviteLookup(playerUuid, targetUuid);
        return listPendingInvites(playerUuid).thenApply(body -> findPendingInviteId(body, targetUuid));
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
        return islandInfoByName(normalizedIslandName)
            .thenCompose(body -> {
                UUID islandId = uuid(text(body, "islandId"));
                return islandId == null ? CompletableFuture.completedFuture(null) : resolveInviteByPlayerUuid(playerUuid, islandId);
            });
    }

    public CompletableFuture<String> acceptInvite(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return coreApiClient.acceptIslandInviteResult(inviteId, playerUuid);
    }

    public CompletableFuture<IslandInviteActionResult> acceptInviteAction(UUID inviteId, UUID playerUuid) {
        return acceptInvite(inviteId, playerUuid).thenApply(body -> inviteAction(body, "ACCEPTED"));
    }

    public CompletableFuture<String> declineInvite(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return coreApiClient.declineIslandInviteResult(inviteId, playerUuid);
    }

    public CompletableFuture<IslandInviteActionResult> declineInviteAction(UUID inviteId, UUID playerUuid) {
        return declineInvite(inviteId, playerUuid).thenApply(body -> inviteAction(body, "DECLINED"));
    }

    public CompletableFuture<String> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        requireIds(islandId, actorUuid, targetUuid);
        String normalizedRoleKey = roleKey == null ? "" : roleKey.trim();
        if (normalizedRoleKey.isBlank()) {
            throw new IllegalArgumentException("roleKey is required");
        }
        return coreApiClient.setIslandMemberResult(islandId, actorUuid, targetUuid, normalizedRoleKey);
    }

    public CompletableFuture<MemberActionResult> setRoleAction(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        return setRole(islandId, actorUuid, targetUuid, roleKey).thenApply(body -> memberAction(body, "MEMBER_ROLE_SET"));
    }

    public CompletableFuture<String> trustTemporarily(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
        requireIds(islandId, actorUuid, targetUuid);
        if (durationSeconds <= 0L) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        return coreApiClient.trustIslandMemberTemporary(islandId, actorUuid, targetUuid, durationSeconds);
    }

    public CompletableFuture<MemberActionResult> trustTemporarilyAction(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
        return trustTemporarily(islandId, actorUuid, targetUuid, durationSeconds).thenApply(body -> memberAction(body, "TEMP_TRUST_SET"));
    }

    public CompletableFuture<String> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.transferIslandOwnershipResult(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> transferOwnershipAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return transferOwnership(islandId, actorUuid, targetUuid).thenApply(body -> memberAction(body, "OWNERSHIP_TRANSFERRED"));
    }

    public CompletableFuture<String> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.banIslandVisitorResult(islandId, actorUuid, targetUuid, reason == null ? "" : reason);
    }

    public CompletableFuture<MemberActionResult> banVisitorAction(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
        return banVisitor(islandId, actorUuid, targetUuid, reason).thenApply(body -> memberAction(body, "VISITOR_BANNED"));
    }

    public CompletableFuture<String> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.pardonIslandVisitorResult(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> pardonVisitorAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return pardonVisitor(islandId, actorUuid, targetUuid).thenApply(body -> memberAction(body, "VISITOR_PARDONED"));
    }

    public CompletableFuture<String> kickVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.kickIslandVisitorResult(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<MemberActionResult> kickVisitorAction(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return kickVisitor(islandId, actorUuid, targetUuid).thenApply(body -> memberAction(body, "VISITOR_KICKED"));
    }

    public CompletableFuture<String> listBans(UUID islandId) {
        requireIslandId(islandId);
        return coreApiClient.listIslandBans(islandId);
    }

    public CompletableFuture<List<BanView>> listBanViews(UUID islandId) {
        requireIslandId(islandId);
        return CoreGuiViews.islandBans(coreApiClient, islandId);
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

    static UUID findPendingInviteId(String body, UUID targetUuid) {
        if (body == null || targetUuid == null) {
            return null;
        }
        Object parsed = SimpleJson.parse(body);
        java.util.List<?> invites = invites(parsed);
        for (Object item : invites) {
            java.util.Map<?, ?> object = SimpleJson.object(item);
            UUID inviteId = uuid(SimpleJson.text(object.get("inviteId")));
            if (targetUuid.equals(inviteId)
                || targetUuid.equals(uuid(SimpleJson.text(object.get("islandId"))))
                || targetUuid.equals(uuid(SimpleJson.text(object.get("inviterUuid"))))) {
                return inviteId;
            }
        }
        return null;
    }

    private static java.util.List<?> invites(Object parsed) {
        java.util.List<?> rootList = SimpleJson.list(parsed);
        if (!rootList.isEmpty()) {
            return rootList;
        }
        java.util.Map<?, ?> root = SimpleJson.object(parsed);
        java.util.List<?> invites = SimpleJson.list(root.get("invites"));
        if (!invites.isEmpty()) {
            return invites;
        }
        return root.containsKey("inviteId") ? java.util.List.of(root) : java.util.List.of();
    }

    private static String text(String json, String key) {
        return SimpleJson.text(SimpleJson.object(SimpleJson.parse(json)).get(key));
    }

    private static IslandInviteActionResult inviteAction(String body, String successCode) {
        java.util.Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = bool(root, "accepted");
        String code = SimpleJson.text(root.get("code"));
        return new IslandInviteActionResult(accepted, accepted ? successCode : (code.isBlank() ? "FAILED" : code));
    }

    private static MemberActionResult memberAction(String body, String successCode) {
        java.util.Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = bool(root, "accepted") && !root.containsKey("error");
        String code = SimpleJson.text(root.get("code"));
        String expiresAt = SimpleJson.text(root.get("expiresAt"));
        return new MemberActionResult(accepted, accepted ? successCode : (code.isBlank() ? "FAILED" : code), expiresAt);
    }

    private static boolean bool(java.util.Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
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
