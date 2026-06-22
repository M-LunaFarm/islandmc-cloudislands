package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class JdkMemberCommandClient implements MemberCommandClient {
    private final JdkCoreApiClient core;

    public JdkMemberCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<MemberActionView> removeMember(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return core.postWithResultBody("/v1/islands/members/remove", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", targetUuid))
            .thenApply(body -> memberAction(body, "MEMBER_REMOVED"));
    }

    @Override
    public CompletableFuture<CoreGuiViews.InviteView> createInvite(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return core.postWithResultBody("/v1/islands/invites", JdkCoreApiClient.jsonObject("islandId", islandId, "inviterUuid", actorUuid, "targetUuid", targetUuid))
            .thenApply(CoreGuiViews::inviteView);
    }

    @Override
    public CompletableFuture<IslandInviteActionResult> acceptInvite(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return core.postWithResultBody("/v1/islands/invites/accept", JdkCoreApiClient.jsonObject("inviteId", inviteId, "playerUuid", playerUuid))
            .thenApply(body -> inviteAction(body, "ACCEPTED"));
    }

    @Override
    public CompletableFuture<IslandInviteActionResult> declineInvite(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return core.postWithResultBody("/v1/islands/invites/decline", JdkCoreApiClient.jsonObject("inviteId", inviteId, "playerUuid", playerUuid))
            .thenApply(body -> inviteAction(body, "DECLINED"));
    }

    @Override
    public CompletableFuture<MemberActionView> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        requireIds(islandId, actorUuid, targetUuid);
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        return core.postWithResultBody(
                "/v1/islands/members/set",
                JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", targetUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey)
            )
            .thenApply(body -> memberAction(body, "MEMBER_ROLE_SET"));
    }

    @Override
    public CompletableFuture<MemberActionView> trustTemporarily(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
        requireIds(islandId, actorUuid, targetUuid);
        if (durationSeconds <= 0L) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        return core.postWithResultBody("/v1/islands/members/trust-temporary", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", targetUuid, "durationSeconds", durationSeconds))
            .thenApply(body -> memberAction(body, "TEMP_TRUST_SET"));
    }

    @Override
    public CompletableFuture<MemberActionView> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return core.postWithResultBody("/v1/islands/transfer", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "targetUuid", targetUuid))
            .thenApply(body -> memberAction(body, "OWNERSHIP_TRANSFERRED"));
    }

    @Override
    public CompletableFuture<MemberActionView> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
        requireIds(islandId, actorUuid, targetUuid);
        return core.postWithResultBody("/v1/islands/bans/set", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", targetUuid, "reason", reason == null ? "" : reason))
            .thenApply(body -> memberAction(body, "VISITOR_BANNED"));
    }

    @Override
    public CompletableFuture<MemberActionView> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return core.postWithResultBody("/v1/islands/bans/remove", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", targetUuid))
            .thenApply(body -> memberAction(body, "VISITOR_PARDONED"));
    }

    @Override
    public CompletableFuture<MemberActionView> kickVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return core.postWithResultBody("/v1/islands/visitors/kick", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", targetUuid))
            .thenApply(body -> memberAction(body, "VISITOR_KICKED"));
    }

    static IslandInviteActionResult inviteAction(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = bool(root, "accepted");
        String code = CoreJson.text(root, "code");
        return new IslandInviteActionResult(accepted, accepted ? successCode : (code.isBlank() ? "FAILED" : code));
    }

    static MemberActionView memberAction(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = bool(root, "accepted") && !root.containsKey("error");
        String code = CoreJson.text(root, "code");
        return new MemberActionView(accepted, accepted ? successCode : (code.isBlank() ? "FAILED" : code), CoreJson.text(root, "expiresAt"));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }

    private static String normalizeRoleKey(String roleKey) {
        return roleKey == null ? "" : roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
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
