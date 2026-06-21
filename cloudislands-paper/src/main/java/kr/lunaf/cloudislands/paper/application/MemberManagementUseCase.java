package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

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

    public CompletableFuture<String> listMembers(UUID islandId) {
        requireIslandId(islandId);
        return coreApiClient.listIslandMembers(islandId);
    }

    public CompletableFuture<String> playerInfoByName(String playerName) {
        String normalizedPlayerName = playerName == null ? "" : playerName.trim();
        if (normalizedPlayerName.isBlank()) {
            throw new IllegalArgumentException("playerName is required");
        }
        return coreApiClient.playerInfoByName(normalizedPlayerName);
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

    public CompletableFuture<String> listPendingInvites(UUID playerUuid) {
        requirePlayerId(playerUuid);
        return coreApiClient.listPendingInvites(playerUuid);
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
        return playerInfoByName(normalizedTarget)
            .handle((body, error) -> error == null ? uuid(text(body, "playerUuid")) : null)
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

    public CompletableFuture<String> declineInvite(UUID inviteId, UUID playerUuid) {
        requireInviteAndPlayer(inviteId, playerUuid);
        return coreApiClient.declineIslandInviteResult(inviteId, playerUuid);
    }

    public CompletableFuture<String> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        requireIds(islandId, actorUuid, targetUuid);
        String normalizedRoleKey = roleKey == null ? "" : roleKey.trim();
        if (normalizedRoleKey.isBlank()) {
            throw new IllegalArgumentException("roleKey is required");
        }
        return coreApiClient.setIslandMemberResult(islandId, actorUuid, targetUuid, normalizedRoleKey);
    }

    public CompletableFuture<String> trustTemporarily(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
        requireIds(islandId, actorUuid, targetUuid);
        if (durationSeconds <= 0L) {
            throw new IllegalArgumentException("durationSeconds must be positive");
        }
        return coreApiClient.trustIslandMemberTemporary(islandId, actorUuid, targetUuid, durationSeconds);
    }

    public CompletableFuture<String> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.transferIslandOwnershipResult(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<String> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.banIslandVisitorResult(islandId, actorUuid, targetUuid, reason == null ? "" : reason);
    }

    public CompletableFuture<String> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.pardonIslandVisitorResult(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<String> kickVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.kickIslandVisitorResult(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<String> listBans(UUID islandId) {
        requireIslandId(islandId);
        return coreApiClient.listIslandBans(islandId);
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
}
