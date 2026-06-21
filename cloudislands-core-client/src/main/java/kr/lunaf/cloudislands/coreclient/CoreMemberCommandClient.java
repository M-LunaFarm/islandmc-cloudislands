package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreMemberCommandClient implements MemberCommandClient {
    private final CoreApiClient delegate;

    public CoreMemberCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<MemberActionView> removeMember(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return delegate.removeIslandMemberResult(islandId, actorUuid, targetUuid)
            .thenApply(body -> memberAction(body, "MEMBER_REMOVED"));
    }

    @Override
    public CompletableFuture<CoreGuiViews.InviteView> createInvite(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return delegate.createIslandInvite(islandId, actorUuid, targetUuid).thenApply(CoreGuiViews::inviteView);
    }

    @Override
    public CompletableFuture<IslandInviteActionResult> acceptInvite(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return delegate.acceptIslandInviteResult(inviteId, playerUuid).thenApply(body -> inviteAction(body, "ACCEPTED"));
    }

    @Override
    public CompletableFuture<IslandInviteActionResult> declineInvite(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return delegate.declineIslandInviteResult(inviteId, playerUuid).thenApply(body -> inviteAction(body, "DECLINED"));
    }

    @Override
    public CompletableFuture<MemberActionView> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        requireIds(islandId, actorUuid, targetUuid);
        String normalizedRoleKey = RoleId.of(roleKey).value();
        return delegate.setIslandMemberResult(islandId, actorUuid, targetUuid, normalizedRoleKey)
            .thenApply(body -> memberAction(body, "MEMBER_ROLE_SET"));
    }

    @Override
    public CompletableFuture<MemberActionView> trustTemporarily(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
        requireIds(islandId, actorUuid, targetUuid);
        if (durationSeconds <= 0L) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        return delegate.trustIslandMemberTemporary(islandId, actorUuid, targetUuid, durationSeconds)
            .thenApply(body -> memberAction(body, "TEMP_TRUST_SET"));
    }

    @Override
    public CompletableFuture<MemberActionView> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return delegate.transferIslandOwnershipResult(islandId, actorUuid, targetUuid)
            .thenApply(body -> memberAction(body, "OWNERSHIP_TRANSFERRED"));
    }

    @Override
    public CompletableFuture<MemberActionView> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
        requireIds(islandId, actorUuid, targetUuid);
        return delegate.banIslandVisitorResult(islandId, actorUuid, targetUuid, reason == null ? "" : reason)
            .thenApply(body -> memberAction(body, "VISITOR_BANNED"));
    }

    @Override
    public CompletableFuture<MemberActionView> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return delegate.pardonIslandVisitorResult(islandId, actorUuid, targetUuid)
            .thenApply(body -> memberAction(body, "VISITOR_PARDONED"));
    }

    @Override
    public CompletableFuture<MemberActionView> kickVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return delegate.kickIslandVisitorResult(islandId, actorUuid, targetUuid)
            .thenApply(body -> memberAction(body, "VISITOR_KICKED"));
    }

    private static IslandInviteActionResult inviteAction(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = bool(root, "accepted");
        String code = CoreJson.text(root, "code");
        return new IslandInviteActionResult(accepted, accepted ? successCode : (code.isBlank() ? "FAILED" : code));
    }

    private static MemberActionView memberAction(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = bool(root, "accepted") && !root.containsKey("error");
        String code = CoreJson.text(root, "code");
        return new MemberActionView(accepted, accepted ? successCode : (code.isBlank() ? "FAILED" : code), CoreJson.text(root, "expiresAt"));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }

    private static void requireIds(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireId(targetUuid, "targetUuid");
    }

    private static void requireInviteAndPlayer(UUID inviteId, UUID playerUuid) {
        requireId(inviteId, "inviteId");
        requireId(playerUuid, "playerUuid");
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
