package kr.lunaf.cloudislands.coreclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    void coreGuiViewsDoesNotExposeRawJsonBodyParsersPublicly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreGuiViews.java"));

        assertFalse(source.contains("public static IslandInfoView islandInfoView(String body)"));
        assertFalse(source.contains("public static List<MemberView> memberViews(String body)"));
        assertFalse(source.contains("public static List<InviteView> inviteViews(String body)"));
        assertFalse(source.contains("public static InviteView inviteView(String body)"));
        assertFalse(source.contains("public static List<BanView> banViews(String body)"));
        assertFalse(source.contains("public static PlayerProfileView playerProfile(String body)"));
        assertFalse(source.contains("public static RoleView roleView(String body)"));
        assertFalse(source.contains("public static List<LogEntryView> logViews(String body)"));
        assertFalse(source.contains("public static NodeSummaryView nodeSummary(String nodeId, String body)"));
        assertFalse(source.contains("public static PermissionRulesView permissionRulesView(String body)"));
    }

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
        assertFalse(BankQueryClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate bank queries to a standalone client");
        assertFalse(BankCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate bank commands to a standalone client");
        assertFalse(CommunicationQueryClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate communication queries to a standalone client");
        assertFalse(CommunicationCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate communication commands to a standalone client");
        assertFalse(IslandLifecycleCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate lifecycle commands to a standalone client");
        assertFalse(PlayerProfileQueryClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate player profile queries to a standalone client");
        assertFalse(PlayerProfileCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate player profile commands to a standalone client");
        assertFalse(TemplateQueryClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate template queries to a standalone client");
        assertFalse(TemplateCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate template commands to a standalone client");
        assertFalse(RouteTicketClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate route tickets to a standalone client");
        assertFalse(JobClaimClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate job claims to a standalone client");
        assertFalse(JobCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate job commands to a standalone client");
        assertFalse(BlockValueCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate block value commands to a standalone client");
        assertFalse(WarehouseQueryClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate warehouse queries to a standalone client");
        assertFalse(WarehouseCommandClient.class.isAssignableFrom(JdkCoreApiClient.class), "JDK Core API client must delegate warehouse commands to a standalone client");
        JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:1"), "token", Duration.ofSeconds(1));
        assertSame(JdkBankQueryClient.class, client.bank().getClass());
        assertSame(JdkBankCommandClient.class, client.bankCommands().getClass());
        assertSame(JdkCommunicationQueryClient.class, client.communication().getClass());
        assertSame(JdkCommunicationCommandClient.class, client.communicationCommands().getClass());
        assertSame(JdkIslandLifecycleCommandClient.class, client.lifecycle().getClass());
        assertSame(JdkPlayerProfileQueryClient.class, client.playerProfiles().getClass());
        assertSame(JdkPlayerProfileCommandClient.class, client.playerProfileCommands().getClass());
        assertSame(JdkTemplateQueryClient.class, client.templates().getClass());
        assertSame(JdkTemplateCommandClient.class, client.templateCommands().getClass());
        assertSame(JdkCoreRouteClient.class, client.routeTickets().getClass());
        assertSame(JdkCoreJobClaimClient.class, client.jobClaims().getClass());
        assertSame(JdkJobCommandClient.class, client.jobCommands().getClass());
        assertSame(JdkBlockValueCommandClient.class, client.blockValueCommands().getClass());
        assertSame(JdkWarehouseQueryClient.class, client.warehouse().getClass());
        assertSame(JdkWarehouseCommandClient.class, client.warehouseCommands().getClass());
    }

    @Test
    void jdkCoreApiClientReusesStandaloneAdminQueryClients() {
        List<String> nestedClients = Arrays.stream(JdkCoreApiClient.class.getDeclaredClasses())
            .map(Class::getSimpleName)
            .toList();

        assertFalse(nestedClients.contains("JdkBankClient"), "bank operations must use CoreBank query and command clients");
        assertFalse(nestedClients.contains("JdkSnapshotClient"), "snapshot operations must use CoreSnapshot query and command clients");
        assertFalse(nestedClients.contains("JdkCommunicationClient"), "communication operations must use CoreCommunication query and command clients");
        assertFalse(nestedClients.contains("JdkEnvironmentClient"), "environment operations must use standalone JDK environment query and command clients");
        assertFalse(nestedClients.contains("JdkSettingsClient"), "settings operations must use a standalone JDK settings command client");
        assertFalse(nestedClients.contains("JdkHomeWarpClient"), "home and warp operations must use CoreHomeWarp query and command clients");
        assertFalse(nestedClients.contains("JdkIslandClient"), "island queries must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkVisitorStatsClient"), "visitor stats must use a standalone typed client");
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
        assertFalse(nestedClients.contains("JdkMemberClient"), "member operations must use standalone query and command clients");
        assertFalse(nestedClients.contains("JdkPermissionClient"), "permissions must use CorePermission query and command clients");
        assertFalse(nestedClients.contains("JdkAdminMetricsClient"), "admin metrics must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminCoreConfigClient"), "admin config must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminStorageClient"), "admin storage must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminEventClient"), "admin events must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminAuditClient"), "admin audit must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminRouteClient"), "admin routes must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminAddonStateClient"), "admin addon state must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminMaintenanceClient"), "admin maintenance must use a standalone typed client");
        assertFalse(nestedClients.contains("JdkAdminNodeClient"), "admin node operations must use CoreAdminNode query and command clients");
        assertFalse(nestedClients.contains("JdkAdminIslandClient"), "admin islands must use a standalone typed client");
    }

    @Test
    void jdkCoreApiClientKeepsHttpStatusSeparateFromBodyAtTransportBoundary() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));
        String transport = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreHttpTransport.java"));
        String response = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreHttpResponse.java"));
        String responseBody = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreResponseBody.java"));

        assertTrue(source.contains("private final CoreHttpTransport transport"), "JDK core client must delegate HTTP transport details");
        assertTrue(source.contains("CompletableFuture<CoreResponseBody> postBody(String path, String body)"), "JDK core client must expose typed body helpers for new domain clients");
        assertTrue(source.contains("CompletableFuture<CoreResponseBody> getBody(String path)"), "JDK core client must expose typed GET body helpers for new domain clients");
        assertFalse(source.contains("CompletableFuture<String>"), "JDK core client must not expose raw response body helpers");
        assertFalse(source.contains("thenApply(CoreResponseBody::value);"), "JDK core client must leave response unwrapping to typed domain clients");
        assertTrue(transport.contains("CompletableFuture<CoreHttpResponse> send(HttpRequest request)"), "transport boundary must return a typed HTTP response");
        assertTrue(transport.contains("CompletableFuture<CoreResponseBody> post(String path, String body)"), "transport public package boundary must not expose raw String futures");
        assertFalse(transport.contains("CompletableFuture<String>"), "transport must keep raw response bodies inside a value object");
        assertTrue(transport.contains("new CoreHttpResponse(response.statusCode(), response.body())"), "raw Java HTTP responses must be converted once");
        assertTrue(transport.contains("response.responseBody(response.successBody())"), "2xx body policy must be expressed on CoreHttpResponse then wrapped");
        assertTrue(transport.contains("response.responseBody(response.resultBody())"), "mutation result body policy must be expressed on CoreHttpResponse then wrapped");
        assertFalse(transport.contains("response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : \"\""), "transport code must not mix status policy and body fallback inline");
        assertFalse(transport.contains("response.statusCode() >= 200 && response.statusCode() < 500 ? response.body() : \"\""), "result-body policy must not be duplicated inline");
        assertTrue(response.contains("record CoreHttpResponse(int statusCode, String body)"), "HTTP status and body must stay paired before domain parsing");
        assertTrue(response.contains("CoreResponseBody responseBody(boolean accepted)"), "HTTP status policy must create a typed response envelope");
        assertTrue(responseBody.contains("record CoreResponseBody(String value, int status, boolean accepted)"), "accepted response body must keep status before domain parsing");
        assertTrue(responseBody.contains("throw error();"), "rejected response bodies must become typed Core API failures before domain parsing");
        assertFalse(responseBody.contains("SimpleJson.object(root.get(\"error\"))"), "response body parser must use shared CoreJson object helpers");
        assertFalse(responseBody.contains("SimpleJson.text(root.get("), "response body parser must use shared CoreJson text helpers");
    }

    @Test
    void coreResponseBodyPreservesHttpStatusAndTypedErrorPayload() {
        CoreResponseBody body = new CoreResponseBody("""
            {"error":{"code":"UPSTREAM_DOWN","message":"core unavailable","requestId":"req-123","details":{"nodeCount":"0","pool":"island"}}}
            """, 503, false);

        CoreApiException exception = assertThrows(CoreApiException.class, body::value);

        assertEquals("UPSTREAM_DOWN", exception.code());
        assertEquals("core unavailable", exception.getMessage());
        assertEquals(503, exception.status());
        assertEquals("req-123", exception.requestId());
        assertEquals(Map.of("nodeCount", "0", "pool", "island"), exception.details());
    }

    @Test
    void migratedDomainClientsUseTypedCoreResponseBodyHelpers() throws Exception {
        for (String clientName : List.of(
                "JdkAdminAuditClient",
                "JdkAdminCoreConfigClient",
                "JdkAdminEventClient",
                "JdkAdminAddonStateQueryClient",
                "JdkAdminIslandQueryClient",
                "JdkAdminMaintenanceClient",
                "JdkAdminMetricsClient",
                "JdkAdminNodeCommandClient",
                "JdkAdminNodeQueryClient",
                "JdkAdminRouteClient",
                "JdkAdminStorageClient",
                "JdkAddonStateClient",
                "JdkHomeWarpCommandClient",
                "JdkCoreJobClaimClient",
                "JdkCoreRouteClient",
                "JdkHomeWarpQueryClient",
                "JdkIslandQueryClient",
                "JdkIslandEnvironmentCommandClient",
                "JdkIslandEnvironmentQueryClient",
                "JdkIslandLifecycleCommandClient",
                "JdkIslandSettingsCommandClient",
                "JdkIslandVisitorStatsQueryClient",
                "JdkMemberCommandClient",
                "JdkMemberQueryClient",
                "JdkNavigationCommandClient",
                "JdkNavigationQueryClient",
                "JdkJobClient",
                "JdkJobCommandClient",
                "JdkPermissionQueryClient",
                "JdkPlayerProfileCommandClient",
                "JdkPlayerProfileQueryClient",
                "JdkProgressionCommandClient",
                "JdkProgressionQueryClient",
                "JdkMigrationCommandClient",
                "JdkPermissionCommandClient",
                "JdkRuntimeCommandClient",
                "JdkRoutingClient",
                "JdkSnapshotClient",
                "JdkTemplateCommandClient",
                "JdkTemplateQueryClient",
                "JdkWarehouseCommandClient",
                "JdkWarehouseQueryClient"
        )) {
            String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/" + clientName + ".java"));
            assertTrue(source.contains("CoreResponseBody::value"), clientName + " must unwrap typed response bodies locally");
            assertFalse(source.contains("core.post("), clientName + " must not call the raw String POST helper");
            assertFalse(source.contains("core.get("), clientName + " must not call the raw String GET helper");
            assertFalse(source.contains("core.postWithResultBody("), clientName + " must not call the raw String result-body helper");
            assertFalse(source.contains("core.deleteWithResultBody("), clientName + " must not call the raw String delete helper");
        }
    }

    @Test
    void coreClientSurfaceDoesNotExposeLegacyEnumRoleIdentity() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java"));

        assertFalse(source.contains("IslandRole"), "Core client facade must keep role identity on RoleId/String domain clients, not the legacy enum adapter");
    }

    @Test
    void jdkCoreApiClientDelegatesBankMethodsToStandaloneClients() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));

        assertFalse(source.contains("public CompletableFuture<IslandBankSnapshot> snapshot("), "bank queries must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<IslandBankChangeSnapshot> depositSnapshot("), "bank deposit must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<IslandBankChangeSnapshot> withdrawSnapshot("), "bank withdraw must not live on the core transport client");
        assertTrue(source.contains("this.bankQueryClient = new JdkBankQueryClient(this);"));
        assertTrue(source.contains("this.bankCommandClient = new JdkBankCommandClient(this);"));
    }

    @Test
    void jdkCoreApiClientDelegatesCommunicationMethodsToStandaloneClients() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));

        assertFalse(source.contains("public CompletableFuture<ChatActionView> sendChat("), "chat commands must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<List<IslandLogRecord>> records("), "log queries must not live on the core transport client");
        assertTrue(source.contains("this.communicationQueryClient = new JdkCommunicationQueryClient(this);"));
        assertTrue(source.contains("this.communicationCommandClient = new JdkCommunicationCommandClient(this);"));
    }

    @Test
    void jdkCoreApiClientDelegatesLifecycleMethodsToStandaloneClient() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));
        String api = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java"));

        assertTrue(api.contains("return lifecycle().createIsland(playerUuid, templateId);"), "CoreApiClient createIsland compatibility method must delegate through the typed lifecycle accessor");
        assertTrue(api.contains("return lifecycle().deleteIsland(requesterUuid, islandId);"), "CoreApiClient deleteIsland compatibility method must delegate through the typed lifecycle accessor");
        assertFalse(source.contains("public CompletableFuture<CreateIslandResult> createIsland("), "createIsland compatibility must stay on the CoreApiClient default adapter");
        assertFalse(source.contains("public CompletableFuture<DeleteIslandResult> deleteIsland("), "deleteIsland compatibility must stay on the CoreApiClient default adapter");
        assertFalse(source.contains("public CompletableFuture<IslandLifecycleActionView> resetIsland("), "lifecycle reset must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<IslandLifecycleActionView> saveIsland("), "lifecycle save must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<IslandLifecycleActionView> activateIsland("), "admin lifecycle commands must not live on the core transport client");
        assertFalse(source.contains("lifecycleAction("), "lifecycle response parsing must live in the lifecycle command client");
        assertFalse(source.contains("parseCreateIslandResult("), "create response parsing must live in the lifecycle command client");
        assertTrue(source.contains("this.lifecycleCommandClient = new JdkIslandLifecycleCommandClient(this);"));
    }

    @Test
    void jdkCoreApiClientDelegatesRouteCompatibilityMethodsToStandaloneClient() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));
        String api = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java"));
        String routeClient = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreRouteClient.java"));
        String routeJson = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreRouteJson.java"));

        assertTrue(source.contains("this.routeCoreClient = new JdkCoreRouteClient(this);"));
        assertTrue(source.contains("public RouteTicketClient routeTickets()"));
        assertTrue(api.contains("return routeTickets().createHomeTicket(playerUuid, homeName);"));
        assertTrue(api.contains("return routeTickets().consumeRouteSession(playerUuid, nodeId, ticketId, nonce, reportMissing);"));
        assertTrue(api.contains("return routeTickets().adminIslandTeleport(playerUuid, islandId);"));
        assertFalse(source.contains("public CompletableFuture<RouteTicket> createHomeTicket("), "route compatibility must stay on the CoreApiClient default adapter");
        assertFalse(source.contains("public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession("), "route session compatibility must stay on the CoreApiClient default adapter");
        assertFalse(source.contains("parseRouteTicketResult("), "route result parsing must not live on the core transport client");
        assertFalse(source.contains("private static final class RouteSessionJson"), "route session parsing must not live on the core transport client");
        assertFalse(source.contains("static final class RouteTicketJson"), "route ticket parsing must not live on the core transport client");
        assertTrue(routeClient.contains("thenApply(CoreRouteJson::routeTicketResult)"));
        assertTrue(routeClient.contains("Optional.of(CoreRouteJson.routeSession(body))"));
        assertTrue(routeJson.contains("static RouteTicket nestedRouteTicket("));
        assertFalse(routeJson.contains("SimpleJson.object(root.get("), "route parser must use shared CoreJson object helpers");
        assertFalse(routeJson.contains("SimpleJson.text(root.get("), "route parser must use shared CoreJson text helpers");
    }

    @Test
    void jdkCoreApiClientDelegatesWarehouseMethodsToStandaloneClients() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));

        assertFalse(source.contains("public CompletableFuture<List<WarehouseItemView>> listItems("), "warehouse queries must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<WarehouseMutationView> deposit("), "warehouse deposit must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<WarehouseMutationView> withdraw("), "warehouse withdraw must not live on the core transport client");
        assertFalse(source.contains("warehouseItems("), "warehouse response parsing must live in the warehouse query client");
        assertFalse(source.contains("warehouseMutation("), "warehouse mutation parsing must live in the warehouse command client");
        assertTrue(source.contains("this.warehouseQueryClient = new JdkWarehouseQueryClient(this);"));
        assertTrue(source.contains("this.warehouseCommandClient = new JdkWarehouseCommandClient(this);"));
    }

    @Test
    void jdkCoreApiClientDelegatesPlayerProfileMethodsToStandaloneClients() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));

        assertFalse(source.contains("public CompletableFuture<PlayerProfileView> profile("), "player profile queries must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<PlayerProfileView> findByName("), "player profile lookup must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<PlayerProfileView> touch("), "player profile touch must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<PlayerProfileView> setLocale("), "player profile locale mutation must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<PlayerProfileView> setPrimaryIsland("), "player profile primary-island mutation must not live on the core transport client");
        assertTrue(source.contains("this.playerProfileQueryClient = new JdkPlayerProfileQueryClient(this);"));
        assertTrue(source.contains("this.playerProfileCommandClient = new JdkPlayerProfileCommandClient(this);"));
    }

    @Test
    void jdkCoreApiClientDelegatesTemplateMethodsToStandaloneClients() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));

        assertFalse(source.contains("public CompletableFuture<List<TemplateView>> list("), "template queries must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<TemplateView> upsert("), "template upsert must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<TemplateView> enable("), "template enable must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<TemplateView> disable("), "template disable must not live on the core transport client");
        assertFalse(source.contains("requireTemplateId("), "template validation must live in the template command client");
        assertTrue(source.contains("this.templateQueryClient = new JdkTemplateQueryClient(this);"));
        assertTrue(source.contains("this.templateCommandClient = new JdkTemplateCommandClient(this);"));
    }

    @Test
    void jdkCoreApiClientDelegatesBlockValueCommandsToStandaloneClient() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));

        assertFalse(source.contains("public CompletableFuture<BlockValueActionView> set("), "block value commands must not live on the core transport client");
        assertFalse(source.contains("requireMaterialKey("), "block value input validation must live in the block value command client");
        assertTrue(source.contains("this.blockValueCommandClient = new JdkBlockValueCommandClient(this);"));
    }

    @Test
    void jdkCoreApiClientDelegatesJobCommandsToStandaloneClient() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));
        String api = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreApiClient.java"));
        String jobClaimClient = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreJobClaimClient.java"));

        assertTrue(api.contains("return jobClaims().claimJobs(nodeId, supportedTypes, maxJobs);"), "runtime job claim compatibility must delegate through the typed job claim accessor");
        assertTrue(source.contains("this.jobClaimClient = new JdkCoreJobClaimClient(this);"));
        assertTrue(source.contains("public JobClaimClient jobClaims()"));
        assertFalse(source.contains("public CompletableFuture<List<IslandJob>> claimJobs("), "job claim compatibility must stay on the CoreApiClient default adapter");
        assertFalse(source.contains("public CompletableFuture<JobActionView> retry("), "job retry must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<JobActionView> cancel("), "job cancel must not live on the core transport client");
        assertFalse(source.contains("public CompletableFuture<JobRecoveryView> recover("), "job recovery must not live on the core transport client");
        assertFalse(source.contains("IslandJobJson::readArray"), "job claim response parsing must not live on the core transport client");
        assertTrue(jobClaimClient.contains("thenApply(IslandJobJson::readArray)"));
        assertFalse(source.contains("requireJobNode("), "job command validation must live in the job command client");
        assertTrue(source.contains("this.jobCommandClient = new JdkJobCommandClient(this);"));
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
        assertFalse(names.contains("adminIslandInfo"));
        assertFalse(names.contains("adminIslandWhere"));
        assertFalse(names.contains("listBlockValues"));
        assertFalse(names.contains("islandVisitorStats"));
        assertFalse(names.contains("addonStateSummary"));
        assertFalse(names.contains("islandInfo"));
        assertFalse(names.contains("islandInfoByOwner"));
        assertFalse(names.contains("islandInfoByName"));
        assertFalse(names.contains("getIsland"));
        assertFalse(names.contains("getIslandByOwner"));
        assertFalse(names.contains("getIslandMembers"));
        assertFalse(names.contains("listIslandMembers"));
    }

    @Test
    void coreJsonPayloadEscapesRouteRequestFieldsOutsideJdkCoreApiClient() throws Exception {
        String coreClient = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCoreApiClient.java"));
        String payload = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreJsonPayload.java"));

        String body = CoreJsonPayload.object(
            "nodeId", "paper\"east",
            "reportMissing", true,
            "retry", 2
        );

        assertEquals("{\"nodeId\":\"paper\\\"east\",\"reportMissing\":true,\"retry\":2}", body);
        assertFalse(coreClient.contains("static String jsonObject("), "JDK core client must not own JSON payload builders");
        assertFalse(coreClient.contains("private static String escape("), "JDK core client must not own JSON escaping");
        assertFalse(payload.contains("RawJson"), "Core payloads must not splice pre-rendered JSON strings into request bodies");
        assertFalse(payload.contains("static Object raw("), "Core payloads must pass structured values into the serializer");
        assertFalse(payload.contains("new StringBuilder"), "Core payloads must not assemble JSON with ad hoc string builders");
        assertFalse(payload.contains("append('\\\"')"), "Core payloads must not hand-write quoted JSON fields");
        assertTrue(payload.contains("SimpleJson.stringify(values)"), "Core payloads must serialize structured objects through the shared JSON writer");
        assertTrue(payload.contains("static String object(Object... fields)"), "JSON payload builder must live in CoreJsonPayload");
    }

    @Test
    void coreJsonPayloadRejectsInvalidFieldNamesAndSerializesStructuredValues() {
        String body = CoreJsonPayload.object(
            "nodeId", "paper\nwest",
            "payload", Map.of("quote", "a\"b"),
            "supportedTypes", List.of("SAVE_ISLAND", "RESTORE_ISLAND"),
            "reportMissing", false,
            "retry", 3
        );
        Map<?, ?> root = CoreJson.object(body);

        assertEquals("paper\nwest", CoreJson.text(root, "nodeId"));
        assertEquals("a\"b", CoreJson.text(kr.lunaf.cloudislands.common.json.SimpleJson.object(root.get("payload")), "quote"));
        assertEquals(List.of("SAVE_ISLAND", "RESTORE_ISLAND"), CoreJson.strings(root, "supportedTypes"));
        assertFalse((Boolean) root.get("reportMissing"));
        assertEquals(3L, ((Number) root.get("retry")).longValue());
        assertThrows(IllegalArgumentException.class, () -> CoreJsonPayload.object(null, "value"));
        assertThrows(IllegalArgumentException.class, () -> CoreJsonPayload.object(" ", "value"));
        assertThrows(IllegalArgumentException.class, () -> CoreJsonPayload.object("nodeId"));
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
        assertEquals("paper-b", CoreJson.text(CoreJson.entries("{\"metadata\":[{\"nodeId\":\"wrong\"}],\"nodes\":[{\"nodeId\":\"paper-b\"}]}", "nodes").get(0), "nodeId"));
        assertTrue(CoreJson.entries("{\"metadata\":[{\"nodeId\":\"wrong\"}],\"nodes\":[{\"nodeId\":\"paper-b\"}]}", "members").isEmpty());
        assertEquals("paper-c", CoreJson.text(CoreJson.entries("[{\"nodeId\":\"paper-c\"}]", "nodes").get(0), "nodeId"));
        assertFalse(CoreJson.accepted(rejected));
        assertEquals("FAILED", CoreJson.code(rejected, "IGNORED"));
        assertFalse(CoreJson.accepted(failed));
        assertFalse(CoreJson.acceptedWithCode(wrongCode, "EXPECTED"));
        assertEquals("STALE_NODE", CoreJson.code(wrongCode, "EXPECTED", false));
    }

    @Test
    void coreJsonRejectsInvalidNumericResponseValues() {
        Map<?, ?> valid = CoreJson.object("{\"count\":\"12\"}");

        assertEquals(12L, CoreJson.number(valid, "count"));
        assertEquals(0L, CoreJson.number(valid, "missing"));
        assertEquals("INVALID_CORE_JSON", assertThrows(CoreApiException.class, () ->
            CoreJson.number(CoreJson.object("{\"count\":\"x\"}"), "count")
        ).code());
        assertEquals("INVALID_CORE_JSON", assertThrows(CoreApiException.class, () ->
            CoreJson.number(CoreJson.object("{\"count\":1.5}"), "count")
        ).code());
        assertEquals("INVALID_CORE_JSON", assertThrows(CoreApiException.class, () ->
            CoreJson.number(CoreJson.object("{\"count\":9223372036854775808}"), "count")
        ).code());
    }

    @Test
    void coreJsonRejectsNonJsonCoreApiBodiesWithTypedError() {
        CoreApiException exception = assertThrows(CoreApiException.class, () -> CoreJson.object("upstream unavailable"));

        assertEquals("INVALID_CORE_JSON", exception.code());
        assertTrue(exception.getMessage().contains("JSON"));
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
    void rawMigrationAndAddonStateJsonAdaptersStayInsideCoreClientPackage() throws Exception {
        String migration = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreMigrationJson.java"));
        String addonState = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreAddonStateJson.java"));

        assertFalse(migration.contains("public final class CoreMigrationJson"));
        assertFalse(migration.contains("public static MigrationRunSnapshot run(String body)"));
        assertFalse(migration.contains("public static String toJson(MigrationRunSnapshot snapshot)"));
        assertFalse(migration.contains("private static boolean bool("), "migration parser must use shared CoreJson boolean helpers");
        assertFalse(addonState.contains("public final class CoreAddonStateJson"));
        assertFalse(addonState.contains("public static Map<String, String> values(String body)"));
        assertFalse(addonState.contains("SimpleJson.object(root.get(\"values\"))"), "addon state parser must use shared CoreJson object helpers");
    }

    @Test
    void coreApiClientDoesNotExposeRawAddonStateMethods() {
        List<String> names = Arrays.stream(CoreApiClient.class.getMethods())
            .map(Method::getName)
            .toList();

        List.of(
            "addonState",
            "putAddonState",
            "saveAddonState",
            "tableKeyValueBulkSaveAddonState",
            "tableBulkAddonState",
            "tableKeyValueBulkLoadAddonState",
            "replaceAddonTableState",
            "removeAddonState",
            "clearAddonState",
            "addonIslandState",
            "putAddonIslandState",
            "saveAddonIslandState",
            "tableKeyValueBulkSaveAddonIslandState",
            "tableBulkAddonIslandState",
            "tableKeyValueBulkLoadAddonIslandState",
            "replaceAddonIslandTableState",
            "removeAddonIslandState",
            "clearAddonIslandState"
        ).forEach(name -> assertFalse(names.contains(name), name));
    }

    @Test
    void islandQueryClientReturnsTypedIslandAndMemberPages() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        UUID firstMemberUuid = UUID.randomUUID();
        UUID secondMemberUuid = UUID.randomUUID();
        UUID thirdMemberUuid = UUID.randomUUID();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/info", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String body = request.contains("ownerUuid")
                    ? """
                        {"islandId":"%s","name":"Owned","state":"ACTIVE","level":2,"createdAt":"owner-created","updatedAt":"owner-updated"}
                        """.formatted(islandId)
                    : request.contains("name")
                        ? """
                            {"islandId":"%s","name":"Named","state":"ACTIVE","level":1}
                            """.formatted(islandId)
                        : """
                            {"islandId":"%s","name":"Spawn","state":"ACTIVE","level":12,"worth":"34.50","publicAccess":true,"locked":false,"size":300,"border":310,"ownerUuid":"owner","createdAt":"created","updatedAt":"updated"}
                            """.formatted(islandId);
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/" + islandId + "/members", exchange -> {
                byte[] response = """
                    {"members":[
                      {"playerUuid":"%s","role":"OWNER","joinedAt":"2026-06-21T10:00:00Z","playerName":"Alice"},
                      {"playerUuid":"%s","role":"MEMBER","joinedAt":"2026-06-21T10:01:00Z","playerName":"Bob"},
                      {"playerUuid":"%s","role":"TRUSTED","joinedAt":"2026-06-21T10:02:00Z","playerName":"Carol"}
                    ]}
                    """.formatted(firstMemberUuid, secondMemberUuid, thirdMemberUuid).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            IslandQueryClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).islands();

            CoreGuiViews.IslandInfoView island = client.getIsland(islandId).join();
            CoreGuiViews.IslandInfoView ownedIsland = client.getIslandByOwner(ownerUuid).join();
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
        } finally {
            server.stop(0);
        }
    }

    @Test
    void permissionCommandClientCarriesTypedVersionAcrossBatch() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> expectedVersions = new ArrayList<>();
        int[] counter = {0};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/permissions/set", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                expectedVersions.add(request.contains("\"expectedVersion\":\"v2\"") ? "v2" : "v1");
                counter[0]++;
                String response = counter[0] == 1
                    ? """
                        {"version":"v2","rules":[{"role":"BUILDER","permission":"BUILD","allowed":true}]}
                        """
                    : """
                        {"version":"v3","rules":[{"role":"BUILDER","permission":"OPEN_CONTAINER","allowed":false}]}
                        """;
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            PermissionCommandClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).permissions();

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
        } finally {
            server.stop(0);
        }
    }

    @Test
    void permissionCommandClientBatchHelperDoesNotExposeRawBodyFuture() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkPermissionCommandClient.java"));

        assertFalse(source.contains("private CompletableFuture<String> setPermissionResult"), "permission batch helper must not expose raw response bodies");
        assertTrue(source.contains("CompletableFuture<MutationResult<PermissionMatrixView>> setPermissionMutation"), "permission batch helper must return typed mutation results");
        assertTrue(source.contains(".thenApply(JdkPermissionCommandClient::mutationResult)"), "permission response parsing must stay inside the typed mutation helper");
        assertTrue(source.contains("private static String setPermissionPayload"), "request JSON construction must be isolated from response typing");
    }

    @Test
    void permissionCommandClientReturnsTypedActionViews() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/permissions/set", exchange -> respondMemberTest(exchange, calls, "set", "{\"accepted\":true,\"code\":\"PERMISSION_SET\"}"));
            server.createContext("/v1/islands/permissions/overrides/set", exchange -> respondMemberTest(exchange, calls, "override", "plain-success"));
            server.start();
            PermissionCommandClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).permissions();

            assertEquals("PERMISSION_SET", client.setPermission(islandId, actorUuid, "builder", IslandPermission.BUILD, true).join().code());
            assertEquals("PERMISSION_OVERRIDE_SET", client.setPermissionOverride(islandId, actorUuid, targetUuid, IslandPermission.BREAK, false).join().code());
            assertEquals(List.of(
                "set:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"BUILDER\",\"roleKey\":\"BUILDER\",\"permission\":\"BUILD\",\"allowed\":true}",
                "override:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + targetUuid + "\",\"permission\":\"BREAK\",\"allowed\":false}"
            ), calls);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void permissionQueryClientReturnsTypedAssignmentsAndRoles() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkPermissionQueryClient.java")));
        String rulesSource = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CorePermissionJson.java")));
        List<String> calls = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/" + islandId + "/permissions", exchange -> {
                calls.add("permissions");
                byte[] response = """
                    {"version":"v1","rules":[{"role":"BUILDER","permission":"BUILD","allowed":true}],"overrides":[{"playerUuid":"%s","permission":"BREAK","allowed":false}]}
                    """.formatted(playerUuid).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/" + islandId + "/roles", exchange -> {
                calls.add("roles");
                byte[] response = """
                    {"roles":[{"role":"BUILDER","weight":50,"displayName":"Builder"}]}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            PermissionQueryClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).permissionQueries();

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
            assertFalse(source.contains("private static String text("), "permission query parser must use shared CoreJson text helpers");
            assertFalse(source.contains("private static boolean bool("), "permission query parser must use shared CoreJson boolean helpers");
            assertFalse(rulesSource.contains("private static String text("), "permission rules parser must use shared CoreJson text helpers");
            assertFalse(rulesSource.contains("private static boolean bool("), "permission rules parser must use shared CoreJson boolean helpers");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void environmentQueryClientReturnsTypedEnvironmentViews() {
        UUID islandId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreEnvironmentJson.java")));
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, IslandQueryClient.class, IslandEnvironmentQueryClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "biome" -> CompletableFuture.completedFuture(CoreEnvironmentJson.biome(islandId, """
                    {"biomeKey":"minecraft:plains","updatedBy":"%s","updatedAt":"2026-06-21T00:00:00Z"}
                    """.formatted(playerUuid)));
                case "getIsland" -> CompletableFuture.completedFuture(CoreIslandJson.info("""
                    {"islandId":"%s","name":"Spawn","state":"ACTIVE","size":300,"border":310}
                    """.formatted(islandId)));
                case "flags" -> CompletableFuture.completedFuture(CoreEnvironmentJson.flags(islandId, "{\"flags\":{\"BORDER_COLOR\":\"blue\"}}"));
                case "limits" -> CompletableFuture.completedFuture(CoreEnvironmentJson.limits(islandId, "{\"limits\":[{\"limitKey\":\"HOPPER\",\"value\":64,\"updatedAt\":\"now\"}]}"));
                case "islandBiome" -> CompletableFuture.completedFuture(new CoreGuiViews.BiomeView("minecraft:plains", playerUuid.toString(), "2026-06-21T00:00:00Z"));
                case "flagValues" -> CompletableFuture.completedFuture(Map.of(IslandFlag.BORDER_COLOR, "blue"));
                case "limitViews" -> CompletableFuture.completedFuture(List.of(new CoreGuiViews.LimitView("HOPPER", 64L, "")));
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandEnvironmentQueryClient client = (IslandEnvironmentQueryClient) raw;

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
        assertFalse(source.contains("SimpleJson.object(root.get("), "environment parser must use shared CoreJson nested object helpers");
        assertFalse(source.contains("SimpleJson.text(entry.get("), "environment parser must use shared CoreJson text helpers");
    }

    @Test
    void environmentCommandClientReturnsTypedActionViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, IslandEnvironmentCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "setBiome" -> {
                    calls.add("biome:" + args[2]);
                    yield CompletableFuture.completedFuture(new EnvironmentActionView(true, "BIOME_SET", "PLAINS", 0L));
                }
                case "setFlag" -> {
                    calls.add("flag:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new EnvironmentActionView(true, "FLAG_SET", "BORDER_VISIBLE", 0L));
                }
                case "setLimit" -> {
                    calls.add("limit:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new EnvironmentActionView(true, "LIMIT_SET", "HOPPER", 64L, islandId.toString(), actorUuid.toString(), "2026-06-21T00:00:00Z"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandEnvironmentCommandClient client = (IslandEnvironmentCommandClient) raw;

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
            new Class<?>[] { CoreApiClient.class, IslandSettingsCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "setPublicAccess" -> {
                    calls.add("public:" + args[2]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "PUBLIC_ACCESS_ENABLED"));
                }
                case "setLocked" -> {
                    calls.add("locked:" + args[2]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "ISLAND_UNLOCKED"));
                }
                case "setName" -> {
                    calls.add("name:" + args[2]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "ISLAND_RENAMED"));
                }
                case "setFlag" -> {
                    calls.add("flag:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new SettingsActionView(true, "FLAG_SET"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        IslandSettingsCommandClient client = (IslandSettingsCommandClient) raw;

        assertEquals("PUBLIC_ACCESS_ENABLED", client.setPublicAccess(islandId, actorUuid, true).join().code());
        assertEquals("ISLAND_UNLOCKED", client.setLocked(islandId, actorUuid, false).join().code());
        assertEquals("ISLAND_RENAMED", client.setName(islandId, actorUuid, "My Island").join().code());
        assertEquals("FLAG_SET", client.setFlag(islandId, actorUuid, IslandFlag.PVP, "false").join().code());
        assertEquals(List.of("public:true", "locked:false", "name:My Island", "flag:PVP:false"), calls);
    }

    @Test
    void snapshotQueryClientReturnsTypedSnapshotsWithChecksums() {
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreSnapshotJson.java")));
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
        assertFalse(source.contains("private static String text("), "snapshot parser must use shared CoreJson text helpers");
        assertFalse(source.contains("SimpleJson.number(values.get("), "snapshot parser must use shared CoreJson numeric helpers");
    }

    @Test
    void visitorStatsClientReturnsTypedRecentVisitors() {
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkIslandVisitorStatsQueryClient.java")));
        IslandVisitorStatsView stats = JdkIslandVisitorStatsQueryClient.stats("""
            {"islandId":"%s","totalVisits":12,"uniqueVisitors":3,"recentVisitors":[
              {"visitorUuid":"visitor-a","lastVisitedAt":"now"},
              {"visitorUuid":"visitor-b","lastVisitedAt":"later"}
            ]}
            """.formatted(islandId));

        assertEquals(islandId.toString(), stats.islandId());
        assertEquals(12L, stats.totalVisits());
        assertEquals(3L, stats.uniqueVisitors());
        assertEquals("visitor-a", stats.recentVisitors().get(0).visitorUuid());
        assertEquals("later", stats.recentVisitors().get(1).lastVisitedAt());
        assertFalse(source.contains("private static String text("), "visitor stats parser must use shared CoreJson text helpers");
        assertTrue(source.contains("CoreJson.objects(root, \"recentVisitors\")"), "visitor stats parser must use shared CoreJson object list helpers");
    }

    @Test
    void adminIslandQueryClientReturnsTypedInfoAndRuntime() {
        UUID islandId = UUID.randomUUID();
        UUID ownerUuid = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreIslandJson.java")));
        String runtimeSource = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminIslandQueryClient.java")));
        CoreGuiViews.IslandInfoView info = CoreIslandJson.info("""
            {"islandId":"%s","ownerUuid":"%s","name":"Spawn","state":"ACTIVE","size":300,"level":42,"worth":"100.25","publicAccess":true}
            """.formatted(islandId, ownerUuid));
        CoreGuiViews.IslandInfoView named = CoreIslandJson.info("""
            {"islandId":"%s","ownerUuid":"%s","name":"Named","state":"ACTIVE"}
            """.formatted(islandId, ownerUuid));
        AdminIslandRuntimeView runtime = JdkAdminIslandQueryClient.runtime("""
            {"islandId":"%s","state":"ACTIVE","activeNode":"paper-a","activeWorld":"island_world","cellX":12,"cellZ":-3,"leaseOwner":"paper-a","fencingToken":9}
            """.formatted(islandId));

        assertEquals("Spawn", info.name());
        assertEquals(ownerUuid.toString(), info.ownerUuid());
        assertEquals("Named", named.name());
        assertEquals("paper-a", runtime.activeNode());
        assertTrue(runtime.hasCell());
        assertEquals(12L, runtime.cellX());
        assertEquals(-3L, runtime.cellZ());
        assertEquals(9L, runtime.fencingToken());
        assertFalse(source.contains("private static String text("), "island info parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static long number("), "island info parser must use shared CoreJson numeric helpers");
        assertFalse(source.contains("private static boolean bool("), "island info parser must use shared CoreJson boolean helpers");
        assertFalse(runtimeSource.contains("private static String text("), "admin island runtime parser must use shared CoreJson text helpers");
        assertFalse(runtimeSource.contains("private static long number("), "admin island runtime parser must use shared CoreJson numeric helpers");
        assertFalse(runtimeSource.contains("SimpleJson."), "admin island runtime parser must use shared CoreJson helpers");
    }

    @Test
    void adminIslandRuntimePreservesNullCellAsAbsent() {
        UUID islandId = UUID.randomUUID();
        AdminIslandRuntimeView runtime = JdkAdminIslandQueryClient.runtime("""
            {"islandId":"%s","state":"INACTIVE_READY","cellX":null,"cellZ":null,"fencingToken":3}
            """.formatted(islandId));

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
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminStorageClient.java")));
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
        assertFalse(source.contains("private static String text("), "admin storage parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static boolean bool("), "admin storage parser must use shared CoreJson boolean helpers");
        assertFalse(source.contains("private static long number("), "admin storage parser must use shared CoreJson numeric helpers");
        assertTrue(source.contains("CoreJson.objects(root, \"nodes\")"), "admin storage parser must use shared CoreJson object list helpers");
    }

    @Test
    void adminMaintenanceClientReturnsTypedCacheAndReloadResults() {
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminMaintenanceClient.java")));
        AdminMaintenanceResultView clear = JdkAdminMaintenanceClient.result("{\"clearedSessions\":2,\"clearedTickets\":3,\"clearedRedisKeys\":4}");
        AdminMaintenanceResultView reload = JdkAdminMaintenanceClient.result("{\"reloaded\":true,\"clearedSessions\":5,\"clearedTickets\":6,\"clearedRedisKeys\":7}");

        assertFalse(clear.reloaded());
        assertEquals(2L, clear.clearedSessions());
        assertEquals(3L, clear.clearedTickets());
        assertTrue(reload.reloaded());
        assertEquals(7L, reload.clearedRedisKeys());
        assertFalse(source.contains("private static String text("), "admin maintenance parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static long number("), "admin maintenance parser must use shared CoreJson numeric helpers");
        assertFalse(source.contains("private static boolean bool("), "admin maintenance parser must use shared CoreJson boolean helpers");
        assertFalse(source.contains("SimpleJson.object(root.get(\"error\"))"), "admin maintenance parser must use shared CoreJson object helpers");
    }

    @Test
    void jdkRuntimeCommandsReturnStandaloneTypedClient() throws Exception {
        JdkCoreApiClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:1"), "token", Duration.ofSeconds(1));

        assertTrue(client.runtimeCommands() instanceof JdkRuntimeCommandClient);
    }

    @Test
    void adminAddonStateClientReturnsTypedSummary() {
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminAddonStateQueryClient.java")));
        AdminAddonStateSummaryView summary = JdkAdminAddonStateQueryClient.summary("""
            {"stateOwnership":"core-addon-state-store","registeredAddonRequired":false,"orphanStatePolicy":"preserve","missingAddonStatePolicy":"ignored","tableKeyPrefix":"table/<name>/","maxKeysPerAddon":128,"maxValueLength":4096,"addons":[{"addonId":"shop","globalKeys":2,"islandKeys":3,"totalKeys":5}]}
            """);

        assertEquals("core-addon-state-store", summary.stateOwnership());
        assertFalse(summary.registeredAddonRequired());
        assertEquals(128L, summary.maxKeysPerAddon());
        assertEquals("shop", summary.addons().get(0).addonId());
        assertEquals(5L, summary.addons().get(0).totalKeys());
        assertFalse(source.contains("private static String text("), "admin addon state parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static long number("), "admin addon state parser must use shared CoreJson numeric helpers");
        assertTrue(source.contains("CoreJson.objects(root, \"addons\")"), "admin addon state parser must use shared CoreJson object list helpers");
    }

    @Test
    void adminCoreConfigClientReturnsTypedConfigView() {
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/AdminCoreConfigView.java")));
        AdminCoreConfigView config = AdminCoreConfigView.parse("""
            {"repositoryMode":"JDBC","jobQueueMode":"REDIS","eventBusMode":"REDIS","islandPortableBundle":true,"databasePoolSize":16,"addonStateBulkSaveGlobalEndpoint":"/v1/addons/state/bulk-save"}
            """);

        assertEquals("JDBC", config.text("repositoryMode"));
        assertEquals("REDIS", config.text("jobQueueMode"));
        assertTrue(config.bool("islandPortableBundle"));
        assertEquals(16L, config.number("databasePoolSize"));
        assertEquals("/v1/addons/state/bulk-save", config.text("addonStateBulkSaveGlobalEndpoint"));
        assertTrue(config.code().isBlank());
        assertFalse(source.contains("SimpleJson.object(root.get(\"error\"))"), "admin core config parser must use shared CoreJson object helpers");
        assertFalse(source.contains("SimpleJson.text(values.get("), "admin core config view must use shared CoreJson text helpers");
        assertFalse(source.contains("SimpleJson.number(values.get("), "admin core config view must use shared CoreJson number helpers");
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
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreCommunicationJson.java")));
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
        assertFalse(source.contains("private static String text("), "communication parser must use shared CoreJson text helpers");
        assertTrue(source.contains("CoreJson.objectValue(values, \"payload\")"), "communication parser must use shared CoreJson nested object helper");
        assertFalse(assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCommunicationQueryClient.java"))).contains("CoreResponseBody::value"), "communication query client must pass typed response envelopes to its parser");
        assertTrue(source.contains("records(CoreResponseBody body)"), "communication log parser must accept typed response envelopes");
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
        assertFalse(assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkCommunicationCommandClient.java"))).contains("CoreResponseBody::value"), "communication command client must pass typed response envelopes to its parser");
    }

    @Test
    void bankQueryClientReturnsTypedBankView() {
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreBankJson.java")));
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
        assertFalse(source.contains("SimpleJson.object(root.get("), "bank parser must use shared CoreJson nested object helpers");
        assertFalse(source.contains("SimpleJson.text(values.get("), "bank parser must use shared CoreJson text helpers");
        assertTrue(source.contains("snapshot(CoreResponseBody body)"), "bank snapshot parser must accept typed response envelopes");
        assertFalse(assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkBankQueryClient.java"))).contains("CoreResponseBody::value"), "bank query client must pass typed response envelopes to its parser");
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
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreBankJson.java")));
        assertTrue(source.contains("mutation(CoreResponseBody body)"), "bank mutation parser must accept typed response envelopes");
        assertFalse(assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkBankCommandClient.java"))).contains("CoreResponseBody::value"), "bank command client must pass typed response envelopes to its parser");
    }

    @Test
    void warehouseQueryClientReturnsTypedWarehouseItems() {
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkWarehouseQueryClient.java")));
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
        assertFalse(source.contains("SimpleJson.text(object.get("), "warehouse parser must use shared CoreJson text helpers");
        assertFalse(source.contains("SimpleJson.number(object.get("), "warehouse parser must use shared CoreJson numeric helpers");
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
    void homeWarpQueryClientReturnsTypedHomesWarpsAndIslandInfo() throws Exception {
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreHomeWarpJson.java")));
        List<String> calls = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/" + islandId + "/homes", exchange -> {
                calls.add("homes");
                byte[] response = """
                    {"homes":[{"islandId":"%s","name":"home","location":{"x":1.0,"y":2.0,"z":3.0},"createdBy":"00000000-0000-0000-0000-000000000001","createdAt":"now"}]}
                    """.formatted(islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/" + islandId + "/warps", exchange -> {
                calls.add("warps");
                byte[] response = """
                    {"warps":[{"islandId":"%s","name":"spawn","location":{"x":1.0,"y":2.0,"z":3.0},"publicAccess":true,"createdBy":"00000000-0000-0000-0000-000000000002","createdAt":"2026-01-02T03:04:05Z","category":"default"}]}
                    """.formatted(islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/info", exchange -> {
                calls.add("info:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"islandId":"%s","name":"Island","state":"ACTIVE"}
                    """.formatted(islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/public-warps", exchange -> {
                calls.add("public:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"warps":[{"islandId":"%s","name":"market","location":{"x":4.0,"y":5.0,"z":6.0},"publicAccess":true,"category":"market"}]}
                    """.formatted(islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            HomeWarpQueryClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).homeWarps();

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
            assertEquals(List.of("homes", "warps", "public:{\"limit\":100}", "homes", "warps", "info:{\"islandId\":\"" + islandId + "\"}", "public:{\"limit\":100}"), calls);
            assertFalse(source.contains("SimpleJson.object(values.get("), "home/warp parser must use shared CoreJson nested object helpers");
            assertTrue(source.contains("CoreJson.decimal(values, key)"), "home/warp location parser must use shared CoreJson decimal helpers");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void homeWarpCommandClientReturnsTypedActionViews() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        kr.lunaf.cloudislands.api.model.IslandLocation location = new kr.lunaf.cloudislands.api.model.IslandLocation("world", 1.0d, 2.0d, 3.0d, 0.0f, 0.0f);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/homes/set", exchange -> respondMemberTest(exchange, calls, "home", "{\"accepted\":true,\"code\":\"HOME_SET\"}"));
            server.createContext("/v1/islands/warps/set", exchange -> respondMemberTest(exchange, calls, "warp", "{\"accepted\":true,\"code\":\"WARP_SET\"}"));
            server.createContext("/v1/islands/warps/delete", exchange -> respondMemberTest(exchange, calls, "delete", "{\"accepted\":true,\"code\":\"WARP_DELETED\"}"));
            server.createContext("/v1/islands/warps/access", exchange -> respondMemberTest(exchange, calls, "access", "plain-success"));
            server.start();
            HomeWarpCommandClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).homeWarpCommands();

            assertEquals("HOME_SET", client.setHome(islandId, actorUuid, "home", location).join().code());
            assertEquals("WARP_SET", client.setWarp(islandId, actorUuid, "spawn", location, true).join().code());
            assertEquals("WARP_DELETED", client.deleteWarp(islandId, actorUuid, "spawn").join().code());
            assertEquals("WARP_PUBLIC", client.setWarpPublicAccess(islandId, actorUuid, "spawn", true).join().code());
            assertEquals(List.of(
                "home:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"home\",\"worldName\":\"world\",\"localX\":1.0,\"localY\":2.0,\"localZ\":3.0,\"yaw\":0.0,\"pitch\":0.0}",
                "warp:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"spawn\",\"worldName\":\"world\",\"localX\":1.0,\"localY\":2.0,\"localZ\":3.0,\"yaw\":0.0,\"pitch\":0.0,\"publicAccess\":true}",
                "delete:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"spawn\"}",
                "access:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"name\":\"spawn\",\"publicAccess\":true}"
            ), calls);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void navigationQueryClientReturnsTypedProfilesPublicIslandsAndReviews() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID reviewerUuid = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkNavigationQueryClient.java")));
        List<String> calls = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/players/info", exchange -> {
                calls.add("profile:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"playerUuid":"%s","lastName":"Alice","primaryIslandId":"%s","lastSeenAt":"now","locale":"ko_kr"}
                    """.formatted(reviewerUuid, islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/players/islands", exchange -> {
                calls.add("player-islands:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"islands":[{"islandId":"%s","name":"Home","state":"ACTIVE","role":"OWNER","level":9,"worth":"3000"}]}
                    """.formatted(islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/public", exchange -> {
                calls.add("public:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"islands":[{"islandId":"%s","ownerUuid":"%s","name":"Spawn","level":7,"worth":"1200"}]}
                    """.formatted(islandId, reviewerUuid).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/reviews", exchange -> {
                calls.add("reviews:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"reviews":[{"islandId":"%s","reviewerUuid":"%s","rating":5,"comment":"nice","createdAt":"2026-06-21T00:00:00Z","updatedAt":"2026-06-21T00:01:00Z"}],"summary":{"count":1,"average":5.0}}
                    """.formatted(islandId, reviewerUuid).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            NavigationQueryClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).navigation();

            CoreGuiViews.PlayerProfileView profile = client.playerProfileByName(" Alice ").join();
            CoreGuiViews.PlayerIslandView playerIsland = client.playerIslands(reviewerUuid).join().get(0);
            CoreGuiViews.PublicIslandView island = client.publicIslands(500).join().get(0);
            ReviewListView reviews = client.listReviews(islandId, 0).join();

            assertEquals(List.of(
                "profile:{\"lastName\":\"Alice\"}",
                "player-islands:{\"playerUuid\":\"" + reviewerUuid + "\"}",
                "public:{\"limit\":100}",
                "reviews:{\"islandId\":\"" + islandId + "\",\"limit\":1}"
            ), calls);
            assertEquals(islandId.toString(), profile.primaryIslandId());
            assertEquals("Home", playerIsland.name());
            assertEquals("OWNER", playerIsland.role());
            assertEquals("Spawn", island.name());
            assertEquals(1L, reviews.count());
            assertEquals(islandId.toString(), reviews.reviews().get(0).islandId());
            assertEquals("nice", reviews.reviews().get(0).comment());
            assertEquals("2026-06-21T00:01:00Z", reviews.reviews().get(0).updatedAt());
            assertFalse(source.contains("private static String text("), "navigation parser must use shared CoreJson text helpers");
            assertFalse(source.contains("SimpleJson.list(root.get(\"reviews\"))"), "navigation review parser must use shared CoreJson object list helpers");
            assertFalse(source.contains("SimpleJson.number(review.get("), "navigation review parser must use shared CoreJson numeric helpers");
        } finally {
            server.stop(0);
        }
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
            new Class<?>[] { CoreApiClient.class, NavigationCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "navigationCommands" -> (NavigationCommandClient) _proxy;
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
                case "setReview" -> {
                    calls.add("review:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new ReviewActionView(true, "REVIEW_SET"));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        NavigationCommandClient client = raw.navigationCommands();

        assertEquals(ticket, client.createVisitTicket(reviewerUuid, islandId).join());
        assertEquals(ticket, client.createVisitTicket(reviewerUuid, " spawn ").join());
        assertEquals(ticket, client.createVisitTicketForOwner(reviewerUuid, ownerUuid).join());
        assertEquals(ticket, client.createRandomVisitTicket(reviewerUuid).join());
        ReviewActionView result = client.setReview(islandId, reviewerUuid, 5, "nice").join();

        assertTrue(result.accepted());
        assertEquals("REVIEW_SET", result.code());
        assertEquals(List.of("visit-id", "visit-name: spawn ", "visit-owner:" + ownerUuid, "visit-random", "review:5:nice"), calls);
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
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkProgressionQueryClient.java")));
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, IslandQueryClient.class, ProgressionQueryClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandInfo", "getIsland" -> {
                    calls.add("info");
                    yield CompletableFuture.completedFuture(CoreIslandJson.info("""
                        {"islandId":"%s","name":"Base","state":"ACTIVE","level":7,"worth":"12.50"}
                        """.formatted(islandId)));
                }
                case "blockDetails" -> {
                    calls.add("blocks:" + args[1]);
                    yield CompletableFuture.completedFuture(JdkProgressionQueryClient.blockDetailsView("""
                        {"blocks":[{"materialKey":"minecraft:diamond_block","count":2,"totalWorth":"2000.00","levelPoints":20}],"summary":{"totalWorth":"2000.00","totalLevelPoints":20}}
                        """));
                }
                case "level" -> {
                    calls.add("level");
                    yield CompletableFuture.completedFuture(JdkProgressionQueryClient.levelView("""
                        {"islandId":"%s","level":9,"worth":"42.50","calculatedAt":"2026-06-21T00:00:00Z"}
                        """.formatted(islandId)));
                }
                case "topWorth", "topLevel" -> {
                    calls.add(method.getName() + ":" + args[0]);
                    yield CompletableFuture.completedFuture(JdkProgressionQueryClient.rankingViews("""
                        {"rankings":[{"islandId":"%s","name":"Base","level":7,"worth":"12.50"}]}
                        """.formatted(islandId), method.getName().equals("topWorth") ? "worth" : "level"));
                }
                case "topReviews" -> {
                    calls.add("reviews:" + args[0]);
                    yield CompletableFuture.completedFuture(JdkProgressionQueryClient.reviewRankingViews("""
                        {"rankings":[{"islandId":"%s","averageRating":4.5,"reviewCount":2}]}
                        """.formatted(islandId)));
                }
                case "upgrades" -> {
                    calls.add("upgrades");
                    yield CompletableFuture.completedFuture(JdkProgressionQueryClient.upgradeViews("""
                        {"upgrades":[{"upgradeKey":"generator","type":"GENERATOR","level":3,"generatorKey":"ore"}]}
                        """));
                }
                case "upgradeRules" -> {
                    calls.add("rules");
                    yield CompletableFuture.completedFuture(JdkProgressionQueryClient.upgradeRuleViews("""
                        {"rules":[{"upgradeKey":"members","type":"MAX_MEMBERS","maxLevel":5,"baseCost":"7500","multiplier":"2"}]}
                        """));
                }
                case "missions" -> {
                    calls.add("missions:" + args[1]);
                    yield CompletableFuture.completedFuture(JdkProgressionQueryClient.missionViews("""
                        {"missions":[{"missionKey":"starter","title":"Starter","progress":1,"goal":2,"completed":false,"reward":"10"}]}
                        """));
                }
                case "rankings" -> {
                    calls.add("topLevel:" + args[0]);
                    calls.add("topWorth:" + args[0]);
                    calls.add("reviews:" + args[0]);
                    yield CompletableFuture.completedFuture(new CoreGuiViews.RankingData(
                        List.of(new CoreGuiViews.RankingView(1, "level", islandId.toString(), 7L, "12.50")),
                        List.of(new CoreGuiViews.RankingView(1, "worth", islandId.toString(), 7L, "12.50")),
                        List.of(new CoreGuiViews.RankingView(1, "reviews", islandId.toString(), 2L, "4.50"))
                    ));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        ProgressionQueryClient client = (ProgressionQueryClient) raw;

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
        assertFalse(source.contains("private static String text("), "progression query parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static boolean bool("), "progression query parser must use shared CoreJson bool helpers");
        assertFalse(source.contains("private static double doubleValue("), "progression query parser must use shared CoreJson decimal helpers");
        assertFalse(source.contains("SimpleJson.object(root.get(\"summary\"))"), "progression summary parser must use shared CoreJson object helpers");
        assertFalse(source.contains("SimpleJson.list(root.get(\"blocks\"))"), "progression block parser must use shared CoreJson object list helpers");
        assertTrue(source.contains("CoreJson.firstText(object, \"key\", \"upgradeKey\")"), "upgrade keys must use shared alternate-field parsing");
        assertTrue(source.contains("CoreJson.firstText(object, \"key\", \"missionKey\")"), "mission keys must use shared alternate-field parsing");

        assertEquals(List.of(
            "info",
            "level",
            "blocks:500",
            "topWorth:500",
            "topLevel:0",
            "reviews:10",
            "topLevel:2",
            "topWorth:2",
            "reviews:2",
            "upgrades",
            "rules",
            "missions:null"
        ), calls);
    }

    @Test
    void progressionCommandClientReturnsTypedMutationViews() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkProgressionCommandClient.java")));
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, ProgressionCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "recalculateLevel" -> {
                    calls.add("recalculate");
                    yield CompletableFuture.completedFuture(JdkProgressionCommandClient.levelView("{\"islandId\":\"%s\",\"level\":8,\"worth\":\"14.00\"}".formatted(islandId)));
                }
                case "purchaseUpgrade" -> {
                    calls.add("purchase:" + args[2]);
                    yield CompletableFuture.completedFuture(JdkProgressionCommandClient.upgradePurchaseResult("""
                        {"accepted":true,"code":"UPGRADED","cost":"10.00","upgrade":{"islandId":"%s","upgradeKey":"generator:ore","type":"GENERATOR","level":3,"updatedAt":"2026-01-02T03:04:05Z"}}
                        """.formatted(islandId), (String) args[2]));
                }
                case "progressMission" -> {
                    calls.add("progress:" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture(JdkProgressionCommandClient.missionCompletionResult("""
                        {"accepted":true,"code":"MISSION_PROGRESS","islandId":"%s","missionKey":"starter","kind":"CHALLENGE","title":"Starter","progress":1,"goal":2,"completed":false,"reward":"10","updatedAt":"2026-01-02T03:04:05Z"}
                        """.formatted(islandId), islandId, (String) args[2], (String) args[3]));
                }
                case "completeMission" -> {
                    calls.add("mission:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(JdkProgressionCommandClient.missionCompletionResult("""
                        {"accepted":true,"code":"MISSION_COMPLETED","islandId":"%s","missionKey":"starter","kind":"CHALLENGE","title":"Starter","progress":2,"goal":2,"completed":true,"reward":"10","updatedAt":"2026-01-02T03:04:05Z"}
                        """.formatted(islandId), islandId, (String) args[2], (String) args[3]));
                }
                case "registerMissionProvider" -> {
                    calls.add("register:" + args[0] + ":" + JdkProgressionCommandClient.missionDefinitionsJson((List<MissionProviderDefinitionSnapshot>) args[1]));
                    yield CompletableFuture.completedFuture(JdkProgressionCommandClient.missionDefinitions("""
                        {"missions":[{"providerId":"addon-one","missionKey":"starter","kind":"MISSION","title":"Starter","goal":3,"reward":"money:5","enabled":true,"updatedAt":"2026-01-02T03:04:05Z"}]}
                        """));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        ProgressionCommandClient client = (ProgressionCommandClient) raw;
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
        assertFalse(source.contains("private static String text("), "progression command parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static boolean bool("), "progression command parser must use shared CoreJson bool helpers");
        assertFalse(source.contains("SimpleJson.object(root.get(\"upgrade\"))"), "progression upgrade parser must use shared CoreJson object helpers");
        assertFalse(source.contains("SimpleJson.number(root.get("), "progression command parser must use shared CoreJson number helpers");
        assertFalse(source.contains("SimpleJson.list(SimpleJson.object(parsed).get(\"missions\"))"), "mission definition parser must use shared CoreJson entries helper");
        assertEquals(List.of(
            "recalculate",
            "purchase:generator",
            "progress:starter:CHALLENGE:1",
            "mission:starter:CHALLENGE",
            "register:addon-one:[{\"missionKey\":\"starter\",\"kind\":\"MISSION\",\"title\":\"Starter \\\"Mission\\\"\",\"goal\":3,\"reward\":\"money:5\",\"enabled\":true}]"
        ), calls);
    }

    @Test
    void memberQueryClientReturnsTypedProfileInvitesAndBans() throws Exception {
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/players/info", exchange -> {
                calls.add("profile:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"playerUuid":"%s","lastName":"Alice","primaryIslandId":"%s","lastSeenAt":"now","locale":"ko_kr"}
                    """.formatted(playerUuid, islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/players/invites", exchange -> {
                calls.add("invites:" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = """
                    {"invites":[{"inviteId":"%s","islandId":"%s","inviterUuid":"%s","targetUuid":"%s","state":"PENDING"}]}
                    """.formatted(inviteId, islandId, playerUuid, playerUuid).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/" + islandId + "/bans", exchange -> {
                calls.add("bans");
                byte[] response = """
                    {"bans":[{"bannedUuid":"%s","actorUuid":"%s","reason":"test"}]}
                    """.formatted(playerUuid, islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            MemberQueryClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).members();

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
            assertEquals(List.of(
                "{\"lastName\":\"Alice\"}",
                "{\"playerUuid\":\"" + playerUuid + "\"}",
                "{\"playerUuid\":\"" + playerUuid + "\"}",
                "bans",
                "bans"
            ), calls.stream().map(call -> call.contains(":") ? call.substring(call.indexOf(':') + 1) : call).toList());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void memberCommandClientReturnsTypedActionsAndInviteViews() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID targetUuid = UUID.randomUUID();
        UUID inviteId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkMemberCommandClient.java")));
        List<String> calls = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/members/remove", exchange -> respondMemberTest(exchange, calls, "remove", "{\"accepted\":true,\"code\":\"MEMBER_REMOVED\"}"));
            server.createContext("/v1/islands/invites", exchange -> respondMemberTest(exchange, calls, "invite", "{\"inviteId\":\"" + inviteId + "\",\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"" + actorUuid + "\"}"));
            server.createContext("/v1/islands/invites/accept", exchange -> respondMemberTest(exchange, calls, "accept", "{\"accepted\":true,\"code\":\"INVITE_ACCEPTED\"}"));
            server.createContext("/v1/islands/invites/decline", exchange -> respondMemberTest(exchange, calls, "decline", "{\"accepted\":false,\"code\":\"INVITE_EXPIRED\"}"));
            server.createContext("/v1/islands/members/set", exchange -> respondMemberTest(exchange, calls, "role", "{\"accepted\":true,\"code\":\"MEMBER_ROLE_SET\"}"));
            server.createContext("/v1/islands/members/trust-temporary", exchange -> respondMemberTest(exchange, calls, "trust", "{\"accepted\":true,\"code\":\"TEMP_TRUST_SET\",\"expiresAt\":\"later\"}"));
            server.createContext("/v1/islands/transfer", exchange -> respondMemberTest(exchange, calls, "transfer", "{\"accepted\":true,\"code\":\"OWNERSHIP_TRANSFERRED\"}"));
            server.createContext("/v1/islands/bans/set", exchange -> respondMemberTest(exchange, calls, "ban", "{\"accepted\":false,\"code\":\"VISITOR_BAN_DENIED\"}"));
            server.createContext("/v1/islands/bans/remove", exchange -> respondMemberTest(exchange, calls, "pardon", "{\"accepted\":true,\"code\":\"VISITOR_PARDONED\"}"));
            server.createContext("/v1/islands/visitors/kick", exchange -> respondMemberTest(exchange, calls, "kick", "{\"accepted\":true,\"code\":\"VISITOR_KICKED\"}"));
            server.start();
            MemberCommandClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).memberCommands();

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
            assertEquals(List.of(
                "remove:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + targetUuid + "\"}",
                "invite:{\"islandId\":\"" + islandId + "\",\"inviterUuid\":\"" + actorUuid + "\",\"targetUuid\":\"" + targetUuid + "\"}",
                "accept:{\"inviteId\":\"" + inviteId + "\",\"playerUuid\":\"" + targetUuid + "\"}",
                "decline:{\"inviteId\":\"" + inviteId + "\",\"playerUuid\":\"" + targetUuid + "\"}",
                "role:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + targetUuid + "\",\"role\":\"CO_OWNER\",\"roleKey\":\"CO_OWNER\"}",
                "trust:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + targetUuid + "\",\"durationSeconds\":60}",
                "transfer:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"targetUuid\":\"" + targetUuid + "\"}",
                "ban:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + targetUuid + "\",\"reason\":\"reason\"}",
                "pardon:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + targetUuid + "\"}",
                "kick:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + targetUuid + "\"}"
            ), calls);
            assertFalse(source.contains("private static boolean bool("), "member command parser must use shared CoreJson boolean helpers");
            assertFalse(source.contains("SimpleJson.text(value)"), "member command parser must not parse boolean values directly");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void adminNodeQueryClientReturnsTypedSummariesAndNodeInfo() {
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminNodeQueryClient.java")));
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
        assertTrue(JdkAdminNodeQueryClient.node("node-a", "{}").isEmpty());
        assertTrue(JdkAdminNodeQueryClient.node("node-a", "[]").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> JdkAdminNodeQueryClient.node("node-a", "{\"state\":\"BROKEN\"}"));
        assertEquals("node=node-a count=2", JdkAdminNodeQueryClient.summary(nodeIslandsBody).text());
        List<AdminIslandRuntimeView> runtimes = JdkAdminNodeQueryClient.runtimes(nodeIslandsBody);
        assertEquals(1, runtimes.size());
        assertEquals(islandId.toString(), runtimes.get(0).islandId());
        assertEquals("world-a", runtimes.get(0).activeWorld());
        assertFalse(source.contains("private static String text("), "admin node parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static long number("), "admin node parser must use shared CoreJson numeric helpers");
        assertFalse(source.contains("private static double decimal("), "admin node parser must use shared CoreJson decimal helpers");
        assertFalse(source.contains("private static boolean bool("), "admin node parser must use shared CoreJson bool helpers");
        assertFalse(source.contains("SimpleJson.object(root.get("), "admin node parser must use shared CoreJson object helpers");
        assertFalse(source.contains("SimpleJson.list(root.get("), "admin node parser must use shared CoreJson object list helpers");
        assertFalse(source.contains("firstPresent("), "admin node parser must use CoreJson.firstText for alternate fields");
        assertTrue(source.contains("CoreJson.objects(root, \"nodes\")"), "admin node parser must parse node arrays through the shared CoreJson object list helper");
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
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CorePlayerProfileJson.java")));
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
        assertFalse(source.contains("private static String text("), "player profile parser must use shared CoreJson text helpers");
    }

    @Test
    void jobClientsReturnTypedJobsAndActions() {
        UUID jobId = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreJobJson.java")));
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
        assertFalse(source.contains("private static String text("), "job parser must use shared CoreJson text helpers");
        assertTrue(source.contains("CoreJson.objects(root, \"jobs\")"), "job parser must use shared CoreJson object list helpers");
    }

    @Test
    void templateClientsReturnTypedTemplates() {
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreTemplateJson.java")));
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
        assertFalse(source.contains("private static boolean bool("), "template parser must use shared CoreJson boolean helpers");
        assertTrue(source.contains("CoreJson.objects(root, \"templates\")"), "template parser must use shared CoreJson object list helpers");
    }

    @Test
    void blockValueClientsReturnTypedValuesAndActions() {
        UUID actorUuid = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreBlockValueJson.java")));
        List<String> calls = new ArrayList<>();
        CoreApiClient raw = (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] { CoreApiClient.class, BlockValueCommandClient.class },
            (_proxy, method, args) -> switch (method.getName()) {
                case "set" -> {
                    calls.add("set:" + args[0] + ":" + args[1].toString().trim() + ":" + args[2] + ":" + args[3] + ":" + args[4]);
                    yield CompletableFuture.completedFuture(new BlockValueActionView(true, "BLOCK_VALUE_SET", args[1].toString().trim()));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );
        BlockValueCommandClient commands = (BlockValueCommandClient) raw;

        List<BlockValueView> values = CoreBlockValueJson.values("""
            {"values":[
              {"materialKey":"minecraft:diamond_block","worth":"100.50","levelPoints":20,"limit":64},
              {"materialKey":"minecraft:emerald_block","worth":"80","levelPoints":10,"limit":32}
            ]}
            """);
        BlockValueActionView result = commands.set(actorUuid, " minecraft:diamond_block ", "100.50", 20L, 64L).join();

        assertEquals(2, values.size());
        assertEquals("minecraft:diamond_block", values.get(0).materialKey());
        assertEquals("100.50", values.get(0).worth());
        assertEquals(20L, values.get(0).levelPoints());
        assertEquals(64L, values.get(0).limit());
        assertTrue(result.accepted());
        assertEquals("BLOCK_VALUE_SET", result.code());
        assertEquals("minecraft:diamond_block", result.materialKey());
        assertTrue(source.contains("values(CoreResponseBody body)"), "block value list parser must accept typed response envelopes");
        assertTrue(source.contains("action(CoreResponseBody body, String materialKey)"), "block value action parser must accept typed response envelopes");
        assertFalse(assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkBlockValueQueryClient.java"))).contains("CoreResponseBody::value"), "block value query client must pass typed response envelopes to its parser");
        assertFalse(assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkBlockValueCommandClient.java"))).contains("CoreResponseBody::value"), "block value command client must pass typed response envelopes to its parser");
        assertEquals(List.of("set:" + actorUuid + ":minecraft:diamond_block:100.50:20:64"), calls);
        assertTrue(source.contains("CoreJson.objects(root, \"values\")"), "block value parser must use shared CoreJson object list helpers");
        assertFalse(source.contains("SimpleJson.list(root.get(\"values\"))"), "block value parser must not traverse raw JSON arrays directly");
    }

    @Test
    void adminRouteClientReturnsTypedTicketsAndClearResults() {
        UUID ticketId = UUID.randomUUID();
        UUID playerUuid = UUID.randomUUID();
        UUID islandId = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/CoreAdminRouteJson.java")));
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
        assertFalse(source.contains("private static String text("), "admin route parser must use shared CoreJson text helpers");
        assertFalse(source.contains("private static boolean bool("), "admin route parser must use shared CoreJson boolean helpers");
        assertTrue(source.contains("CoreJson.objects(root, \"sessions\")"), "admin route parser must use shared CoreJson session list helpers");
        assertTrue(source.contains("CoreJson.objects(root, \"tickets\")"), "admin route parser must use shared CoreJson ticket list helpers");
    }

    @Test
    void adminEventAndAuditClientsReturnTypedEntries() {
        String eventSource = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminEventClient.java")));
        String auditSource = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkAdminAuditClient.java")));
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
        assertFalse(eventSource.contains("private static String text("), "admin event parser must use shared CoreJson text helpers");
        assertFalse(eventSource.contains("SimpleJson.object(event.get(\"fields\"))"), "admin event parser must use shared CoreJson object helpers");
        assertTrue(eventSource.contains("CoreJson.stringMap(CoreJson.objectValue(event, \"fields\"))"), "admin event parser must use shared CoreJson string map helpers");
        assertTrue(eventSource.contains("CoreJson.objects(root, \"events\")"), "admin event parser must use shared CoreJson object list helpers");
        assertFalse(auditSource.contains("private static String text("), "admin audit parser must use shared CoreJson text helpers");
        assertFalse(auditSource.contains("SimpleJson.object(entry.get(\"payload\"))"), "admin audit parser must use shared CoreJson object helpers");
        assertTrue(auditSource.contains("CoreJson.stringMap(CoreJson.objectValue(entry, \"payload\"))"), "admin audit parser must use shared CoreJson string map helpers");
        assertTrue(auditSource.contains("CoreJson.objects(root, \"audit\")"), "admin audit parser must use shared CoreJson object list helpers");
    }

    @Test
    void lifecycleCommandClientReturnsTypedResetResult() {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        String source = assertDoesNotThrow(() -> Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreclient/JdkIslandLifecycleCommandClient.java")));
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
        assertFalse(source.contains("private static boolean bool("), "lifecycle command parser must use shared CoreJson boolean helpers");
        assertFalse(source.contains("SimpleJson.object(root.get(\"error\"))"), "lifecycle command parser must use shared CoreJson nested object helpers");
        assertFalse(source.contains("SimpleJson.text(root.get("), "lifecycle command parser must use shared CoreJson text helpers");
    }

    @Test
    void permissionCommandClientReturnsTypedRoleMutations() throws Exception {
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            server.createContext("/v1/islands/roles/upsert", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                calls.add("upsert:" + request);
                byte[] response = """
                    {"islandId":"%s","role":"BUILDER","roleKey":"BUILDER","weight":42,"displayName":"Builder"}
                    """.formatted(islandId).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.createContext("/v1/islands/roles/reset", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                calls.add("reset:" + request);
                byte[] response = """
                    {"accepted":true,"code":"ROLE_RESET","role":"BUILDER","roleKey":"BUILDER","removed":true}
                    """.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            PermissionCommandClient client = new JdkCoreApiClient(new URI("http://127.0.0.1:" + server.getAddress().getPort()), "token", Duration.ofSeconds(2)).permissions();

            MutationResult<CoreGuiViews.RoleView> upserted = client.upsertRole(islandId, actorUuid, "builder", 42, "Builder").join();
            MutationResult<CoreGuiViews.RoleView> reset = client.resetRole(islandId, actorUuid, "builder").join();

            assertEquals(List.of(
                "upsert:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"BUILDER\",\"roleKey\":\"BUILDER\",\"weight\":42,\"displayName\":\"Builder\"}",
                "reset:{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"role\":\"BUILDER\",\"roleKey\":\"BUILDER\"}"
            ), calls);
            assertEquals("BUILDER", upserted.value().role());
            assertEquals(42, upserted.value().weight());
            assertEquals("Builder", upserted.value().displayName());
            assertEquals("BUILDER", reset.value().role());
            assertTrue(reset.changed());
        } finally {
            server.stop(0);
        }
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

    private static void respondMemberTest(com.sun.net.httpserver.HttpExchange exchange, List<String> calls, String key, String responseBody) throws java.io.IOException {
        calls.add(key + ":" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
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
