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
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
        return coreApiClient.removeIslandMemberResult(islandId, actorUuid, targetUuid);
    }
}
