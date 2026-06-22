package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.junit.jupiter.api.Test;

class CoreTypedClientsTest {
    @Test
    void jdkCoreApiClientOverridesAllTypedDomainAccessors() throws Exception {
        List<String> defaultAccessors = new ArrayList<>();
        List<String> missingOverrides = new ArrayList<>();
        for (Method method : CoreApiClient.class.getMethods()) {
            if (method.isDefault() && method.getParameterCount() == 0) {
                defaultAccessors.add(method.getName());
                Method jdkMethod = JdkCoreApiClient.class.getMethod(method.getName());
                if (jdkMethod.getDeclaringClass() != JdkCoreApiClient.class) {
                    missingOverrides.add(method.getName());
                }
            }
        }

        assertFalse(defaultAccessors.isEmpty());
        assertEquals(List.of(), missingOverrides);
        assertSame(JdkCoreApiClient.class, JdkCoreApiClient.class.getMethod("progression").getDeclaringClass());
        assertTrue(PlayerProfileQueryClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must expose typed player profile queries directly");
        assertTrue(PlayerProfileCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must expose typed player profile commands directly");
        assertTrue(TemplateQueryClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must expose typed template queries directly");
        assertTrue(TemplateCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must expose typed template commands directly");
        assertTrue(JobCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must expose typed job commands directly");
        assertTrue(BlockValueCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must expose typed block value commands directly");
        assertSame(JdkCoreApiClient.class, JdkCoreApiClient.class.getMethod("profile", UUID.class).getDeclaringClass());
        assertSame(JdkCoreApiClient.class, JdkCoreApiClient.class.getMethod("setPrimaryIsland", UUID.class, UUID.class).getDeclaringClass());
        assertSame(JdkCoreApiClient.class, JdkCoreApiClient.class.getMethod("list").getDeclaringClass());
        assertSame(JdkCoreApiClient.class, JdkCoreApiClient.class.getMethod("upsert", String.class, String.class, boolean.class, String.class).getDeclaringClass());
        assertSame(JdkCoreApiClient.class, JdkCoreApiClient.class.getMethod("retry", UUID.class).getDeclaringClass());
        assertSame(JdkCoreApiClient.class, JdkCoreApiClient.class.getMethod("set", UUID.class, String.class, String.class, long.class, long.class).getDeclaringClass());
    }

    @Test
    void jdkCoreApiClientReusesStandaloneAdminQueryClients() {
        List<String> nestedClients = Arrays.stream(JdkCoreApiClient.class.getDeclaredClasses())
            .map(Class::getSimpleName)
            .toList();

        assertFalse(nestedClients.contains("JdkBankClient"), "bank operations must use CoreBank query and command clients");
        assertFalse(nestedClients.contains("JdkSnapshotClient"), "snapshot operations must use CoreSnapshot query and command clients");
        assertFalse(nestedClients.contains("JdkCommunicationClient"), "communication operations must use CoreCommunication query and command clients");
        assertFalse(nestedClients.contains("JdkEnvironmentClient"), "environment operations must use CoreIslandEnvironment query and command clients");
        assertFalse(nestedClients.contains("JdkSettingsClient"), "settings operations must use CoreIslandSettingsCommandClient");
        assertFalse(nestedClients.contains("JdkHomeWarpClient"), "home and warp operations must use CoreHomeWarp query and command clients");
        assertFalse(nestedClients.contains("JdkIslandClient"), "island queries must use CoreIslandQueryClient");
        assertFalse(nestedClients.contains("JdkVisitorStatsClient"), "visitor stats must use CoreIslandVisitorStatsQueryClient");
        assertFalse(nestedClients.contains("JdkPlayerProfileClient"), "player profiles must use CorePlayerProfile query and command clients");
        assertFalse(nestedClients.contains("JdkTemplateClient"), "templates must use CoreTemplate query and command clients");
        assertFalse(nestedClients.contains("JdkJobClient"), "jobs must use CoreJob query and command clients");
        assertFalse(nestedClients.contains("JdkBlockValueClient"), "block values must use CoreBlockValue query and command clients");
        assertFalse(nestedClients.contains("JdkNavigationClient"), "navigation must use CoreNavigation query and command clients");
        assertFalse(nestedClients.contains("JdkRoutingClient"), "routing must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkRuntimeClient"), "runtime operations must use JdkCoreApiClient's direct RuntimeCommandClient implementation");
        assertFalse(nestedClients.contains("JdkWarehouseClient"), "warehouse operations must use CoreWarehouse query and command clients");
        assertFalse(nestedClients.contains("JdkLifecycleClient"), "lifecycle operations must use JdkCoreApiClient's typed lifecycle implementation");
        assertFalse(nestedClients.contains("JdkProgressionClient"), "progression operations must use CoreProgression query and command clients");
        assertFalse(nestedClients.contains("JdkMemberQueryClient"), "member queries must use CoreMemberQueryClient");
        assertFalse(nestedClients.contains("JdkMemberCommandClient"), "member commands must use CoreMemberCommandClient");
        assertFalse(nestedClients.contains("JdkPermissionClient"), "permissions must use CorePermission query and command clients");
        assertFalse(nestedClients.contains("JdkAdminMetricsClient"), "admin metrics must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminCoreConfigClient"), "admin config must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminStorageClient"), "admin storage must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminEventClient"), "admin events must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminAuditClient"), "admin audit must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminRouteClient"), "admin routes must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminAddonStateClient"), "admin addon state must use CoreAdminAddonStateQueryClient");
        assertFalse(nestedClients.contains("JdkAdminMaintenanceClient"), "admin maintenance must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminNodeClient"), "admin node operations must use CoreAdminNode query and command clients");
        assertFalse(nestedClients.contains("JdkAdminIslandClient"), "admin islands must use CoreAdminIslandQueryClient");
    }

    @Test
    void coreApiClientDoesNotExposeRawAdminDiagnosticsMethods() {
        List<String> names = Arrays.stream(CoreApiClient.class.getMethods())
            .map(Method::getName)
            .toList();

        assertFalse(names.contains("metrics"));
        assertFalse(names.contains("coreConfig"));
        assertFalse(names.contains("storageStatus"));
        assertFalse(names.contains("clearCacheResult"));
        assertFalse(names.contains("reloadResult"));
        assertFalse(names.contains("listEvents"));
        assertFalse(names.contains("listEventsSince"));
        assertFalse(names.contains("listAuditLogs"));
        assertFalse(names.contains("debugRoutes"));
        assertFalse(names.contains("routeTicket"));
        assertFalse(names.contains("routeTicketForPlayer"));
        assertFalse(names.contains("publishRouteSession"));
        assertFalse(names.contains("publishRouteSessionResult"));
        assertFalse(names.contains("clearRoute"));
        assertFalse(names.contains("clearRouteResult"));
        assertFalse(names.contains("listNodes"));
        assertFalse(names.contains("nodeInfo"));
        assertFalse(names.contains("nodeIslands"));
        assertFalse(names.contains("drainNodeResult"));
        assertFalse(names.contains("undrainNodeResult"));
        assertFalse(names.contains("sweepNodeResult"));
        assertFalse(names.contains("kickAllNodeResult"));
        assertFalse(names.contains("shutdownNodeSafelyResult"));
    }

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
    void coreJsonNormalizesTypedCommandResultStatus() {
        Map<?, ?> blank = CoreJson.object("");
        Map<?, ?> ok = CoreJson.object("{\"ok\":true,\"nodeId\":\"paper-a\",\"nodes\":[\"paper-a\",\"\"]}");
        Map<?, ?> rejected = CoreJson.object("{\"accepted\":false}");
        Map<?, ?> failed = CoreJson.object("{\"error\":{\"code\":\"STALE_NODE\"}}");
        Map<?, ?> wrongCode = CoreJson.object("{\"accepted\":true,\"code\":\"STALE_NODE\"}");

        assertTrue(CoreJson.accepted(blank));
        assertEquals("FALLBACK", CoreJson.code(blank, "FALLBACK"));
        assertTrue(CoreJson.accepted(ok));
        assertTrue(CoreJson.acceptedWithCode(ok, "FALLBACK"));
        assertEquals("paper-a", CoreJson.text(ok, "nodeId"));
        assertEquals(List.of("paper-a"), CoreJson.strings(ok, "nodes"));
        assertEquals(2, CoreJson.entries("[{\"nodeId\":\"paper-a\"},{\"nodeId\":\"paper-b\"}]").size());
        assertEquals("paper-a", CoreJson.text(CoreJson.entries("{\"nodes\":[{\"nodeId\":\"paper-a\"}]}").get(0), "nodeId"));
        assertFalse(CoreJson.accepted(rejected));
        assertEquals("FAILED", CoreJson.code(rejected, "IGNORED"));
        assertFalse(CoreJson.accepted(failed));
        assertFalse(CoreJson.acceptedWithCode(wrongCode, "EXPECTED"));
        assertEquals("STALE_NODE", CoreJson.code(wrongCode, "EXPECTED", false));
    }

    @Test
    void coreAddonStateJsonReturnsTypedValuesWithoutManualStringScanning() {
        Map<String, String> values = CoreAddonStateJson.values("""
            {"addonId":"shop","values":{"plain":"one","quote":"a\\"b","slash":"a\\\\b","unicode":"\\uac12","number":12,"ignored":null}}
            """);

        assertEquals("one", values.get("plain"));
        assertEquals("a\"b", values.get("quote"));
        assertEquals("a\\b", values.get("slash"));
        assertEquals("값", values.get("unicode"));
        assertEquals("12", values.get("number"));
        assertFalse(values.containsKey("ignored"));
        assertEquals(Map.of(), CoreAddonStateJson.values(""));
        assertEquals(Map.of(), CoreAddonStateJson.values("{\"addonId\":\"shop\"}"));
    }

    @Test
    void coreAddonStateClientReturnsTypedValuesForStateEndpoints() {
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> {
                String methodName = method.getName();
                return switch (methodName) {
                    case "addonState",
                        "putAddonState",
                        "saveAddonState",
                        "tableKeyValueBulkSaveAddonState",
                        "tableBulkAddonState",
                        "tableKeyValueBulkLoadAddonState",
                        "replaceAddonTableState",
                        "removeAddonState",
                        "addonIslandState",
                        "putAddonIslandState",
                        "saveAddonIslandState",
                        "tableKeyValueBulkSaveAddonIslandState",
                        "tableBulkAddonIslandState",
                        "tableKeyValueBulkLoadAddonIslandState",
                        "replaceAddonIslandTableState",
                        "removeAddonIslandState" -> {
                        calls.add(methodName + ":" + args[0]);
                        yield CompletableFuture.completedFuture("""
                            {"values":{"method":"%s","ok":true}}
                            """.formatted(methodName));
                    }
                    case "clearAddonState", "clearAddonIslandState" -> {
                        calls.add(methodName + ":" + args[0]);
                        yield CompletableFuture.completedFuture("{\"ok\":true}");
                    }
                    case "clearAddonTableState", "clearAddonIslandTableState" -> {
                        calls.add(methodName + ":" + args[0]);
                        yield CompletableFuture.completedFuture("""
                            {"values":{"method":"%s","ok":true}}
                            """.formatted(methodName));
                    }
                    default -> throw new UnsupportedOperationException(methodName);
                };
            }
        );
        AddonStateClient client = new CoreAddonStateClient(raw);

        assertEquals("addonState", client.state(" shop ").join().get("method"));
        assertEquals("putAddonState", client.putState("shop", Map.of("enabled", "true")).join().get("method"));
        assertEquals("saveAddonState", client.saveState("shop", Map.of("enabled", "true"), Map.of("items", Map.of("one", "1"))).join().get("method"));
        assertEquals("tableKeyValueBulkSaveAddonState", client.tableKeyValueBulkSaveState("shop", Map.of(), Map.of("items", Map.of("two", "2"))).join().get("method"));
        assertEquals("tableBulkAddonState", client.tableBulkState("shop", Map.of("items", Map.of("three", "3"))).join().get("method"));
        assertEquals("tableKeyValueBulkLoadAddonState", client.tableKeyValueBulkLoadState("shop", " items ").join().get("method"));
        assertEquals("replaceAddonTableState", client.replaceTableState("shop", "items", Map.of("four", "4")).join().get("method"));
        assertEquals("removeAddonState", client.removeState("shop", "enabled").join().get("method"));
        assertEquals("clearAddonTableState", client.clearTableState("shop", "items").join().get("method"));
        client.clearState("shop").join();
        assertEquals("addonIslandState", client.islandState("shop", islandId).join().get("method"));
        assertEquals("putAddonIslandState", client.putIslandState("shop", islandId, Map.of("enabled", "true")).join().get("method"));
        assertEquals("saveAddonIslandState", client.saveIslandState("shop", islandId, Map.of("enabled", "true"), Map.of("items", Map.of("one", "1"))).join().get("method"));
        assertEquals("tableKeyValueBulkSaveAddonIslandState", client.tableKeyValueBulkSaveIslandState("shop", islandId, Map.of(), Map.of("items", Map.of("two", "2"))).join().get("method"));
        assertEquals("tableBulkAddonIslandState", client.tableBulkIslandState("shop", islandId, Map.of("items", Map.of("three", "3"))).join().get("method"));
        assertEquals("tableKeyValueBulkLoadAddonIslandState", client.tableKeyValueBulkLoadIslandState("shop", islandId, "items").join().get("method"));
        assertEquals("replaceAddonIslandTableState", client.replaceIslandTableState("shop", islandId, "items", Map.of("four", "4")).join().get("method"));
        assertEquals("removeAddonIslandState", client.removeIslandState("shop", islandId, "enabled").join().get("method"));
        assertEquals("clearAddonIslandTableState", client.clearIslandTableState("shop", islandId, "items").join().get("method"));
        client.clearIslandState("shop", islandId).join();

        assertTrue(calls.contains("addonState:shop"));
        assertTrue(calls.contains("clearAddonState:shop"));
        assertTrue(calls.contains("clearAddonIslandState:shop"));
    }

    @Test
    void islandQueryClientReturnsTypedIslandAndMemberPages() {
        UUID islandId = UUID.randomUUID();
        UUID firstMemberUuid = UUID.randomUUID();
        UUID secondMemberUuid = UUID.randomUUID();
        UUID thirdMemberUuid = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandInfo" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Spawn","state":"ACTIVE","level":12,"worth":"34.50","publicAccess":true,"locked":false,"size":300,"border":310,"ownerUuid":"owner","createdAt":"created","updatedAt":"updated"}
                    """.formatted(islandId));
                case "islandInfoByOwner" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Owned","state":"ACTIVE","level":2,"createdAt":"owner-created","updatedAt":"owner-updated"}
                    """.formatted(islandId));
                case "islandInfoByName" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Named","state":"ACTIVE","level":1}
                    """.formatted(islandId));
                case "listIslandMembers" -> CompletableFuture.completedFuture("""
                    {"members":[
                      {"playerUuid":"%s","role":"OWNER","joinedAt":"2026-06-21T10:00:00Z","playerName":"Alice"},
                      {"playerUuid":"%s","role":"MEMBER","joinedAt":"2026-06-21T10:01:00Z","playerName":"Bob"},
                      {"playerUuid":"%s","role":"TRUSTED","joinedAt":"2026-06-21T10:02:00Z","playerName":"Carol"}
                    ]}
                    """.formatted(firstMemberUuid, secondMemberUuid, thirdMemberUuid));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandQueryClient client = new CoreIslandQueryClient(raw);

        CoreGuiViews.IslandInfoView island = client.getIsland(islandId).join();
        CoreGuiViews.IslandInfoView ownedIsland = client.getIslandByOwner(UUID.randomUUID()).join();
        CoreGuiViews.IslandInfoView namedIsland = client.findIslandByName(" Named ").join();
        List<CoreGuiViews.MemberView> members = client.listMembers(islandId).join();
        IslandMemberSnapshot memberSnapshot = client.memberSnapshots(islandId).join().get(0);
        MemberPage firstPage = client.listMembers(islandId, new MemberCursor(0, 2)).join();
        MemberPage secondPage = client.listMembers(islandId, firstPage.nextCursor()).join();

        assertEquals("Spawn", island.name());
        assertEquals("created", island.createdAt());
        assertEquals("updated", island.updatedAt());
        assertEquals("Owned", ownedIsland.name());
        assertEquals("owner-created", ownedIsland.createdAt());
        assertEquals("owner-updated", ownedIsland.updatedAt());
        assertEquals("Named", namedIsland.name());
        assertEquals(12L, island.level());
        assertEquals(3, members.size());
        assertEquals(firstMemberUuid, memberSnapshot.playerUuid());
        assertEquals("OWNER", memberSnapshot.roleKey());
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
                        {"version":"v1","rules":[{"role":"BUILDER","permission":"BUILD","allowed":true}],"overrides":[{"playerUuid":"%s","permission":"BREAK","allowed":false}]}
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
        CoreGuiViews.PermissionRulesView rules = client.permissionRules(islandId).join();
        List<CoreGuiViews.RoleView> roles = client.roles(islandId).join();

        assertEquals(List.of("permissions", "permissions", "roles"), calls);
        assertEquals("BUILDER", permissions.get(0).role());
        assertEquals(playerUuid.toString(), permissions.get(1).playerUuid());
        assertFalse(permissions.get(1).allowed());
        assertEquals("v1", permissions.get(1).version());
        assertEquals("v1", rules.version());
        assertEquals("BUILD", rules.rules().get(0).permission());
        assertEquals("BUILDER", roles.get(0).role());
    }

    @Test
    void environmentQueryClientReturnsTypedEnvironmentViews() {
        UUID islandId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandBiome" -> CompletableFuture.completedFuture("""
                    {"biomeKey":"minecraft:plains","updatedBy":"%s","updatedAt":"2026-06-21T00:00:00Z"}
                    """.formatted(playerUuid));
                case "islandInfo" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","name":"Spawn","state":"ACTIVE","size":300,"border":310}
                    """.formatted(islandId));
                case "listIslandFlags" -> CompletableFuture.completedFuture("{\"flags\":{\"BORDER_COLOR\":\"blue\"}}");
                case "listIslandLimits" -> CompletableFuture.completedFuture("{\"limits\":[{\"limitKey\":\"HOPPER\",\"value\":64,\"updatedAt\":\"now\"}]}");
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandEnvironmentQueryClient client = new CoreIslandEnvironmentQueryClient(raw);

        IslandBiomeSnapshot biomeSnapshot = client.biome(islandId).join();
        IslandFlagsSnapshot flagsSnapshot = client.flags(islandId).join();
        IslandLimitSnapshot limitSnapshot = client.limits(islandId).join().get(0);
        CoreGuiViews.BiomeView biome = client.islandBiome(islandId).join();
        assertEquals(islandId, biomeSnapshot.islandId());
        assertEquals("minecraft:plains", biomeSnapshot.biomeKey());
        assertEquals(playerUuid, biomeSnapshot.updatedBy());
        assertEquals("blue", flagsSnapshot.values().get(IslandFlag.BORDER_COLOR));
        assertEquals("HOPPER", limitSnapshot.limitKey());
        assertEquals(64L, limitSnapshot.value());
        assertEquals("minecraft:plains", biome.key());
        assertEquals(playerUuid.toString(), biome.updatedBy());
        assertEquals("2026-06-21T00:00:00Z", biome.updatedAt());
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
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"LIMIT_SET\",\"islandId\":\"%s\",\"updatedBy\":\"%s\",\"updatedAt\":\"2026-06-21T00:00:00Z\",\"limitKey\":\"HOPPER\",\"value\":64}".formatted(islandId, actorUuid));
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
        assertEquals(islandId.toString(), limit.islandId());
        assertEquals(actorUuid.toString(), limit.updatedBy());
        assertEquals("2026-06-21T00:00:00Z", limit.updatedAt());
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
            new Class<?>[] { CoreApiClient.class, SnapshotQueryClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "records" -> CompletableFuture.completedFuture(List.of(new IslandSnapshotRecord(UUID.randomUUID(), islandId, 7L, "snapshots/7.tar", "manual", new UUID(0L, 0L), "abcdef1234567890", 4096L, Instant.EPOCH)));
                case "listSnapshots" -> CompletableFuture.completedFuture(List.of(new CoreGuiViews.SnapshotView(7L, "manual", 4096L, "", "abcdef1234567890", "snapshots/7.tar")));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        SnapshotQueryClient client = (SnapshotQueryClient) raw;

        IslandSnapshotRecord record = client.records(islandId, 500).join().get(0);
        CoreGuiViews.SnapshotView snapshot = client.listSnapshots(islandId, 500).join().get(0);

        assertEquals(7L, record.snapshotNo());
        assertEquals("manual", record.reason());
        assertEquals(4096L, record.sizeBytes());
        assertEquals("abcdef1234567890", record.checksum());
        assertEquals("snapshots/7.tar", record.storagePath());
        assertEquals(7L, snapshot.snapshotNo());
        assertEquals("manual", snapshot.reason());
        assertEquals(4096L, snapshot.sizeBytes());
        assertEquals("abcdef1234567890", snapshot.checksum());
        assertEquals("snapshots/7.tar", snapshot.storagePath());
    }

    @Test
    void visitorStatsClientReturnsTypedRecentVisitors() {
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandVisitorStats" -> {
                    calls.add(args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"%s","totalVisits":12,"uniqueVisitors":3,"recentVisitors":[
                          {"visitorUuid":"visitor-a","lastVisitedAt":"now"},
                          {"visitorUuid":"visitor-b","lastVisitedAt":"later"}
                        ]}
                        """.formatted(args[0]));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandVisitorStatsQueryClient client = new CoreIslandVisitorStatsQueryClient(raw);

        IslandVisitorStatsView stats = client.stats(islandId, 500).join();

        assertEquals(islandId.toString(), stats.islandId());
        assertEquals(12L, stats.totalVisits());
        assertEquals(3L, stats.uniqueVisitors());
        assertEquals("visitor-a", stats.recentVisitors().get(0).visitorUuid());
        assertEquals("later", stats.recentVisitors().get(1).lastVisitedAt());
        assertEquals(List.of(islandId + ":100"), calls);
    }

    @Test
    void adminIslandQueryClientReturnsTypedInfoAndRuntime() {
        UUID islandId = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "adminIslandInfo" -> {
                    calls.add("info:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"%s","ownerUuid":"%s","name":"Spawn","state":"ACTIVE","size":300,"level":42,"worth":"100.25","publicAccess":true}
                        """.formatted(islandId, ownerUuid));
                }
                case "islandInfoByName" -> {
                    calls.add("name:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"%s","ownerUuid":"%s","name":"Named","state":"ACTIVE"}
                        """.formatted(islandId, ownerUuid));
                }
                case "adminIslandWhere" -> {
                    calls.add("where:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"%s","state":"ACTIVE","activeNode":"paper-a","activeWorld":"island_world","cellX":12,"cellZ":-3,"leaseOwner":"paper-a","fencingToken":9}
                        """.formatted(islandId));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        AdminIslandQueryClient client = new CoreAdminIslandQueryClient(raw);

        CoreGuiViews.IslandInfoView info = client.info(islandId).join();
        CoreGuiViews.IslandInfoView named = client.infoByName(" Named ").join();
        AdminIslandRuntimeView runtime = client.runtime(islandId).join();

        assertEquals("Spawn", info.name());
        assertEquals(ownerUuid.toString(), info.ownerUuid());
        assertEquals("Named", named.name());
        assertEquals("paper-a", runtime.activeNode());
        assertTrue(runtime.hasCell());
        assertEquals(12L, runtime.cellX());
        assertEquals(-3L, runtime.cellZ());
        assertEquals(9L, runtime.fencingToken());
        assertEquals(List.of("info:" + islandId, "name:Named", "where:" + islandId), calls);
    }

    @Test
    void adminIslandRuntimePreservesNullCellAsAbsent() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "adminIslandWhere" -> CompletableFuture.completedFuture("""
                    {"islandId":"%s","state":"INACTIVE_READY","cellX":null,"cellZ":null,"fencingToken":3}
                    """.formatted(islandId));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        AdminIslandRuntimeView runtime = new CoreAdminIslandQueryClient(raw).runtime(islandId).join();

        assertFalse(runtime.hasCell());
        assertNull(runtime.activeNode());
        assertNull(runtime.activeWorld());
        assertNull(runtime.leaseOwner());
        assertNull(runtime.activatedAt());
        assertNull(runtime.lastHeartbeat());
        assertEquals(3L, runtime.fencingToken());
    }

    @Test
    void adminStorageClientReturnsTypedNodeStorageStatus() {
        AdminStorageStatusView status = JdkAdminStorageClient.status("""
            {"nodes":[
              {"id":"paper-east","storageAvailable":true,"storage":{"backend":"minio","primaryDegraded":false,"uploadSeconds":1.25,"downloadSeconds":0.75,"healthCheckFailures":1,"uploadFailures":2,"downloadFailures":3,"operationFailures":4}},
              {"nodeId":"paper-west","storageAvailable":false,"storage":{"primaryDegraded":true}}
            ]}
            """);

        assertEquals(2, status.nodes().size());
        assertEquals(1L, status.unavailableCount());
        assertEquals("paper-east", status.nodes().get(0).nodeId());
        assertEquals("minio", status.nodes().get(0).backend());
        assertEquals(1.25D, status.nodes().get(0).uploadSeconds());
        assertEquals(10L, status.nodes().get(0).totalFailures());
        assertEquals("paper-west", status.nodes().get(1).nodeId());
        assertFalse(status.nodes().get(1).storageAvailable());
        assertTrue(status.nodes().get(1).primaryDegraded());
    }

    @Test
    void adminMaintenanceClientReturnsTypedCacheAndReloadResults() {
        AdminMaintenanceResultView clear = JdkAdminMaintenanceClient.result("{\"clearedSessions\":2,\"clearedTickets\":3,\"clearedRedisKeys\":4}");
        AdminMaintenanceResultView reload = JdkAdminMaintenanceClient.result("{\"reloaded\":true,\"clearedSessions\":5,\"clearedTickets\":6,\"clearedRedisKeys\":7}");

        assertFalse(clear.reloaded());
        assertEquals(2L, clear.clearedSessions());
        assertEquals(3L, clear.clearedTickets());
        assertTrue(reload.reloaded());
        assertEquals(7L, reload.clearedRedisKeys());
    }

    @Test
    void jdkRuntimeCommandsReturnDirectTypedClient() throws Exception {
        JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:1"), "token", Duration.ofSeconds(1));

        assertSame(client, client.runtimeCommands());
    }

    @Test
    void adminAddonStateClientReturnsTypedSummary() {
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "addonStateSummary" -> CompletableFuture.completedFuture("""
                    {"stateOwnership":"core-addon-state-store","registeredAddonRequired":false,"orphanStatePolicy":"preserve","missingAddonStatePolicy":"ignored","tableKeyPrefix":"table/<name>/","maxKeysPerAddon":128,"maxValueLength":4096,"addons":[{"addonId":"shop","globalKeys":2,"islandKeys":3,"totalKeys":5}]}
                    """);
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        AdminAddonStateSummaryView summary = new CoreAdminAddonStateQueryClient(raw).summary().join();

        assertEquals("core-addon-state-store", summary.stateOwnership());
        assertFalse(summary.registeredAddonRequired());
        assertEquals(128L, summary.maxKeysPerAddon());
        assertEquals("shop", summary.addons().get(0).addonId());
        assertEquals(5L, summary.addons().get(0).totalKeys());
    }

    @Test
    void adminCoreConfigClientReturnsTypedConfigView() {
        AdminCoreConfigView config = AdminCoreConfigView.parse("""
            {"repositoryMode":"JDBC","jobQueueMode":"REDIS","eventBusMode":"REDIS","islandPortableBundle":true,"databasePoolSize":16,"addonStateBulkSaveGlobalEndpoint":"/v1/addons/state/bulk-save"}
            """);

        assertEquals("JDBC", config.text("repositoryMode"));
        assertEquals("REDIS", config.text("jobQueueMode"));
        assertTrue(config.bool("islandPortableBundle"));
        assertEquals(16L, config.number("databasePoolSize"));
        assertEquals("/v1/addons/state/bulk-save", config.text("addonStateBulkSaveGlobalEndpoint"));
        assertTrue(config.code().isBlank());
    }

    @Test
    void adminMetricsClientReturnsTypedSummary() {
        AdminMetricsSummaryView summary = AdminMetricsSummaryView.parse("""
            # HELP cloudislands_jobs_total Jobs
            # TYPE cloudislands_jobs_total counter
            cloudislands_jobs_total{state="done"} 3
            cloudislands_jobs_total{state="failed"} 1
            cloudislands_nodes 2
            """);

        assertEquals(3L, summary.samples());
        assertEquals(List.of("cloudislands_jobs_total", "cloudislands_nodes"), summary.names());
    }

    @Test
    void snapshotCommandClientReturnsTypedActionViews() {
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, SnapshotCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "recordSnapshot" -> {
                    calls.add("record:" + args[1] + ":" + args[3] + ":" + args[7]);
                    yield CompletableFuture.completedFuture(new SnapshotActionView(true, "RECORDED"));
                }
                case "requestSnapshot" -> {
                    calls.add("request:" + args[1]);
                    yield CompletableFuture.completedFuture(new SnapshotActionView(true, "SNAPSHOT_REQUESTED"));
                }
                case "restoreSnapshot" -> {
                    calls.add("restore:" + args[1]);
                    yield CompletableFuture.completedFuture(new SnapshotActionView(true, "RESTORE_REQUESTED"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        SnapshotCommandClient client = (SnapshotCommandClient) raw;

        assertEquals("RECORDED", client.recordSnapshot(islandId, 9L, "snapshots/latest.tar", "periodic", "abc", 2048L, "node-a", 123L).join().code());
        assertEquals("SNAPSHOT_REQUESTED", client.requestSnapshot(islandId, "manual").join().code());
        assertEquals("RESTORE_REQUESTED", client.restoreSnapshot(islandId, 7L).join().code());
        assertEquals(List.of("record:9:periodic:123", "request:manual", "restore:7"), calls);
    }

    @Test
    void communicationQueryClientReturnsTypedLogEntries() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, CommunicationQueryClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "records" -> CompletableFuture.completedFuture(List.of(new IslandLogRecord(UUID.randomUUID(), islandId, actorUuid, "CREATE", Map.of("target", "island"), Instant.parse("2026-01-02T03:04:05Z"))));
                case "listLogs" -> CompletableFuture.completedFuture(List.of(new CoreGuiViews.LogEntryView(actorUuid.toString(), "CREATE", Map.of("target", "island"), "2026-01-02T03:04:05Z")));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        CommunicationQueryClient client = (CommunicationQueryClient) raw;

        IslandLogRecord record = client.records(islandId, 500).join().get(0);
        CoreGuiViews.LogEntryView log = client.listLogs(islandId, 500).join().get(0);

        assertEquals(actorUuid, record.actorUuid());
        assertEquals("CREATE", record.action());
        assertEquals("island", record.payload().get("target"));
        assertFalse(record.payload().containsKey("activeNode"));
        assertEquals(actorUuid.toString(), log.actorUuid());
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
            new Class<?>[] { CoreApiClient.class, CommunicationCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "sendChat" -> {
                    calls.add("chat:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new ChatActionView(true, "CHAT_SENT", "TEAM", "hello"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        CommunicationCommandClient client = (CommunicationCommandClient) raw;

        ChatActionView chat = client.sendChat(islandId, actorUuid, "TEAM", "hello").join();

        assertTrue(chat.accepted());
        assertEquals("CHAT_SENT", chat.code());
        assertEquals("TEAM", chat.channel());
        assertEquals("hello", chat.message());
        assertEquals(List.of("chat:TEAM:hello"), calls);
    }

    @Test
    void bankQueryClientReturnsTypedBankView() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, BankQueryClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "snapshot" -> CompletableFuture.completedFuture(new IslandBankSnapshot(islandId, "55", Instant.parse("2026-01-02T03:04:05Z")));
                case "islandBank" -> CompletableFuture.completedFuture(new CoreGuiViews.BankView("55", "2026-01-02T03:04:05Z"));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        BankQueryClient client = (BankQueryClient) raw;

        IslandBankSnapshot snapshot = client.snapshot(islandId).join();
        CoreGuiViews.BankView bank = client.islandBank(islandId).join();

        assertEquals(islandId, snapshot.islandId());
        assertEquals("55", snapshot.balance());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), snapshot.updatedAt());
        assertEquals("55", bank.balance());
        assertEquals("2026-01-02T03:04:05Z", bank.updatedAt());
    }

    @Test
    void bankCommandClientReturnsTypedMutationViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, BankCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "depositSnapshot" -> {
                    calls.add("deposit:" + args[2]);
                    yield CompletableFuture.completedFuture(new IslandBankChangeSnapshot(true, "", new IslandBankSnapshot(islandId, "70", Instant.parse("2026-01-02T03:04:05Z"))));
                }
                case "deposit" -> {
                    calls.add("deposit:" + args[2]);
                    yield CompletableFuture.completedFuture(new BankMutationView(true, "", islandId.toString(), "70", "2026-01-02T03:04:05Z"));
                }
                case "withdrawSnapshot" -> {
                    calls.add("withdraw:" + args[2]);
                    yield CompletableFuture.completedFuture(new IslandBankChangeSnapshot(false, "NO_FUNDS", new IslandBankSnapshot(islandId, "20", Instant.parse("2026-01-02T04:04:05Z"))));
                }
                case "withdraw" -> {
                    calls.add("withdraw:" + args[2]);
                    yield CompletableFuture.completedFuture(new BankMutationView(false, "NO_FUNDS", islandId.toString(), "20", "2026-01-02T04:04:05Z"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        BankCommandClient client = (BankCommandClient) raw;

        IslandBankChangeSnapshot depositSnapshot = client.depositSnapshot(islandId, actorUuid, "15").join();
        BankMutationView deposit = client.deposit(islandId, actorUuid, "15").join();
        BankMutationView withdraw = client.withdraw(islandId, actorUuid, "4").join();

        assertTrue(depositSnapshot.accepted());
        assertEquals(islandId, depositSnapshot.bank().islandId());
        assertEquals("70", depositSnapshot.bank().balance());
        assertTrue(deposit.accepted());
        assertEquals(islandId.toString(), deposit.islandId());
        assertEquals("70", deposit.balance());
        assertEquals("2026-01-02T03:04:05Z", deposit.updatedAt());
        assertFalse(withdraw.accepted());
        assertEquals("NO_FUNDS", withdraw.code());
        assertEquals(islandId.toString(), withdraw.islandId());
        assertEquals("20", withdraw.balance());
        assertEquals("2026-01-02T04:04:05Z", withdraw.updatedAt());
        assertEquals(List.of("deposit:15", "deposit:15", "withdraw:4"), calls);
    }

    @Test
    void warehouseQueryClientReturnsTypedWarehouseItems() {
        UUID islandId = UUID.randomUUID();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, WarehouseQueryClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listItems" -> CompletableFuture.completedFuture(List.of(new WarehouseItemView(islandId.toString(), "STONE", 12L, "2026-06-21T15:00:00Z")));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        WarehouseQueryClient client = (WarehouseQueryClient) raw;

        List<WarehouseItemView> items = client.listItems(islandId, 500).join();

        assertEquals(1, items.size());
        assertEquals(islandId.toString(), items.get(0).islandId());
        assertEquals("STONE", items.get(0).materialKey());
        assertEquals(12L, items.get(0).amount());
        assertEquals("2026-06-21T15:00:00Z", items.get(0).updatedAt());
    }

    @Test
    void warehouseCommandClientReturnsTypedMutationViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, WarehouseCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "deposit" -> {
                    calls.add("deposit:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new WarehouseMutationView(true, "", "STONE", 12));
                }
                case "withdraw" -> {
                    calls.add("withdraw:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new WarehouseMutationView(false, "NO_STOCK", "DIRT", 7));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        WarehouseCommandClient client = (WarehouseCommandClient) raw;

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
                    {"homes":[{"islandId":"%s","name":"home","location":{"x":1.0,"y":2.0,"z":3.0},"createdBy":"00000000-0000-0000-0000-000000000001","createdAt":"now"}]}
                    """.formatted(islandId));
                case "listIslandWarps" -> CompletableFuture.completedFuture("""
                    {"warps":[{"islandId":"%s","name":"spawn","location":{"x":1.0,"y":2.0,"z":3.0},"publicAccess":true,"createdBy":"00000000-0000-0000-0000-000000000002","createdAt":"2026-01-02T03:04:05Z","category":"default"}]}
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

        IslandHomeSnapshot homeSnapshot = client.homeSnapshots(islandId).join().get(0);
        IslandWarpSnapshot warpSnapshot = client.warpSnapshots(islandId).join().get(0);
        IslandWarpSnapshot publicWarpSnapshot = client.publicWarpSnapshots(200, null, null).join().get(0);
        CoreGuiViews.HomeView home = client.homes(islandId).join().get(0);
        CoreGuiViews.WarpView warp = client.warps(islandId).join().get(0);
        assertEquals(islandId, homeSnapshot.islandId());
        assertEquals("home", homeSnapshot.name());
        assertEquals(1.0d, homeSnapshot.location().localX());
        assertEquals("spawn", warpSnapshot.name());
        assertEquals("default", warpSnapshot.category());
        assertEquals("market", publicWarpSnapshot.name());
        assertEquals(islandId.toString(), home.islandId());
        assertEquals("home", home.name());
        assertEquals("00000000-0000-0000-0000-000000000001", home.createdBy());
        assertEquals("spawn", warp.name());
        assertEquals("00000000-0000-0000-0000-000000000002", warp.createdBy());
        assertEquals("2026-01-02T03:04:05Z", warp.createdAt());
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
                case "playerProfiles" -> new PlayerProfileQueryClient() {
                    @Override
                    public CompletableFuture<PlayerProfileView> profile(UUID playerUuid) {
                        throw new UnsupportedOperationException("profile");
                    }

                    @Override
                    public CompletableFuture<PlayerProfileView> findByName(String playerName) {
                        calls.add("profile:" + playerName);
                        return CompletableFuture.completedFuture(new PlayerProfileView(reviewerUuid.toString(), playerName, islandId.toString(), "now", "ko_kr"));
                    }
                };
                case "listPlayerIslands" -> {
                    calls.add("player-islands:" + args[0]);
                    yield CompletableFuture.completedFuture("""
                        {"islands":[{"islandId":"%s","name":"Home","state":"ACTIVE","role":"OWNER","level":9,"worth":"3000"}]}
                        """.formatted(islandId));
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
                        {"reviews":[{"islandId":"%s","reviewerUuid":"%s","rating":5,"comment":"nice","createdAt":"2026-06-21T00:00:00Z","updatedAt":"2026-06-21T00:01:00Z"}],"summary":{"count":1,"average":5.0}}
                        """.formatted(islandId, reviewerUuid));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        NavigationQueryClient client = new CoreNavigationQueryClient(raw);

        CoreGuiViews.PlayerProfileView profile = client.playerProfileByName(" Alice ").join();
        CoreGuiViews.PlayerIslandView playerIsland = client.playerIslands(reviewerUuid).join().get(0);
        CoreGuiViews.PublicIslandView island = client.publicIslands(500).join().get(0);
        ReviewListView reviews = client.listReviews(islandId, 0).join();

        assertEquals(List.of("profile:Alice", "player-islands:" + reviewerUuid, "public:100", "reviews:1"), calls);
        assertEquals(islandId.toString(), profile.primaryIslandId());
        assertEquals("Home", playerIsland.name());
        assertEquals("OWNER", playerIsland.role());
        assertEquals("Spawn", island.name());
        assertEquals(1L, reviews.count());
        assertEquals(islandId.toString(), reviews.reviews().get(0).islandId());
        assertEquals("nice", reviews.reviews().get(0).comment());
        assertEquals("2026-06-21T00:01:00Z", reviews.reviews().get(0).updatedAt());
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
        assertEquals("ROUTE_SESSION_PUBLISHED", JdkRoutingClient.routePublishResult("{\"ok\":true}").code());
        assertEquals("cleared", JdkRoutingClient.routeClearResult("cleared").code());
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
                case "getIslandLevel" -> {
                    calls.add("level");
                    yield CompletableFuture.completedFuture("""
                        {"islandId":"%s","level":9,"worth":"42.50","calculatedAt":"2026-06-21T00:00:00Z"}
                        """.formatted(islandId));
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
                        {"upgrades":[{"upgradeKey":"generator","type":"GENERATOR","level":3,"generatorKey":"ore"}]}
                        """);
                }
                case "listUpgradeRules" -> {
                    calls.add("rules");
                    yield CompletableFuture.completedFuture("""
                        {"rules":[{"upgradeKey":"members","type":"MAX_MEMBERS","maxLevel":5,"baseCost":"7500","multiplier":"2"}]}
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
        LevelView level = client.level(islandId).join();
        assertEquals(9L, level.level());
        assertEquals("42.50", level.worth());
        assertEquals("2026-06-21T00:00:00Z", level.calculatedAt());
        assertEquals("2000.00", client.blockDetails(islandId, 500).join().totalWorth());
        assertEquals("12.50", client.topWorth(500).join().get(0).worth());
        assertEquals(7L, client.topLevel(0).join().get(0).level());
        assertEquals(2L, client.topReviews(10).join().get(0).reviewCount());
        CoreGuiViews.RankingData rankings = client.rankings(2).join();
        assertEquals("level", rankings.levels().get(0).label());
        assertEquals("worth", rankings.worths().get(0).label());
        assertEquals("reviews", rankings.reviews().get(0).label());
        assertEquals("4.50", rankings.reviews().get(0).worth());
        CoreGuiViews.UpgradeView upgrade = client.upgrades(islandId).join().get(0);
        assertEquals("generator", upgrade.key());
        assertEquals("ore", upgrade.generatorKey());
        assertEquals("MAX_MEMBERS", client.upgradeRules().join().get(0).type());
        assertEquals("starter", client.missions(islandId, null).join().get(0).key());

        assertEquals(List.of(
            "info",
            "level",
            "blocks:100",
            "topIslandsByWorth:100",
            "topIslandsByLevel:1",
            "reviews:10",
            "topIslandsByLevel:2",
            "topIslandsByWorth:2",
            "reviews:2",
            "upgrades",
            "rules",
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
                        {"accepted":true,"code":"UPGRADED","cost":"10.00","upgrade":{"islandId":"%s","upgradeKey":"generator:ore","type":"GENERATOR","level":3,"updatedAt":"2026-01-02T03:04:05Z"}}
                        """.formatted(islandId));
                }
                case "progressIslandMission" -> {
                    calls.add("progress:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("""
                        {"accepted":true,"code":"MISSION_PROGRESS","islandId":"%s","missionKey":"starter","kind":"CHALLENGE","title":"Starter","progress":1,"goal":2,"completed":false,"reward":"10","updatedAt":"2026-01-02T03:04:05Z"}
                        """.formatted(islandId));
                }
                case "completeIslandMission" -> {
                    calls.add("mission:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("""
                        {"accepted":true,"code":"MISSION_COMPLETED","islandId":"%s","missionKey":"starter","kind":"CHALLENGE","title":"Starter","progress":2,"goal":2,"completed":true,"reward":"10","updatedAt":"2026-01-02T03:04:05Z"}
                        """.formatted(islandId));
                }
                case "registerMissionProvider" -> {
                    calls.add("register:" + args[0] + ":" + args[1]);
                    yield CompletableFuture.completedFuture("""
                        {"missions":[{"providerId":"addon-one","missionKey":"starter","kind":"MISSION","title":"Starter","goal":3,"reward":"money:5","enabled":true,"updatedAt":"2026-01-02T03:04:05Z"}]}
                        """);
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        ProgressionCommandClient client = new CoreProgressionCommandClient(raw);
        List<MissionProviderDefinitionSnapshot> definitions = List.of(new MissionProviderDefinitionSnapshot(
            "addon-one",
            "Starter",
            "MISSION",
            "Starter \"Mission\"",
            3L,
            "money:5"
        ));

        LevelView level = client.recalculateLevel(islandId, actorUuid).join();
        ProgressionUpgradePurchaseView upgrade = client.purchaseUpgrade(islandId, actorUuid, "generator").join();
        ProgressionMissionCompletionView progress = client.progressMission(islandId, actorUuid, "starter", "CHALLENGE", 1L).join();
        ProgressionMissionCompletionView mission = client.completeMission(islandId, actorUuid, "starter", "CHALLENGE").join();
        List<MissionProviderDefinitionSnapshot> registered = client.registerMissionProvider("addon-one", definitions).join();

        assertEquals(8L, level.level());
        assertEquals(islandId.toString(), level.islandId());
        assertEquals(islandId.toString(), upgrade.islandId());
        assertEquals("generator:ore", upgrade.upgradeKey());
        assertEquals("GENERATOR", upgrade.type());
        assertEquals(3L, upgrade.level());
        assertEquals("2026-01-02T03:04:05Z", upgrade.updatedAt());
        assertEquals(1L, progress.progress());
        assertFalse(progress.completed());
        assertEquals(islandId.toString(), mission.islandId());
        assertEquals("CHALLENGE", mission.kind());
        assertEquals(2L, mission.progress());
        assertEquals(2L, mission.goal());
        assertTrue(mission.completed());
        assertEquals("Starter", mission.title());
        assertEquals("addon-one", registered.getFirst().providerId());
        assertEquals("starter", registered.getFirst().missionKey());
        assertEquals(3L, registered.getFirst().goal());
        assertEquals(List.of(
            "recalculate",
            "purchase:generator",
            "progress:starter:CHALLENGE:1",
            "mission:starter:CHALLENGE",
            "register:addon-one:[{\"missionKey\":\"starter\",\"kind\":\"MISSION\",\"title\":\"Starter \\\"Mission\\\"\",\"goal\":3,\"reward\":\"money:5\",\"enabled\":true}]"
        ), calls);
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
                case "playerProfiles" -> new PlayerProfileQueryClient() {
                    @Override
                    public CompletableFuture<PlayerProfileView> profile(UUID playerUuid) {
                        throw new UnsupportedOperationException("profile");
                    }

                    @Override
                    public CompletableFuture<PlayerProfileView> findByName(String playerName) {
                        calls.add("profile:" + playerName);
                        return CompletableFuture.completedFuture(new PlayerProfileView(playerUuid.toString(), playerName, islandId.toString(), "now", "ko_kr"));
                    }
                };
                case "listPendingInvites" -> {
                    calls.add("invites");
                    yield CompletableFuture.completedFuture("""
                        {"invites":[{"inviteId":"%s","islandId":"%s","inviterUuid":"%s","targetUuid":"%s","state":"PENDING"}]}
                        """.formatted(inviteId, islandId, playerUuid, playerUuid));
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
        IslandInviteSnapshot inviteSnapshot = client.inviteSnapshots(playerUuid).join().get(0);
        assertEquals(inviteId, inviteSnapshot.inviteId());
        assertEquals(playerUuid, inviteSnapshot.targetUuid());
        CoreGuiViews.InviteView invite = client.pendingInvites(playerUuid).join().get(0);
        assertEquals(inviteId.toString(), invite.inviteId());
        assertEquals(playerUuid.toString(), invite.targetUuid());
        assertEquals("PENDING", invite.state());
        IslandBanSnapshot banSnapshot = client.banSnapshots(islandId).join().get(0);
        assertEquals(playerUuid, banSnapshot.bannedUuid());
        assertEquals("test", client.bans(islandId).join().get(0).reason());
        assertEquals(List.of("profile:Alice", "invites", "invites", "bans", "bans"), calls);
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
        assertEquals("MEMBER_ROLE_SET", client.setRole(islandId, actorUuid, targetUuid, "co-owner").join().code());
        assertEquals("later", client.trustTemporarily(islandId, actorUuid, targetUuid, 60L).join().expiresAt());
        assertEquals("OWNERSHIP_TRANSFERRED", client.transferOwnership(islandId, actorUuid, targetUuid).join().code());
        assertFalse(client.banVisitor(islandId, actorUuid, targetUuid, "reason").join().accepted());
        assertEquals("VISITOR_PARDONED", client.pardonVisitor(islandId, actorUuid, targetUuid).join().code());
        assertEquals("VISITOR_KICKED", client.kickVisitor(islandId, actorUuid, targetUuid).join().code());
        assertEquals(List.of("remove", "invite", "accept", "decline", "role:CO_OWNER", "trust:60", "transfer", "ban:reason", "pardon", "kick"), calls);
    }

    @Test
    void adminNodeQueryClientReturnsTypedSummariesAndNodeInfo() {
        UUID islandId = UUID.randomUUID();
        String nodesBody = """
            {"nodes":[
              {"nodeId":"node-a","pool":"default","serverName":"server-a","nodeVersion":"1.0.0","state":"READY","players":5,"softPlayerCap":50,"hardPlayerCap":80,"reservedSlots":2,"activeIslands":2,"maxActiveIslands":10,"mspt":"12.5","activationQueue":1,"maxActivationQueue":5,"chunkLoadPressure":"0.25","heapUsedMb":512,"heapMaxMb":2048,"recentFailurePenalty":3,"storageAvailable":true,"supportedTemplates":"default,nether","lastHeartbeat":"2026-06-21T00:00:00Z","score":"88.5","scoreBreakdown":{"load":"1.5"},"eligibleForNewActivation":true,"allocationBlockReason":"","levelScan":{"running":true,"lastIsland":"island-a","startedAt":10},"storage":{"primaryDegraded":true,"uploadSeconds":"0.4","downloadSeconds":"0.5","healthCheckFailures":1}},
              {"nodeId":"node-b","state":"DOWN"}
            ]}
            """;
        String nodeInfoBody = """
            {"state":"READY","pool":"default","players":5,"softPlayerCap":50,"hardPlayerCap":80,"activeIslands":2,"maxActiveIslands":10,"activationQueue":1,"maxActivationQueue":5,"mspt":"12.5","lastHeartbeat":"2026-06-21T00:00:00Z"}
            """;
        String nodeIslandsBody = """
            {"nodeId":"node-a","count":2,"islands":[{"islandId":"%s","state":"ACTIVE","activeNode":"node-a","activeWorld":"world-a","cellX":1,"cellZ":2,"fencingToken":9,"activatedAt":"2026-06-21T00:00:00Z"}]}
            """.formatted(islandId);

        assertEquals("nodes=2", JdkAdminNodeQueryClient.summary(nodesBody).text());
        List<IslandNodeSnapshot> nodes = JdkAdminNodeQueryClient.nodes(nodesBody);
        assertEquals(2, nodes.size());
        assertEquals("node-a", nodes.get(0).nodeId());
        assertEquals("server-a", nodes.get(0).serverName());
        assertEquals(88.5D, nodes.get(0).score());
        assertTrue(nodes.get(0).eligibleForNewActivation());
        assertEquals(Map.of("load", 1.5D), nodes.get(0).scoreBreakdown());
        assertTrue(nodes.get(0).levelScan().running());
        assertTrue(nodes.get(0).storage().primaryDegraded());
        assertEquals("READY", JdkAdminNodeQueryClient.nodeSummary("node-a", nodeInfoBody).state());
        assertEquals("node-a", JdkAdminNodeQueryClient.node("node-a", nodeInfoBody).orElseThrow().nodeId());
        assertEquals("node=node-a count=2", JdkAdminNodeQueryClient.summary(nodeIslandsBody).text());
        List<AdminIslandRuntimeView> runtimes = JdkAdminNodeQueryClient.runtimes(nodeIslandsBody);
        assertEquals(1, runtimes.size());
        assertEquals(islandId.toString(), runtimes.get(0).islandId());
        assertEquals("world-a", runtimes.get(0).activeWorld());
    }

    @Test
    void adminNodeCommandClientReturnsTypedActions() {
        assertEquals("drainNode", JdkAdminNodeCommandClient.actionResult("{\"accepted\":true,\"nodeId\":\"node-a\",\"operation\":\"drainNode\"}").operation());
        assertEquals("undrainNode", JdkAdminNodeCommandClient.actionResult("{\"accepted\":true,\"nodeId\":\"node-a\",\"operation\":\"undrainNode\"}").operation());
        AdminNodeActionView sweep = JdkAdminNodeCommandClient.actionResult("{\"accepted\":true,\"nodeId\":\"node-a\",\"operation\":\"sweepNode\",\"nodes\":[\"node-a\",\"node-b\"],\"recoveryRequired\":2}");
        assertEquals("sweepNode", sweep.operation());
        assertEquals(List.of("node-a", "node-b"), sweep.nodes());
        assertEquals(2, sweep.recoveryRequired());
        assertEquals("kickAllNode", JdkAdminNodeCommandClient.actionResult("{\"accepted\":true,\"nodeId\":\"node-a\",\"operation\":\"kickAllNode\"}").operation());
        assertEquals("shutdownNodeSafely", JdkAdminNodeCommandClient.actionResult("{\"accepted\":true,\"nodeId\":\"node-a\",\"operation\":\"shutdownNodeSafely\"}").operation());
    }

    @Test
    void playerProfileClientsReturnTypedProfiles() {
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, PlayerProfileQueryClient.class, PlayerProfileCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "profile" -> {
                    calls.add("info:" + args[0]);
                    yield CompletableFuture.completedFuture(new PlayerProfileView(args[0].toString(), "Alice", islandId.toString(), "now", "ko_kr"));
                }
                case "findByName" -> {
                    calls.add("name:" + args[0]);
                    yield CompletableFuture.completedFuture(new PlayerProfileView(playerUuid.toString(), args[0].toString(), "", "later", "en_us"));
                }
                case "touch" -> {
                    calls.add("touch:" + args[1] + ":" + (args.length > 2 ? args[2] : ""));
                    yield CompletableFuture.completedFuture(new PlayerProfileView(
                        args[0].toString(),
                        args[1].toString(),
                        islandId.toString(),
                        "now",
                        args.length > 2 ? args[2].toString() : ""
                    ));
                }
                case "setLocale" -> {
                    calls.add("locale:" + args[1]);
                    yield CompletableFuture.completedFuture(new PlayerProfileView(
                        args[0].toString(),
                        "Alice",
                        islandId.toString(),
                        "now",
                        args[1].toString()
                    ));
                }
                case "setPrimaryIsland" -> {
                    calls.add("setIsland:" + args[1]);
                    yield CompletableFuture.completedFuture(new PlayerProfileView(
                        args[0].toString(),
                        "Alice",
                        args[1].toString(),
                        "now",
                        "ko_kr"
                    ));
                }
                case "clearPrimaryIsland" -> {
                    calls.add("clearIsland:" + args[0]);
                    yield CompletableFuture.completedFuture(new PlayerProfileView(
                        args[0].toString(),
                        "Alice",
                        "",
                        "now",
                        "ko_kr"
                    ));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        PlayerProfileQueryClient queries = (PlayerProfileQueryClient) raw;
        PlayerProfileCommandClient commands = (PlayerProfileCommandClient) raw;

        assertEquals("Alice", queries.profile(playerUuid).join().lastName());
        assertEquals("en_us", queries.findByName("Alice").join().locale());
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
        UUID islandId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, JobQueryClient.class, JobCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "list" -> CompletableFuture.completedFuture(List.of(
                    new JobView(jobId.toString(), "SAVE_ISLAND", islandId.toString(), "node-a", "PENDING", 7, 2L, "node-lock", "retry soon", Map.of("reason", "manual"), "2026-06-21T00:00:00Z", "2026-06-21T00:00:05Z"),
                    new JobView("fallback-id", "RESTORE_ISLAND", "", "", "DONE", 0, 1L, "", "", Map.of(), "", "")
                ));
                case "retry" -> {
                    calls.add("retry:" + args[0]);
                    yield CompletableFuture.completedFuture(new JobActionView(true, "JOB_RETRIED"));
                }
                case "cancel" -> {
                    calls.add("cancel:" + args[0]);
                    yield CompletableFuture.completedFuture(new JobActionView(false, "JOB_LOCKED"));
                }
                case "recover" -> {
                    calls.add("recover:" + args[0].toString().trim() + ":" + args[1] + ":" + args[2]);
                    yield CompletableFuture.completedFuture(new JobRecoveryView(true, "3", "RECOVERED"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        JobQueryClient queries = (JobQueryClient) raw;
        JobCommandClient commands = (JobCommandClient) raw;

        List<JobView> jobs = queries.list().join();
        JobActionView retried = commands.retry(jobId).join();
        JobActionView canceled = commands.cancel(jobId).join();
        JobRecoveryView recovered = commands.recover(" node-a ", 50L, 2).join();

        assertEquals(2, jobs.size());
        assertEquals(jobId.toString(), jobs.get(0).id());
        assertEquals("SAVE_ISLAND", jobs.get(0).type());
        assertEquals(islandId.toString(), jobs.get(0).islandId());
        assertEquals("PENDING", jobs.get(0).state());
        assertEquals("node-a", jobs.get(0).targetNode());
        assertEquals(7, jobs.get(0).priority());
        assertEquals(2L, jobs.get(0).attempts());
        assertEquals("node-lock", jobs.get(0).lockedBy());
        assertEquals("retry soon", jobs.get(0).error());
        assertEquals(Map.of("reason", "manual"), jobs.get(0).payload());
        assertEquals("2026-06-21T00:00:00Z", jobs.get(0).createdAt());
        assertEquals("2026-06-21T00:00:05Z", jobs.get(0).updatedAt());
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
            new Class<?>[] { CoreApiClient.class, TemplateQueryClient.class, TemplateCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "list" -> CompletableFuture.completedFuture(List.of(
                    new TemplateView("default", "Default", true, ""),
                    new TemplateView("hard", "Hard", false, "1.21.11")
                ));
                case "upsert" -> {
                    calls.add("upsert:" + args[0].toString().trim() + ":" + args[1] + ":" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new TemplateView(args[0].toString().trim(), args[1].toString(), Boolean.parseBoolean(args[2].toString()), args[3].toString()));
                }
                case "enable" -> {
                    calls.add("enable:" + args[0]);
                    yield CompletableFuture.completedFuture(new TemplateView(args[0].toString(), "Enabled", true, ""));
                }
                case "disable" -> {
                    calls.add("disable:" + args[0]);
                    yield CompletableFuture.completedFuture(new TemplateView(args[0].toString(), "Disabled", false, ""));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        TemplateQueryClient queries = (TemplateQueryClient) raw;
        TemplateCommandClient commands = (TemplateCommandClient) raw;

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
    void blockValueClientsReturnTypedValuesAndActions() {
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, BlockValueCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "listBlockValues" -> CompletableFuture.completedFuture("""
                    {"values":[
                      {"materialKey":"minecraft:diamond_block","worth":"100.50","levelPoints":20,"limit":64},
                      {"materialKey":"minecraft:emerald_block","worth":"80","levelPoints":10,"limit":32}
                    ]}
                    """);
                case "set" -> {
                    calls.add("set:" + args[0] + ":" + args[1].toString().trim() + ":" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture(new BlockValueActionView(true, "BLOCK_VALUE_SET", args[1].toString().trim()));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        BlockValueQueryClient queries = new CoreBlockValueQueryClient(raw);
        BlockValueCommandClient commands = (BlockValueCommandClient) raw;

        List<BlockValueView> values = queries.list().join();
        BlockValueActionView result = commands.set(actorUuid, " minecraft:diamond_block ", "100.50", 20L, 64L).join();

        assertEquals(2, values.size());
        assertEquals("minecraft:diamond_block", values.get(0).materialKey());
        assertEquals("100.50", values.get(0).worth());
        assertEquals(20L, values.get(0).levelPoints());
        assertEquals(64L, values.get(0).limit());
        assertTrue(result.accepted());
        assertEquals("BLOCK_VALUE_SET", result.code());
        assertEquals("minecraft:diamond_block", result.materialKey());
        assertEquals(List.of("set:" + actorUuid + ":minecraft:diamond_block:100.50:20:64"), calls);
    }

    @Test
    void adminRouteClientReturnsTypedTicketsAndClearResults() {
        UUID ticketId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        AdminRouteDebugView debug = CoreAdminRouteJson.debug("""
            {"sessions":[{"playerUuid":"%s","ticketId":"%s","targetNode":"node-a","targetServerName":"island-a","nonce":"nonce-a","expiresAt":"soon"}],
             "tickets":[{"ticketId":"%s","playerUuid":"%s","islandId":"%s","action":"HOME","state":"READY","targetNode":"node-a","targetWorld":"world-a","targetType":"home","homeName":"base","expiresAt":"soon","nonce":"nonce-a"}]}
            """.formatted(playerUuid, ticketId, ticketId, playerUuid, islandId));
        AdminRouteTicketView ticket = CoreAdminRouteJson.ticket("""
            {"ticket":{"ticketId":"%s","playerUuid":"%s","islandId":"%s","action":"HOME","state":"READY","targetNode":"node-a","targetWorld":"world-a","targetType":"home","homeName":"base","expiresAt":"soon","nonce":"nonce-a"}}
            """.formatted(ticketId, playerUuid, islandId)).orElseThrow();
        AdminRouteTicketView playerTicket = CoreAdminRouteJson.ticket("""
            {"ticketId":"%s","playerUuid":"%s","islandId":"%s","action":"VISIT","state":"PENDING","targetNode":"node-b"}
            """.formatted(ticketId, playerUuid, islandId)).orElseThrow();
        AdminRouteClearView clear = CoreAdminRouteJson.clear("{\"clearedSession\":true,\"clearedTicket\":false,\"reason\":\"MANUAL_CLEAR\"}");
        AdminRouteClearView timeoutClear = CoreAdminRouteJson.clear("{\"clearedSession\":true,\"clearedTicket\":false,\"reason\":\"ROUTE_READY_TIMEOUT\"}");

        assertEquals(1, debug.sessions().size());
        assertEquals("island-a", debug.sessions().get(0).targetServerName());
        assertEquals("nonce-a", debug.sessions().get(0).nonce());
        assertEquals(1, debug.tickets().size());
        assertEquals("READY", debug.tickets().get(0).state());
        assertEquals("world-a", debug.tickets().get(0).targetWorld());
        assertEquals("nonce-a", debug.tickets().get(0).nonce());
        assertEquals(ticketId.toString(), ticket.ticketId());
        assertEquals("HOME", ticket.action());
        assertEquals("home", ticket.targetType());
        assertEquals("world-a", ticket.targetWorld());
        assertEquals("nonce-a", ticket.nonce());
        assertEquals("base", ticket.homeName());
        assertEquals("VISIT", playerTicket.action());
        assertTrue(clear.clearedSession());
        assertFalse(clear.clearedTicket());
        assertEquals("MANUAL_CLEAR", clear.reason());
        assertEquals("ROUTE_READY_TIMEOUT", timeoutClear.reason());
    }

    @Test
    void adminEventAndAuditClientsReturnTypedEntries() {
        AdminEventStreamView stream = JdkAdminEventClient.stream("""
            {"oldestSeq":1,"latestSeq":3,"events":[
              {"seq":3,"type":"ROUTE_CLEAR","fields":{"playerUuid":"player-a","ticketId":"ticket-a","clearedSession":"true","targetNode":"node-a"},"occurredAt":"now"}
            ]}
            """);
        AdminEventStreamView since = JdkAdminEventClient.stream("""
            {"oldestSeq":1,"latestSeq":4,"events":[
              {"seq":4,"type":"ISLAND_ACTIVATED","fields":{"islandId":"island-a","nodeId":"node-b"},"occurredAt":"later"}
            ]}
            """);
        List<AdminAuditEntryView> auditEntries = JdkAdminAuditClient.entries("""
            {"audit":[
              {"id":"audit-a","actorUuid":null,"actorType":"ADMIN","action":"NODE_DRAIN","targetType":"NODE","targetId":"node-a","payload":{"reason":"maintenance"},"createdAt":"now"}
            ]}
            """);

        assertEquals(3L, stream.latestSeq());
        assertEquals("ROUTE_CLEAR", stream.events().get(0).type());
        assertEquals("ticket-a", stream.events().get(0).fields().get("ticketId"));
        assertEquals(4L, since.events().get(0).seq());
        assertEquals("node-b", since.events().get(0).fields().get("nodeId"));
        assertEquals("NODE_DRAIN", auditEntries.get(0).action());
        assertEquals("maintenance", auditEntries.get(0).payload().get("reason"));
    }

    @Test
    void lifecycleCommandClientReturnsTypedResetResult() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, IslandLifecycleCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "createIsland" -> {
                    String templateId = args[1] == null || args[1].toString().isBlank() ? "default" : args[1].toString().trim();
                    calls.add("create:" + templateId);
                    yield CompletableFuture.completedFuture(new CreateIslandResult(true, "CREATED", null, null));
                }
                case "deleteIsland" -> {
                    calls.add("delete:" + args[1]);
                    yield CompletableFuture.completedFuture(new DeleteIslandResult(true, "DELETED", (UUID) args[1]));
                }
                case "resetIsland" -> {
                    String reason = args[2] == null || args[2].toString().isBlank() ? "player-reset" : args[2].toString().trim();
                    calls.add("reset:" + reason);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "RESET_QUEUED", islandId.toString(), 0L, ""));
                }
                case "activateIsland" -> {
                    calls.add("activate:" + args[0]);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "ACTIVATING", islandId.toString(), 0L, ""));
                }
                case "deactivateIsland" -> {
                    calls.add("deactivate:" + args[0]);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "SAVING", islandId.toString(), 0L, ""));
                }
                case "migrateIsland" -> {
                    String targetNode = args[1] == null ? "" : args[1].toString().trim();
                    calls.add("migrate:" + args[0] + ":" + targetNode);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(false, "NO_READY_NODE", islandId.toString(), 0L, ""));
                }
                case "saveIsland" -> {
                    String reason = args[1] == null || args[1].toString().isBlank() ? "ADMIN_SAVE" : args[1].toString().trim();
                    calls.add("save:" + reason);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "SNAPSHOT_QUEUED", islandId.toString(), 0L, ""));
                }
                case "snapshotIsland" -> {
                    String reason = args[1] == null || args[1].toString().isBlank() ? "ADMIN_MANUAL" : args[1].toString().trim();
                    calls.add("snapshot:" + reason);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "SNAPSHOT_QUEUED", islandId.toString(), 0L, ""));
                }
                case "restoreIslandSnapshot" -> {
                    calls.add("restore:" + args[1]);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "RESTORE_QUEUED", islandId.toString(), 7L, "snapshots/7.tar"));
                }
                case "rollbackIslandSnapshot" -> {
                    calls.add("rollback:" + args[1]);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "RESTORE_QUEUED", islandId.toString(), 6L, ""));
                }
                case "quarantineIsland" -> {
                    String reason = args[1] == null || args[1].toString().isBlank() ? "admin" : args[1].toString().trim();
                    calls.add("quarantine:" + reason);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "QUARANTINED", islandId.toString(), 0L, ""));
                }
                case "repairIsland" -> {
                    String reason = args[1] == null || args[1].toString().isBlank() ? "admin" : args[1].toString().trim();
                    calls.add("repair:" + reason);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "REPAIRED", islandId.toString(), 0L, ""));
                }
                case "adminDeleteIsland" -> {
                    calls.add("adminDelete:" + args[0]);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "DELETED", islandId.toString(), 0L, ""));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandLifecycleCommandClient client = (IslandLifecycleCommandClient) raw;

        assertEquals("CREATED", client.createIsland(actorUuid, " ").join().code());
        assertEquals(islandId, client.deleteIsland(actorUuid, islandId).join().islandId());
        IslandLifecycleActionView result = client.resetIsland(islandId, actorUuid, " ").join();

        assertTrue(result.accepted());
        assertEquals("RESET_QUEUED", result.code());
        assertEquals("ACTIVATING", client.activateIsland(islandId).join().code());
        assertEquals("SAVING", client.deactivateIsland(islandId).join().code());
        assertFalse(client.migrateIsland(islandId, " node-b ").join().accepted());
        assertEquals("SNAPSHOT_QUEUED", client.saveIsland(islandId, " now ").join().code());
        assertEquals("SNAPSHOT_QUEUED", client.snapshotIsland(islandId, " ").join().code());
        IslandLifecycleActionView restored = client.restoreIslandSnapshot(islandId, 7L).join();
        assertEquals(7L, restored.snapshotNo());
        assertEquals("snapshots/7.tar", restored.storagePath());
        assertEquals(6L, client.rollbackIslandSnapshot(islandId, 6L).join().snapshotNo());
        assertEquals("QUARANTINED", client.quarantineIsland(islandId, " bad ").join().code());
        assertEquals("REPAIRED", client.repairIsland(islandId, " repair ").join().code());
        assertEquals("DELETED", client.adminDeleteIsland(islandId).join().code());
        assertEquals(List.of(
            "create:default",
            "delete:" + islandId,
            "reset:player-reset",
            "activate:" + islandId,
            "deactivate:" + islandId,
            "migrate:" + islandId + ":node-b",
            "save:now",
            "snapshot:ADMIN_MANUAL",
            "restore:7",
            "rollback:6",
            "quarantine:bad",
            "repair:repair",
            "adminDelete:" + islandId
        ), calls);
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

    private static NodeHeartbeatRequest heartbeat(String nodeId) {
        return new NodeHeartbeatRequest(
            NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION,
            nodeId,
            "island",
            nodeId,
            "test",
            NodeState.READY,
            1,
            90,
            110,
            20,
            2,
            600,
            19.5D,
            0,
            20,
            0.0D,
            128,
            512,
            0,
            true,
            "*"
        );
    }
}
