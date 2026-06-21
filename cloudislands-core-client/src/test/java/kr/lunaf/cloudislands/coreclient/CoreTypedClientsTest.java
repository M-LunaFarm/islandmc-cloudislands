package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import org.junit.jupiter.api.Test;

class CoreTypedClientsTest {
    @Test
    void jdkCoreApiClientJsonObjectEscapesRouteRequestFields() throws Exception {
        Method method = JdkCoreApiClient.class.getDeclaredMethod("jsonObject", Object[].class);
        method.setAccessible(true);

        String body = (String) method.invoke(null, (Object) new Object[] {
            "nodeId", "paper\"east",
            "reportMissing", true,
            "retry", 2
        });

        assertEquals("{\"nodeId\":\"paper\\\"east\",\"reportMissing\":true,\"retry\":2}", body);
    }

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
                case "islandInfoByName" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Named","state":"ACTIVE","level":1}
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
        CoreGuiViews.IslandInfoView namedIsland = client.findIslandByName(" Named ").join();
        List<CoreGuiViews.MemberView> members = client.listMembers(islandId).join();
        MemberPage firstPage = client.listMembers(islandId, new MemberCursor(0, 2)).join();
        MemberPage secondPage = client.listMembers(islandId, firstPage.nextCursor()).join();

        assertEquals("Spawn", island.name());
        assertEquals("Named", namedIsland.name());
        assertEquals(12L, island.level());
        assertEquals(3, members.size());
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
    void permissionCommandClientReturnsTypedActionViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "setIslandPermissionResult" -> {
                    calls.add("set:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"PERMISSION_SET\"}");
                }
                case "setIslandPermissionOverride" -> {
                    calls.add("override:" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("plain-success");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        PermissionCommandClient client = new CorePermissionCommandClient(raw);

        assertEquals("PERMISSION_SET", client.setPermission(islandId, actorUuid, "builder", IslandPermission.BUILD, true).join().code());
        assertEquals("PERMISSION_OVERRIDE_SET", client.setPermissionOverride(islandId, actorUuid, targetUuid, IslandPermission.BREAK, false).join().code());
        assertEquals(List.of("set:BUILDER:BUILD:true", "override:BREAK:false"), calls);
    }

    @Test
    void permissionQueryClientReturnsTypedAssignmentsAndRoles() {
        UUID islandId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listIslandPermissions" -> {
                    calls.add("permissions");
                    yield CompletableFuture.completedFuture("""
                        {"version":"v1","rules":[{"role":"BUILDER","permission":"BUILD","allowed":true},{"playerUuid":"%s","permission":"BREAK","allowed":false}]}
                        """.formatted(playerUuid));
                }
                case "listIslandRoles" -> {
                    calls.add("roles");
                    yield CompletableFuture.completedFuture("""
                        {"roles":[{"role":"BUILDER","weight":50,"displayName":"Builder"}]}
                        """);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        PermissionQueryClient client = new CorePermissionQueryClient(raw);

        List<PermissionAssignmentView> permissions = client.permissions(islandId).join();
        List<CoreGuiViews.RoleView> roles = client.roles(islandId).join();

        assertEquals(List.of("permissions", "roles"), calls);
        assertEquals("BUILDER", permissions.get(0).role());
        assertEquals(playerUuid.toString(), permissions.get(1).playerUuid());
        assertFalse(permissions.get(1).allowed());
        assertEquals("v1", permissions.get(1).version());
        assertEquals("BUILDER", roles.get(0).role());
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
    void environmentCommandClientReturnsTypedActionViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "setIslandBiomeResult" -> {
                    calls.add("biome:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"BIOME_SET\",\"biomeKey\":\"PLAINS\"}");
                }
                case "setIslandFlagResult" -> {
                    calls.add("flag:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"FLAG_SET\",\"flag\":\"BORDER_VISIBLE\"}");
                }
                case "setIslandLimit" -> {
                    calls.add("limit:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"LIMIT_SET\",\"limitKey\":\"HOPPER\",\"value\":64}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandEnvironmentCommandClient client = new CoreIslandEnvironmentCommandClient(raw);

        EnvironmentActionView biome = client.setBiome(islandId, actorUuid, "PLAINS").join();
        EnvironmentActionView flag = client.setFlag(islandId, actorUuid, IslandFlag.BORDER_VISIBLE, "true").join();
        EnvironmentActionView limit = client.setLimit(islandId, actorUuid, "HOPPER", 64L).join();

        assertEquals("PLAINS", biome.key());
        assertEquals("BORDER_VISIBLE", flag.key());
        assertEquals("HOPPER", limit.key());
        assertEquals(64L, limit.value());
        assertEquals(List.of("biome:PLAINS", "flag:BORDER_VISIBLE:true", "limit:HOPPER:64"), calls);
    }

    @Test
    void settingsCommandClientReturnsTypedActionViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "setIslandPublicAccessResult" -> {
                    calls.add("public:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"PUBLIC_ACCESS_ENABLED\"}");
                }
                case "setIslandLockedResult" -> {
                    calls.add("locked:" + args[2]);
                    yield CompletableFuture.completedFuture("plain-success");
                }
                case "setIslandNameResult" -> {
                    calls.add("name:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"ISLAND_RENAMED\"}");
                }
                case "setIslandFlagResult" -> {
                    calls.add("flag:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"FLAG_SET\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandSettingsCommandClient client = new CoreIslandSettingsCommandClient(raw);

        assertEquals("PUBLIC_ACCESS_ENABLED", client.setPublicAccess(islandId, actorUuid, true).join().code());
        assertEquals("ISLAND_UNLOCKED", client.setLocked(islandId, actorUuid, false).join().code());
        assertEquals("ISLAND_RENAMED", client.setName(islandId, actorUuid, "My Island").join().code());
        assertEquals("FLAG_SET", client.setFlag(islandId, actorUuid, IslandFlag.PVP, "false").join().code());
        assertEquals(List.of("public:true", "locked:false", "name:My Island", "flag:PVP:false"), calls);
    }

    @Test
    void snapshotQueryClientReturnsTypedSnapshotsWithChecksums() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listIslandSnapshots" -> CompletableFuture.completedFuture("""
                    {"snapshots":[{"snapshotNo":7,"reason":"manual","sizeBytes":4096,"createdAt":"now","checksum":"abcdef1234567890"}]}
                    """);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        SnapshotQueryClient client = new CoreSnapshotQueryClient(raw);

        CoreGuiViews.SnapshotView snapshot = client.listSnapshots(islandId, 500).join().get(0);

        assertEquals(7L, snapshot.snapshotNo());
        assertEquals("manual", snapshot.reason());
        assertEquals(4096L, snapshot.sizeBytes());
        assertEquals("abcdef1234567890", snapshot.checksum());
    }

    @Test
    void snapshotCommandClientReturnsTypedActionViews() {
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "requestIslandSnapshotResult" -> {
                    calls.add("request:" + args[1]);
                    yield CompletableFuture.completedFuture("{\"code\":\"SNAPSHOT_REQUESTED\"}");
                }
                case "restoreIslandSnapshotResult" -> {
                    calls.add("restore:" + args[1]);
                    yield CompletableFuture.completedFuture("plain-success");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        SnapshotCommandClient client = new CoreSnapshotCommandClient(raw);

        assertEquals("SNAPSHOT_REQUESTED", client.requestSnapshot(islandId, "  ").join().code());
        assertEquals("RESTORE_REQUESTED", client.restoreSnapshot(islandId, 7L).join().code());
        assertEquals(List.of("request:manual", "restore:7"), calls);
    }

    @Test
    void communicationQueryClientReturnsTypedLogEntries() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listIslandLogs" -> CompletableFuture.completedFuture("""
                    {"logs":[{"actorUuid":"actor","action":"CREATE","createdAt":"now","payload":{"target":"island","activeNode":"node-1"}}]}
                    """);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        CommunicationQueryClient client = new CoreCommunicationQueryClient(raw);

        CoreGuiViews.LogEntryView log = client.listLogs(islandId, 500).join().get(0);

        assertEquals("actor", log.actorUuid());
        assertEquals("CREATE", log.action());
        assertEquals("island", log.payload().get("target"));
        assertFalse(log.payload().containsKey("activeNode"));
    }

    @Test
    void communicationCommandClientReturnsTypedChatAction() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "sendIslandChat" -> {
                    calls.add("chat:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"CHAT_SENT\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        CommunicationCommandClient client = new CoreCommunicationCommandClient(raw);

        ChatActionView chat = client.sendChat(islandId, actorUuid, "team", " hello ").join();

        assertTrue(chat.accepted());
        assertEquals("CHAT_SENT", chat.code());
        assertEquals(List.of("chat:TEAM:hello"), calls);
    }

    @Test
    void bankQueryClientReturnsTypedBankView() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandBank" -> CompletableFuture.completedFuture("{\"balance\":\"55\",\"updatedAt\":\"now\"}");
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        BankQueryClient client = new CoreBankQueryClient(raw);

        CoreGuiViews.BankView bank = client.islandBank(islandId).join();

        assertEquals("55", bank.balance());
        assertEquals("now", bank.updatedAt());
    }

    @Test
    void bankCommandClientReturnsTypedMutationViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "depositIslandBank" -> {
                    calls.add("deposit:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"balance\":\"70\"}");
                }
                case "withdrawIslandBank" -> {
                    calls.add("withdraw:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":false,\"code\":\"NO_FUNDS\",\"balance\":\"20\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        BankCommandClient client = new CoreBankCommandClient(raw);

        BankMutationView deposit = client.deposit(islandId, actorUuid, "15").join();
        BankMutationView withdraw = client.withdraw(islandId, actorUuid, "4").join();

        assertTrue(deposit.accepted());
        assertEquals("70", deposit.balance());
        assertFalse(withdraw.accepted());
        assertEquals("NO_FUNDS", withdraw.code());
        assertEquals("20", withdraw.balance());
        assertEquals(List.of("deposit:15", "withdraw:4"), calls);
    }

    @Test
    void warehouseQueryClientReturnsTypedWarehouseItems() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandWarehouse" -> CompletableFuture.completedFuture("""
                    {"items":[{"materialKey":"STONE","amount":12},{"materialKey":"","amount":9},{"materialKey":"DIRT","amount":0}]}
                    """);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        WarehouseQueryClient client = new CoreWarehouseQueryClient(raw);

        List<WarehouseItemView> items = client.listItems(islandId, 500).join();

        assertEquals(1, items.size());
        assertEquals("STONE", items.get(0).materialKey());
        assertEquals(12L, items.get(0).amount());
    }

    @Test
    void warehouseCommandClientReturnsTypedMutationViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "depositIslandWarehouse" -> {
                    calls.add("deposit:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"materialKey\":\"STONE\",\"amount\":12}");
                }
                case "withdrawIslandWarehouse" -> {
                    calls.add("withdraw:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":false,\"code\":\"NO_STOCK\",\"materialKey\":\"DIRT\",\"amount\":7}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        WarehouseCommandClient client = new CoreWarehouseCommandClient(raw);

        WarehouseMutationView deposit = client.deposit(islandId, actorUuid, "STONE", 12L).join();
        WarehouseMutationView withdraw = client.withdraw(islandId, actorUuid, "DIRT", 7L).join();

        assertTrue(deposit.accepted());
        assertEquals("STONE", deposit.materialKey());
        assertFalse(withdraw.accepted());
        assertEquals("NO_STOCK", withdraw.code());
        assertEquals(List.of("deposit:STONE:12", "withdraw:DIRT:7"), calls);
    }

    @Test
    void homeWarpQueryClientReturnsTypedHomesWarpsAndIslandInfo() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listIslandHomes" -> CompletableFuture.completedFuture("""
                    {"homes":[{"name":"home","location":{"x":1.0,"y":2.0,"z":3.0},"createdAt":"now"}]}
                    """);
                case "listIslandWarps" -> CompletableFuture.completedFuture("""
                    {"warps":[{"islandId":"%s","name":"spawn","location":{"x":1.0,"y":2.0,"z":3.0},"publicAccess":true,"category":"default"}]}
                    """.formatted(islandId));
                case "islandInfo" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Island","state":"ACTIVE"}
                    """.formatted(islandId));
                case "listPublicWarps" -> CompletableFuture.completedFuture("""
                    {"warps":[{"islandId":"%s","name":"market","location":{"x":4.0,"y":5.0,"z":6.0},"publicAccess":true,"category":"market"}]}
                    """.formatted(islandId));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        HomeWarpQueryClient client = new CoreHomeWarpQueryClient(raw);

        assertEquals("home", client.homes(islandId).join().get(0).name());
        assertEquals("spawn", client.warps(islandId).join().get(0).name());
        assertEquals("Island", client.islandInfo(islandId).join().name());
        assertEquals("market", client.publicWarps(200, null, null).join().get(0).name());
    }

    @Test
    void homeWarpCommandClientReturnsTypedActionViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        kr.lunaf.cloudislands.api.model.IslandLocation location = new kr.lunaf.cloudislands.api.model.IslandLocation("world", 1.0d, 2.0d, 3.0d, 0.0f, 0.0f);
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "setIslandHomeResult" -> {
                    calls.add("home:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"HOME_SET\"}");
                }
                case "setIslandWarpResult" -> {
                    calls.add("warp:" + args[2] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"WARP_SET\"}");
                }
                case "deleteIslandWarpResult" -> {
                    calls.add("delete:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"WARP_DELETED\"}");
                }
                case "setIslandWarpPublicAccessResult" -> {
                    calls.add("access:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("plain-success");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        HomeWarpCommandClient client = new CoreHomeWarpCommandClient(raw);

        assertEquals("HOME_SET", client.setHome(islandId, actorUuid, "home", location).join().code());
        assertEquals("WARP_SET", client.setWarp(islandId, actorUuid, "spawn", location, true).join().code());
        assertEquals("WARP_DELETED", client.deleteWarp(islandId, actorUuid, "spawn").join().code());
        assertEquals("WARP_PUBLIC", client.setWarpPublicAccess(islandId, actorUuid, "spawn", true).join().code());
        assertEquals(List.of("home:home", "warp:spawn:true", "delete:spawn", "access:spawn:true"), calls);
    }

    @Test
    void navigationQueryClientReturnsTypedProfilesPublicIslandsAndReviews() {
        UUID islandId = UUID.randomUUID();
        UUID reviewerUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "playerInfoByName" -> {
                    calls.add("profile:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","primaryIslandId":"%s"}
                        """.formatted(reviewerUuid, islandId));
                }
                case "listPublicIslands" -> {
                    calls.add("public:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"islands":[{"islandId":"%s","ownerUuid":"%s","name":"Spawn","level":7,"worth":"1200"}]}
                        """.formatted(islandId, reviewerUuid));
                }
                case "listIslandReviews" -> {
                    calls.add("reviews:" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"reviews":[{"reviewerUuid":"%s","rating":5,"comment":"nice"}],"summary":{"count":1,"average":5.0}}
                        """.formatted(reviewerUuid));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        NavigationQueryClient client = new CoreNavigationQueryClient(raw);

        CoreGuiViews.PlayerProfileView profile = client.playerProfileByName(" Alice ").join();
        CoreGuiViews.PublicIslandView island = client.publicIslands(500).join().get(0);
        ReviewListView reviews = client.listReviews(islandId, 0).join();

        assertEquals(List.of("profile:Alice", "public:100", "reviews:1"), calls);
        assertEquals(islandId.toString(), profile.primaryIslandId());
        assertEquals("Spawn", island.name());
        assertEquals(1L, reviews.count());
        assertEquals("nice", reviews.reviews().get(0).comment());
    }

    @Test
    void navigationCommandClientReturnsTypedReviewAction() {
        UUID islandId = UUID.randomUUID();
        UUID reviewerUuid = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        RouteTicket ticket = routeTicket(reviewerUuid, islandId);
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "createVisitTicket" -> {
                    calls.add(args[1] instanceof UUID ? "visit-id" : "visit-name:" + args[1]);
                    yield CompletableFuture.completedFuture(ticket);
                }
                case "createVisitTicketForOwner" -> {
                    calls.add("visit-owner:" + args[1]);
                    yield CompletableFuture.completedFuture(ticket);
                }
                case "createRandomVisitTicket" -> {
                    calls.add("visit-random");
                    yield CompletableFuture.completedFuture(ticket);
                }
                case "setIslandReview" -> {
                    calls.add("review:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"REVIEW_SET\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        NavigationCommandClient client = new CoreNavigationCommandClient(raw);

        assertEquals(ticket, client.createVisitTicket(reviewerUuid, islandId).join());
        assertEquals(ticket, client.createVisitTicket(reviewerUuid, " spawn ").join());
        assertEquals(ticket, client.createVisitTicketForOwner(reviewerUuid, ownerUuid).join());
        assertEquals(ticket, client.createRandomVisitTicket(reviewerUuid).join());
        ReviewActionView result = client.setReview(islandId, reviewerUuid, 5, "nice").join();

        assertTrue(result.accepted());
        assertEquals("REVIEW_SET", result.code());
        assertEquals(List.of("visit-id", "visit-name:spawn", "visit-owner:" + ownerUuid, "visit-random", "review:5:nice"), calls);
    }

    @Test
    void routingCommandClientReturnsTypedRouteOperations() {
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        RouteTicket ticket = routeTicket(playerUuid, islandId);
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "createWarpTicket" -> {
                    calls.add("warp:" + args[2]);
                    yield CompletableFuture.completedFuture(ticket);
                }
                case "routeTicketStatus" -> {
                    calls.add("status:" + args[2]);
                    yield CompletableFuture.completedFuture(Optional.of(ticket));
                }
                case "publishRouteSession" -> {
                    calls.add("publish:" + ((RouteTicket) args[0]).ticketId());
                    yield CompletableFuture.completedFuture(null);
                }
                case "clearRoute" -> {
                    calls.add("clear:" + args[2]);
                    yield CompletableFuture.completedFuture("cleared");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        RoutingCommandClient client = new CoreRoutingCommandClient(raw);

        assertEquals(ticket, client.createWarpTicket(playerUuid, islandId, "spawn").join());
        assertEquals(Optional.of(ticket), client.routeTicketStatus(ticket).join());
        assertEquals(null, client.publishRouteSession(ticket).join());
        assertEquals("cleared", client.clearRoute(ticket, "").join().code());
        assertEquals(List.of("warp:spawn", "status:nonce", "publish:" + ticket.ticketId(), "clear:PLUGIN_MESSAGE_FAILED"), calls);
    }

    @Test
    void progressionQueryClientReturnsTypedReadViews() {
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandInfo" -> {
                    calls.add("info");
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"%s","name":"Base","state":"ACTIVE","level":7,"worth":"12.50"}
                        """.formatted(islandId));
                }
                case "islandBlockDetails" -> {
                    calls.add("blocks:" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"blocks":[{"materialKey":"minecraft:diamond_block","count":2,"totalWorth":"2000.00","levelPoints":20}],"summary":{"totalWorth":"2000.00","totalLevelPoints":20}}
                        """);
                }
                case "topIslandsByWorth", "topIslandsByLevel" -> {
                    calls.add(method.getName() + ":" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"rankings":[{"islandId":"%s","name":"Base","level":7,"worth":"12.50"}]}
                        """.formatted(islandId));
                }
                case "topIslandsByReviews" -> {
                    calls.add("reviews:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"rankings":[{"islandId":"%s","averageRating":4.5,"reviewCount":2}]}
                        """.formatted(islandId));
                }
                case "listIslandUpgrades" -> {
                    calls.add("upgrades");
                    yield CompletableFuture.completedFuture("""
                        {"upgrades":[{"upgradeKey":"generator:ore","type":"GENERATOR","level":3}]}
                        """);
                }
                case "listIslandMissions" -> {
                    calls.add("missions:" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"missions":[{"missionKey":"starter","title":"Starter","progress":1,"goal":2,"completed":false,"reward":"10"}]}
                        """);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        ProgressionQueryClient client = new CoreProgressionQueryClient(raw);

        assertEquals(7L, client.islandInfo(islandId).join().level());
        assertEquals("2000.00", client.blockDetails(islandId, 500).join().totalWorth());
        assertEquals("12.50", client.topWorth(500).join().get(0).worth());
        assertEquals(7L, client.topLevel(0).join().get(0).level());
        assertEquals(2L, client.topReviews(10).join().get(0).reviewCount());
        CoreGuiViews.RankingData rankings = client.rankings(2).join();
        assertEquals("level", rankings.levels().get(0).label());
        assertEquals("worth", rankings.worths().get(0).label());
        assertEquals("reviews", rankings.reviews().get(0).label());
        assertEquals("4.50", rankings.reviews().get(0).worth());
        assertEquals("generator:ore", client.upgrades(islandId).join().get(0).key());
        assertEquals("starter", client.missions(islandId, null).join().get(0).key());

        assertEquals(List.of(
            "info",
            "blocks:100",
            "topIslandsByWorth:100",
            "topIslandsByLevel:1",
            "reviews:10",
            "topIslandsByLevel:2",
            "topIslandsByWorth:2",
            "reviews:2",
            "upgrades",
            "missions:MISSION"
        ), calls);
    }

    @Test
    void progressionCommandClientReturnsTypedMutationViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "recalculateIslandLevel" -> {
                    calls.add("recalculate");
                    yield CompletableFuture.completedFuture("{\"islandId\":\"%s\",\"level\":8,\"worth\":\"14.00\"}".formatted(islandId));
                }
                case "purchaseIslandUpgrade" -> {
                    calls.add("purchase:" + args[2]);
                    yield CompletableFuture.completedFuture("""
                        {"accepted":true,"code":"UPGRADED","cost":"10.00","upgrade":{"upgradeKey":"generator:ore","level":3}}
                        """);
                }
                case "completeIslandMission" -> {
                    calls.add("mission:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("""
                        {"accepted":true,"code":"MISSION_COMPLETED","missionKey":"starter","title":"Starter","reward":"10"}
                        """);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        ProgressionCommandClient client = new CoreProgressionCommandClient(raw);

        CoreGuiViews.IslandInfoView level = client.recalculateLevel(islandId, actorUuid).join();
        ProgressionUpgradePurchaseView upgrade = client.purchaseUpgrade(islandId, actorUuid, "generator").join();
        ProgressionMissionCompletionView mission = client.completeMission(islandId, actorUuid, "starter", "CHALLENGE").join();

        assertEquals(8L, level.level());
        assertEquals("generator:ore", upgrade.upgradeKey());
        assertEquals(3L, upgrade.level());
        assertEquals("Starter", mission.title());
        assertEquals(List.of("recalculate", "purchase:generator", "mission:starter:CHALLENGE"), calls);
    }

    @Test
    void memberQueryClientReturnsTypedProfileInvitesAndBans() {
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "playerInfoByName" -> {
                    calls.add("profile:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","primaryIslandId":"%s"}
                        """.formatted(playerUuid, islandId));
                }
                case "listPendingInvites" -> {
                    calls.add("invites");
                    yield CompletableFuture.completedFuture("""
                        {"invites":[{"inviteId":"%s","islandId":"%s","inviterUuid":"%s"}]}
                        """.formatted(inviteId, islandId, playerUuid));
                }
                case "listIslandBans" -> {
                    calls.add("bans");
                    yield CompletableFuture.completedFuture("""
                        {"bans":[{"bannedUuid":"%s","actorUuid":"%s","reason":"test"}]}
                        """.formatted(playerUuid, islandId));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        MemberQueryClient client = new CoreMemberQueryClient(raw);

        assertEquals(playerUuid.toString(), client.playerProfileByName(" Alice ").join().playerUuid());
        assertEquals(inviteId.toString(), client.pendingInvites(playerUuid).join().get(0).inviteId());
        assertEquals("test", client.bans(islandId).join().get(0).reason());
        assertEquals(List.of("profile:Alice", "invites", "bans"), calls);
    }

    @Test
    void memberCommandClientReturnsTypedActionsAndInviteViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "removeIslandMemberResult" -> {
                    calls.add("remove");
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"MEMBER_REMOVED\"}");
                }
                case "createIslandInvite" -> {
                    calls.add("invite");
                    yield CompletableFuture.completedFuture("{\"inviteId\":\"%s\",\"islandId\":\"%s\",\"inviterUuid\":\"%s\"}".formatted(inviteId, islandId, actorUuid));
                }
                case "acceptIslandInviteResult" -> {
                    calls.add("accept");
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"INVITE_ACCEPTED\"}");
                }
                case "declineIslandInviteResult" -> {
                    calls.add("decline");
                    yield CompletableFuture.completedFuture("{\"accepted\":false,\"code\":\"INVITE_EXPIRED\"}");
                }
                case "setIslandMemberResult" -> {
                    calls.add("role:" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"MEMBER_ROLE_SET\"}");
                }
                case "trustIslandMemberTemporary" -> {
                    calls.add("trust:" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"TEMP_TRUST_SET\",\"expiresAt\":\"later\"}");
                }
                case "transferIslandOwnershipResult" -> {
                    calls.add("transfer");
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"OWNERSHIP_TRANSFERRED\"}");
                }
                case "banIslandVisitorResult" -> {
                    calls.add("ban:" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":false,\"code\":\"VISITOR_BAN_DENIED\"}");
                }
                case "pardonIslandVisitorResult" -> {
                    calls.add("pardon");
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"VISITOR_PARDONED\"}");
                }
                case "kickIslandVisitorResult" -> {
                    calls.add("kick");
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"VISITOR_KICKED\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        MemberCommandClient client = new CoreMemberCommandClient(raw);

        assertEquals("MEMBER_REMOVED", client.removeMember(islandId, actorUuid, targetUuid).join().code());
        assertEquals(inviteId.toString(), client.createInvite(islandId, actorUuid, targetUuid).join().inviteId());
        assertEquals("ACCEPTED", client.acceptInvite(inviteId, targetUuid).join().code());
        assertEquals("INVITE_EXPIRED", client.declineInvite(inviteId, targetUuid).join().code());
        assertEquals("MEMBER_ROLE_SET", client.setRole(islandId, actorUuid, targetUuid, "trusted").join().code());
        assertEquals("later", client.trustTemporarily(islandId, actorUuid, targetUuid, 60L).join().expiresAt());
        assertEquals("OWNERSHIP_TRANSFERRED", client.transferOwnership(islandId, actorUuid, targetUuid).join().code());
        assertFalse(client.banVisitor(islandId, actorUuid, targetUuid, "reason").join().accepted());
        assertEquals("VISITOR_PARDONED", client.pardonVisitor(islandId, actorUuid, targetUuid).join().code());
        assertEquals("VISITOR_KICKED", client.kickVisitor(islandId, actorUuid, targetUuid).join().code());
        assertEquals(List.of("remove", "invite", "accept", "decline", "role:trusted", "trust:60", "transfer", "ban:reason", "pardon", "kick"), calls);
    }

    @Test
    void adminNodeQueryClientReturnsTypedSummariesAndNodeInfo() {
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listNodes" -> {
                    calls.add("nodes");
                    yield CompletableFuture.completedFuture("{\"nodes\":[{\"nodeId\":\"node-a\"},{\"nodeId\":\"node-b\"}]}");
                }
                case "nodeInfo" -> {
                    calls.add("info:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"state":"READY","pool":"default","players":5,"softPlayerCap":50,"hardPlayerCap":80,"activeIslands":2,"maxActiveIslands":10,"activationQueue":1,"maxActivationQueue":5,"mspt":"12.5"}
                        """);
                }
                case "nodeIslands" -> {
                    calls.add("islands:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("{\"nodeId\":\"node-a\",\"count\":2}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        AdminNodeQueryClient client = new CoreAdminNodeQueryClient(raw);

        assertEquals("nodes=2", client.listNodesSummary().join().text());
        assertEquals("READY", client.nodeInfo(" node-a ").join().state());
        assertEquals("node=node-a count=2", client.nodeIslandsSummary("node-a", 500).join().text());
        assertEquals(List.of("nodes", "info:node-a", "islands:node-a:100"), calls);
    }

    @Test
    void adminNodeCommandClientReturnsTypedActions() {
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "drainNode", "undrainNode", "sweepNode" -> {
                    calls.add(method.getName() + ":" + args[0]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"nodeId\":\"node-a\",\"operation\":\"" + method.getName() + "\"}");
                }
                case "kickAllNode", "shutdownNodeSafely" -> {
                    calls.add(method.getName() + ":" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"nodeId\":\"node-a\",\"operation\":\"" + method.getName() + "\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        AdminNodeCommandClient client = new CoreAdminNodeCommandClient(raw);

        assertEquals("drainNode", client.drainNode(" node-a ").join().operation());
        assertEquals("undrainNode", client.undrainNode("node-a").join().operation());
        assertEquals("sweepNode", client.sweepNode("node-a").join().operation());
        assertEquals("kickAllNode", client.kickAllNode("node-a", "admin").join().operation());
        assertEquals("shutdownNodeSafely", client.shutdownNodeSafely("node-a", "admin").join().operation());
        assertEquals(List.of(
            "drainNode:node-a",
            "undrainNode:node-a",
            "sweepNode:node-a",
            "kickAllNode:node-a:admin",
            "shutdownNodeSafely:node-a:admin"
        ), calls);
    }

    @Test
    void playerProfileClientsReturnTypedProfiles() {
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "playerInfo" -> {
                    calls.add("info:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","lastName":"Alice","primaryIslandId":"%s","lastSeenAt":"now","locale":"ko_kr"}
                        """.formatted(args[0], islandId));
                }
                case "playerInfoByName" -> {
                    calls.add("name:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","lastName":"%s","primaryIslandId":null,"lastSeenAt":"later","locale":"en_us"}
                        """.formatted(playerUuid, args[0]));
                }
                case "touchPlayerProfile" -> {
                    calls.add("touch:" + args[1] + ":" + (args.length > 2 ? args[2] : ""));
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","lastName":"%s","primaryIslandId":"%s","lastSeenAt":"now","locale":"%s"}
                        """.formatted(args[0], args[1], islandId, args.length > 2 ? args[2] : ""));
                }
                case "setPlayerLocale" -> {
                    calls.add("locale:" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","lastName":"Alice","primaryIslandId":"%s","lastSeenAt":"now","locale":"%s"}
                        """.formatted(args[0], islandId, args[1]));
                }
                case "setPlayerIsland" -> {
                    calls.add("setIsland:" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","lastName":"Alice","primaryIslandId":"%s","lastSeenAt":"now","locale":"ko_kr"}
                        """.formatted(args[0], args[1]));
                }
                case "clearPlayerIsland" -> {
                    calls.add("clearIsland:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"playerUuid":"%s","lastName":"Alice","primaryIslandId":null,"lastSeenAt":"now","locale":"ko_kr"}
                        """.formatted(args[0]));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        PlayerProfileQueryClient queries = new CorePlayerProfileQueryClient(raw);
        PlayerProfileCommandClient commands = new CorePlayerProfileCommandClient(raw);

        assertEquals("Alice", queries.profile(playerUuid).join().lastName());
        assertEquals("en_us", queries.findByName(" Alice ").join().locale());
        assertEquals(islandId.toString(), commands.touch(playerUuid, "Alice", "fr_fr").join().primaryIslandId());
        assertEquals("de_de", commands.setLocale(playerUuid, "de_de").join().locale());
        assertEquals(islandId.toString(), commands.setPrimaryIsland(playerUuid, islandId).join().primaryIslandId());
        assertEquals("", commands.clearPrimaryIsland(playerUuid).join().primaryIslandId());
        assertEquals(List.of(
            "info:" + playerUuid,
            "name:Alice",
            "touch:Alice:fr_fr",
            "locale:de_de",
            "setIsland:" + islandId,
            "clearIsland:" + playerUuid
        ), calls);
    }

    @Test
    void jobClientsReturnTypedJobsAndActions() {
        UUID jobId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listJobs" -> CompletableFuture.completedFuture("""
                    {"jobs":[
                      {"id":"%s","type":"SAVE_ISLAND","state":"PENDING","targetNode":"node-a","attempts":2,"errorMessage":"retry soon"},
                      {"jobId":"fallback-id","type":"RESTORE_ISLAND","state":"DONE","attempts":1}
                    ]}
                    """.formatted(jobId));
                case "retryJobResult" -> {
                    calls.add("retry:" + args[0]);
                    yield CompletableFuture.completedFuture("{\"ok\":true}");
                }
                case "cancelJobResult" -> {
                    calls.add("cancel:" + args[0]);
                    yield CompletableFuture.completedFuture("{\"accepted\":false,\"code\":\"JOB_LOCKED\"}");
                }
                case "recoverJobsResult" -> {
                    calls.add("recover:" + args[0] + ":" + args[1] + ":" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"recovered\":3}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        JobQueryClient queries = new CoreJobQueryClient(raw);
        JobCommandClient commands = new CoreJobCommandClient(raw);

        List<JobView> jobs = queries.list().join();
        JobActionView retried = commands.retry(jobId).join();
        JobActionView canceled = commands.cancel(jobId).join();
        JobRecoveryView recovered = commands.recover(" node-a ", 50L, 2).join();

        assertEquals(2, jobs.size());
        assertEquals(jobId.toString(), jobs.get(0).id());
        assertEquals("SAVE_ISLAND", jobs.get(0).type());
        assertEquals("PENDING", jobs.get(0).state());
        assertEquals("node-a", jobs.get(0).targetNode());
        assertEquals(2L, jobs.get(0).attempts());
        assertEquals("retry soon", jobs.get(0).error());
        assertEquals("fallback-id", jobs.get(1).id());
        assertTrue(retried.accepted());
        assertEquals("JOB_RETRIED", retried.code());
        assertFalse(canceled.accepted());
        assertEquals("JOB_LOCKED", canceled.code());
        assertTrue(recovered.accepted());
        assertEquals("3", recovered.recovered());
        assertEquals("RECOVERED", recovered.code());
        assertEquals(List.of("retry:" + jobId, "cancel:" + jobId, "recover:node-a:50:2"), calls);
    }

    @Test
    void templateClientsReturnTypedTemplates() {
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listTemplates" -> CompletableFuture.completedFuture("""
                    {"templates":[
                      {"id":"default","displayName":"Default","enabled":true,"minNodeVersion":""},
                      {"id":"hard","displayName":"Hard","enabled":false,"minNodeVersion":"1.21.11"}
                    ]}
                    """);
                case "upsertTemplate" -> {
                    calls.add("upsert:" + args[0] + ":" + args[1] + ":" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("""
                        {"id":"%s","displayName":"%s","enabled":%s,"minNodeVersion":"%s"}
                        """.formatted(args[0], args[1], args[2], args[3]));
                }
                case "enableTemplate" -> {
                    calls.add("enable:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"id":"%s","displayName":"Enabled","enabled":true,"minNodeVersion":""}
                        """.formatted(args[0]));
                }
                case "disableTemplate" -> {
                    calls.add("disable:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"id":"%s","displayName":"Disabled","enabled":false,"minNodeVersion":""}
                        """.formatted(args[0]));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        TemplateQueryClient queries = new CoreTemplateQueryClient(raw);
        TemplateCommandClient commands = new CoreTemplateCommandClient(raw);

        List<TemplateView> templates = queries.list().join();
        TemplateView upserted = commands.upsert(" hard ", "Hard", false, "1.21.11").join();
        TemplateView enabled = commands.enable("hard").join();
        TemplateView disabled = commands.disable("hard").join();

        assertEquals(2, templates.size());
        assertEquals("default", templates.get(0).id());
        assertTrue(templates.get(0).enabled());
        assertEquals("1.21.11", templates.get(1).minNodeVersion());
        assertEquals("hard", upserted.id());
        assertFalse(upserted.enabled());
        assertEquals("hard", enabled.id());
        assertTrue(enabled.enabled());
        assertEquals("hard", disabled.id());
        assertFalse(disabled.enabled());
        assertEquals(List.of(
            "upsert:hard:Hard:false:1.21.11",
            "enable:hard",
            "disable:hard"
        ), calls);
    }

    @Test
    void lifecycleCommandClientReturnsTypedResetResult() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "createIsland" -> {
                    calls.add("create:" + args[1]);
                    yield CompletableFuture.completedFuture(new CreateIslandResult(true, "CREATED", null, null));
                }
                case "deleteIsland" -> {
                    calls.add("delete:" + args[1]);
                    yield CompletableFuture.completedFuture(new DeleteIslandResult(true, "DELETED", (UUID) args[1]));
                }
                case "resetIslandResult" -> {
                    calls.add("reset:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandLifecycleCommandClient client = new CoreIslandLifecycleCommandClient(raw);

        assertEquals("CREATED", client.createIsland(actorUuid, " ").join().code());
        assertEquals(islandId, client.deleteIsland(actorUuid, islandId).join().islandId());
        IslandLifecycleActionView result = client.resetIsland(islandId, actorUuid, " ").join();

        assertTrue(result.accepted());
        assertEquals("RESET_QUEUED", result.code());
        assertEquals(List.of("create:default", "delete:" + islandId, "reset:player-reset"), calls);
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

    private static RouteTicket routeTicket(UUID playerUuid, UUID islandId) {
        return new RouteTicket(
            UUID.randomUUID(),
            playerUuid,
            RouteAction.VISIT,
            islandId,
            "node-1",
            "world",
            RouteTicketState.READY,
            Instant.EPOCH.plusSeconds(60),
            "nonce",
            Map.of()
        );
    }
}
