package kr.lunaf.cloudislands.paper.application;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    public CompletableFuture<String> createInvite(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIds(islandId, actorUuid, targetUuid);
        return coreApiClient.createIslandInvite(islandId, actorUuid, targetUuid);
    }

    public CompletableFuture<String> listPendingInvites(UUID playerUuid) {
        requirePlayerId(playerUuid);
        return coreApiClient.listPendingInvites(playerUuid);
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

    private static void requireIds(UUID islandId, UUID actorUuid, UUID targetUuid) {
        requireIslandId(islandId);
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
    }
}
