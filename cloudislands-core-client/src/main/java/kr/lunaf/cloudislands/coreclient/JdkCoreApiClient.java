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
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
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
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.json.IslandJobJson;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class JdkCoreApiClient implements CoreApiClient {
    private final URI baseUri;
    private final String authToken;
    private final String adminToken;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final JdkBankClient bankClient;
    private final JdkSnapshotClient snapshotClient;
    private final JdkCommunicationClient communicationClient;
    private final JdkEnvironmentClient environmentClient;
    private final JdkSettingsClient settingsClient;
    private final JdkHomeWarpClient homeWarpClient;
    private final JdkRoutingClient routingClient;
    private final JdkRuntimeClient runtimeClient;
    private final JdkIslandClient islandClient;
    private final JdkMemberQueryClient memberQueryClient;
    private final JdkMemberCommandClient memberCommandClient;
    private final JdkWarehouseClient warehouseClient;
    private final JdkPlayerProfileClient playerProfileClient;
    private final JdkTemplateClient templateClient;
    private final JdkJobClient jobClient;
    private final JdkBlockValueClient blockValueClient;
    private final JdkAdminMetricsClient adminMetricsClient;
    private final JdkAdminCoreConfigClient adminCoreConfigClient;
    private final JdkAdminStorageClient adminStorageClient;
    private final JdkAdminEventClient adminEventClient;
    private final JdkAdminAuditClient adminAuditClient;
    private final JdkAdminRouteClient adminRouteClient;
    private final JdkAdminAddonStateClient adminAddonStateClient;
    private final JdkAdminMaintenanceClient adminMaintenanceClient;
    private final JdkAdminNodeClient adminNodeClient;
    private final JdkAdminIslandClient adminIslandClient;

    public JdkCoreApiClient(URI baseUri, String authToken, Duration timeout) {
        this(baseUri, authToken, System.getenv().getOrDefault("CI_ADMIN_TOKEN", ""), timeout);
    }

    public JdkCoreApiClient(URI baseUri, String authToken, String adminToken, Duration timeout) {
        this.baseUri = baseUri;
        this.authToken = authToken;
        this.adminToken = adminToken == null ? "" : adminToken;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(5) : timeout;
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
        this.bankClient = new JdkBankClient();
        this.snapshotClient = new JdkSnapshotClient();
        this.communicationClient = new JdkCommunicationClient();
        this.environmentClient = new JdkEnvironmentClient();
        this.settingsClient = new JdkSettingsClient();
        this.homeWarpClient = new JdkHomeWarpClient();
        this.routingClient = new JdkRoutingClient();
        this.runtimeClient = new JdkRuntimeClient();
        this.islandClient = new JdkIslandClient();
        this.memberQueryClient = new JdkMemberQueryClient();
        this.memberCommandClient = new JdkMemberCommandClient();
        this.warehouseClient = new JdkWarehouseClient();
        this.playerProfileClient = new JdkPlayerProfileClient();
        this.templateClient = new JdkTemplateClient();
        this.jobClient = new JdkJobClient();
        this.blockValueClient = new JdkBlockValueClient();
        this.adminMetricsClient = new JdkAdminMetricsClient();
        this.adminCoreConfigClient = new JdkAdminCoreConfigClient();
        this.adminStorageClient = new JdkAdminStorageClient();
        this.adminEventClient = new JdkAdminEventClient();
        this.adminAuditClient = new JdkAdminAuditClient();
        this.adminRouteClient = new JdkAdminRouteClient();
        this.adminAddonStateClient = new JdkAdminAddonStateClient();
        this.adminMaintenanceClient = new JdkAdminMaintenanceClient();
        this.adminNodeClient = new JdkAdminNodeClient();
        this.adminIslandClient = new JdkAdminIslandClient();
    }

    @Override
    public BankQueryClient bank() {
        return bankClient;
    }

    @Override
    public BankCommandClient bankCommands() {
        return bankClient;
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
        return communicationClient;
    }

    @Override
    public CommunicationCommandClient communicationCommands() {
        return communicationClient;
    }

    @Override
    public IslandEnvironmentQueryClient environment() {
        return environmentClient;
    }

    @Override
    public IslandEnvironmentCommandClient environmentCommands() {
        return environmentClient;
    }

    @Override
    public IslandSettingsCommandClient settingsCommands() {
        return settingsClient;
    }

    @Override
    public HomeWarpQueryClient homeWarps() {
        return homeWarpClient;
    }

    @Override
    public HomeWarpCommandClient homeWarpCommands() {
        return homeWarpClient;
    }

    @Override
    public RoutingCommandClient routingCommands() {
        return routingClient;
    }

    @Override
    public RuntimeCommandClient runtimeCommands() {
        return runtimeClient;
    }

    @Override
    public WarehouseQueryClient warehouse() {
        return warehouseClient;
    }

    @Override
    public WarehouseCommandClient warehouseCommands() {
        return warehouseClient;
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
    public PlayerProfileQueryClient playerProfiles() {
        return playerProfileClient;
    }

    @Override
    public PlayerProfileCommandClient playerProfileCommands() {
        return playerProfileClient;
    }

    @Override
    public TemplateQueryClient templates() {
        return templateClient;
    }

    @Override
    public TemplateCommandClient templateCommands() {
        return templateClient;
    }

    @Override
    public JobQueryClient jobs() {
        return jobClient;
    }

    @Override
    public JobCommandClient jobCommands() {
        return jobClient;
    }

    @Override
    public BlockValueQueryClient blockValues() {
        return blockValueClient;
    }

    @Override
    public BlockValueCommandClient blockValueCommands() {
        return blockValueClient;
    }

    @Override
    public AdminMetricsQueryClient adminMetrics() {
        return adminMetricsClient;
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
    public AdminMaintenanceCommandClient adminMaintenance() {
        return adminMaintenanceClient;
    }

    @Override
    public AdminNodeQueryClient adminNodes() {
        return adminNodeClient;
    }

    @Override
    public AdminNodeCommandClient adminNodeCommands() {
        return adminNodeClient;
    }

    @Override
    public AdminIslandQueryClient adminIslands() {
        return adminIslandClient;
    }

    @Override
    public CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId) {
        return post("/v1/islands", jsonObject("playerUuid", playerUuid, "templateId", templateId))
            .thenApply(JdkCoreApiClient::parseCreateIslandResult);
    }

    @Override
    public CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId) {
        return deleteWithResultBody("/v1/islands/" + islandId + "?requesterUuid=" + requesterUuid)
            .thenApply(body -> parseDeleteIslandResult(body, islandId));
    }

    @Override
    public CompletableFuture<String> resetIsland(UUID islandId, UUID actorUuid, String reason) {
        return resetIslandResult(islandId, actorUuid, reason);
    }

    @Override
    public CompletableFuture<String> resetIslandResult(UUID islandId, UUID actorUuid, String reason) {
        return postWithResultBody("/v1/islands/reset", jsonObject("islandId", islandId, "actorUuid", actorUuid, "reason", reason));
    }

    @Override
    public CompletableFuture<String> islandInfo(UUID islandId) {
        return post("/v1/islands/info", jsonObject("islandId", islandId));
    }

    @Override
    public CompletableFuture<String> islandInfoByOwner(UUID ownerUuid) {
        return post("/v1/islands/info", jsonObject("ownerUuid", ownerUuid));
    }

    @Override
    public CompletableFuture<String> islandInfoByName(String name) {
        return post("/v1/islands/info", jsonObject("name", name));
    }

    @Override
    public CompletableFuture<String> getIsland(UUID islandId) {
        return get("/v1/islands/" + islandId);
    }

    @Override
    public CompletableFuture<String> getIslandByOwner(UUID ownerUuid) {
        return get("/v1/islands/by-owner/" + ownerUuid);
    }

    @Override
    public CompletableFuture<String> getIslandMembers(UUID islandId) {
        return get("/v1/islands/" + islandId + "/members");
    }

    @Override
    public CompletableFuture<String> getIslandRuntime(UUID islandId) {
        return get("/v1/islands/" + islandId + "/runtime");
    }

    @Override
    public CompletableFuture<String> getIslandFlags(UUID islandId) {
        return get("/v1/islands/" + islandId + "/flags");
    }

    @Override
    public CompletableFuture<String> getIslandLevel(UUID islandId) {
        return get("/v1/islands/" + islandId + "/level");
    }

    @Override
    public CompletableFuture<String> getPlayerProfile(UUID playerUuid) {
        return get("/v1/players/" + playerUuid + "/profile");
    }

    @Override
    public CompletableFuture<String> getPlayerIsland(UUID playerUuid) {
        return get("/v1/players/" + playerUuid + "/island");
    }

    @Override
    public CompletableFuture<String> getJoinedIslands(UUID playerUuid) {
        return get("/v1/players/" + playerUuid + "/islands");
    }

    @Override
    public CompletableFuture<Void> setIslandName(UUID islandId, UUID actorUuid, String name) {
        return setIslandNameResult(islandId, actorUuid, name).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandNameResult(UUID islandId, UUID actorUuid, String name) {
        return postWithResultBody("/v1/islands/name", jsonObject("islandId", islandId, "actorUuid", actorUuid, "name", name));
    }

    @Override
    public CompletableFuture<String> listIslandMembers(UUID islandId) {
        return get("/v1/islands/" + islandId + "/members");
    }

    @Override
    public CompletableFuture<Void> setIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid, IslandRole role) {
        return setIslandMemberResult(islandId, actorUuid, playerUuid, role).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid, IslandRole role) {
        return setIslandMemberResult(islandId, actorUuid, playerUuid, role.name());
    }

    @Override
    public CompletableFuture<String> setIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid, String roleKey) {
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        return postWithResultBody("/v1/islands/members/set", jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", playerUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey));
    }

    @Override
    public CompletableFuture<String> trustIslandMemberTemporary(UUID islandId, UUID actorUuid, UUID playerUuid, long durationSeconds) {
        return postWithResultBody("/v1/islands/members/trust-temporary", jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", playerUuid, "durationSeconds", durationSeconds));
    }

    @Override
    public CompletableFuture<Void> transferIslandOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return transferIslandOwnershipResult(islandId, actorUuid, targetUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> transferIslandOwnershipResult(UUID islandId, UUID actorUuid, UUID targetUuid) {
        return postWithResultBody("/v1/islands/transfer", jsonObject("islandId", islandId, "actorUuid", actorUuid, "targetUuid", targetUuid));
    }

    @Override
    public CompletableFuture<Void> removeIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return removeIslandMemberResult(islandId, actorUuid, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> removeIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return postWithResultBody("/v1/islands/members/remove", jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<String> createIslandInvite(UUID islandId, UUID inviterUuid, UUID targetUuid) {
        return postWithResultBody("/v1/islands/invites", jsonObject("islandId", islandId, "inviterUuid", inviterUuid, "targetUuid", targetUuid));
    }

    @Override
    public CompletableFuture<String> listPendingInvites(UUID playerUuid) {
        return post("/v1/players/invites", jsonObject("playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<String> listPlayerIslands(UUID playerUuid) {
        return post("/v1/players/islands", jsonObject("playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<Void> acceptIslandInvite(UUID inviteId, UUID playerUuid) {
        return acceptIslandInviteResult(inviteId, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> acceptIslandInviteResult(UUID inviteId, UUID playerUuid) {
        return postWithResultBody("/v1/islands/invites/accept", jsonObject("inviteId", inviteId, "playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<Void> declineIslandInvite(UUID inviteId, UUID playerUuid) {
        return declineIslandInviteResult(inviteId, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> declineIslandInviteResult(UUID inviteId, UUID playerUuid) {
        return postWithResultBody("/v1/islands/invites/decline", jsonObject("inviteId", inviteId, "playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<Void> banIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        return banIslandVisitorResult(islandId, actorUuid, playerUuid, reason).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> banIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        return postWithResultBody("/v1/islands/bans/set", jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", playerUuid, "reason", reason));
    }

    @Override
    public CompletableFuture<String> listIslandBans(UUID islandId) {
        return get("/v1/islands/" + islandId + "/bans");
    }

    @Override
    public CompletableFuture<Void> pardonIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return pardonIslandVisitorResult(islandId, actorUuid, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> pardonIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return postWithResultBody("/v1/islands/bans/remove", jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<Void> kickIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return kickIslandVisitorResult(islandId, actorUuid, playerUuid).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> kickIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return postWithResultBody("/v1/islands/visitors/kick", jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<String> islandVisitorStats(UUID islandId, int limit) {
        return postWithResultBody("/v1/islands/visitors/stats", jsonObject("islandId", islandId, "limit", Math.max(1, Math.min(limit, 100))));
    }

    @Override
    public CompletableFuture<String> listIslandFlags(UUID islandId) {
        return get("/v1/islands/" + islandId + "/flags");
    }

    @Override
    public CompletableFuture<Void> setIslandFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        return setIslandFlagResult(islandId, actorUuid, flag, value).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandFlagResult(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        return postWithResultBody("/v1/islands/flags/set", jsonObject("islandId", islandId, "actorUuid", actorUuid, "flag", flag.name(), "value", value));
    }

    @Override
    public CompletableFuture<String> islandBiome(UUID islandId) {
        return get("/v1/islands/" + islandId + "/biome");
    }

    @Override
    public CompletableFuture<Void> setIslandBiome(UUID islandId, UUID actorUuid, String biomeKey) {
        return setIslandBiomeResult(islandId, actorUuid, biomeKey).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandBiomeResult(UUID islandId, UUID actorUuid, String biomeKey) {
        return postWithResultBody("/v1/islands/biome/set", jsonObject("islandId", islandId, "actorUuid", actorUuid, "biomeKey", biomeKey));
    }

    @Override
    public CompletableFuture<String> listIslandHomes(UUID islandId) {
        return get("/v1/islands/" + islandId + "/homes");
    }

    @Override
    public CompletableFuture<Void> setIslandHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        return setIslandHomeResult(islandId, actorUuid, name, location).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandHomeResult(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        return postWithResultBody("/v1/islands/homes/set", locationPayload(islandId, actorUuid, name, location));
    }

    @Override
    public CompletableFuture<String> listIslandPermissions(UUID islandId) {
        return get("/v1/islands/" + islandId + "/permissions");
    }

    @Override
    public CompletableFuture<Void> setIslandPermission(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) {
        return setIslandPermissionResult(islandId, actorUuid, role, permission, allowed).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandPermissionResult(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) {
        return setIslandPermissionResult(islandId, actorUuid, role.name(), permission, allowed);
    }

    @Override
    public CompletableFuture<String> setIslandPermissionResult(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed) {
        return setIslandPermissionResult(islandId, actorUuid, roleKey, permission, allowed, "");
    }

    @Override
    public CompletableFuture<String> setIslandPermissionResult(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed, String expectedVersion) {
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        String payload = expectedVersion == null || expectedVersion.isBlank()
            ? jsonObject("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey, "permission", permission.name(), "allowed", allowed)
            : jsonObject("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey, "permission", permission.name(), "allowed", allowed, "expectedVersion", expectedVersion);
        return postWithResultBody("/v1/islands/permissions/set", payload);
    }

    @Override
    public CompletableFuture<String> setIslandPermissionOverride(UUID islandId, UUID actorUuid, UUID playerUuid, IslandPermission permission, boolean allowed) {
        return postWithResultBody("/v1/islands/permissions/overrides/set", jsonObject("islandId", islandId, "actorUuid", actorUuid, "playerUuid", playerUuid, "permission", permission.name(), "allowed", allowed));
    }

    @Override
    public CompletableFuture<String> listIslandRoles(UUID islandId) {
        return get("/v1/islands/" + islandId + "/roles");
    }

    @Override
    public CompletableFuture<String> upsertIslandRole(UUID islandId, UUID actorUuid, IslandRole role, int weight, String displayName) {
        return upsertIslandRole(islandId, actorUuid, role.name(), weight, displayName);
    }

    @Override
    public CompletableFuture<String> upsertIslandRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName) {
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        return postWithResultBody("/v1/islands/roles/upsert", jsonObject("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey, "weight", weight, "displayName", displayName));
    }

    @Override
    public CompletableFuture<String> resetIslandRole(UUID islandId, UUID actorUuid, IslandRole role) {
        return resetIslandRole(islandId, actorUuid, role.name());
    }

    @Override
    public CompletableFuture<String> resetIslandRole(UUID islandId, UUID actorUuid, String roleKey) {
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        return postWithResultBody("/v1/islands/roles/reset", jsonObject("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey));
    }

    @Override
    public CompletableFuture<String> listIslandWarps(UUID islandId) {
        return get("/v1/islands/" + islandId + "/warps");
    }

    @Override
    public CompletableFuture<String> listPublicWarps(int limit) {
        return post("/v1/islands/public-warps", jsonObject("limit", limit));
    }

    @Override
    public CompletableFuture<String> listPublicWarps(int limit, String category, String query) {
        return post("/v1/islands/public-warps", jsonObject("limit", limit, "category", category, "query", query));
    }

    @Override
    public CompletableFuture<String> listIslandReviews(UUID islandId, int limit) {
        return post("/v1/islands/reviews", jsonObject("islandId", islandId, "limit", limit));
    }

    @Override
    public CompletableFuture<String> setIslandReview(UUID islandId, UUID reviewerUuid, int rating, String comment) {
        return postWithResultBody("/v1/islands/reviews/set", jsonObject("islandId", islandId, "reviewerUuid", reviewerUuid, "rating", rating, "comment", comment));
    }

    @Override
    public CompletableFuture<String> deleteIslandReview(UUID islandId, UUID reviewerUuid) {
        return postWithResultBody("/v1/islands/reviews/delete", jsonObject("islandId", islandId, "reviewerUuid", reviewerUuid));
    }

    @Override
    public CompletableFuture<String> islandWarehouse(UUID islandId, int limit) {
        return post("/v1/islands/warehouse", jsonObject("islandId", islandId, "limit", limit));
    }

    @Override
    public CompletableFuture<String> depositIslandWarehouse(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        return postWithResultBody("/v1/islands/warehouse/deposit", jsonObject("islandId", islandId, "actorUuid", actorUuid, "materialKey", materialKey, "amount", amount));
    }

    @Override
    public CompletableFuture<String> withdrawIslandWarehouse(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        return postWithResultBody("/v1/islands/warehouse/withdraw", jsonObject("islandId", islandId, "actorUuid", actorUuid, "materialKey", materialKey, "amount", amount));
    }

    @Override
    public CompletableFuture<Void> setIslandWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
        return setIslandWarpResult(islandId, actorUuid, name, location, publicAccess).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
        return postWithResultBody("/v1/islands/warps/set", warpPayload(islandId, actorUuid, name, "", location, publicAccess));
    }

    @Override
    public CompletableFuture<String> setIslandWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, String category) {
        return postWithResultBody("/v1/islands/warps/set", warpPayload(islandId, actorUuid, name, category, location, publicAccess));
    }

    @Override
    public CompletableFuture<Void> deleteIslandWarp(UUID islandId, UUID actorUuid, String name) {
        return deleteIslandWarpResult(islandId, actorUuid, name).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> deleteIslandWarpResult(UUID islandId, UUID actorUuid, String name) {
        return postWithResultBody("/v1/islands/warps/delete", jsonObject("islandId", islandId, "actorUuid", actorUuid, "name", name));
    }

    @Override
    public CompletableFuture<Void> setIslandWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess) {
        return setIslandWarpPublicAccessResult(islandId, actorUuid, name, publicAccess).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandWarpPublicAccessResult(UUID islandId, UUID actorUuid, String name, boolean publicAccess) {
        return postWithResultBody("/v1/islands/warps/access", jsonObject("islandId", islandId, "actorUuid", actorUuid, "name", name, "publicAccess", publicAccess));
    }

    @Override
    public CompletableFuture<Void> setIslandPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess) {
        return setIslandPublicAccessResult(islandId, actorUuid, publicAccess).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandPublicAccessResult(UUID islandId, UUID actorUuid, boolean publicAccess) {
        return postWithResultBody("/v1/islands/access", jsonObject("islandId", islandId, "actorUuid", actorUuid, "publicAccess", publicAccess));
    }

    @Override
    public CompletableFuture<Void> setIslandLocked(UUID islandId, UUID actorUuid, boolean locked) {
        return setIslandLockedResult(islandId, actorUuid, locked).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setIslandLockedResult(UUID islandId, UUID actorUuid, boolean locked) {
        return postWithResultBody("/v1/islands/lock", jsonObject("islandId", islandId, "actorUuid", actorUuid, "locked", locked));
    }

    @Override
    public CompletableFuture<Void> recordBlockDelta(UUID islandId, String materialKey, long delta) {
        return recordBlockDeltaResult(islandId, materialKey, delta).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> recordBlockDeltaResult(UUID islandId, String materialKey, long delta) {
        return postWithResultBody("/v1/islands/blocks/delta", jsonObject("islandId", islandId, "materialKey", materialKey, "delta", delta));
    }

    @Override
    public CompletableFuture<String> replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        return postWithResultBody("/v1/islands/blocks/replace", jsonObject("islandId", islandId, "counts", countsPayload(counts)));
    }

    @Override
    public CompletableFuture<String> islandBlockDetails(UUID islandId, int limit) {
        return post("/v1/islands/blocks", jsonObject("islandId", islandId, "limit", limit));
    }

    @Override
    public CompletableFuture<String> recalculateIslandLevel(UUID islandId, UUID actorUuid) {
        return post("/v1/islands/level/recalculate", jsonObject("islandId", islandId, "actorUuid", actorUuid));
    }

    @Override
    public CompletableFuture<String> topIslandsByLevel(int limit) {
        return post("/v1/rankings/level", jsonObject("limit", limit));
    }

    @Override
    public CompletableFuture<String> topIslandsByWorth(int limit) {
        return post("/v1/rankings/worth", jsonObject("limit", limit));
    }

    @Override
    public CompletableFuture<String> topIslandsByReviews(int limit) {
        return post("/v1/rankings/reviews", jsonObject("limit", limit));
    }

    @Override
    public CompletableFuture<String> listPublicIslands(int limit) {
        return post("/v1/islands/public", jsonObject("limit", limit));
    }

    @Override
    public CompletableFuture<Void> setBlockValue(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) {
        return setBlockValueResult(actorUuid, materialKey, worth, levelPoints, limit).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> setBlockValueResult(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) {
        return postWithResultBody("/v1/admin/block-values", jsonObject("actorUuid", actorUuid, "materialKey", materialKey, "worth", worth, "levelPoints", levelPoints, "limit", limit));
    }

    @Override
    public CompletableFuture<String> listBlockValues() {
        return postWithResultBody("/v1/admin/block-values/list", "{}");
    }

    @Override
    public CompletableFuture<String> listUpgradeRules() {
        return post("/v1/upgrades/rules", "{}");
    }

    @Override
    public CompletableFuture<String> listIslandUpgrades(UUID islandId) {
        return post("/v1/islands/upgrades", jsonObject("islandId", islandId));
    }

    @Override
    public CompletableFuture<String> purchaseIslandUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) {
        return postWithResultBody("/v1/islands/upgrades/purchase", jsonObject("islandId", islandId, "actorUuid", actorUuid, "upgradeKey", upgradeKey));
    }

    @Override
    public CompletableFuture<String> listIslandMissions(UUID islandId, String kind) {
        return post("/v1/islands/missions", jsonObject("islandId", islandId, "kind", kind));
    }

    @Override
    public CompletableFuture<String> completeIslandMission(UUID islandId, UUID actorUuid, String missionKey) {
        return completeIslandMission(islandId, actorUuid, missionKey, "MISSION");
    }

    @Override
    public CompletableFuture<String> completeIslandMission(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        return postWithResultBody("/v1/islands/missions/complete", jsonObject("islandId", islandId, "actorUuid", actorUuid, "missionKey", missionKey, "kind", kind));
    }

    @Override
    public CompletableFuture<String> progressIslandMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        return postWithResultBody("/v1/islands/missions/progress", jsonObject("islandId", islandId, "actorUuid", actorUuid, "missionKey", missionKey, "kind", kind, "amount", Math.max(0L, amount)));
    }

    @Override
    public CompletableFuture<String> registerMissionProvider(String providerId, String definitionsJson) {
        String definitions = definitionsJson == null || definitionsJson.isBlank() ? "[]" : definitionsJson;
        return postWithResultBody("/v1/addons/missions/register", jsonObject("providerId", providerId, "missions", rawJson(definitions)));
    }

    @Override
    public CompletableFuture<String> listIslandLimits(UUID islandId) {
        return post("/v1/islands/limits", jsonObject("islandId", islandId));
    }

    @Override
    public CompletableFuture<String> setIslandLimit(UUID islandId, UUID actorUuid, String limitKey, long value) {
        return post("/v1/islands/limits/set", jsonObject("islandId", islandId, "actorUuid", actorUuid, "limitKey", limitKey, "value", value));
    }

    @Override
    public CompletableFuture<String> sendIslandChat(UUID islandId, UUID actorUuid, String channel, String message) {
        return post("/v1/islands/chat", jsonObject("islandId", islandId, "actorUuid", actorUuid, "channel", channel, "message", message));
    }

    @Override
    public CompletableFuture<String> listIslandSnapshots(UUID islandId, int limit) {
        return post("/v1/islands/snapshots", jsonObject("islandId", islandId, "limit", limit));
    }

    @Override
    public CompletableFuture<String> recordIslandSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId) {
        return postWithResultBody("/v1/islands/snapshots/record", jsonObject("islandId", islandId, "snapshotNo", snapshotNo, "storagePath", storagePath, "reason", reason, "checksum", checksum, "sizeBytes", sizeBytes, "nodeId", nodeId));
    }

    @Override
    public CompletableFuture<String> recordIslandSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
        return postWithResultBody("/v1/islands/snapshots/record", jsonObject("islandId", islandId, "snapshotNo", snapshotNo, "storagePath", storagePath, "reason", reason, "checksum", checksum, "sizeBytes", sizeBytes, "nodeId", nodeId, "fencingToken", fencingToken));
    }

    @Override
    public CompletableFuture<String> requestIslandSaveResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/save", jsonObject("islandId", islandId, "reason", reason));
    }

    @Override
    public CompletableFuture<Void> requestIslandSnapshot(UUID islandId, String reason) {
        return requestIslandSnapshotResult(islandId, reason).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> requestIslandSnapshotResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/snapshot", jsonObject("islandId", islandId, "reason", reason));
    }

    @Override
    public CompletableFuture<Void> restoreIslandSnapshot(UUID islandId, long snapshotNo) {
        return restoreIslandSnapshotResult(islandId, snapshotNo).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> restoreIslandSnapshotResult(UUID islandId, long snapshotNo) {
        return postWithResultBody("/v1/admin/islands/restore", jsonObject("islandId", islandId, "snapshotNo", snapshotNo));
    }

    @Override
    public CompletableFuture<String> rollbackIslandSnapshotResult(UUID islandId, long snapshotNo) {
        return postWithResultBody("/v1/admin/islands/rollback", jsonObject("islandId", islandId, "snapshotNo", snapshotNo));
    }

    @Override
    public CompletableFuture<String> listIslandLogs(UUID islandId, int limit) {
        return post("/v1/islands/logs", jsonObject("islandId", islandId, "limit", limit));
    }

    @Override
    public CompletableFuture<String> islandBank(UUID islandId) {
        return post("/v1/islands/bank", jsonObject("islandId", islandId));
    }

    @Override
    public CompletableFuture<String> depositIslandBank(UUID islandId, UUID actorUuid, String amount) {
        return postWithResultBody("/v1/islands/bank/deposit", jsonObject("islandId", islandId, "actorUuid", actorUuid, "amount", amount));
    }

    @Override
    public CompletableFuture<String> withdrawIslandBank(UUID islandId, UUID actorUuid, String amount) {
        return postWithResultBody("/v1/islands/bank/withdraw", jsonObject("islandId", islandId, "actorUuid", actorUuid, "amount", amount));
    }

    private final class JdkBankClient implements BankQueryClient, BankCommandClient {
        @Override
        public CompletableFuture<kr.lunaf.cloudislands.api.model.IslandBankSnapshot> snapshot(UUID islandId) {
            requireId(islandId, "islandId");
            return post("/v1/islands/bank", jsonObject("islandId", islandId))
                .thenApply(CoreBankJson::snapshot);
        }

        @Override
        public CompletableFuture<kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot> depositSnapshot(UUID islandId, UUID actorUuid, String amount) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return postWithResultBody("/v1/islands/bank/deposit", jsonObject("islandId", islandId, "actorUuid", actorUuid, "amount", amount == null ? "" : amount))
                .thenApply(CoreBankJson::mutation);
        }

        @Override
        public CompletableFuture<kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot> withdrawSnapshot(UUID islandId, UUID actorUuid, String amount) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return postWithResultBody("/v1/islands/bank/withdraw", jsonObject("islandId", islandId, "actorUuid", actorUuid, "amount", amount == null ? "" : amount))
                .thenApply(CoreBankJson::mutation);
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkSnapshotClient implements SnapshotQueryClient, SnapshotCommandClient {
        @Override
        public CompletableFuture<List<IslandSnapshotRecord>> records(UUID islandId, int limit) {
            requireId(islandId, "islandId");
            return post("/v1/islands/snapshots", jsonObject("islandId", islandId, "limit", Math.max(1, Math.min(limit, 100))))
                .thenApply(CoreSnapshotJson::records);
        }

        @Override
        public CompletableFuture<SnapshotActionView> recordSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
            requireId(islandId, "islandId");
            if (snapshotNo <= 0L) {
                throw new IllegalArgumentException("positive snapshotNo is required");
            }
            String payload = fencingToken > 0L
                ? jsonObject(
                    "islandId", islandId,
                    "snapshotNo", snapshotNo,
                    "storagePath", storagePath == null ? "" : storagePath.trim(),
                    "reason", reason == null || reason.isBlank() ? "manual" : reason.trim(),
                    "checksum", checksum == null ? "" : checksum.trim(),
                    "sizeBytes", Math.max(0L, sizeBytes),
                    "nodeId", nodeId == null ? "" : nodeId.trim(),
                    "fencingToken", fencingToken
                )
                : jsonObject(
                    "islandId", islandId,
                    "snapshotNo", snapshotNo,
                    "storagePath", storagePath == null ? "" : storagePath.trim(),
                    "reason", reason == null || reason.isBlank() ? "manual" : reason.trim(),
                    "checksum", checksum == null ? "" : checksum.trim(),
                    "sizeBytes", Math.max(0L, sizeBytes),
                    "nodeId", nodeId == null ? "" : nodeId.trim()
                );
            return postWithResultBody(
                    "/v1/islands/snapshots/record",
                    payload
                )
                .thenApply(body -> CoreSnapshotJson.action(body, "SNAPSHOT_RECORDED"));
        }

        @Override
        public CompletableFuture<SnapshotActionView> requestSnapshot(UUID islandId, String reason) {
            requireId(islandId, "islandId");
            return postWithResultBody("/v1/admin/islands/snapshot", jsonObject("islandId", islandId, "reason", reason == null || reason.isBlank() ? "manual" : reason.trim()))
                .thenApply(body -> CoreSnapshotJson.action(body, "SNAPSHOT_REQUESTED"));
        }

        @Override
        public CompletableFuture<SnapshotActionView> restoreSnapshot(UUID islandId, long snapshotNo) {
            requireId(islandId, "islandId");
            if (snapshotNo <= 0L) {
                throw new IllegalArgumentException("positive snapshotNo is required");
            }
            return postWithResultBody("/v1/admin/islands/restore", jsonObject("islandId", islandId, "snapshotNo", snapshotNo))
                .thenApply(body -> CoreSnapshotJson.action(body, "RESTORE_REQUESTED"));
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkCommunicationClient implements CommunicationQueryClient, CommunicationCommandClient {
        @Override
        public CompletableFuture<List<IslandLogRecord>> records(UUID islandId, int limit) {
            requireId(islandId, "islandId");
            return post("/v1/islands/logs", jsonObject("islandId", islandId, "limit", Math.max(1, Math.min(limit, 100))))
                .thenApply(CoreCommunicationJson::records);
        }

        @Override
        public CompletableFuture<ChatActionView> sendChat(UUID islandId, UUID actorUuid, String channel, String message) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            String normalizedChannel = channel == null || channel.isBlank() ? "ISLAND" : channel.trim().toUpperCase(java.util.Locale.ROOT);
            String normalizedMessage = message == null ? "" : message.trim();
            if (normalizedMessage.isBlank()) {
                throw new IllegalArgumentException("message is required");
            }
            return postWithResultBody("/v1/islands/chat", jsonObject("islandId", islandId, "actorUuid", actorUuid, "channel", normalizedChannel, "message", normalizedMessage))
                .thenApply(body -> CoreCommunicationJson.chatAction(body, "CHAT_SENT"));
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkEnvironmentClient implements IslandEnvironmentQueryClient, IslandEnvironmentCommandClient {
        @Override
        public CompletableFuture<IslandBiomeSnapshot> biome(UUID islandId) {
            requireId(islandId, "islandId");
            return post("/v1/islands/biome", jsonObject("islandId", islandId))
                .thenApply(body -> CoreEnvironmentJson.biome(islandId, body));
        }

        @Override
        public CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId) {
            requireId(islandId, "islandId");
            return islandInfo(islandId).thenApply(CoreGuiViews::islandInfoView);
        }

        @Override
        public CompletableFuture<IslandFlagsSnapshot> flags(UUID islandId) {
            requireId(islandId, "islandId");
            return get("/v1/islands/" + islandId + "/flags")
                .thenApply(body -> CoreEnvironmentJson.flags(islandId, body));
        }

        @Override
        public CompletableFuture<List<IslandLimitSnapshot>> limits(UUID islandId) {
            requireId(islandId, "islandId");
            return post("/v1/islands/limits", jsonObject("islandId", islandId))
                .thenApply(body -> CoreEnvironmentJson.limits(islandId, body));
        }

        @Override
        public CompletableFuture<EnvironmentActionView> setBiome(UUID islandId, UUID actorUuid, String biomeKey) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return JdkCoreApiClient.this.setIslandBiomeResult(islandId, actorUuid, biomeKey == null ? "" : biomeKey)
                .thenApply(body -> actionResult(body, "BIOME_SET"));
        }

        @Override
        public CompletableFuture<EnvironmentActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            if (flag == null) {
                throw new IllegalArgumentException("flag is required");
            }
            return JdkCoreApiClient.this.setIslandFlagResult(islandId, actorUuid, flag, value == null ? "" : value)
                .thenApply(body -> actionResult(body, "FLAG_SET"));
        }

        @Override
        public CompletableFuture<EnvironmentActionView> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return JdkCoreApiClient.this.setIslandLimit(islandId, actorUuid, limitKey == null ? "" : limitKey, value)
                .thenApply(body -> actionResult(body, "LIMIT_SET"));
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }

        private EnvironmentActionView actionResult(String body, String successCode) {
            Map<?, ?> root = CoreJson.object(body);
            return new EnvironmentActionView(
                CoreJson.accepted(root),
                CoreJson.code(root, successCode),
                CoreJson.firstText(root, "limitKey", "biomeKey", "flag", "key"),
                CoreJson.number(root, "value"),
                CoreJson.text(root, "islandId"),
                CoreJson.text(root, "updatedBy"),
                CoreJson.text(root, "updatedAt")
            );
        }
    }

    private final class JdkSettingsClient implements IslandSettingsCommandClient {
        @Override
        public CompletableFuture<SettingsActionView> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return JdkCoreApiClient.this.setIslandPublicAccessResult(islandId, actorUuid, publicAccess)
                .thenApply(body -> actionResult(body, publicAccess ? "PUBLIC_ACCESS_ENABLED" : "PUBLIC_ACCESS_DISABLED"));
        }

        @Override
        public CompletableFuture<SettingsActionView> setLocked(UUID islandId, UUID actorUuid, boolean locked) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return JdkCoreApiClient.this.setIslandLockedResult(islandId, actorUuid, locked)
                .thenApply(body -> actionResult(body, locked ? "ISLAND_LOCKED" : "ISLAND_UNLOCKED"));
        }

        @Override
        public CompletableFuture<SettingsActionView> setName(UUID islandId, UUID actorUuid, String name) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return JdkCoreApiClient.this.setIslandNameResult(islandId, actorUuid, name == null ? "" : name)
                .thenApply(body -> actionResult(body, "ISLAND_RENAMED"));
        }

        @Override
        public CompletableFuture<SettingsActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            if (flag == null) {
                throw new IllegalArgumentException("flag is required");
            }
            return JdkCoreApiClient.this.setIslandFlagResult(islandId, actorUuid, flag, value == null ? "" : value)
                .thenApply(body -> actionResult(body, "FLAG_SET"));
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }

        private SettingsActionView actionResult(String body, String successCode) {
            Map<?, ?> root = CoreJson.object(body);
            return new SettingsActionView(CoreJson.accepted(root), CoreJson.code(root, successCode));
        }
    }

    private final class JdkHomeWarpClient implements HomeWarpQueryClient, HomeWarpCommandClient {
        @Override
        public CompletableFuture<List<IslandHomeSnapshot>> homeSnapshots(UUID islandId) {
            requireId(islandId, "islandId");
            return get("/v1/islands/" + islandId + "/homes")
                .thenApply(body -> CoreHomeWarpJson.homes(islandId, body));
        }

        @Override
        public CompletableFuture<List<IslandWarpSnapshot>> warpSnapshots(UUID islandId) {
            requireId(islandId, "islandId");
            return get("/v1/islands/" + islandId + "/warps")
                .thenApply(body -> CoreHomeWarpJson.warps(islandId, body));
        }

        @Override
        public CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId) {
            requireId(islandId, "islandId");
            return JdkCoreApiClient.this.islandInfo(islandId).thenApply(CoreGuiViews::islandInfoView);
        }

        @Override
        public CompletableFuture<List<IslandWarpSnapshot>> publicWarpSnapshots(int limit, String category, String query) {
            int safeLimit = Math.max(1, Math.min(limit, 100));
            return post("/v1/islands/public-warps", jsonObject("limit", safeLimit, "category", category == null ? "" : category, "query", query == null ? "" : query))
                .thenApply(body -> CoreHomeWarpJson.warps(null, body));
        }

        @Override
        public CompletableFuture<HomeWarpActionView> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            requireLocation(location);
            return JdkCoreApiClient.this.setIslandHomeResult(islandId, actorUuid, normalizeName(name), location)
                .thenApply(body -> actionResult(body, "HOME_SET"));
        }

        @Override
        public CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            requireLocation(location);
            return JdkCoreApiClient.this.setIslandWarpResult(islandId, actorUuid, normalizeName(name), location, publicAccess)
                .thenApply(body -> actionResult(body, "WARP_SET"));
        }

        @Override
        public CompletableFuture<HomeWarpActionView> deleteWarp(UUID islandId, UUID actorUuid, String name) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return JdkCoreApiClient.this.deleteIslandWarpResult(islandId, actorUuid, normalizeName(name))
                .thenApply(body -> actionResult(body, "WARP_DELETED"));
        }

        @Override
        public CompletableFuture<HomeWarpActionView> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return JdkCoreApiClient.this.setIslandWarpPublicAccessResult(islandId, actorUuid, normalizeName(name), publicAccess)
                .thenApply(body -> actionResult(body, publicAccess ? "WARP_PUBLIC" : "WARP_PRIVATE"));
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }

        private void requireLocation(IslandLocation location) {
            if (location == null) {
                throw new IllegalArgumentException("location is required");
            }
        }

        private String normalizeName(String name) {
            return name == null || name.isBlank() ? "default" : name;
        }

        private HomeWarpActionView actionResult(String body, String successCode) {
            Map<?, ?> root = CoreJson.object(body);
            return new HomeWarpActionView(CoreJson.accepted(root), CoreJson.code(root, successCode));
        }
    }

    private final class JdkIslandClient implements IslandQueryClient {
        @Override
        public CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId) {
            requireId(islandId, "islandId");
            return JdkCoreApiClient.this.islandInfo(islandId).thenApply(CoreGuiViews::islandInfoView);
        }

        @Override
        public CompletableFuture<CoreGuiViews.IslandInfoView> getIslandByOwner(UUID ownerUuid) {
            requireId(ownerUuid, "ownerUuid");
            return JdkCoreApiClient.this.islandInfoByOwner(ownerUuid).thenApply(CoreGuiViews::islandInfoView);
        }

        @Override
        public CompletableFuture<CoreGuiViews.IslandInfoView> findIslandByName(String islandName) {
            String normalized = islandName == null ? "" : islandName.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("islandName is required");
            }
            return JdkCoreApiClient.this.islandInfoByName(normalized).thenApply(CoreGuiViews::islandInfoView);
        }

        @Override
        public CompletableFuture<List<IslandMemberSnapshot>> memberSnapshots(UUID islandId) {
            requireId(islandId, "islandId");
            return get("/v1/islands/" + islandId + "/members")
                .thenApply(body -> CoreMemberJson.members(islandId, body));
        }

        @Override
        public CompletableFuture<List<CoreGuiViews.MemberView>> listMembers(UUID islandId) {
            requireId(islandId, "islandId");
            return JdkCoreApiClient.this.listIslandMembers(islandId).thenApply(CoreGuiViews::memberViews);
        }

        @Override
        public CompletableFuture<MemberPage> listMembers(UUID islandId, MemberCursor cursor) {
            MemberCursor safeCursor = cursor == null ? MemberCursor.firstPage(45) : cursor;
            return listMembers(islandId).thenApply(members -> {
                List<CoreGuiViews.MemberView> safeMembers = members == null ? List.of() : members;
                int total = safeMembers.size();
                int from = Math.min(safeCursor.offset(), total);
                int to = Math.min(from + safeCursor.limit(), total);
                MemberCursor next = to < total ? new MemberCursor(to, safeCursor.limit()) : null;
                return new MemberPage(safeMembers.subList(from, to), safeCursor, next, total);
            });
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkMemberQueryClient implements MemberQueryClient {
        @Override
        public CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName) {
            String normalized = playerName == null ? "" : playerName.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("playerName is required");
            }
            return playerInfoByName(normalized).thenApply(CoreGuiViews::playerProfile);
        }

        @Override
        public CompletableFuture<List<IslandInviteSnapshot>> inviteSnapshots(UUID playerUuid) {
            requireId(playerUuid, "playerUuid");
            return post("/v1/players/invites", jsonObject("playerUuid", playerUuid))
                .thenApply(CoreMemberJson::invites);
        }

        @Override
        public CompletableFuture<List<IslandBanSnapshot>> banSnapshots(UUID islandId) {
            requireId(islandId, "islandId");
            return get("/v1/islands/" + islandId + "/bans")
                .thenApply(body -> CoreMemberJson.bans(islandId, body));
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkMemberCommandClient implements MemberCommandClient {
        @Override
        public CompletableFuture<MemberActionView> removeMember(UUID islandId, UUID actorUuid, UUID targetUuid) {
            requireIds(islandId, actorUuid, targetUuid);
            return postWithResultBody("/v1/islands/members/remove", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "playerUuid", targetUuid
            )).thenApply(body -> CoreMemberCommandClient.memberAction(body, "MEMBER_REMOVED"));
        }

        @Override
        public CompletableFuture<CoreGuiViews.InviteView> createInvite(UUID islandId, UUID actorUuid, UUID targetUuid) {
            requireIds(islandId, actorUuid, targetUuid);
            return postWithResultBody("/v1/islands/invites", jsonObject(
                "islandId", islandId,
                "inviterUuid", actorUuid,
                "targetUuid", targetUuid
            )).thenApply(CoreGuiViews::inviteView);
        }

        @Override
        public CompletableFuture<IslandInviteActionResult> acceptInvite(UUID inviteId, UUID playerUuid) {
            requireInviteAndPlayer(inviteId, playerUuid);
            return postWithResultBody("/v1/islands/invites/accept", jsonObject("inviteId", inviteId, "playerUuid", playerUuid))
                .thenApply(body -> CoreMemberCommandClient.inviteAction(body, "ACCEPTED"));
        }

        @Override
        public CompletableFuture<IslandInviteActionResult> declineInvite(UUID inviteId, UUID playerUuid) {
            requireInviteAndPlayer(inviteId, playerUuid);
            return postWithResultBody("/v1/islands/invites/decline", jsonObject("inviteId", inviteId, "playerUuid", playerUuid))
                .thenApply(body -> CoreMemberCommandClient.inviteAction(body, "DECLINED"));
        }

        @Override
        public CompletableFuture<MemberActionView> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
            requireIds(islandId, actorUuid, targetUuid);
            String normalizedRoleKey = normalizeRoleKey(roleKey);
            return postWithResultBody("/v1/islands/members/set", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "playerUuid", targetUuid,
                "role", normalizedRoleKey,
                "roleKey", normalizedRoleKey
            )).thenApply(body -> CoreMemberCommandClient.memberAction(body, "MEMBER_ROLE_SET"));
        }

        @Override
        public CompletableFuture<MemberActionView> trustTemporarily(UUID islandId, UUID actorUuid, UUID targetUuid, long durationSeconds) {
            requireIds(islandId, actorUuid, targetUuid);
            if (durationSeconds <= 0L) {
                throw new IllegalArgumentException("durationSeconds must be positive");
            }
            return postWithResultBody("/v1/islands/members/trust-temporary", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "playerUuid", targetUuid,
                "durationSeconds", durationSeconds
            )).thenApply(body -> CoreMemberCommandClient.memberAction(body, "TEMP_TRUST_SET"));
        }

        @Override
        public CompletableFuture<MemberActionView> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) {
            requireIds(islandId, actorUuid, targetUuid);
            return postWithResultBody("/v1/islands/transfer", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "targetUuid", targetUuid
            )).thenApply(body -> CoreMemberCommandClient.memberAction(body, "OWNERSHIP_TRANSFERRED"));
        }

        @Override
        public CompletableFuture<MemberActionView> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) {
            requireIds(islandId, actorUuid, targetUuid);
            return postWithResultBody("/v1/islands/bans/set", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "playerUuid", targetUuid,
                "reason", reason == null ? "" : reason
            )).thenApply(body -> CoreMemberCommandClient.memberAction(body, "VISITOR_BANNED"));
        }

        @Override
        public CompletableFuture<MemberActionView> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
            requireIds(islandId, actorUuid, targetUuid);
            return postWithResultBody("/v1/islands/bans/remove", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "playerUuid", targetUuid
            )).thenApply(body -> CoreMemberCommandClient.memberAction(body, "VISITOR_PARDONED"));
        }

        @Override
        public CompletableFuture<MemberActionView> kickVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) {
            requireIds(islandId, actorUuid, targetUuid);
            return postWithResultBody("/v1/islands/visitors/kick", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "playerUuid", targetUuid
            )).thenApply(body -> CoreMemberCommandClient.memberAction(body, "VISITOR_KICKED"));
        }

        private void requireIds(UUID islandId, UUID actorUuid, UUID targetUuid) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            requireId(targetUuid, "targetUuid");
        }

        private void requireInviteAndPlayer(UUID inviteId, UUID playerUuid) {
            requireId(inviteId, "inviteId");
            requireId(playerUuid, "playerUuid");
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkPlayerProfileClient implements PlayerProfileQueryClient, PlayerProfileCommandClient {
        @Override
        public CompletableFuture<PlayerProfileView> profile(UUID playerUuid) {
            requireId(playerUuid, "playerUuid");
            return postWithResultBody("/v1/admin/players/info", jsonObject("playerUuid", playerUuid))
                .thenApply(CorePlayerProfileJson::profile);
        }

        @Override
        public CompletableFuture<PlayerProfileView> findByName(String lastName) {
            String normalized = lastName == null ? "" : lastName.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("lastName is required");
            }
            return post("/v1/players/info", jsonObject("lastName", normalized))
                .thenApply(CorePlayerProfileJson::profile);
        }

        @Override
        public CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName) {
            requireId(playerUuid, "playerUuid");
            return postWithResultBody("/v1/players/touch", jsonObject("playerUuid", playerUuid, "lastName", lastName == null ? "" : lastName))
                .thenApply(CorePlayerProfileJson::profile);
        }

        @Override
        public CompletableFuture<PlayerProfileView> touch(UUID playerUuid, String lastName, String locale) {
            requireId(playerUuid, "playerUuid");
            return postWithResultBody("/v1/players/touch", jsonObject("playerUuid", playerUuid, "lastName", lastName == null ? "" : lastName, "locale", locale == null ? "" : locale))
                .thenApply(CorePlayerProfileJson::profile);
        }

        @Override
        public CompletableFuture<PlayerProfileView> setLocale(UUID playerUuid, String locale) {
            requireId(playerUuid, "playerUuid");
            return postWithResultBody("/v1/players/locale", jsonObject("playerUuid", playerUuid, "locale", locale == null ? "" : locale))
                .thenApply(CorePlayerProfileJson::profile);
        }

        @Override
        public CompletableFuture<PlayerProfileView> setPrimaryIsland(UUID playerUuid, UUID islandId) {
            requireId(playerUuid, "playerUuid");
            requireId(islandId, "islandId");
            return postWithResultBody("/v1/admin/players/setisland", jsonObject("playerUuid", playerUuid, "islandId", islandId))
                .thenApply(CorePlayerProfileJson::profile);
        }

        @Override
        public CompletableFuture<PlayerProfileView> clearPrimaryIsland(UUID playerUuid) {
            requireId(playerUuid, "playerUuid");
            return postWithResultBody("/v1/admin/players/clearisland", jsonObject("playerUuid", playerUuid))
                .thenApply(CorePlayerProfileJson::profile);
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkTemplateClient implements TemplateQueryClient, TemplateCommandClient {
        @Override
        public CompletableFuture<List<TemplateView>> list() {
            return postWithResultBody("/v1/admin/templates/list", "{}")
                .thenApply(CoreTemplateJson::templates);
        }

        @Override
        public CompletableFuture<TemplateView> upsert(String templateId, String displayName, boolean enabled, String minNodeVersion) {
            String safeTemplateId = requireTemplateId(templateId);
            return postWithResultBody("/v1/admin/templates/upsert", jsonObject(
                "templateId", safeTemplateId,
                "displayName", displayName == null ? "" : displayName,
                "enabled", enabled,
                "minNodeVersion", minNodeVersion == null ? "" : minNodeVersion
            )).thenApply(CoreTemplateJson::template);
        }

        @Override
        public CompletableFuture<TemplateView> enable(String templateId) {
            return postWithResultBody("/v1/admin/templates/enable", jsonObject("templateId", requireTemplateId(templateId)))
                .thenApply(CoreTemplateJson::template);
        }

        @Override
        public CompletableFuture<TemplateView> disable(String templateId) {
            return postWithResultBody("/v1/admin/templates/disable", jsonObject("templateId", requireTemplateId(templateId)))
                .thenApply(CoreTemplateJson::template);
        }

        private String requireTemplateId(String templateId) {
            String normalized = templateId == null ? "" : templateId.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("templateId is required");
            }
            return normalized;
        }
    }

    private final class JdkJobClient implements JobQueryClient, JobCommandClient {
        @Override
        public CompletableFuture<List<JobView>> list() {
            return postWithResultBody("/v1/admin/jobs/list", "{}")
                .thenApply(CoreJobJson::jobs);
        }

        @Override
        public CompletableFuture<JobActionView> retry(UUID jobId) {
            requireId(jobId);
            return postWithResultBody("/v1/admin/jobs/retry", jsonObject("jobId", jobId))
                .thenApply(body -> CoreJobJson.action(body, "JOB_RETRIED"));
        }

        @Override
        public CompletableFuture<JobActionView> cancel(UUID jobId) {
            requireId(jobId);
            return postWithResultBody("/v1/admin/jobs/cancel", jsonObject("jobId", jobId))
                .thenApply(body -> CoreJobJson.action(body, "JOB_CANCELED"));
        }

        @Override
        public CompletableFuture<JobRecoveryView> recover(String nodeId, long minIdleMillis, int maxJobs) {
            String safeNodeId = nodeId == null ? "" : nodeId.trim();
            if (safeNodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId is required");
            }
            return postWithResultBody("/v1/admin/jobs/recover", jsonObject(
                "nodeId", safeNodeId,
                "minIdleMillis", Math.max(0L, minIdleMillis),
                "maxJobs", Math.max(1, maxJobs)
            )).thenApply(CoreJobJson::recovery);
        }

        private void requireId(UUID jobId) {
            if (jobId == null) {
                throw new IllegalArgumentException("jobId is required");
            }
        }
    }

    private final class JdkBlockValueClient implements BlockValueQueryClient, BlockValueCommandClient {
        @Override
        public CompletableFuture<List<BlockValueView>> list() {
            return postWithResultBody("/v1/admin/block-values/list", "{}")
                .thenApply(CoreBlockValueJson::values);
        }

        @Override
        public CompletableFuture<BlockValueActionView> set(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) {
            UUID safeActor = actorUuid == null ? new UUID(0L, 0L) : actorUuid;
            String safeMaterial = requireMaterial(materialKey);
            return postWithResultBody("/v1/admin/block-values", jsonObject(
                "actorUuid", safeActor,
                "materialKey", safeMaterial,
                "worth", worth == null ? "0" : worth,
                "levelPoints", levelPoints,
                "limit", limit
            )).thenApply(body -> CoreBlockValueJson.action(body, safeMaterial));
        }

        private String requireMaterial(String materialKey) {
            String normalized = materialKey == null ? "" : materialKey.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("materialKey is required");
            }
            return normalized;
        }
    }

    private final class JdkAdminMetricsClient implements AdminMetricsQueryClient {
        @Override
        public CompletableFuture<AdminMetricsSummaryView> summary() {
            return postWithResultBody("/metrics", "{}")
                .thenApply(AdminMetricsSummaryView::parse);
        }
    }

    private final class JdkAdminCoreConfigClient implements AdminCoreConfigQueryClient {
        @Override
        public CompletableFuture<AdminCoreConfigView> config() {
            return postWithResultBody("/v1/admin/config", "{}")
                .thenApply(AdminCoreConfigView::parse);
        }
    }

    private final class JdkAdminStorageClient implements AdminStorageQueryClient {
        @Override
        public CompletableFuture<AdminStorageStatusView> status() {
            return postWithResultBody("/v1/admin/storage", "{}")
                .thenApply(CoreAdminStorageQueryClient::status);
        }
    }

    private final class JdkAdminEventClient implements AdminEventQueryClient {
        @Override
        public CompletableFuture<AdminEventStreamView> list(int limit) {
            return postWithResultBody("/v1/events", jsonObject("limit", Math.max(1, Math.min(limit, 4096))))
                .thenApply(CoreAdminEventQueryClient::stream);
        }

        @Override
        public CompletableFuture<AdminEventStreamView> listSince(long sinceSeq, int limit) {
            return postWithResultBody("/v1/events", jsonObject("sinceSeq", Math.max(0L, sinceSeq), "limit", Math.max(1, Math.min(limit, 4096))))
                .thenApply(CoreAdminEventQueryClient::stream);
        }
    }

    private final class JdkAdminAuditClient implements AdminAuditQueryClient {
        @Override
        public CompletableFuture<List<AdminAuditEntryView>> list(int limit) {
            return postWithResultBody("/v1/admin/audit/list", jsonObject("limit", Math.max(1, Math.min(limit, 500))))
                .thenApply(CoreAdminAuditQueryClient::entries);
        }
    }

    private final class JdkAdminRouteClient implements AdminRouteClient {
        @Override
        public CompletableFuture<AdminRouteDebugView> debug(UUID playerUuid) {
            requireId(playerUuid, "playerUuid");
            return postWithResultBody("/v1/admin/routes/debug", jsonObject("playerUuid", playerUuid))
                .thenApply(CoreAdminRouteJson::debug);
        }

        @Override
        public CompletableFuture<Optional<AdminRouteTicketView>> ticket(UUID ticketId) {
            requireId(ticketId, "ticketId");
            return postWithResultBody("/v1/admin/routes/ticket", jsonObject("ticketId", ticketId))
                .thenApply(CoreAdminRouteJson::ticket);
        }

        @Override
        public CompletableFuture<Optional<AdminRouteTicketView>> ticketForPlayer(UUID playerUuid) {
            requireId(playerUuid, "playerUuid");
            return postWithResultBody("/v1/admin/routes/ticket", jsonObject("playerUuid", playerUuid))
                .thenApply(CoreAdminRouteJson::ticket);
        }

        @Override
        public CompletableFuture<AdminRouteClearView> clear(UUID playerUuid, UUID ticketId) {
            return clear(playerUuid, ticketId, "MANUAL_CLEAR");
        }

        @Override
        public CompletableFuture<AdminRouteClearView> clear(UUID playerUuid, UUID ticketId, String reason) {
            requireId(playerUuid, "playerUuid");
            requireId(ticketId, "ticketId");
            return postWithResultBody("/v1/admin/routes/clear", jsonObject(
                "playerUuid", playerUuid,
                "ticketId", ticketId,
                "reason", reason == null || reason.isBlank() ? "MANUAL_CLEAR" : reason
            )).thenApply(CoreAdminRouteJson::clear);
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkAdminAddonStateClient implements AdminAddonStateQueryClient {
        @Override
        public CompletableFuture<AdminAddonStateSummaryView> summary() {
            return post("/v1/admin/addons/state/summary", "{}")
                .thenApply(CoreAdminAddonStateQueryClient::summary);
        }
    }

    private final class JdkAdminMaintenanceClient implements AdminMaintenanceCommandClient {
        @Override
        public CompletableFuture<AdminMaintenanceResultView> clearCache() {
            return postWithResultBody("/v1/admin/cache/clear", "{}")
                .thenApply(CoreAdminMaintenanceCommandClient::result);
        }

        @Override
        public CompletableFuture<AdminMaintenanceResultView> reload() {
            return postWithResultBody("/v1/admin/reload", "{}")
                .thenApply(CoreAdminMaintenanceCommandClient::result);
        }
    }

    private final class JdkAdminNodeClient implements AdminNodeQueryClient, AdminNodeCommandClient {
        @Override
        public CompletableFuture<List<IslandNodeSnapshot>> nodes() {
            return postWithResultBody("/v1/admin/nodes/list", "{}")
                .thenApply(CoreAdminNodeQueryClient::nodes);
        }

        @Override
        public CompletableFuture<AdminNodeSummaryView> listNodesSummary() {
            return postWithResultBody("/v1/admin/nodes/list", "{}")
                .thenApply(CoreAdminNodeQueryClient::summary);
        }

        @Override
        public CompletableFuture<Optional<IslandNodeSnapshot>> nodeSnapshot(String nodeId) {
            String normalizedNodeId = requireNodeId(nodeId);
            return postWithResultBody("/v1/admin/nodes/info", jsonObject("nodeId", normalizedNodeId))
                .thenApply(body -> CoreAdminNodeQueryClient.node(normalizedNodeId, body));
        }

        @Override
        public CompletableFuture<CoreGuiViews.NodeSummaryView> nodeInfo(String nodeId) {
            String normalizedNodeId = requireNodeId(nodeId);
            return postWithResultBody("/v1/admin/nodes/info", jsonObject("nodeId", normalizedNodeId))
                .thenApply(body -> CoreGuiViews.nodeSummary(normalizedNodeId, body));
        }

        @Override
        public CompletableFuture<List<AdminIslandRuntimeView>> nodeIslandRuntimes(String nodeId, int limit) {
            return postWithResultBody("/v1/admin/nodes/islands", jsonObject(
                "nodeId", requireNodeId(nodeId),
                "limit", Math.max(1, Math.min(limit, 100))
            )).thenApply(CoreAdminNodeQueryClient::runtimes);
        }

        @Override
        public CompletableFuture<AdminNodeSummaryView> nodeIslandsSummary(String nodeId, int limit) {
            return postWithResultBody("/v1/admin/nodes/islands", jsonObject(
                "nodeId", requireNodeId(nodeId),
                "limit", Math.max(1, Math.min(limit, 100))
            )).thenApply(CoreAdminNodeQueryClient::summary);
        }

        @Override
        public CompletableFuture<AdminNodeActionView> drainNode(String nodeId) {
            return postWithResultBody("/v1/admin/nodes/drain", jsonObject("nodeId", requireNodeId(nodeId)))
                .thenApply(CoreAdminNodeCommandClient::actionResult);
        }

        @Override
        public CompletableFuture<AdminNodeActionView> undrainNode(String nodeId) {
            return postWithResultBody("/v1/admin/nodes/undrain", jsonObject("nodeId", requireNodeId(nodeId)))
                .thenApply(CoreAdminNodeCommandClient::actionResult);
        }

        @Override
        public CompletableFuture<AdminNodeActionView> sweepNode(String nodeId) {
            return postWithResultBody("/v1/admin/nodes/sweep", jsonObject("nodeId", requireNodeId(nodeId)))
                .thenApply(CoreAdminNodeCommandClient::actionResult);
        }

        @Override
        public CompletableFuture<AdminNodeActionView> kickAllNode(String nodeId, String reason) {
            return postWithResultBody("/v1/admin/nodes/kickall", jsonObject(
                "nodeId", requireNodeId(nodeId),
                "reason", reason == null ? "" : reason
            )).thenApply(CoreAdminNodeCommandClient::actionResult);
        }

        @Override
        public CompletableFuture<AdminNodeActionView> shutdownNodeSafely(String nodeId, String reason) {
            return postWithResultBody("/v1/admin/nodes/shutdown-safe", jsonObject(
                "nodeId", requireNodeId(nodeId),
                "reason", reason == null ? "" : reason
            )).thenApply(CoreAdminNodeCommandClient::actionResult);
        }

        private String requireNodeId(String nodeId) {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId is required");
            }
            return nodeId.trim();
        }
    }

    private final class JdkAdminIslandClient implements AdminIslandQueryClient {
        @Override
        public CompletableFuture<CoreGuiViews.IslandInfoView> info(UUID lookupUuid) {
            requireId(lookupUuid, "lookupUuid");
            return postWithResultBody("/v1/admin/islands/info", jsonObject("lookupUuid", lookupUuid))
                .thenApply(CoreGuiViews::islandInfoView);
        }

        @Override
        public CompletableFuture<CoreGuiViews.IslandInfoView> infoByName(String islandName) {
            String normalized = islandName == null ? "" : islandName.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("islandName is required");
            }
            return post("/v1/islands/info", jsonObject("name", normalized))
                .thenApply(CoreGuiViews::islandInfoView);
        }

        @Override
        public CompletableFuture<AdminIslandRuntimeView> runtime(UUID islandId) {
            requireId(islandId, "islandId");
            return postWithResultBody("/v1/admin/islands/where", jsonObject("islandId", islandId))
                .thenApply(CoreAdminIslandQueryClient::runtime);
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkRoutingClient implements RoutingCommandClient {
        @Override
        public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) {
            requireId(playerUuid, "playerUuid");
            requireId(islandId, "islandId");
            return postWithResultBody("/v1/routes/warp", jsonObject(
                "playerUuid", playerUuid,
                "islandId", islandId,
                "warpName", warpName == null ? "" : warpName
            )).thenApply(JdkCoreApiClient::parseRouteTicketResult);
        }

        @Override
        public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(RouteTicket ticket) {
            requireTicket(ticket);
            return post("/v1/routes/ticket-status", jsonObject(
                "ticketId", ticket.ticketId(),
                "playerUuid", ticket.playerUuid(),
                "nonce", ticket.nonce()
            )).thenApply(body -> body.isBlank() ? Optional.empty() : Optional.ofNullable(RouteTicketJson.parse(body)));
        }

        @Override
        public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) {
            return publishRouteSessionResult(ticket).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<RoutePublishView> publishRouteSessionResult(RouteTicket ticket) {
            requireTicket(ticket);
            String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
            return postWithResultBody("/v1/routes/session", jsonObject(
                "playerUuid", ticket.playerUuid(),
                "ticketId", ticket.ticketId(),
                "targetNode", ticket.targetNode(),
                "targetServerName", targetServerName,
                "nonce", ticket.nonce(),
                "expiresAt", ticket.expiresAt()
            )).thenApply(CoreRoutingCommandClient::routePublishResult);
        }

        @Override
        public CompletableFuture<RouteClearView> clearRoute(RouteTicket ticket, String reason) {
            requireTicket(ticket);
            String normalizedReason = reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason;
            return postWithResultBody("/v1/admin/routes/clear", jsonObject(
                "playerUuid", ticket.playerUuid(),
                "ticketId", ticket.ticketId(),
                "reason", normalizedReason
            )).thenApply(CoreRoutingCommandClient::routeClearResult);
        }

        private void requireTicket(RouteTicket ticket) {
            if (ticket == null) {
                throw new IllegalArgumentException("ticket is required");
            }
            requireId(ticket.ticketId(), "ticketId");
            requireId(ticket.playerUuid(), "playerUuid");
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
    }

    private final class JdkRuntimeClient implements RuntimeCommandClient {
        @Override
        public CompletableFuture<RuntimeActionView> publishHeartbeat(NodeHeartbeatRequest request) {
            if (request == null) {
                throw new IllegalArgumentException("request is required");
            }
            return postWithResultBody("/v1/nodes/heartbeat", jsonObject(
                "protocolVersion", request.protocolVersion(),
                "nodeId", request.nodeId(),
                "pool", request.pool(),
                "velocityServerName", request.velocityServerName(),
                "nodeVersion", request.nodeVersion(),
                "state", request.state().name(),
                "players", request.players(),
                "softPlayerCap", request.softPlayerCap(),
                "hardPlayerCap", request.hardPlayerCap(),
                "reservedSlots", request.reservedSlots(),
                "activeIslands", request.activeIslands(),
                "maxActiveIslands", request.maxActiveIslands(),
                "mspt", request.mspt(),
                "activationQueue", request.activationQueue(),
                "maxActivationQueue", request.maxActivationQueue(),
                "chunkLoadPressure", request.chunkLoadPressure(),
                "heapUsedMb", request.heapUsedMb(),
                "heapMaxMb", request.heapMaxMb(),
                "recentFailurePenalty", request.recentFailurePenalty(),
                "storageAvailable", request.storageAvailable(),
                "supportedTemplates", request.supportedTemplates()
            )).thenApply(body -> CoreRuntimeCommandClient.action(body, "HEARTBEAT_ACCEPTED"));
        }

        @Override
        public CompletableFuture<RuntimeActionView> recordBlockDelta(UUID islandId, String materialKey, long delta) {
            requireId(islandId, "islandId");
            String safeMaterialKey = materialKey == null ? "" : materialKey.trim();
            if (safeMaterialKey.isBlank()) {
                throw new IllegalArgumentException("materialKey is required");
            }
            return postWithResultBody("/v1/islands/blocks/delta", jsonObject(
                "islandId", islandId,
                "materialKey", safeMaterialKey,
                "delta", delta
            )).thenApply(body -> CoreRuntimeCommandClient.action(body, "BLOCK_DELTA_RECORDED"));
        }

        @Override
        public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
            return postWithResultBody("/v1/jobs/complete", jsonObject(
                "nodeId", requireNode(nodeId),
                "jobId", requireId(jobId, "jobId"),
                "payload", rawJson(mapJson(payload == null ? Map.of() : payload))
            )).thenApply(body -> CoreRuntimeCommandClient.action(body, "JOB_COMPLETED"));
        }

        @Override
        public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, String errorMessage) {
            return postWithResultBody("/v1/jobs/fail", jsonObject(
                "nodeId", requireNode(nodeId),
                "jobId", requireId(jobId, "jobId"),
                "error", errorMessage == null ? "" : errorMessage
            )).thenApply(body -> CoreRuntimeCommandClient.action(body, "JOB_FAILED"));
        }

        private UUID requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
            return id;
        }

        private String requireNode(String nodeId) {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId is required");
            }
            return nodeId.trim();
        }
    }

    private final class JdkWarehouseClient implements WarehouseQueryClient, WarehouseCommandClient {
        @Override
        public CompletableFuture<List<WarehouseItemView>> listItems(UUID islandId, int limit) {
            requireId(islandId, "islandId");
            return post("/v1/islands/warehouse", jsonObject("islandId", islandId, "limit", Math.max(1, Math.min(limit, 100))))
                .thenApply(body -> CoreWarehouseQueryClient.itemViews(islandId, body));
        }

        @Override
        public CompletableFuture<WarehouseMutationView> deposit(UUID islandId, UUID actorUuid, String materialKey, long amount) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return postWithResultBody("/v1/islands/warehouse/deposit", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "materialKey", materialKey == null ? "" : materialKey,
                "amount", amount
            )).thenApply(CoreWarehouseCommandClient::warehouseMutation);
        }

        @Override
        public CompletableFuture<WarehouseMutationView> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount) {
            requireId(islandId, "islandId");
            requireId(actorUuid, "actorUuid");
            return postWithResultBody("/v1/islands/warehouse/withdraw", jsonObject(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "materialKey", materialKey == null ? "" : materialKey,
                "amount", amount
            )).thenApply(CoreWarehouseCommandClient::warehouseMutation);
        }

        private void requireId(UUID id, String name) {
            if (id == null) {
                throw new IllegalArgumentException(name + " is required");
            }
        }
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
    public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) {
        return publishRouteSessionResult(ticket).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> publishRouteSessionResult(RouteTicket ticket) {
        String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
        return postWithResultBody("/v1/routes/session", jsonObject(
            "playerUuid", ticket.playerUuid(),
            "ticketId", ticket.ticketId(),
            "targetNode", ticket.targetNode(),
            "targetServerName", targetServerName,
            "nonce", ticket.nonce(),
            "expiresAt", ticket.expiresAt()
        ));
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
    public CompletableFuture<String> listNodes() {
        return postWithResultBody("/v1/admin/nodes/list", "{}");
    }

    @Override
    public CompletableFuture<String> nodeInfo(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/info", jsonObject("nodeId", nodeId));
    }

    @Override
    public CompletableFuture<String> nodeIslands(String nodeId, int limit) {
        return postWithResultBody("/v1/admin/nodes/islands", jsonObject("nodeId", nodeId, "limit", Math.max(1, Math.min(limit, 200))));
    }

    @Override
    public CompletableFuture<String> drainNode(String nodeId) {
        return drainNodeResult(nodeId);
    }

    @Override
    public CompletableFuture<String> drainNodeResult(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/drain", jsonObject("nodeId", nodeId));
    }

    @Override
    public CompletableFuture<String> drainNodePath(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/" + pathNodeId(nodeId) + "/drain", "{}");
    }

    @Override
    public CompletableFuture<String> undrainNode(String nodeId) {
        return undrainNodeResult(nodeId);
    }

    @Override
    public CompletableFuture<String> undrainNodeResult(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/undrain", jsonObject("nodeId", nodeId));
    }

    @Override
    public CompletableFuture<String> undrainNodePath(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/" + pathNodeId(nodeId) + "/undrain", "{}");
    }

    @Override
    public CompletableFuture<String> sweepNode(String nodeId) {
        return sweepNodeResult(nodeId);
    }

    @Override
    public CompletableFuture<String> sweepNodeResult(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/sweep", jsonObject("nodeId", nodeId));
    }

    @Override
    public CompletableFuture<String> sweepNodePath(String nodeId) {
        return postWithResultBody("/v1/admin/nodes/" + pathNodeId(nodeId) + "/sweep", "{}");
    }

    @Override
    public CompletableFuture<String> kickAllNode(String nodeId, String reason) {
        return kickAllNodeResult(nodeId, reason);
    }

    @Override
    public CompletableFuture<String> kickAllNodeResult(String nodeId, String reason) {
        return postWithResultBody("/v1/admin/nodes/kickall", jsonObject("nodeId", nodeId, "reason", reason));
    }

    @Override
    public CompletableFuture<String> shutdownNodeSafely(String nodeId, String reason) {
        return shutdownNodeSafelyResult(nodeId, reason);
    }

    @Override
    public CompletableFuture<String> shutdownNodeSafelyResult(String nodeId, String reason) {
        return postWithResultBody("/v1/admin/nodes/shutdown-safe", jsonObject("nodeId", nodeId, "reason", reason));
    }

    @Override
    public CompletableFuture<String> shutdownNodeSafelyPath(String nodeId, String reason) {
        return postWithResultBody("/v1/admin/nodes/" + pathNodeId(nodeId) + "/shutdown-safe", jsonObject("reason", reason));
    }

    @Override
    public CompletableFuture<String> activateIsland(UUID islandId) {
        return activateIslandResult(islandId);
    }

    @Override
    public CompletableFuture<String> activateIslandResult(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/activate", jsonObject("islandId", islandId));
    }

    @Override
    public CompletableFuture<String> deactivateIsland(UUID islandId) {
        return deactivateIslandResult(islandId);
    }

    @Override
    public CompletableFuture<String> deactivateIslandResult(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/deactivate", jsonObject("islandId", islandId));
    }

    @Override
    public CompletableFuture<String> migrateIsland(UUID islandId, String targetNode) {
        return migrateIslandResult(islandId, targetNode);
    }

    @Override
    public CompletableFuture<String> migrateIslandResult(UUID islandId, String targetNode) {
        return postWithResultBody("/v1/admin/islands/migrate", jsonObject("islandId", islandId, "targetNode", targetNode));
    }

    @Override
    public CompletableFuture<String> quarantineIsland(UUID islandId, String reason) {
        return quarantineIslandResult(islandId, reason);
    }

    @Override
    public CompletableFuture<String> quarantineIslandResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/" + islandId + "/quarantine", jsonObject("reason", reason));
    }

    @Override
    public CompletableFuture<String> adminIslandInfo(UUID lookupUuid) {
        return postWithResultBody("/v1/admin/islands/info", jsonObject("lookupUuid", lookupUuid));
    }

    @Override
    public CompletableFuture<String> adminIslandWhere(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/where", jsonObject("islandId", islandId));
    }

    @Override
    public CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId) {
        return postWithResultBody("/v1/admin/islands/tp", jsonObject("playerUuid", playerUuid, "islandId", islandId)).thenApply(JdkCoreApiClient::parseRouteTicketResult);
    }

    @Override
    public CompletableFuture<String> adminDeleteIsland(UUID islandId) {
        return adminDeleteIslandResult(islandId);
    }

    @Override
    public CompletableFuture<String> adminDeleteIslandResult(UUID islandId) {
        return postWithResultBody("/v1/admin/islands/" + islandId + "/delete", "{}");
    }

    @Override
    public CompletableFuture<String> repairIsland(UUID islandId, String reason) {
        return repairIslandResult(islandId, reason);
    }

    @Override
    public CompletableFuture<String> repairIslandResult(UUID islandId, String reason) {
        return postWithResultBody("/v1/admin/islands/" + islandId + "/repair", jsonObject("reason", reason));
    }

    @Override
    public CompletableFuture<String> debugRoutes(UUID playerUuid) {
        return postWithResultBody("/v1/admin/routes/debug", jsonObject("playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<String> routeTicket(UUID ticketId) {
        return postWithResultBody("/v1/admin/routes/ticket", jsonObject("ticketId", ticketId));
    }

    @Override
    public CompletableFuture<String> routeTicketForPlayer(UUID playerUuid) {
        return postWithResultBody("/v1/admin/routes/ticket", jsonObject("playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<String> clearRoute(UUID playerUuid, UUID ticketId) {
        return clearRouteResult(playerUuid, ticketId);
    }

    @Override
    public CompletableFuture<String> clearRouteResult(UUID playerUuid, UUID ticketId) {
        return clearRouteResult(playerUuid, ticketId, "MANUAL_CLEAR");
    }

    @Override
    public CompletableFuture<String> clearRoute(UUID playerUuid, UUID ticketId, String reason) {
        return clearRouteResult(playerUuid, ticketId, reason);
    }

    @Override
    public CompletableFuture<String> clearRouteResult(UUID playerUuid, UUID ticketId, String reason) {
        return postWithResultBody("/v1/admin/routes/clear", jsonObject("playerUuid", playerUuid, "ticketId", ticketId, "reason", reason == null || reason.isBlank() ? "MANUAL_CLEAR" : reason));
    }

    @Override
    public CompletableFuture<String> listEvents() {
        return postWithResultBody("/v1/events", "{}");
    }

    @Override
    public CompletableFuture<String> listEvents(int limit) {
        return postWithResultBody("/v1/events", jsonObject("limit", Math.max(1, Math.min(limit, 4096))));
    }

    @Override
    public CompletableFuture<String> listEventsSince(long sinceSeq, int limit) {
        return postWithResultBody("/v1/events", jsonObject("sinceSeq", Math.max(0L, sinceSeq), "limit", Math.max(1, Math.min(limit, 4096))));
    }

    @Override
    public CompletableFuture<String> listAuditLogs() {
        return listAuditLogs(100);
    }

    @Override
    public CompletableFuture<String> listAuditLogs(int limit) {
        return postWithResultBody("/v1/admin/audit/list", jsonObject("limit", Math.max(1, Math.min(limit, 500))));
    }

    @Override
    public CompletableFuture<String> metrics() {
        return postWithResultBody("/metrics", "{}");
    }

    @Override
    public CompletableFuture<String> coreConfig() {
        return postWithResultBody("/v1/admin/config", "{}");
    }

    @Override
    public CompletableFuture<String> storageStatus() {
        return postWithResultBody("/v1/admin/storage", "{}");
    }

    @Override
    public CompletableFuture<String> clearCache() {
        return clearCacheResult();
    }

    @Override
    public CompletableFuture<String> clearCacheResult() {
        return postWithResultBody("/v1/admin/cache/clear", "{}");
    }

    @Override
    public CompletableFuture<String> reload() {
        return reloadResult();
    }

    @Override
    public CompletableFuture<String> reloadResult() {
        return postWithResultBody("/v1/admin/reload", "{}");
    }

    @Override
    public CompletableFuture<String> addonStateSummary() {
        return post("/v1/admin/addons/state/summary", "{}");
    }

    @Override
    public CompletableFuture<String> addonState(String addonId) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return post("/v1/addons/state", jsonObject("addonId", addonId));
    }

    @Override
    public CompletableFuture<String> putAddonState(String addonId, String key, String value) {
        if (blank(addonId) || blank(key)) {
            return invalidAddonState("Addon id and key are required");
        }
        return postWithResultBody("/v1/addons/state/set", jsonObject("addonId", addonId, "key", key, "value", value));
    }

    @Override
    public CompletableFuture<String> putAddonState(String addonId, Map<String, String> values) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/bulk", jsonObject("addonId", addonId, "values", rawJson(stringMapJson(values))));
    }

    @Override
    public CompletableFuture<String> putAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/bulk", jsonObject("addonId", addonId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> saveAddonState(String addonId, Map<String, String> values) {
        return saveAddonState(addonId, values, Map.of());
    }

    @Override
    public CompletableFuture<String> saveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/save", jsonObject("addonId", addonId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.GLOBAL_LEGACY_ENDPOINT, jsonObject("addonId", addonId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.GLOBAL_ENDPOINT, jsonObject("addonId", addonId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAddonState(AddonStateBulkSaveRequest request) {
        if (request == null || blank(request.addonId())) {
            return invalidAddonState("Addon state request and addon id are required");
        }
        if (request.islandScoped()) {
            return tableKeyValueBulkSaveAddonIslandState(request);
        }
        return postWithResultBody(AddonStateBulkSaveRequest.GLOBAL_ENDPOINT, bulkSaveRequestJson(request, false));
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonTableKeyValueState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonState(addonId, values, tables);
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAddonState(String addonId, String table, Map<String, String> values) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.GLOBAL_ENDPOINT, jsonObject("addonId", addonId, "table", table, "values", rawJson(stringMapJson(values == null ? Map.of() : values))));
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonTableKeyValueState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonState(addonId, table, values);
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAliasAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/table/key-value/bulk/save", jsonObject("addonId", addonId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/table/key-value/bulk", jsonObject("addonId", addonId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkAddonTableKeyValueState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkAddonState(addonId, values, tables);
    }

    @Override
    public CompletableFuture<String> tableBulkAddonState(String addonId, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/table/bulk", jsonObject("addonId", addonId, "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkAddonTableState(String addonId, Map<String, Map<String, String>> tables) {
        return tableBulkAddonState(addonId, tables);
    }

    @Override
    public CompletableFuture<String> tableBulkSetAddonState(String addonId, Map<String, Map<String, String>> tables) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_SET_ENDPOINT, jsonObject("addonId", addonId, "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkSetAddonTableState(String addonId, Map<String, Map<String, String>> tables) {
        return tableBulkSetAddonState(addonId, tables);
    }

    @Override
    public CompletableFuture<String> addonTableState(String addonId, String table) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return post(AddonStateBulkLoadRequest.GLOBAL_TABLE_LOAD_ALIAS, jsonObject("addonId", addonId, "table", table));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkLoadAddonState(String addonId, String table) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return post(AddonStateBulkLoadRequest.GLOBAL_ENDPOINT, jsonObject("addonId", addonId, "table", table));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkLoadAddonState(AddonStateBulkLoadRequest request) {
        if (request == null) {
            return invalidAddonState("Addon state bulk load request is required");
        }
        if (request.islandScoped()) {
            return tableKeyValueBulkLoadAddonIslandState(request);
        }
        return tableKeyValueBulkLoadAddonState(request.addonId(), request.table());
    }

    @Override
    public CompletableFuture<String> putAddonTableState(String addonId, String table, Map<String, String> values) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return postWithResultBody("/v1/addons/state/table/bulk", jsonObject("addonId", addonId, "table", table, "values", rawJson(stringMapJson(values))));
    }

    @Override
    public CompletableFuture<String> saveAddonTableState(String addonId, String table, Map<String, String> values) {
        return putAddonTableState(addonId, table, values);
    }

    @Override
    public CompletableFuture<String> replaceAddonTableState(String addonId, String table, Map<String, String> values) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return postWithResultBody("/v1/addons/state/table/replace", jsonObject("addonId", addonId, "table", table, "values", rawJson(stringMapJson(values))));
    }

    @Override
    public CompletableFuture<String> clearAddonTableState(String addonId, String table) {
        if (blank(addonId) || blank(table)) {
            return invalidAddonState("Addon id and table are required");
        }
        return postWithResultBody("/v1/addons/state/table/clear", jsonObject("addonId", addonId, "table", table));
    }

    @Override
    public CompletableFuture<String> removeAddonState(String addonId, String key) {
        if (blank(addonId) || blank(key)) {
            return invalidAddonState("Addon id and key are required");
        }
        return postWithResultBody("/v1/addons/state/remove", jsonObject("addonId", addonId, "key", key));
    }

    @Override
    public CompletableFuture<String> clearAddonState(String addonId) {
        if (blank(addonId)) {
            return invalidAddonState("Addon id is required");
        }
        return postWithResultBody("/v1/addons/state/clear", jsonObject("addonId", addonId));
    }

    @Override
    public CompletableFuture<String> addonIslandState(String addonId, UUID islandId) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return post("/v1/addons/islands/state", jsonObject("addonId", addonId, "islandId", islandId));
    }

    @Override
    public CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, String key, String value) {
        if (blank(addonId) || missingIslandId(islandId) || blank(key)) {
            return invalidAddonState("Addon id, island id, and key are required");
        }
        return postWithResultBody("/v1/addons/islands/state/set", jsonObject("addonId", addonId, "islandId", islandId, "key", key, "value", value));
    }

    @Override
    public CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, Map<String, String> values) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/bulk", jsonObject("addonId", addonId, "islandId", islandId, "values", rawJson(stringMapJson(values))));
    }

    @Override
    public CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/bulk", jsonObject("addonId", addonId, "islandId", islandId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> saveAddonIslandState(String addonId, UUID islandId, Map<String, String> values) {
        return saveAddonIslandState(addonId, islandId, values, Map.of());
    }

    @Override
    public CompletableFuture<String> saveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/save", jsonObject("addonId", addonId, "islandId", islandId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.ISLAND_LEGACY_ENDPOINT, jsonObject("addonId", addonId, "islandId", islandId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.ISLAND_ENDPOINT, jsonObject("addonId", addonId, "islandId", islandId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAddonIslandState(AddonStateBulkSaveRequest request) {
        if (request == null || blank(request.addonId())) {
            return invalidAddonState("Addon state request and addon id are required");
        }
        if (!request.islandScoped()) {
            return tableKeyValueBulkSaveAddonState(request);
        }
        return postWithResultBody(AddonStateBulkSaveRequest.ISLAND_ENDPOINT, bulkSaveRequestJson(request, true));
    }

    private String bulkSaveRequestJson(AddonStateBulkSaveRequest request, boolean includeIslandId) {
        Map<String, String> rootValues = request.tableScoped() ? Map.of() : request.values();
        if (includeIslandId) {
            if (request.tableScoped()) {
                return jsonObject("addonId", request.addonId(), "islandId", request.islandId(), "table", request.table(), "values", rawJson(stringMapJson(request.values())), "tables", rawJson(tableMapJson(request.tablesWithScopedTable())));
            }
            return jsonObject("addonId", request.addonId(), "islandId", request.islandId(), "values", rawJson(stringMapJson(rootValues)), "tables", rawJson(tableMapJson(request.tablesWithScopedTable())));
        }
        if (request.tableScoped()) {
            return jsonObject("addonId", request.addonId(), "table", request.table(), "values", rawJson(stringMapJson(request.values())), "tables", rawJson(tableMapJson(request.tablesWithScopedTable())));
        }
        return jsonObject("addonId", request.addonId(), "values", rawJson(stringMapJson(rootValues)), "tables", rawJson(tableMapJson(request.tablesWithScopedTable())));
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonIslandTableKeyValueState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, values, tables);
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAddonIslandState(String addonId, UUID islandId, String table, Map<String, String> values) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.ISLAND_ENDPOINT, jsonObject("addonId", addonId, "islandId", islandId, "table", table, "values", rawJson(stringMapJson(values == null ? Map.of() : values))));
    }

    @Override
    public CompletableFuture<String> bulkSaveAddonIslandTableKeyValueState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, table, values);
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkSaveAliasAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/key-value/bulk/save", jsonObject("addonId", addonId, "islandId", islandId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/key-value/bulk", jsonObject("addonId", addonId, "islandId", islandId, "values", rawJson(stringMapJson(values == null ? Map.of() : values)), "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkAddonIslandTableKeyValueState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkAddonIslandState(addonId, islandId, values, tables);
    }

    @Override
    public CompletableFuture<String> tableBulkAddonIslandState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/bulk", jsonObject("addonId", addonId, "islandId", islandId, "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkAddonIslandTableState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableBulkAddonIslandState(addonId, islandId, tables);
    }

    @Override
    public CompletableFuture<String> tableBulkSetAddonIslandState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody(AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_SET_ENDPOINT, jsonObject("addonId", addonId, "islandId", islandId, "tables", rawJson(tableMapJson(tables))));
    }

    @Override
    public CompletableFuture<String> bulkSetAddonIslandTableState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableBulkSetAddonIslandState(addonId, islandId, tables);
    }

    @Override
    public CompletableFuture<String> addonIslandTableState(String addonId, UUID islandId, String table) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return post(AddonStateBulkLoadRequest.ISLAND_TABLE_LOAD_ALIAS, jsonObject("addonId", addonId, "islandId", islandId, "table", table));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkLoadAddonIslandState(String addonId, UUID islandId, String table) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return post(AddonStateBulkLoadRequest.ISLAND_ENDPOINT, jsonObject("addonId", addonId, "islandId", islandId, "table", table));
    }

    @Override
    public CompletableFuture<String> tableKeyValueBulkLoadAddonIslandState(AddonStateBulkLoadRequest request) {
        if (request == null) {
            return invalidAddonState("Addon island state bulk load request is required");
        }
        if (!request.islandScoped()) {
            return tableKeyValueBulkLoadAddonState(request);
        }
        return tableKeyValueBulkLoadAddonIslandState(request.addonId(), request.islandId(), request.table());
    }

    @Override
    public CompletableFuture<String> putAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/bulk", jsonObject("addonId", addonId, "islandId", islandId, "table", table, "values", rawJson(stringMapJson(values))));
    }

    @Override
    public CompletableFuture<String> saveAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return putAddonIslandTableState(addonId, islandId, table, values);
    }

    @Override
    public CompletableFuture<String> replaceAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/replace", jsonObject("addonId", addonId, "islandId", islandId, "table", table, "values", rawJson(stringMapJson(values))));
    }

    @Override
    public CompletableFuture<String> clearAddonIslandTableState(String addonId, UUID islandId, String table) {
        if (blank(addonId) || missingIslandId(islandId) || blank(table)) {
            return invalidAddonState("Addon id, island id, and table are required");
        }
        return postWithResultBody("/v1/addons/islands/state/table/clear", jsonObject("addonId", addonId, "islandId", islandId, "table", table));
    }

    @Override
    public CompletableFuture<String> removeAddonIslandState(String addonId, UUID islandId, String key) {
        if (blank(addonId) || missingIslandId(islandId) || blank(key)) {
            return invalidAddonState("Addon id, island id, and key are required");
        }
        return postWithResultBody("/v1/addons/islands/state/remove", jsonObject("addonId", addonId, "islandId", islandId, "key", key));
    }

    @Override
    public CompletableFuture<String> clearAddonIslandState(String addonId, UUID islandId) {
        if (blank(addonId) || missingIslandId(islandId)) {
            return invalidAddonState("Addon id and island id are required");
        }
        return postWithResultBody("/v1/addons/islands/state/clear", jsonObject("addonId", addonId, "islandId", islandId));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean missingIslandId(UUID islandId) {
        return islandId == null || islandId.equals(new UUID(0L, 0L));
    }

    private static CompletableFuture<String> invalidAddonState(String message) {
        return CompletableFuture.completedFuture(jsonObject("code", "INVALID_ADDON_STATE", "message", message));
    }

    private static String stringMapJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return values.entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null)
                .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(entry.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    @Override
    public CompletableFuture<String> migrateSuperiorSkyblock2(String action, String path) {
        String normalizedAction = action == null || action.isBlank() ? "scan" : action.toLowerCase();
        String endpoint = switch (normalizedAction) {
            case "scan" -> "scan";
            case "status" -> "status";
            case "dryrun", "dry-run" -> "dryrun";
            case "extract", "extract-worlds", "world-extract" -> "extract";
            case "import" -> "import";
            case "verify" -> "verify";
            case "rollback" -> "rollback";
            default -> "";
        };
        if (endpoint.isBlank()) {
            return CompletableFuture.completedFuture(jsonObject("code", "INVALID_MIGRATION_ACTION", "message", "Unknown SuperiorSkyblock2 migration action: " + normalizedAction));
        }
        String value = path == null ? "" : path;
        String payload = endpoint.equals("import")
            ? jsonObject("approval", value)
            : (endpoint.equals("rollback") || endpoint.equals("status")) ? "{}" : jsonObject("path", value);
        return postWithResultBody("/v1/admin/migrations/superiorskyblock2/" + endpoint, payload);
    }

    @Override
    public CompletableFuture<String> playerInfo(UUID playerUuid) {
        return postWithResultBody("/v1/admin/players/info", jsonObject("playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<String> playerInfoByName(String lastName) {
        return post("/v1/players/info", jsonObject("lastName", lastName));
    }

    @Override
    public CompletableFuture<String> touchPlayerProfile(UUID playerUuid, String lastName) {
        return postWithResultBody("/v1/players/touch", jsonObject("playerUuid", playerUuid, "lastName", lastName));
    }

    @Override
    public CompletableFuture<String> touchPlayerProfile(UUID playerUuid, String lastName, String locale) {
        return postWithResultBody("/v1/players/touch", jsonObject("playerUuid", playerUuid, "lastName", lastName, "locale", locale));
    }

    @Override
    public CompletableFuture<String> setPlayerLocale(UUID playerUuid, String locale) {
        return postWithResultBody("/v1/players/locale", jsonObject("playerUuid", playerUuid, "locale", locale));
    }

    @Override
    public CompletableFuture<String> setPlayerIsland(UUID playerUuid, UUID islandId) {
        return postWithResultBody("/v1/admin/players/setisland", jsonObject("playerUuid", playerUuid, "islandId", islandId));
    }

    @Override
    public CompletableFuture<String> clearPlayerIsland(UUID playerUuid) {
        return postWithResultBody("/v1/admin/players/clearisland", jsonObject("playerUuid", playerUuid));
    }

    @Override
    public CompletableFuture<String> listTemplates() {
        return postWithResultBody("/v1/admin/templates/list", "{}");
    }

    @Override
    public CompletableFuture<String> upsertTemplate(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        return postWithResultBody("/v1/admin/templates/upsert", jsonObject("templateId", templateId, "displayName", displayName, "enabled", enabled, "minNodeVersion", minNodeVersion));
    }

    @Override
    public CompletableFuture<String> enableTemplate(String templateId) {
        return postWithResultBody("/v1/admin/templates/enable", jsonObject("templateId", templateId));
    }

    @Override
    public CompletableFuture<String> disableTemplate(String templateId) {
        return postWithResultBody("/v1/admin/templates/disable", jsonObject("templateId", templateId));
    }

    @Override
    public CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        String types = supportedTypes.stream().map(Enum::name).collect(Collectors.joining(","));
        return postWithResultBody("/v1/jobs/claim", jsonObject("nodeId", nodeId, "supportedTypes", types, "maxJobs", maxJobs)).thenApply(IslandJobJson::readArray);
    }

    @Override
    public CompletableFuture<String> listJobs() {
        return postWithResultBody("/v1/admin/jobs/list", "{}");
    }

    @Override
    public CompletableFuture<String> retryJob(UUID jobId) {
        return retryJobResult(jobId);
    }

    @Override
    public CompletableFuture<String> retryJobResult(UUID jobId) {
        return postWithResultBody("/v1/admin/jobs/retry", jsonObject("jobId", jobId));
    }

    @Override
    public CompletableFuture<String> cancelJob(UUID jobId) {
        return cancelJobResult(jobId);
    }

    @Override
    public CompletableFuture<String> cancelJobResult(UUID jobId) {
        return postWithResultBody("/v1/admin/jobs/cancel", jsonObject("jobId", jobId));
    }

    @Override
    public CompletableFuture<String> recoverJobs(String nodeId, long minIdleMillis, int maxJobs) {
        return recoverJobsResult(nodeId, minIdleMillis, maxJobs);
    }

    @Override
    public CompletableFuture<String> recoverJobsResult(String nodeId, long minIdleMillis, int maxJobs) {
        return postWithResultBody("/v1/admin/jobs/recover", jsonObject("nodeId", nodeId, "minIdleMillis", minIdleMillis, "maxJobs", maxJobs));
    }

    @Override
    public CompletableFuture<Void> completeJob(String nodeId, UUID jobId) {
        return completeJob(nodeId, jobId, Map.of());
    }

    @Override
    public CompletableFuture<Void> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
        return completeJobResult(nodeId, jobId, payload).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload) {
        return postWithResultBody("/v1/jobs/complete", jsonObject("nodeId", nodeId, "jobId", jobId, "payload", rawJson(mapJson(payload))));
    }

    @Override
    public CompletableFuture<Void> failJob(String nodeId, UUID jobId, String errorMessage) {
        return failJobResult(nodeId, jobId, errorMessage).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> failJobResult(String nodeId, UUID jobId, String errorMessage) {
        return postWithResultBody("/v1/jobs/fail", jsonObject("nodeId", nodeId, "jobId", jobId, "error", errorMessage));
    }

    @Override
    public CompletableFuture<Void> publishHeartbeat(NodeHeartbeatRequest request) {
        return publishHeartbeatResult(request).thenApply(_body -> null);
    }

    @Override
    public CompletableFuture<String> publishHeartbeatResult(NodeHeartbeatRequest request) {
        return postWithResultBody("/v1/nodes/heartbeat", jsonObject(
            "protocolVersion", request.protocolVersion(),
            "nodeId", request.nodeId(),
            "pool", request.pool(),
            "velocityServerName", request.velocityServerName(),
            "nodeVersion", request.nodeVersion(),
            "state", request.state().name(),
            "players", request.players(),
            "softPlayerCap", request.softPlayerCap(),
            "hardPlayerCap", request.hardPlayerCap(),
            "reservedSlots", request.reservedSlots(),
            "activeIslands", request.activeIslands(),
            "maxActiveIslands", request.maxActiveIslands(),
            "mspt", request.mspt(),
            "activationQueue", request.activationQueue(),
            "maxActivationQueue", request.maxActivationQueue(),
            "chunkLoadPressure", request.chunkLoadPressure(),
            "heapUsedMb", request.heapUsedMb(),
            "heapMaxMb", request.heapMaxMb(),
            "recentFailurePenalty", request.recentFailurePenalty(),
            "storageAvailable", request.storageAvailable(),
            "supportedTemplates", request.supportedTemplates()
        ));
    }

    private String mapJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        if (payload == null || payload.isEmpty()) {
            return builder.append("}").toString();
        }
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append("}").toString();
    }

    private String tableMapJson(Map<String, Map<String, String>> tables) {
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

    private CompletableFuture<String> post(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : "");
    }

    private CompletableFuture<String> get(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .GET();
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300 ? response.body() : "");
    }

    private CompletableFuture<String> postWithResultBody(String path, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 500 ? response.body() : "");
    }

    private CompletableFuture<String> deleteWithResultBody(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + authToken)
            .DELETE();
        addAdminHeaders(builder, path);
        CoreMutationContext.apply(builder);
        HttpRequest request = builder.build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 500 ? response.body() : "");
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

    private static String jsonObject(Object... fields) {
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

    private static RawJson rawJson(String value) {
        return new RawJson(value == null || value.isBlank() ? "{}" : value);
    }

    private record RawJson(String value) {
    }

    private static String warpPayload(UUID islandId, UUID actorUuid, String name, String category, IslandLocation location, boolean publicAccess) {
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

    private static String locationPayload(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
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

    private static String countsPayload(Map<String, Long> counts) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            if (!first) {
                builder.append('|');
            }
            first = false;
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
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
