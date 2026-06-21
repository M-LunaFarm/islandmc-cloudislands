package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import org.junit.jupiter.api.Test;

class CoreTypedClientsTest {
    @Test
    void islandQueryClientReturnsTypedIslandAndMemberPages() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandInfo" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Spawn","state":"ACTIVE","level":12,"worth":"34.50","publicAccess":true,"locked":false,"size":300,"border":310,"ownerUuid":"owner"}
                    """.formatted(islandId));
                case "listIslandMembers" -> CompletableFuture.completedFuture("""
                    {"members":[
                      {"playerUuid":"p1","role":"OWNER","joinedAt":"t1","playerName":"Alice"},
                      {"playerUuid":"p2","role":"MEMBER","joinedAt":"t2","playerName":"Bob"},
                      {"playerUuid":"p3","role":"TRUSTED","joinedAt":"t3","playerName":"Carol"}
                    ]}
                    """);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandQueryClient client = new CoreIslandQueryClient(raw);

        CoreGuiViews.IslandInfoView island = client.getIsland(islandId).join();
        MemberPage firstPage = client.listMembers(islandId, new MemberCursor(0, 2)).join();
        MemberPage secondPage = client.listMembers(islandId, firstPage.nextCursor()).join();

        assertEquals("Spawn", island.name());
        assertEquals(12L, island.level());
        assertEquals(2, firstPage.members().size());
        assertTrue(firstPage.hasNext());
        assertEquals("Bob", firstPage.members().get(1).playerName());
        assertEquals(1, secondPage.members().size());
        assertFalse(secondPage.hasNext());
        assertEquals("Carol", secondPage.members().get(0).playerName());
    }

    @Test
    void permissionCommandClientCarriesTypedVersionAcrossBatch() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> expectedVersions = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> {
                if (method.getName().equals("setIslandPermissionResult")) {
                    expectedVersions.add((String) args[5]);
                    return CompletableFuture.completedFuture("""
                        {"version":"v%s","rules":[{"role":"%s","permission":"%s","allowed":%s}]}
                        """.formatted(expectedVersions.size() + 1, args[2], args[3], args[4]));
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
        PermissionCommandClient client = new CorePermissionCommandClient(raw);

        MutationResult<PermissionMatrixView> result = client.updatePermissions(new UpdatePermissionsRequest(
            islandId,
            actorUuid,
            List.of(
                new UpdatePermissionsRequest.Change("builder", IslandPermission.BUILD, true, "v1"),
                new UpdatePermissionsRequest.Change("builder", IslandPermission.OPEN_CONTAINER, false, "stale")
            )
        )).join();

        assertEquals(List.of("v1", "v2"), expectedVersions);
        assertEquals("v3", result.version());
        assertTrue(result.changed());
        assertEquals("BUILDER", result.value().rules().get(0).role());
        assertEquals("OPEN_CONTAINER", result.value().rules().get(0).permission());
        assertFalse(result.value().rules().get(0).allowed());
    }

    @Test
    void environmentQueryClientReturnsTypedEnvironmentViews() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandBiome" -> CompletableFuture.completedFuture("{\"biomeKey\":\"minecraft:plains\"}");
                case "islandInfo" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Spawn","state":"ACTIVE","size":300,"border":310}
                    """.formatted(islandId));
                case "listIslandFlags" -> CompletableFuture.completedFuture("{\"flags\":{\"BORDER_COLOR\":\"blue\"}}");
                case "listIslandLimits" -> CompletableFuture.completedFuture("{\"limits\":[{\"limitKey\":\"HOPPER\",\"value\":64,\"updatedAt\":\"now\"}]}");
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandEnvironmentQueryClient client = new CoreIslandEnvironmentQueryClient(raw);

        assertEquals("minecraft:plains", client.islandBiome(islandId).join().key());
        assertEquals(300L, client.getIsland(islandId).join().size());
        assertEquals("blue", client.flagValues(islandId).join().get(IslandFlag.BORDER_COLOR));
        assertEquals("HOPPER", client.limitViews(islandId).join().get(0).key());
    }

    @Test
    void permissionCommandClientReturnsTypedRoleMutations() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "upsertIslandRole" -> {
                    calls.add("upsert:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"%s","role":"%s","roleKey":"%s","weight":%s,"displayName":"%s"}
                        """.formatted(islandId, args[2], args[2], args[3], args[4]));
                }
                case "resetIslandRole" -> {
                    calls.add("reset:" + args[2]);
                    yield CompletableFuture.completedFuture("""
                        {"accepted":true,"code":"ROLE_RESET","role":"%s","roleKey":"%s","removed":true}
                        """.formatted(args[2], args[2]));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        PermissionCommandClient client = new CorePermissionCommandClient(raw);

        MutationResult<CoreGuiViews.RoleView> upserted = client.upsertRole(islandId, actorUuid, "builder", 42, "Builder").join();
        MutationResult<CoreGuiViews.RoleView> reset = client.resetRole(islandId, actorUuid, "builder").join();

        assertEquals(List.of("upsert:BUILDER:42:Builder", "reset:BUILDER"), calls);
        assertEquals("BUILDER", upserted.value().role());
        assertEquals(42, upserted.value().weight());
        assertEquals("Builder", upserted.value().displayName());
        assertEquals("BUILDER", reset.value().role());
        assertTrue(reset.changed());
    }
}
