package kr.lunaf.cloudislands.coreclient;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class JdkCoreApiClient implements CoreApiClient {
    private final CoreHttpTransport transport;
    private final BankQueryClient bankQueryClient;
    private final BankCommandClient bankCommandClient;
    private final CommunicationQueryClient communicationQueryClient;
    private final CommunicationCommandClient communicationCommandClient;
    private final IslandLifecycleCommandClient lifecycleCommandClient;
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
    private final JdkCoreRouteClient routeCoreClient;
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
    private final JdkCoreJobClaimClient jobClaimClient;
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
        this(baseUri, authToken, adminToken, "", timeout);
    }

    public JdkCoreApiClient(URI baseUri, String authToken, String adminToken, String nodeId, Duration timeout) {
        this.transport = new CoreHttpTransport(baseUri, authToken, adminToken, nodeId, timeout);
        this.bankQueryClient = new JdkBankQueryClient(this);
        this.bankCommandClient = new JdkBankCommandClient(this);
        this.communicationQueryClient = new JdkCommunicationQueryClient(this);
        this.communicationCommandClient = new JdkCommunicationCommandClient(this);
        this.lifecycleCommandClient = new JdkIslandLifecycleCommandClient(this);
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
        this.routeCoreClient = new JdkCoreRouteClient(this);
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
        this.jobClaimClient = new JdkCoreJobClaimClient(this);
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
    public RouteTicketClient routeTickets() {
        return routeCoreClient;
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
        return lifecycleCommandClient;
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
    public JobClaimClient jobClaims() {
        return jobClaimClient;
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

    CompletableFuture<CoreResponseBody> postBody(String path, String body) {
        return transport.post(path, body);
    }

    CompletableFuture<CoreResponseBody> getBody(String path) {
        return transport.get(path);
    }

    CompletableFuture<CoreResponseBody> postResultBody(String path, String body) {
        return transport.postWithResultBody(path, body);
    }

    CompletableFuture<CoreResponseBody> deleteResultBody(String path) {
        return transport.deleteWithResultBody(path);
    }

}
