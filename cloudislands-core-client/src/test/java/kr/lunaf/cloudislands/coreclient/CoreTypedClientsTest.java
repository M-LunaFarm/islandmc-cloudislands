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
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "setIslandReview" -> {
                    calls.add("review:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"REVIEW_SET\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        NavigationCommandClient client = new CoreNavigationCommandClient(raw);

        ReviewActionView result = client.setReview(islandId, reviewerUuid, 5, "nice").join();

        assertTrue(result.accepted());
        assertEquals("REVIEW_SET", result.code());
        assertEquals(List.of("review:5:nice"), calls);
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
        assertEquals("generator:ore", client.upgrades(islandId).join().get(0).key());
        assertEquals("starter", client.missions(islandId, null).join().get(0).key());

        assertEquals(List.of(
            "info",
            "blocks:100",
            "topIslandsByWorth:100",
            "topIslandsByLevel:1",
            "reviews:10",
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
    void lifecycleCommandClientReturnsTypedResetResult() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "resetIslandResult" -> {
                    calls.add("reset:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandLifecycleCommandClient client = new CoreIslandLifecycleCommandClient(raw);

        IslandLifecycleActionView result = client.resetIsland(islandId, actorUuid, " ").join();

        assertTrue(result.accepted());
        assertEquals("RESET_QUEUED", result.code());
        assertEquals(List.of("reset:player-reset"), calls);
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
