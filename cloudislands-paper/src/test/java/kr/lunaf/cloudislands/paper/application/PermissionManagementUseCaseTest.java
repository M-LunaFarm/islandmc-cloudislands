package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class PermissionManagementUseCaseTest {
    @Test
    void sequentialSaveCarriesForwardLatestCoreVersionInsideBatch() {
        List<String> expectedVersions = new ArrayList<>();
        CoreApiClient client = coreApiClient(expectedVersions);
        PermissionManagementUseCase useCase = new PermissionManagementUseCase(client);
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> auditActions = new ArrayList<>();

        String body = useCase.saveSequentially(
            islandId,
            actorUuid,
            List.of(
                new PermissionManagementUseCase.PermissionChange("builder", IslandPermission.BUILD, true, "v1"),
                new PermissionManagementUseCase.PermissionChange("member", IslandPermission.OPEN_CONTAINER, false, "stale")
            ),
            (auditAction, operation) -> {
                auditActions.add(auditAction);
                return operation.get();
            }
        ).join();

        assertEquals(List.of("v1", "v2"), expectedVersions);
        assertEquals(List.of("island.permission.batch-save", "island.permission.batch-save"), auditActions);
        assertEquals("{\"version\":\"v3\"}", body);
    }

    @Test
    void permissionChangeNormalizesRoleIdAndRejectsMissingPermission() {
        PermissionManagementUseCase.PermissionChange change =
            new PermissionManagementUseCase.PermissionChange("co-owner", IslandPermission.BUILD, true, " v7 ");

        assertEquals("CO_OWNER", change.roleKey());
        assertEquals("v7", change.expectedVersion());
        assertEquals("CO_OWNER:BUILD", change.key());
        assertThrows(IllegalArgumentException.class, () -> new PermissionManagementUseCase.PermissionChange("member", null, true, ""));
    }

    private CoreApiClient coreApiClient(List<String> expectedVersions) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> {
                if (method.getName().equals("setIslandPermissionResult") && args.length == 6) {
                    expectedVersions.add((String) args[5]);
                    return CompletableFuture.completedFuture("{\"version\":\"v" + (expectedVersions.size() + 1) + "\"}");
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
