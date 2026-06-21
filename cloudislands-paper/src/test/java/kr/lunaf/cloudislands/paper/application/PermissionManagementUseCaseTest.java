package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.RoleView;
import kr.lunaf.cloudislands.coreclient.CorePermissionCommandClient;
import kr.lunaf.cloudislands.coreclient.CorePermissionQueryClient;
import kr.lunaf.cloudislands.coreclient.MutationResult;
import kr.lunaf.cloudislands.coreclient.PermissionMatrixView;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionActionResult;
import kr.lunaf.cloudislands.paper.application.PermissionManagementUseCase.PermissionView;
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

        MutationResult<PermissionMatrixView> result = useCase.saveSequentiallyTyped(
            islandId,
            actorUuid,
            List.of(
                new PermissionManagementUseCase.PermissionChange("builder", IslandPermission.BUILD, true, "v1"),
                new PermissionManagementUseCase.PermissionChange("member", IslandPermission.OPEN_CONTAINER, false, "stale")
            ),
            auditActionRecorder(auditActions)
        ).join();

        assertEquals(List.of("v1", "v2"), expectedVersions);
        assertEquals(List.of("island.permission.batch-save"), auditActions);
        assertEquals("v3", result.version());
        assertEquals("MEMBER", result.value().rules().get(0).role());
    }

    @Test
    void sequentialSaveTypedReturnsPermissionMatrixWithoutLeakingRawJsonToApplication() {
        List<String> expectedVersions = new ArrayList<>();
        CoreApiClient client = coreApiClient(expectedVersions);
        PermissionManagementUseCase useCase = new PermissionManagementUseCase(client);

        MutationResult<PermissionMatrixView> result = useCase.saveSequentiallyTyped(
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of(
                new PermissionManagementUseCase.PermissionChange("builder", IslandPermission.BUILD, true, "v1"),
                new PermissionManagementUseCase.PermissionChange("member", IslandPermission.OPEN_CONTAINER, false, "stale")
            )
        ).join();

        assertEquals(List.of("v1", "v2"), expectedVersions);
        assertEquals("v3", result.version());
        assertEquals("MEMBER", result.value().rules().get(0).role());
        assertEquals("OPEN_CONTAINER", result.value().rules().get(0).permission());
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

    @Test
    void roleAndPermissionMutationsRunBehindUsecaseAuditBoundaries() {
        List<String> calls = new ArrayList<>();
        CoreApiClient client = commandClient(calls);
        PermissionManagementUseCase useCase = new PermissionManagementUseCase(client);
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.fromString("00000000-0000-0000-0000-000000000080");

        assertEquals("BUILDER", useCase.listRoleViews(islandId).join().getFirst().role());
        assertEquals("BUILD", useCase.listPermissionViews(islandId).join().getFirst().permission());
        assertEquals("BUILDER", useCase.upsertRoleTyped(islandId, actorUuid, "builder", 50, "", mutationRunner(calls)).join().value().role());
        assertEquals("BUILDER", useCase.resetRoleTyped(islandId, actorUuid, "builder", idempotentRunner(calls)).join().value().role());
        assertEquals("PERMISSION_SET", useCase.setPermissionAction(islandId, actorUuid, new PermissionManagementUseCase.PermissionChange("builder", IslandPermission.BUILD, true, ""), mutationRunner(calls)).join().code());
        assertEquals("PERMISSION_OVERRIDE_SET", useCase.setPermissionOverrideAction(islandId, actorUuid, targetUuid, IslandPermission.BREAK, false, mutationRunner(calls)).join().code());

        assertEquals(List.of(
            "listIslandRoles",
            "listIslandPermissions",
            "audit:island.role.upsert",
            "upsert:BUILDER:50:BUILDER",
            "audit-idempotent:island.role.reset",
            "reset:BUILDER",
            "audit:island.permission.set",
            "set:BUILDER:BUILD:true",
            "audit:island.permission.override.set",
            "override:BREAK:false"
        ), calls);
    }

    @Test
    void permissionReadsAndMutationsCanReturnTypedResultsBehindUsecaseBoundaries() {
        List<String> calls = new ArrayList<>();
        CoreApiClient client = commandClient(calls);
        PermissionManagementUseCase useCase = new PermissionManagementUseCase(client);
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();

        List<RoleView> roles = useCase.listRoleViews(islandId).join();
        assertEquals("BUILDER", roles.get(0).role());
        assertEquals(50, roles.get(0).weight());

        List<PermissionView> permissions = useCase.listPermissionViews(islandId).join();
        assertEquals("BUILDER", permissions.get(0).role());
        assertEquals("BUILD", permissions.get(0).permission());
        assertTrue(permissions.get(0).allowed());
        assertEquals("BREAK", permissions.get(1).permission());

        PermissionActionResult set = useCase.setPermissionAction(islandId, actorUuid, new PermissionManagementUseCase.PermissionChange("builder", IslandPermission.BUILD, true, ""), mutationRunner(calls)).join();
        assertTrue(set.accepted());
        assertEquals("PERMISSION_SET", set.code());

        PermissionActionResult override = useCase.setPermissionOverrideAction(islandId, actorUuid, targetUuid, IslandPermission.BREAK, false, mutationRunner(calls)).join();
        assertTrue(override.accepted());
        assertEquals("PERMISSION_OVERRIDE_SET", override.code());
    }

    @Test
    void roleMutationsCanReturnTypedResultsBehindUsecaseAuditBoundaries() {
        List<String> calls = new ArrayList<>();
        CoreApiClient client = commandClient(calls);
        PermissionManagementUseCase useCase = new PermissionManagementUseCase(client);
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        MutationResult<RoleView> upserted = useCase.upsertRoleTyped(islandId, actorUuid, "builder", 50, "", mutationRunner(calls)).join();
        MutationResult<RoleView> reset = useCase.resetRoleTyped(islandId, actorUuid, "builder", idempotentRunner(calls)).join();

        assertEquals("BUILDER", upserted.value().role());
        assertEquals(50, upserted.value().weight());
        assertEquals("BUILDER", upserted.value().displayName());
        assertEquals("BUILDER", reset.value().role());
        assertEquals(List.of(
            "audit:island.role.upsert",
            "upsert:BUILDER:50:BUILDER",
            "audit-idempotent:island.role.reset",
            "reset:BUILDER"
        ), calls);
    }

    private CoreApiClient coreApiClient(List<String> expectedVersions) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> {
                if (method.getName().equals("permissions")) {
                    return new CorePermissionCommandClient((CoreApiClient) _proxy);
                }
                if (method.getName().equals("permissionQueries")) {
                    return new CorePermissionQueryClient((CoreApiClient) _proxy);
                }
                if (method.getName().equals("setIslandPermissionResult") && args.length == 6) {
                    expectedVersions.add((String) args[5]);
                    return CompletableFuture.completedFuture("""
                        {"version":"v%s","rules":[{"role":"%s","permission":"%s","allowed":%s}]}
                        """.formatted(expectedVersions.size() + 1, args[2], args[3], args[4]));
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private CoreApiClient commandClient(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "permissions" -> new CorePermissionCommandClient((CoreApiClient) _proxy);
                case "permissionQueries" -> new CorePermissionQueryClient((CoreApiClient) _proxy);
                case "listIslandRoles" -> {
                    calls.add("listIslandRoles");
                    yield CompletableFuture.completedFuture("{\"roles\":[{\"role\":\"BUILDER\",\"weight\":50,\"displayName\":\"Builder\"}]}");
                }
                case "listIslandPermissions" -> {
                    calls.add("listIslandPermissions");
                    yield CompletableFuture.completedFuture("{\"rules\":[{\"role\":\"BUILDER\",\"permission\":\"BUILD\",\"allowed\":true},{\"playerUuid\":\"00000000-0000-0000-0000-000000000080\",\"permission\":\"BREAK\",\"allowed\":false}]}");
                }
                case "upsertIslandRole" -> {
                    calls.add("upsert:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"00000000-0000-0000-0000-000000000001","role":"%s","roleKey":"%s","weight":%s,"displayName":"%s"}
                        """.formatted(args[2], args[2], args[3], args[4]));
                }
                case "resetIslandRole" -> {
                    calls.add("reset:" + args[2]);
                    yield CompletableFuture.completedFuture("""
                        {"accepted":true,"code":"ROLE_RESET","role":"%s","roleKey":"%s","removed":true}
                        """.formatted(args[2], args[2]));
                }
                case "setIslandPermissionResult" -> {
                    calls.add("set:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"PERMISSION_SET\"}");
                }
                case "setIslandPermissionOverride" -> {
                    calls.add("override:" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"PERMISSION_OVERRIDE_SET\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
    }

    private PermissionManagementUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new PermissionManagementUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private PermissionManagementUseCase.MutationRunner auditActionRecorder(List<String> calls) {
        return new PermissionManagementUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                calls.add(auditAction);
                return operation.get();
            }
        };
    }

    private PermissionManagementUseCase.IdempotentMutationRunner idempotentRunner(List<String> calls) {
        return new PermissionManagementUseCase.IdempotentMutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                calls.add("audit-idempotent:" + auditAction);
                return operation.get();
            }
        };
    }
}
