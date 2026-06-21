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

    private static void requireIds(UUID islandId, UUID actorUuid, UUID targetUuid) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
    }
}
