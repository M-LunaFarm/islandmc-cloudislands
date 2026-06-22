package kr.lunaf.cloudislands.coreclient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.json.IslandJobJson;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class JdkCoreApiClient implements CoreApiClient, IslandLifecycleCommandClient {
    private final URI baseUri;
    private final String authToken;
    private final String adminToken;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final BankQueryClient bankQueryClient;
    private final BankCommandClient bankCommandClient;
    private final CommunicationQueryClient communicationQueryClient;
    private final CommunicationCommandClient communicationCommandClient;
    private final JdkSnapshotClient snapshotClient;
    private final IslandEnvironmentQueryClient environmentQueryClient;
    private final IslandEnvironmentCommandClient environmentCommandClient;
    private final IslandSettingsCommandClient settingsClient;
    private final PermissionQueryClient permissionQueryClient;
    private final PermissionCommandClient permissionCommandClient;
    private final HomeWarpQueryClient homeWarpQueryClient;
    private final HomeWarpCommandClient homeWarpCommandClient;
    private final WarehouseQueryClient warehouseQueryClient;
    private final WarehouseCommandClient warehouseCommandClient;
    private final RoutingCommandClient routingClient;
    private final NavigationQueryClient navigationQueryClient;
    private final NavigationCommandClient navigationCommandClient;
    private final RuntimeCommandClient runtimeCommandClient;
    private final IslandQueryClient islandClient;
    private final ProgressionQueryClient progressionQueryClient;
    private final ProgressionCommandClient progressionCommandClient;
    private final MemberQueryClient memberQueryClient;
    private final MemberCommandClient memberCommandClient;
    private final IslandVisitorStatsQueryClient visitorStatsClient;
    private final PlayerProfileQueryClient playerProfileQueryClient;
    private final PlayerProfileCommandClient playerProfileCommandClient;
    private final TemplateQueryClient templateQueryClient;
    private final TemplateCommandClient templateCommandClient;
    private final JdkJobClient jobQueryClient;
    private final JobCommandClient jobCommandClient;
    private final BlockValueQueryClient blockValueQueryClient;
    private final BlockValueCommandClient blockValueCommandClient;
    private final AdminMetricsQueryClient adminMetricsClient;
    private final AdminCoreConfigQueryClient adminCoreConfigClient;
    private final AdminStorageQueryClient adminStorageClient;
    private final AdminEventQueryClient adminEventClient;
    private final AdminAuditQueryClient adminAuditClient;
    private final AdminRouteClient adminRouteClient;
    private final AdminAddonStateQueryClient adminAddonStateClient;
    private final AddonStateClient addonStateClient;
    private final AdminMaintenanceCommandClient adminMaintenanceClient;
    private final AdminNodeQueryClient adminNodeQueryClient;
    private final AdminNodeCommandClient adminNodeCommandClient;
    private final AdminIslandQueryClient adminIslandClient;
    private final MigrationCommandClient migrationCommandClient;

    public JdkCoreApiClient(URI baseUri, String authToken, Duration timeout) {
        this(baseUri, authToken, System.getenv().getOrDefault("CI_ADMIN_TOKEN", ""), timeout);
    }

    public JdkCoreApiClient(URI baseUri, String authToken, String adminToken, Duration timeout) {
        this.baseUri = baseUri;
        this.authToken = authToken;
        this.adminToken = adminToken == null ? "" : adminToken;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(5) : timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.bankQueryClient = new JdkBankQueryClient(this);
        this.bankCommandClient = new JdkBankCommandClient(this);
        this.communicationQueryClient = new JdkCommunicationQueryClient(this);
        this.communicationCommandClient = new JdkCommunicationCommandClient(this);
        this.snapshotClient = new JdkSnapshotClient(this);
        this.environmentQueryClient = new JdkIslandEnvironmentQueryClient(this);
        this.environmentCommandClient = new JdkIslandEnvironmentCommandClient(this);
        this.settingsClient = new JdkIslandSettingsCommandClient(this);
        this.permissionQueryClient = new JdkPermissionQueryClient(this);
        this.permissionCommandClient = new JdkPermissionCommandClient(this);
        this.homeWarpQueryClient = new JdkHomeWarpQueryClient(this);
        this.homeWarpCommandClient = new JdkHomeWarpCommandClient(this);
        this.warehouseQueryClient = new JdkWarehouseQueryClient(this);
        this.warehouseCommandClient = new JdkWarehouseCommandClient(this);
        this.routingClient = new JdkRoutingClient(this);
        this.navigationQueryClient = new JdkNavigationQueryClient(this);
        this.navigationCommandClient = new JdkNavigationCommandClient(this);
        this.runtimeCommandClient = new JdkRuntimeCommandClient(this);
        this.islandClient = new JdkIslandQueryClient(this);
        this.progressionQueryClient = new JdkProgressionQueryClient(this);
        this.progressionCommandClient = new JdkProgressionCommandClient(this);
        this.memberQueryClient = new JdkMemberQueryClient(this);
        this.memberCommandClient = new JdkMemberCommandClient(this);
        this.visitorStatsClient = new JdkIslandVisitorStatsQueryClient(this);
        this.playerProfileQueryClient = new JdkPlayerProfileQueryClient(this);
        this.playerProfileCommandClient = new JdkPlayerProfileCommandClient(this);
        this.templateQueryClient = new JdkTemplateQueryClient(this);
        this.templateCommandClient = new JdkTemplateCommandClient(this);
        this.jobQueryClient = new JdkJobClient(this);
        this.jobCommandClient = new JdkJobCommandClient(this);
        this.blockValueQueryClient = new JdkBlockValueQueryClient(this);
        this.blockValueCommandClient = new JdkBlockValueCommandClient(this);
        this.adminMetricsClient = new JdkAdminMetricsClient(this);
        this.adminCoreConfigClient = new JdkAdminCoreConfigClient(this);
        this.adminStorageClient = new JdkAdminStorageClient(this);
        this.adminEventClient = new JdkAdminEventClient(this);
        this.adminAuditClient = new JdkAdminAuditClient(this);
        this.adminRouteClient = new JdkAdminRouteClient(this);
        this.adminAddonStateClient = new JdkAdminAddonStateQueryClient(this);
        this.addonStateClient = new JdkAddonStateClient(this);
        this.adminMaintenanceClient = new JdkAdminMaintenanceClient(this);
        this.adminNodeQueryClient = new JdkAdminNodeQueryClient(this);
        this.adminNodeCommandClient = new JdkAdminNodeCommandClient(this);
        this.adminIslandClient = new JdkAdminIslandQueryClient(this);
        this.migrationCommandClient = new JdkMigrationCommandClient(this);
    }

    @Override
    public BankQueryClient bank() {
        return bankQueryClient;
    }

    @Override
    public BankCommandClient bankCommands() {
        return bankCommandClient;
    }

    @Override
    public SnapshotQueryClient snapshots() {
        return snapshotClient;
    }

    @Override
    public SnapshotCommandClient snapshotCommands() {
        return snapshotClient;
    }

    @Override
    public CommunicationQueryClient communication() {
        return communicationQueryClient;
    }

    @Override
    public CommunicationCommandClient communicationCommands() {
        return communicationCommandClient;
    }

    @Override
    public IslandEnvironmentQueryClient environment() {
        return environmentQueryClient;
    }

    @Override
    public IslandEnvironmentCommandClient environmentCommands() {
        return environmentCommandClient;
    }

    @Override
    public IslandSettingsCommandClient settingsCommands() {
        return settingsClient;
    }

    @Override
    public PermissionCommandClient permissions() {
        return permissionCommandClient;
    }

    @Override
    public PermissionQueryClient permissionQueries() {
        return permissionQueryClient;
    }

    @Override
    public HomeWarpQueryClient homeWarps() {
        return homeWarpQueryClient;
    }

    @Override
    public HomeWarpCommandClient homeWarpCommands() {
        return homeWarpCommandClient;
    }

    @Override
    public RoutingCommandClient routingCommands() {
        return routingClient;
    }

    @Override
    public NavigationQueryClient navigation() {
        return navigationQueryClient;
    }

    @Override
    public NavigationCommandClient navigationCommands() {
        return navigationCommandClient;
    }

    @Override
    public RuntimeCommandClient runtimeCommands() {
        return runtimeCommandClient;
    }

    @Override
    public IslandLifecycleCommandClient lifecycle() {
        return this;
    }

    @Override
    public ProgressionQueryClient progression() {
        return progressionQueryClient;
    }

    @Override
    public ProgressionCommandClient progressionCommands() {
        return progressionCommandClient;
    }

    @Override
    public WarehouseQueryClient warehouse() {
        return warehouseQueryClient;
    }

    @Override
    public WarehouseCommandClient warehouseCommands() {
        return warehouseCommandClient;
    }

    @Override
    public IslandQueryClient islands() {
        return islandClient;
    }

    @Override
    public MemberQueryClient members() {
        return memberQueryClient;
    }

    @Override
    public MemberCommandClient memberCommands() {
        return memberCommandClient;
    }

    @Override
    public IslandVisitorStatsQueryClient visitorStats() {
        return visitorStatsClient;
    }

    @Override
    public PlayerProfileQueryClient playerProfiles() {
        return playerProfileQueryClient;
    }

    @Override
    public PlayerProfileCommandClient playerProfileCommands() {
        return playerProfileCommandClient;
    }

    @Override
    public TemplateQueryClient templates() {
        return templateQueryClient;
    }

    @Override
    public TemplateCommandClient templateCommands() {
        return templateCommandClient;
    }

    @Override
    public JobQueryClient jobs() {
        return jobQueryClient;
    }

    @Override
    public JobCommandClient jobCommands() {
        return jobCommandClient;
    }

    @Override
    public BlockValueQueryClient blockValues() {
        return blockValueQueryClient;
    }

    @Override
    public BlockValueCommandClient blockValueCommands() {
        return blockValueCommandClient;
    }

    @Override
    public AdminMetricsQueryClient adminMetrics() {
        return adminMetricsClient;
    }

    @Override
    public MigrationCommandClient migrations() {
        return migrationCommandClient;
    }

    @Override
    public AdminCoreConfigQueryClient adminCoreConfig() {
        return adminCoreConfigClient;
    }

    @Override
    public AdminStorageQueryClient adminStorage() {
        return adminStorageClient;
    }

    @Override
    public AdminEventQueryClient adminEvents() {
        return adminEventClient;
    }

    @Override
    public AdminAuditQueryClient adminAudit() {
        return adminAuditClient;
    }

    @Override
    public AdminRouteClient adminRoutes() {
        return adminRouteClient;
    }

    @Override
    public AdminAddonStateQueryClient adminAddonState() {
        return adminAddonStateClient;
    }

    @Override
    public AddonStateClient addonStates() {
        return addonStateClient;
    }

    @Override
    public AdminMaintenanceCommandClient adminMaintenance() {
        return adminMaintenanceClient;
    }

    @Override
    public AdminNodeQueryClient adminNodes() {
        return adminNodeQueryClient;
    }

    @Override
    public AdminNodeCommandClient adminNodeCommands() {
        return adminNodeCommandClient;
    }

    @Override
    public AdminIslandQueryClient adminIslands() {
        return adminIslandClient;
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    @Override
    public CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId) {
        requireId(playerUuid, "playerUuid");
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        return post("/v1/islands", jsonObject("playerUuid", playerUuid, "templateId", normalizedTemplateId))
            .thenApply(JdkCoreApiClient::parseCreateIslandResult);
    }

    @Override
    public CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId) {
        requireId(requesterUuid, "requesterUuid");
        requireId(islandId, "islandId");
        return deleteWithResultBody("/v1/islands/" + islandId + "?requesterUuid=" + requesterUuid)
            .thenApply(body -> parseDeleteIslandResult(body, islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> resetIsland(UUID islandId, UUID actorUuid, String reason) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return postWithResultBody("/v1/islands/reset", jsonObject("islandId", islandId, "actorUuid", actorUuid, "reason", reason == null || reason.isBlank() ? "player-reset" : reason.trim()))
            .thenApply(body -> lifecycleAction(body, "RESET_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> saveIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/save", jsonObject("islandId", islandId, "reason", lifecycleReason(reason, "ADMIN_SAVE")))
            .thenApply(body -> lifecycleAction(body, "SNAPSHOT_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> snapshotIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/snapshot", jsonObject("islandId", islandId, "reason", lifecycleReason(reason, "ADMIN_MANUAL")))
            .thenApply(body -> lifecycleAction(body, "SNAPSHOT_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> restoreIslandSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/restore", jsonObject("islandId", islandId, "snapshotNo", snapshotNo))
            .thenApply(body -> lifecycleAction(body, "RESTORE_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> rollbackIslandSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/rollback", jsonObject("islandId", islandId, "snapshotNo", snapshotNo))
            .thenApply(body -> lifecycleAction(body, "RESTORE_QUEUED", islandId));
    }

    private static String lifecycleReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason.trim();
    }

    private static IslandLifecycleActionView lifecycleAction(String body, String successCode, UUID fallbackIslandId) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> error = SimpleJson.object(root.get("error"));
        boolean accepted = error.isEmpty()
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = CoreJson.text(root, "code");
        if (code.isBlank()) {
            code = SimpleJson.text(error.get("code"));
        }
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        String islandId = CoreJson.text(root, "islandId");
        if (islandId.isBlank() && fallbackIslandId != null) {
            islandId = fallbackIslandId.toString();
        }
        return new IslandLifecycleActionView(
            accepted,
            code,
            islandId,
            CoreJson.number(root, "snapshotNo"),
            CoreJson.text(root, "storagePath")
        );
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) {
        return createHomeTicket(playerUuid, "default");
    }

    @Override
    public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) {
        return postWithResultBody("/v1/routes/home", jsonObject("playerUuid", playerUuid, "homeName", homeName)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) {
        return postWithResultBody("/v1/routes/visit", jsonObject("playerUuid", visitorUuid, "islandId", targetIslandId)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) {
        return postWithResultBody("/v1/routes/visit", jsonObject("playerUuid", visitorUuid, "islandName", islandName)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) {
        return postWithResultBody("/v1/routes/visit", jsonObject("playerUuid", visitorUuid, "ownerUuid", ownerUuid)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) {
        return postWithResultBody("/v1/routes/random", jsonObject("playerUuid", visitorUuid)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
        return postWithResultBody("/v1/routes/warp", jsonObject("playerUuid", playerUuid, "islandId", islandId, "warpName", warpName)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<RouteTicket> createMigrationReturnTicket(UUID playerUuid, UUID islandId, String targetNode, double localX, double localY, double localZ, float yaw, float pitch) {
        return postWithResultBody("/v1/routes/migration-return", jsonObject(
            "playerUuid", playerUuid,
            "islandId", islandId,
            "targetNode", targetNode,
            "localX", localX,
            "localY", localY,
            "localZ", localZ,
            "yaw", yaw,
            "pitch", pitch
        )).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> findRouteSession(UUID playerUuid, String nodeId) {
        return post("/v1/routes/session/find", jsonObject("playerUuid", playerUuid, "nodeId", nodeId)).thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> findAnyRouteSession(UUID playerUuid) {
        return post("/v1/routes/session/find-any", jsonObject("playerUuid", playerUuid)).thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId) {
        return consumeRouteSession(playerUuid, nodeId, true);
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, boolean reportMissing) {
        return post("/v1/routes/session/consume", jsonObject("playerUuid", playerUuid, "nodeId", nodeId, "reportMissing", reportMissing)).thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, UUID ticketId, String nonce, boolean reportMissing) {
        return post("/v1/routes/session/consume", jsonObject("playerUuid", playerUuid, "nodeId", nodeId, "ticketId", ticketId, "nonce", nonce, "reportMissing", reportMissing)).thenApply(body -> body.isBlank() ? Optional.empty() : Optional.of(RouteSessionJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce) {
        return post("/v1/routes/ticket-status", jsonObject("ticketId", ticketId, "playerUuid", playerUuid, "nonce", nonce)).thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(RouteTicketJson.parse(body)));
    }

    @Override
    public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) {
        return post("/v1/routes/consume", jsonObject("ticketId", ticketId, "playerUuid", playerUuid, "nodeId", nodeId, "nonce", nonce)).thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(RouteTicketJson.parse(body)));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> activateIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/activate", jsonObject("islandId", islandId))
            .thenApply(body -> lifecycleAction(body, "ACTIVATING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> deactivateIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/deactivate", jsonObject("islandId", islandId))
            .thenApply(body -> lifecycleAction(body, "SAVING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> migrateIsland(UUID islandId, String targetNode) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/migrate", jsonObject("islandId", islandId, "targetNode", targetNode == null ? "" : targetNode.trim()))
            .thenApply(body -> lifecycleAction(body, "MIGRATING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> quarantineIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/" + islandId + "/quarantine", jsonObject("reason", lifecycleReason(reason, "admin")))
            .thenApply(body -> lifecycleAction(body, "QUARANTINED", islandId));
    }

    @Override
    public CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId) {
        return postWithResultBody("/v1/admin/islands/tp", jsonObject("playerUuid", playerUuid, "islandId", islandId)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> adminDeleteIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/" + islandId + "/delete", "{}")
            .thenApply(body -> lifecycleAction(body, "DELETED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> repairIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return postWithResultBody("/v1/admin/islands/" + islandId + "/repair", jsonObject("reason", lifecycleReason(reason, "admin")))
            .thenApply(body -> lifecycleAction(body, "REPAIRED", islandId));
    }

    static String stringMapJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null)
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(entry.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    @Override
    public CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        String types = supportedTypes.stream().map(Enum::name).collect(Collectors.joining(","));
        return postWithResultBody("/v1/jobs/claim", jsonObject("nodeId", nodeId, "supportedTypes", types, "maxJobs", maxJobs)).thenApply(IslandJobJson::readArray);
    }

    String tableMapJson(Map<String, Map<String, String>> tables) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        if (tables != null) {
            for (Map.Entry<String, Map<String, String>> entry : tables.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append("\"").append(escape(entry.getKey())).append("\":").append(stringMapJson(entry.getValue()));
            }
        }
        return builder.append("}").toString();
    }

    CompletableFuture<String> post(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return send(request).thenApply(response -> response.bodyOrEmpty(response.successBody()));
    }

    CompletableFuture<String> get(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .GET();
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return send(request).thenApply(response -> response.bodyOrEmpty(response.successBody()));
    }

    CompletableFuture<String> postWithResultBody(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return send(request).thenApply(response -> response.bodyOrEmpty(response.resultBody()));
    }

    private CompletableFuture<String> deleteWithResultBody(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .DELETE();
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return send(request).thenApply(response -> response.bodyOrEmpty(response.resultBody()));
    }

    private CompletableFuture<CoreHttpResponse> send(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> new CoreHttpResponse(response.statusCode(), response.body()));
    }

    private static RouteTicket parseRouteTicketResult(String body) {
        if (body == null || body.isBlank()) {
            throw new CoreApiException("ROUTE_FAILED", "Route ticket could not be created");
        }
        Object parsed = SimpleJson.parse(body);
        if (!containsField(parsed, "ticketId")) {
            Map<?, ?> root = SimpleJson.object(parsed);
            String code = SimpleJson.text(root.get("code"));
            String message = SimpleJson.text(root.get("message"));
            throw new CoreApiException(code.isBlank() ? "ROUTE_FAILED" : code, message.isBlank() ? "Route ticket could not be created" : message);
        }
        RouteTicket ticket = RouteTicketJson.parse(body);
        if (ticket == null) {
            throw new CoreApiException("ROUTE_FAILED", "Route ticket could not be parsed");
        }
        return ticket;
    }

    private static boolean containsField(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey(key)) {
                return true;
            }
            for (Object nested : map.values()) {
                if (containsField(nested, key)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object nested : list) {
                if (containsField(nested, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static CreateIslandResult parseCreateIslandResult(String body) {
        Map<?, ?> root = resultObject(body);
        return new CreateIslandResult(
            bool(root, "accepted"),
            resultCode(root),
            null,
            RouteTicketJson.parseNested(body == null ? "" : body, "ticket")
        );
    }

    private static DeleteIslandResult parseDeleteIslandResult(String body, UUID fallbackIslandId) {
        Map<?, ?> root = resultObject(body);
        return new DeleteIslandResult(
            bool(root, "accepted"),
            resultCode(root),
            uuid(root, "islandId", fallbackIslandId)
        );
    }

    private static Map<?, ?> resultObject(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return SimpleJson.object(SimpleJson.parse(body));
    }

    private static String resultCode(Map<?, ?> root) {
        String code = SimpleJson.text(root.get("code"));
        return code.isBlank() ? "FAILED" : code;
    }

    private static boolean bool(Map<?, ?> root, String key) {
        Object value = root.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }

    private static UUID uuid(Map<?, ?> root, String key, UUID fallback) {
        String value = SimpleJson.text(root.get(key));
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String text(Map<?, ?> root, String key, String fallback) {
        String value = SimpleJson.text(root.get(key));
        return value.isBlank() ? fallback : value;
    }

    private boolean adminProtected(String path) {
        return path.startsWith("/v1/admin")
            || path.equals("/v1/audit")
            || path.equals("/v1/events")
            || path.equals("/metrics")
            || path.equals("/v1/jobs")
            || path.equals("/v1/jobs/claim")
            || path.equals("/v1/jobs/complete")
            || path.equals("/v1/jobs/fail")
            || path.equals("/v1/jobs/recover");
    }

    private void addAdminHeaders(HttpRequest.Builder builder, String path) {
        if (adminToken.isBlank() || !adminProtected(path)) {
            return;
        }
        builder.header("X-CloudIslands-Admin-Token", adminToken);
        String permission = adminPermission(path);
        if (!permission.isBlank()) {
            builder.header("X-CloudIslands-Admin-Permissions", permission);
        }
    }

    private String adminPermission(String path) {
        if (path.equals("/v1/jobs") || path.equals("/v1/jobs/claim") || path.equals("/v1/jobs/complete") || path.equals("/v1/jobs/fail") || path.equals("/v1/jobs/recover")) {
            return "JOB_MANAGE";
        }
        if (path.equals("/v1/audit") || path.equals("/v1/admin/audit") || path.equals("/v1/admin/audit/list") || path.equals("/v1/events") || path.equals("/metrics")) {
            return "AUDIT_READ";
        }
        if (path.equals("/v1/admin/cache/clear") || path.equals("/v1/admin/reload")) {
            return "CACHE_CLEAR";
        }
        if (path.startsWith("/v1/admin/migrations/")) {
            return "MIGRATION_MANAGE";
        }
        if (path.startsWith("/v1/admin/players/")) {
            return "PLAYER_MANAGE";
        }
        if (path.startsWith("/v1/admin/templates/")) {
            return "TEMPLATE_MANAGE";
        }
        if (path.startsWith("/v1/admin/routes/")) {
            return "ROUTE_MANAGE";
        }
        if (path.startsWith("/v1/admin/block-values")) {
            return "ECONOMY_MANAGE";
        }
        if (path.startsWith("/v1/admin/nodes/")) {
            if (path.endsWith("/drain") || path.endsWith("/sweep")) {
                return "NODE_DRAIN";
            }
            if (path.endsWith("/undrain")) {
                return "NODE_UNDRAIN";
            }
            if (path.endsWith("/kickall")) {
                return "NODE_KICK";
            }
            if (path.endsWith("/shutdown-safe")) {
                return "NODE_SHUTDOWN";
            }
            return "AUDIT_READ";
        }
        if (path.startsWith("/v1/admin/islands/")) {
            if (path.endsWith("/activate")) {
                return "ISLAND_ACTIVATE";
            }
            if (path.endsWith("/deactivate")) {
                return "ISLAND_DEACTIVATE";
            }
            if (path.endsWith("/migrate")) {
                return "ISLAND_MIGRATE";
            }
            if (path.endsWith("/snapshot")) {
                return "ISLAND_SNAPSHOT";
            }
            if (path.endsWith("/save")) {
                return "ISLAND_SNAPSHOT";
            }
            if (path.endsWith("/restore")) {
                return "ISLAND_RESTORE";
            }
            if (path.endsWith("/rollback")) {
                return "ISLAND_RESTORE";
            }
            if (path.endsWith("/quarantine")) {
                return "ISLAND_QUARANTINE";
            }
            if (path.endsWith("/delete")) {
                return "ISLAND_DELETE";
            }
            if (path.endsWith("/repair")) {
                return "ISLAND_REPAIR";
            }
            if (path.endsWith("/tp")) {
                return "ISLAND_TELEPORT";
            }
            return "AUDIT_READ";
        }
        return path.startsWith("/v1/admin") ? "AUDIT_READ" : "";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    static String jsonObject(Object... fields) {
        if (fields == null || fields.length == 0) {
            return "{}";
        }
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("JSON object fields must be key-value pairs");
        }
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < fields.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escape(String.valueOf(fields[i]))).append("\":");
            appendJsonValue(builder, fields[i + 1]);
        }
        return builder.append('}').toString();
    }

    private static void appendJsonValue(StringBuilder builder, Object value) {
        if (value instanceof RawJson rawJson) {
            builder.append(rawJson.value());
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        builder.append('"').append(escape(value == null ? "" : String.valueOf(value))).append('"');
    }

    static Object rawJson(String value) {
        return new RawJson(value == null || value.isBlank() ? "{}" : value);
    }

    private record RawJson(String value) {
    }

    static String warpPayload(UUID islandId, UUID actorUuid, String name, String category, IslandLocation location, boolean publicAccess) {
        if (category == null || category.isBlank()) {
            return jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "name", name,
                "worldName", location.worldName(),
                "localX", location.localX(),
                "localY", location.localY(),
                "localZ", location.localZ(),
                "yaw", location.yaw(),
                "pitch", location.pitch(),
                "publicAccess", publicAccess
            );
        }
        return jsonObject(
            "islandId", islandId,
            "actorUuid", actorUuid,
            "name", name,
            "category", category,
            "worldName", location.worldName(),
            "localX", location.localX(),
            "localY", location.localY(),
            "localZ", location.localZ(),
            "yaw", location.yaw(),
            "pitch", location.pitch(),
            "publicAccess", publicAccess
        );
    }

    static String locationPayload(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        return jsonObject(
            "islandId", islandId,
            "actorUuid", actorUuid,
            "name", name,
            "worldName", location.worldName(),
            "localX", location.localX(),
            "localY", location.localY(),
            "localZ", location.localZ(),
            "yaw", location.yaw(),
            "pitch", location.pitch()
        );
    }

    private static String normalizeRoleKey(String roleKey) {
        return roleKey == null ? "" : roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static String pathNodeId(String nodeId) {
        String value = nodeId == null ? "" : nodeId.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        if (value.contains("/") || value.contains("\\") || value.contains("?") || value.contains("#")) {
            throw new IllegalArgumentException("nodeId must be a single path segment");
        }
        return value;
    }

    private static final class RouteSessionJson {
        static PlayerRouteSession parse(String json) {
            Map<?, ?> root = resultObject(json);
            return new PlayerRouteSession(
                uuid(root, "playerUuid", new UUID(0L, 0L)),
                uuid(root, "ticketId", new UUID(0L, 0L)),
                text(root, "targetNode", ""),
                text(root, "targetServerName", ""),
                text(root, "nonce", ""),
                Instant.parse(text(root, "expiresAt", Instant.now().toString()))
            );
        }
    }

    private static final class RouteTicketJson {
        private RouteTicketJson() {}

        static RouteTicket parseNested(String json, String field) {
            if (json == null || json.isBlank()) {
                return null;
            }
            Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
            Map<?, ?> nested = SimpleJson.object(root.get(field));
            if (nested.isEmpty()) {
                return null;
            }
            return parseObject(nested);
        }

        static RouteTicket parse(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            Map<?, ?> ticket = ticketObject(SimpleJson.parse(json));
            return ticket.isEmpty() ? null : parseObject(ticket);
        }

        private static Map<?, ?> ticketObject(Object value) {
            if (value instanceof Map<?, ?> map) {
                if (map.containsKey("ticketId")) {
                    return map;
                }
                Map<?, ?> nestedTicket = SimpleJson.object(map.get("ticket"));
                if (!nestedTicket.isEmpty()) {
                    return nestedTicket;
                }
                for (Object nested : map.values()) {
                    Map<?, ?> found = ticketObject(nested);
                    if (!found.isEmpty()) {
                        return found;
                    }
                }
            }
            if (value instanceof List<?> list) {
                for (Object nested : list) {
                    Map<?, ?> found = ticketObject(nested);
                    if (!found.isEmpty()) {
                        return found;
                    }
                }
            }
            return Map.of();
        }

        private static RouteTicket parseObject(Map<?, ?> ticket) {
            UUID ticketId = uuid(ticket, "ticketId", UUID.randomUUID());
            UUID playerUuid = uuid(ticket, "playerUuid", new UUID(0L, 0L));
            UUID islandId = uuid(ticket, "islandId", new UUID(0L, 0L));
            RouteAction action = enumValue(RouteAction.class, text(ticket, "action", "HOME"), RouteAction.HOME);
            RouteTicketState state = enumValue(RouteTicketState.class, text(ticket, "state", "READY"), RouteTicketState.READY);
            String targetNode = text(ticket, "targetNode", "");
            String targetWorld = text(ticket, "targetWorld", "ci_shard_001");
            String nonce = text(ticket, "nonce", "");
            String serverName = text(ticket, "targetServerName", targetNode);
            java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("targetServerName", serverName);
            putIfPresent(payload, ticket, "targetType");
            putIfPresent(payload, ticket, "homeName");
            putIfPresent(payload, ticket, "warpName");
            putIfPresent(payload, ticket, "localX");
            putIfPresent(payload, ticket, "localY");
            putIfPresent(payload, ticket, "localZ");
            putIfPresent(payload, ticket, "yaw");
            putIfPresent(payload, ticket, "pitch");
            Instant expiresAt = Instant.parse(text(ticket, "expiresAt", Instant.now().plusSeconds(30).toString()));
            return new RouteTicket(ticketId, playerUuid, action, islandId, targetNode, targetWorld, state, expiresAt, nonce, Map.copyOf(payload));
        }

        private static void putIfPresent(Map<String, String> payload, Map<?, ?> ticket, String field) {
            if (ticket.containsKey(field)) {
                String value = SimpleJson.text(ticket.get(field));
                payload.put(field, value);
            }
        }

        private static String text(Map<?, ?> ticket, String field, String fallback) {
            String value = SimpleJson.text(ticket.get(field));
            return value.isBlank() ? fallback : value;
        }

        private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
            try {
                return Enum.valueOf(type, value);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
