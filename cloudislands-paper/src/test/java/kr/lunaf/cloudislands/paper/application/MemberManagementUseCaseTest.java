package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class MemberManagementUseCaseTest {
    @Test
    void resolvesDirectInviteTargetThroughPendingInviteIndex() {
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID inviteId = uuid("00000000-0000-0000-0000-000000000010");
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "listPendingInvites", "{\"invites\":[{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"00000000-0000-0000-0000-000000000030\"}]}"
        )));

        assertEquals(inviteId, useCase.resolveInviteIdOrDirectId(playerUuid, islandId).join());
        assertEquals(inviteId, useCase.resolveInviteByPlayerUuid(playerUuid, islandId).join());
    }

    @Test
    void keepsUnknownDirectUuidAsInviteIdForBackwardCompatibleCommands() {
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID requestedInviteId = uuid("00000000-0000-0000-0000-000000000099");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "listPendingInvites", "{\"invites\":[]}"
        )));

        assertEquals(requestedInviteId, useCase.resolveInviteIdOrDirectId(playerUuid, requestedInviteId).join());
    }

    @Test
    void resolvesTextTargetByPlayerThenFallsBackToIslandName() {
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID inviteId = uuid("00000000-0000-0000-0000-000000000010");
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");

        MemberManagementUseCase useCase = new MemberManagementUseCase(client(Map.of(
            "listPendingInvites", "{\"invites\":[{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"00000000-0000-0000-0000-000000000030\"}]}",
            "islandInfoByName", "{\"islandId\":\"" + islandId + "\"}",
            "playerInfoByName", "ERROR"
        )));

        assertEquals(inviteId, useCase.resolveInviteByPlayerNameOrIslandName(playerUuid, "spawn").join());
    }

    private static CoreApiClient client(Map<String, String> bodies) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, _args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "test-core-client";
                        case "hashCode" -> 1;
                        case "equals" -> _proxy == _args[0];
                        default -> null;
                    };
                }
                String value = bodies.get(method.getName());
                if ("ERROR".equals(value)) {
                    return CompletableFuture.failedFuture(new IllegalStateException(method.getName() + " failed"));
                }
                return CompletableFuture.completedFuture(value == null ? "{}" : value);
            });
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
