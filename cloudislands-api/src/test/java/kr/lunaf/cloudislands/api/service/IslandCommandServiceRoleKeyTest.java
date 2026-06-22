package kr.lunaf.cloudislands.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.api.model.RoleId;
import org.junit.jupiter.api.Test;

class IslandCommandServiceRoleKeyTest {
    @Test
    void legacyEnumRoleMethodsAreDeprecatedAdapters() throws Exception {
        assertDeprecatedAdapter("setRole", UUID.class, UUID.class, UUID.class, IslandRole.class);
        assertDeprecatedAdapter("setRoleResult", UUID.class, UUID.class, UUID.class, IslandRole.class);
        assertDeprecatedAdapter("setPermission", UUID.class, UUID.class, IslandRole.class, IslandPermission.class, boolean.class);
        assertDeprecatedAdapter("setPermissionResult", UUID.class, UUID.class, IslandRole.class, IslandPermission.class, boolean.class);
        assertDeprecatedAdapter("upsertRole", UUID.class, UUID.class, IslandRole.class, int.class, String.class);
        assertDeprecatedAdapter("upsertRoleResult", UUID.class, UUID.class, IslandRole.class, int.class, String.class);
        assertDeprecatedAdapter("resetRole", UUID.class, UUID.class, IslandRole.class);
        assertDeprecatedAdapter("resetRoleResult", UUID.class, UUID.class, IslandRole.class);
    }

    @Test
    void roleKeyDefaultsDoNotRejectCustomRolesBeforeImplementationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandCommandService service = service(calls);
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();

        assertEquals("MEMBER_ROLE_SET", service.setRoleResult(islandId, actorUuid, targetUuid, RoleId.of("builder-role")).join().code());
        assertEquals("PERMISSION_SET", service.setPermissionResult(islandId, actorUuid, RoleId.of("builder-role"), IslandPermission.BUILD, true).join().code());
        assertEquals("BUILDER_ROLE", service.upsertRoleResult(islandId, actorUuid, RoleId.of("builder-role"), 42, "Builder").join().roleKey());
        assertEquals("ROLE_RESET", service.resetRoleResult(islandId, actorUuid, RoleId.of("builder-role")).join().code());

        assertEquals(List.of(
            "setRole:BUILDER_ROLE",
            "setPermission:BUILDER_ROLE:BUILD:true",
            "upsertRole:BUILDER_ROLE:42:Builder",
            "resetRole:BUILDER_ROLE"
        ), calls);
    }

    @Test
    void legacyEnumRoleMethodsDelegateToRoleKeyContract() {
        List<String> calls = new ArrayList<>();
        IslandCommandService service = service(calls);
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();

        service.setRoleResult(islandId, actorUuid, targetUuid, IslandRole.TRUSTED).join();
        service.setPermissionResult(islandId, actorUuid, IslandRole.TRUSTED, IslandPermission.INTERACT, false).join();
        service.upsertRoleResult(islandId, actorUuid, IslandRole.TRUSTED, 30, "Trusted").join();
        service.resetRoleResult(islandId, actorUuid, IslandRole.TRUSTED).join();

        assertEquals(List.of(
            "setRole:TRUSTED",
            "setPermission:TRUSTED:INTERACT:false",
            "upsertRole:TRUSTED:30:Trusted",
            "resetRole:TRUSTED"
        ), calls);
    }

    private static IslandCommandService service(List<String> calls) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            return switch (method.getName()) {
                case "setRoleResult" -> {
                    calls.add("setRole:" + args[3]);
                    yield CompletableFuture.completedFuture(new IslandActionResult(true, "MEMBER_ROLE_SET"));
                }
                case "setPermissionResult" -> {
                    calls.add("setPermission:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture(new IslandActionResult(true, "PERMISSION_SET"));
                }
                case "upsertRoleResult" -> {
                    calls.add("upsertRole:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture(new IslandRoleSnapshot((UUID) args[0], (String) args[2], (int) args[3], (String) args[4]));
                }
                case "resetRoleResult" -> {
                    calls.add("resetRole:" + args[2]);
                    yield CompletableFuture.completedFuture(new IslandActionResult(true, "ROLE_RESET"));
                }
                case "toString" -> "role-key-command-service";
                default -> throw new UnsupportedOperationException(method.toString());
            };
        };
        return (IslandCommandService) Proxy.newProxyInstance(
            IslandCommandService.class.getClassLoader(),
            new Class<?>[] {IslandCommandService.class},
            handler
        );
    }

    private static void assertDeprecatedAdapter(String name, Class<?>... parameterTypes) throws Exception {
        Method method = IslandCommandService.class.getMethod(name, parameterTypes);
        assertTrue(method.isAnnotationPresent(Deprecated.class), method + " must remain a deprecated enum adapter");
    }
}
