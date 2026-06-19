package kr.lunaf.cloudislands.coreservice;

import static kr.lunaf.cloudislands.coreservice.config.CoreSetupSummary.*;
import static kr.lunaf.cloudislands.coreservice.config.CoreNetworkExposure.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.audit.JdbcAuditLogger;
import kr.lunaf.cloudislands.coreservice.audit.RedisAuditLogger;
import kr.lunaf.cloudislands.coreservice.bank.CachingIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.bank.InMemoryIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.bank.JdbcIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.cache.RedisCacheAdmin;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.db.BoundedDataSource;
import kr.lunaf.cloudislands.coreservice.db.DriverManagerDataSource;
import kr.lunaf.cloudislands.coreservice.db.JdbcDialectDataSource;
import kr.lunaf.cloudislands.coreservice.db.JdbcSchemaBootstrap;
import kr.lunaf.cloudislands.coreservice.db.MeteredDataSource;
import kr.lunaf.cloudislands.coreservice.event.CompositeGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.RedisStreamEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminIslandLifecycleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminRuntimeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminNodeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AddonRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AuditRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.EventRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.HealthRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandBankRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandBlockLevelRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandCatalogRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandCommunicationRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandMemberRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandPlayerLifecycleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandQueryRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandSettingsRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandSnapshotRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandUpgradeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandVisitorRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandWarpRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.JobRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.NodeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.PlayerProfileRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.PermissionRoleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.ProgressionRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.ProtocolRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.RoutePreparationRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.RouteTicketRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.SuperiorSkyblock2MigrationRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.TemplateRoutes;
import kr.lunaf.cloudislands.coreservice.islandlog.CachingIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.JdbcIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.JdbcIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.limit.CachingIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.limit.JdbcIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.metrics.PrometheusMetricsRenderer;
import kr.lunaf.cloudislands.coreservice.mission.CachingIslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.mission.InMemoryIslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.mission.JdbcIslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.CachingIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.permission.JdbcIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.CachingPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.profile.JdbcPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.CachingRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.DirtyRankingRecalculationTask;
import kr.lunaf.cloudislands.coreservice.ranking.CachingIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.JdbcIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.JdbcRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.JdbcIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.JdbcIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.JdbcIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.redis.RedisStreamWriterAdapter;
import kr.lunaf.cloudislands.coreservice.role.CachingIslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.role.InMemoryIslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.role.JdbcIslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.IpAllowlist;
import kr.lunaf.cloudislands.coreservice.security.MtlsHeaderGuard;
import kr.lunaf.cloudislands.coreservice.session.InMemoryRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.session.RedisRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionStore;
import kr.lunaf.cloudislands.coreservice.snapshot.CachingIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.InMemoryIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.JdbcIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.template.CachingIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.JdbcIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.ticket.CachingRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.ticket.JdbcRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.coreservice.upgrade.CachingIslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.ConfigUpgradePolicy;
import kr.lunaf.cloudislands.coreservice.upgrade.InMemoryIslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeService;
import kr.lunaf.cloudislands.coreservice.upgrade.JdbcIslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePurchaseResult;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradeRule;
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import kr.lunaf.cloudislands.migration.rollback.CompositeRollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService.RollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.StorageRollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.jdbc.JdbcMigrationRollbackTarget;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.LocalIslandStorage;
import kr.lunaf.cloudislands.storage.s3.S3IslandStorage;
import kr.lunaf.cloudislands.coreservice.addon.AddonStateRepository;
import kr.lunaf.cloudislands.coreservice.addon.InMemoryAddonStateRepository;
import kr.lunaf.cloudislands.coreservice.addon.JdbcAddonStateRepository;

public final class CloudIslandsCoreApplication {
    private static final Logger LOGGER = Logger.getLogger(CloudIslandsCoreApplication.class.getName());
    private static final int MIN_NODE_PROTOCOL_VERSION = 1;
    private static final int MAX_NODE_PROTOCOL_VERSION = NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION;
    private final HttpServer server;
    private final ApiTokenGuard tokenGuard;
    private final FixedWindowRateLimiter rateLimiter;
    private final AdminEndpointGuard adminGuard;
    private final IpAllowlist ipAllowlist;
    private final MtlsHeaderGuard mtlsGuard;
    private final NodeFailureMonitor nodeFailureMonitor;
    private final RouteTicketExpiryMonitor routeTicketExpiryMonitor;
    private final JobRecoveryMonitor jobRecoveryMonitor;
    private final IslandStorage deleteStorage;
    private final IslandRepository islandRepository;
    private final PlayerProfileRepository playerProfiles;
    private final IslandRuntimeRepository runtimeRepository;
    private final IslandJobQueue jobs;
    private final GlobalEventPublisher events;
    private final IslandSnapshotRepository snapshotRepository;
    private final DirtyRankingRecalculationTask rankingRecalculationTask;
    private final int snapshotKeepLatest;
    private final kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy snapshotRetentionPolicy;
    private final java.util.concurrent.atomic.AtomicLong securityRejectsTotal = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong securityRejectsRateLimited = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong securityRejectsUnauthorized = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong securityRejectsMtlsRequired = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong securityRejectsIpNotAllowed = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong securityRejectsAdminPermissionDenied = new java.util.concurrent.atomic.AtomicLong();
    private AuditLogger audit;

    public CloudIslandsCoreApplication(int port) throws IOException {
        this(CoreServiceConfig.fromEnvironment().withPort(port));
    }

    public CloudIslandsCoreApplication(CoreServiceConfig config) throws IOException {
        Clock clock = Clock.systemUTC();
        this.tokenGuard = new ApiTokenGuard(config.coreToken());
        this.rateLimiter = new FixedWindowRateLimiter(clock, config.rateLimitRequests(), config.rateLimitWindow().toMillis());
        this.adminGuard = new AdminEndpointGuard(config.adminToken(), config.adminApiEnabled(), config.adminPermissions());
        this.ipAllowlist = new IpAllowlist(config.ipAllowlist());
        this.mtlsGuard = new MtlsHeaderGuard(config.requireMtls(), config.mtlsVerifiedHeader(), config.mtlsVerifiedValue(), config.mtlsTrustedProxies());
        logSecurityPosture(LOGGER, config);
        config.validateStartupStorage();
        this.deleteStorage = migrationRollbackStorage(config);
        MeteredDataSource meteredDataSource = new MeteredDataSource(new BoundedDataSource(new DriverManagerDataSource(config.jdbcUrl(), config.databaseUsername(), config.databasePassword()), config.databasePoolSize()));
        DataSource dataSource = new JdbcDialectDataSource(meteredDataSource);
        boolean coreJdbcActive = config.jdbcRepositories() || config.jdbcJobs();
        if (coreJdbcActive && config.setupDatabaseAutoSchema()) {
            LOGGER.info("CloudIslands database schema bootstrap applied=" + JdbcSchemaBootstrap.apply(dataSource));
        }
        NodeRegistry baseNodes = config.jdbcRepositories() ? new JdbcNodeRegistry(dataSource) : new InMemoryNodeRegistry();
        NodeRegistry nodes = config.redisEvents() || config.redisJobs()
            ? new CachingNodeRegistry(baseNodes, config.redisUri(), config.heartbeatTimeout())
            : baseNodes;
        NodeAllocator allocator = new NodeAllocator(config.heartbeatTimeout(), config.softFullPolicy(), config.hardFullPolicy());
        RouteTicketStore baseTickets = config.jdbcRepositories() ? new JdbcRouteTicketStore(dataSource, clock) : new InMemoryRouteTicketStore(clock);
        RouteTicketStore tickets = config.redisEvents() || config.redisJobs()
            ? new CachingRouteTicketStore(baseTickets, config.redisUri())
            : baseTickets;
        RouteSessionStore sessions = config.redisEvents() || config.redisJobs()
            ? new RedisRouteSessionStore(config.redisUri())
            : new InMemoryRouteSessionStore(clock);
        IslandJobQueue jobs = config.jdbcJobs() ? new JdbcIslandJobQueue(dataSource, clock, config.leaseDuration()) : config.redisJobs() ? new RedisIslandJobQueue(config.redisUri()) : new InMemoryIslandJobPublisher();
        InMemoryGlobalEventPublisher inMemoryEvents = new InMemoryGlobalEventPublisher();
        RedisStreamWriterAdapter redisEventWriter = config.redisEvents() ? new RedisStreamWriterAdapter(config.redisUri()) : null;
        RedisStreamEventPublisher redisEventPublisher = redisEventWriter == null ? null : new RedisStreamEventPublisher(redisEventWriter);
        RedisCacheAdmin redisCacheAdmin = config.redisEvents() || config.redisJobs() ? new RedisCacheAdmin(config.redisUri()) : null;
        RedisActivationLock activationLock = config.redisEvents() || config.redisJobs() ? new RedisActivationLock(config.redisUri(), config.routePreparingTicketTtl()) : null;
        RedisPlayerCreationLock playerCreationLock = config.redisEvents() || config.redisJobs() ? new RedisPlayerCreationLock(config.redisUri(), config.routePreparingTicketTtl()) : null;
        GlobalEventPublisher events = redisEventPublisher != null
            ? new CompositeGlobalEventPublisher(java.util.List.of(inMemoryEvents, redisEventPublisher))
            : inMemoryEvents;
        IslandRepository baseIslandRepository = config.jdbcRepositories() ? new JdbcIslandRepository(dataSource) : new InMemoryIslandRepository();
        IslandRepository islandRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandRepository(baseIslandRepository, config.redisUri())
            : baseIslandRepository;
        this.islandRepository = islandRepository;
        IslandMetadataRepository baseMetadataRepository = config.jdbcRepositories() ? new JdbcIslandMetadataRepository(dataSource) : new InMemoryIslandMetadataRepository();
        IslandMetadataRepository metadataRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandMetadataRepository(baseMetadataRepository, config.redisUri())
            : baseMetadataRepository;
        PlayerProfileRepository basePlayerProfiles = config.jdbcRepositories() ? new JdbcPlayerProfileRepository(dataSource) : new InMemoryPlayerProfileRepository();
        PlayerProfileRepository playerProfiles = config.redisEvents() || config.redisJobs()
            ? new CachingPlayerProfileRepository(basePlayerProfiles, config.redisUri())
            : basePlayerProfiles;
        this.playerProfiles = playerProfiles;
        IslandPermissionRuleRepository basePermissionRules = config.jdbcRepositories() ? new JdbcIslandPermissionRuleRepository(dataSource) : new InMemoryIslandPermissionRuleRepository();
        IslandPermissionRuleRepository permissionRules = config.redisEvents() || config.redisJobs()
            ? new CachingIslandPermissionRuleRepository(basePermissionRules, config.redisUri())
            : basePermissionRules;
        IslandRoleRepository baseRoleRepository = config.jdbcRepositories() ? new JdbcIslandRoleRepository(dataSource) : new InMemoryIslandRoleRepository();
        IslandRoleRepository roleRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandRoleRepository(baseRoleRepository, config.redisUri())
            : baseRoleRepository;
        IslandRuntimeRepository baseRuntimeRepository = config.jdbcRepositories() ? new JdbcIslandRuntimeRepository(dataSource) : new InMemoryIslandRuntimeRepository();
        IslandRuntimeRepository runtimeRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandRuntimeRepository(baseRuntimeRepository, config.redisUri())
            : baseRuntimeRepository;
        this.runtimeRepository = runtimeRepository;
        this.jobs = jobs;
        this.events = events;
        IslandSnapshotRepository baseSnapshotRepository = config.jdbcRepositories() ? new JdbcIslandSnapshotRepository(dataSource) : new InMemoryIslandSnapshotRepository();
        IslandSnapshotRepository snapshotRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandSnapshotRepository(baseSnapshotRepository, config.redisUri())
            : baseSnapshotRepository;
        this.snapshotRepository = snapshotRepository;
        this.snapshotKeepLatest = Math.max(1, config.snapshotKeepLatest());
        this.snapshotRetentionPolicy = config.snapshotRetentionPolicy();
        RankingRepository baseRankingRepository = config.jdbcRepositories() ? new JdbcRankingRepository(dataSource) : new InMemoryRankingRepository();
        RankingRepository rankingRepository = config.redisEvents() || config.redisJobs()
            ? new CachingRankingRepository(baseRankingRepository, config.redisUri())
            : baseRankingRepository;
        IslandLevelRepository baseLevelRepository = config.jdbcRepositories() ? new JdbcIslandLevelRepository(dataSource) : new InMemoryIslandLevelRepository();
        IslandLevelRepository levelRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandLevelRepository(baseLevelRepository, config.redisUri())
            : baseLevelRepository;
        kr.lunaf.cloudislands.coreservice.ranking.ConfigBlockValues.load(config.blockValuesFile()).forEach(levelRepository::putBlockValue);
        RankingRecalculationService levelRecalculation = new RankingRecalculationService(rankingRepository, events, config.levelFormulaExpression(), config.worthFormulaType());
        this.rankingRecalculationTask = new DirtyRankingRecalculationTask(rankingRepository, levelRepository, metadataRepository, levelRecalculation);
        IslandUpgradeRepository baseUpgradeRepository = config.jdbcRepositories() ? new JdbcIslandUpgradeRepository(dataSource) : new InMemoryIslandUpgradeRepository();
        IslandUpgradeRepository upgradeRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandUpgradeRepository(baseUpgradeRepository, config.redisUri())
            : baseUpgradeRepository;
        UpgradePolicy upgradePolicy = ConfigUpgradePolicy.load(config.upgradesFile());
        IslandBankRepository baseBankRepository = config.jdbcRepositories() ? new JdbcIslandBankRepository(dataSource) : new InMemoryIslandBankRepository();
        IslandBankRepository bankRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandBankRepository(baseBankRepository, config.redisUri())
            : baseBankRepository;
        IslandUpgradeService upgradeService = new IslandUpgradeService(upgradeRepository, bankRepository, upgradePolicy);
        IslandMissionRepository baseMissionRepository = config.jdbcRepositories() ? new JdbcIslandMissionRepository(dataSource) : new InMemoryIslandMissionRepository();
        IslandMissionRepository missionRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandMissionRepository(baseMissionRepository, config.redisUri())
            : baseMissionRepository;
        IslandLimitRepository baseLimitRepository = config.jdbcRepositories() ? new JdbcIslandLimitRepository(dataSource) : new InMemoryIslandLimitRepository();
        IslandLimitRepository limitRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandLimitRepository(baseLimitRepository, config.redisUri())
            : baseLimitRepository;
        IslandTemplateRepository baseTemplateRepository = config.jdbcRepositories() ? new JdbcIslandTemplateRepository(dataSource) : new InMemoryIslandTemplateRepository();
        IslandTemplateRepository templateRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandTemplateRepository(baseTemplateRepository, config.redisUri())
            : baseTemplateRepository;
        AddonStateRepository addonStates = config.jdbcRepositories() ? new JdbcAddonStateRepository(dataSource) : new InMemoryAddonStateRepository();
        AuditLogger baseAudit = config.jdbcRepositories() ? new JdbcAuditLogger(dataSource) : new InMemoryAuditLogger();
        AuditLogger audit = redisEventWriter == null ? baseAudit : new RedisAuditLogger(baseAudit, redisEventWriter, RedisKeys.auditStream());
        this.audit = audit;
        IslandLogRepository baseIslandLogs = config.jdbcRepositories() ? new JdbcIslandLogRepository(dataSource) : new InMemoryIslandLogRepository();
        IslandLogRepository islandLogs = config.redisEvents() || config.redisJobs()
            ? new CachingIslandLogRepository(baseIslandLogs, config.redisUri())
            : baseIslandLogs;
        RoutingOrchestrator routing = new RoutingOrchestrator(nodes, allocator, tickets, islandRepository, metadataRepository, runtimeRepository, templateRepository, jobs, events, config.islandPool(), config.routeTicketTtl(), config.routePreparingTicketTtl(), activationLock);
        CreateIslandWorkflow createIsland = new CreateIslandWorkflow(islandRepository, metadataRepository, playerProfiles, templateRepository, nodes, allocator, runtimeRepository, jobs, events, tickets, config.islandPool(), config.routePreparingTicketTtl(), playerCreationLock);
        IslandLifecycleWorkflow lifecycle = new IslandLifecycleWorkflow(runtimeRepository, islandRepository, templateRepository, nodes, allocator, jobs, events, config.islandPool(), config.migrationPolicy(), activationLock);
        MigrationAdminService migrationAdmin = new MigrationAdminService(
            islandRepository,
            metadataRepository,
            playerProfiles,
            permissionRules,
            upgradeRepository,
            bankRepository,
            limitRepository,
            missionRepository,
            levelRepository,
            snapshotRepository,
            migrationRollbackTarget(config, dataSource),
            runtimeRepository,
            Path.of(config.storageLocalPath()),
            lifecycle
        );
        kr.lunaf.cloudislands.coreservice.job.JobCompletionService jobCompletion = new kr.lunaf.cloudislands.coreservice.job.JobCompletionService(runtimeRepository, events, snapshotRepository, tickets, jobs, islandRepository, playerProfiles, config.routeTicketTtl(), config.snapshotRetentionPolicy(), activationLock);
        PrometheusMetricsRenderer metrics = new PrometheusMetricsRenderer(nodes, jobs, tickets, runtimeRepository, inMemoryEvents, config.heartbeatTimeout(), meteredDataSource::lastQuerySeconds, meteredDataSource::activeConnections, meteredDataSource::openedConnections, meteredDataSource::connectionFailures, meteredDataSource::queryFailures, () -> redisEventPublisher == null ? 0L : redisEventPublisher.failuresTotal(), () -> redisCacheFailures(nodes, tickets, sessions, islandRepository, metadataRepository, playerProfiles, permissionRules, roleRepository, runtimeRepository, rankingRepository, levelRepository, bankRepository, limitRepository, missionRepository, upgradeRepository, templateRepository, snapshotRepository, islandLogs, redisCacheAdmin, activationLock, playerCreationLock, audit), () -> config.databasePoolSize(), () -> coreJdbcFallbackActive(config), config::setupDatabaseDurable, config::setupDatabaseRequestedBackend, config::setupDatabaseEffectiveAuthority, config::setupDatabaseFallbackTarget, () -> config.coreToken() != null && !config.coreToken().isBlank(), () -> config.adminToken() != null && !config.adminToken().isBlank(), config::adminApiEnabled, config::requireMtls, () -> config.ipAllowlist() != null && !config.ipAllowlist().isBlank(), () -> publicBind(config.bind()) && (config.ipAllowlist() == null || config.ipAllowlist().isBlank()), () -> config.redisUri() != null && !internalHost(config.redisUri().getHost()), () -> coreJdbcActive && !internalHost(jdbcHost(config.jdbcUrl())), () -> "S3".equalsIgnoreCase(config.storageType()) && config.storageEndpoint() != null && !internalHost(config.storageEndpoint().getHost()), () -> "S3".equalsIgnoreCase(config.storageType()) && config.storageEndpoint() != null && "http".equalsIgnoreCase(config.storageEndpoint().getScheme()) && !internalHost(config.storageEndpoint().getHost()), () -> config.rateLimitRequests(), () -> config.rateLimitWindow().toSeconds(), this.rankingRecalculationTask::drainedTotal, this.rankingRecalculationTask::recalculatedTotal, this.rankingRecalculationTask::failuresTotal, this.rankingRecalculationTask::lastBatchSize, securityRejectsTotal::get, securityRejectsRateLimited::get, securityRejectsUnauthorized::get, securityRejectsMtlsRequired::get, securityRejectsIpNotAllowed::get, securityRejectsAdminPermissionDenied::get);
        this.nodeFailureMonitor = new NodeFailureMonitor(nodes, runtimeRepository, islandRepository, events, config.heartbeatTimeout(), tickets, sessions, snapshotRepository, lifecycle);
        this.routeTicketExpiryMonitor = new RouteTicketExpiryMonitor(tickets, events, config.routeTicketTtl());
        this.jobRecoveryMonitor = new JobRecoveryMonitor(jobs, Duration.ofSeconds(60), config.leaseDuration().toMillis(), 16);
        this.server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        new HealthRoutes(config, metrics::render).register(this::route);
        route("/v1/admin/config", exchange -> write(exchange, 200, configSummaryJson(config, nodes)));
        new NodeRoutes(nodes, config.heartbeatTimeout(), runtimeRepository).register(this::route);
        new JobRoutes(jobs, jobCompletion, audit).register(this::route);
        new EventRoutes(inMemoryEvents).register(this::route);
        new AuditRoutes(audit).register(this::route);
        new AddonRoutes(addonStates, audit, events).register(this::route);
        new ProgressionRoutes(rankingRepository, upgradePolicy, levelRepository, missionRepository, limitRepository, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(this::route);
        new PermissionRoleRoutes(islandRepository, metadataRepository, permissionRules, roleRepository, islandLogs, audit, events).register(this::route);
        new IslandBankRoutes(bankRepository, limitRepository, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(this::route);
        new IslandBlockLevelRoutes(levelRepository, rankingRepository, levelRecalculation, islandRepository, metadataRepository, permissionRules, audit, events).register(this::route);
        new IslandUpgradeRoutes(upgradeRepository, upgradeService, upgradePolicy, bankRepository, limitRepository, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(this::route);
        new IslandCommunicationRoutes(islandLogs, islandRepository, metadataRepository, playerProfiles, events).register(this::route);
        new IslandSnapshotRoutes(snapshotRepository, runtimeRepository, snapshotRetentionPolicy, events).register(this::route);
        new IslandMemberRoutes(islandRepository, metadataRepository, limitRepository, permissionRules, playerProfiles, islandLogs, audit, events).register(this::route);
        new IslandVisitorRoutes(islandRepository, metadataRepository, limitRepository, permissionRules, islandLogs, audit, events).register(this::route);
        new IslandSettingsRoutes(islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(this::route);
        new IslandWarpRoutes(islandRepository, metadataRepository, limitRepository, permissionRules, islandLogs, audit, events).register(this::route);
        new IslandCatalogRoutes(islandRepository, metadataRepository, createIsland, islandLogs, audit).register(this::route);
        new IslandPlayerLifecycleRoutes(islandRepository, metadataRepository, permissionRules, lifecycle, this::requestIslandDelete, islandLogs, audit, events).register(this::route);
        new IslandQueryRoutes(
            islandRepository,
            metadataRepository,
            runtimeRepository,
            levelRepository,
            levelRecalculation,
            permissionRules,
            roleRepository,
            limitRepository,
            upgradeRepository,
            bankRepository,
            missionRepository,
            snapshotRepository,
            islandLogs,
            playerProfiles,
            audit,
            this::requestIslandDelete
        ).register(this::routePrefix);
        new RoutePreparationRoutes(routing).register(this::route);
        new RouteTicketRoutes(routing, tickets, sessions, audit, events).register(this::route);
        new AdminRuntimeRoutes(sessions, tickets, redisCacheAdmin, audit, events).register(this::route);
        new SuperiorSkyblock2MigrationRoutes(config.superiorSkyblock2MigrationEnabled(), migrationAdmin, audit).register(this::route);
        new PlayerProfileRoutes(playerProfiles, audit).register(this::route);
        new TemplateRoutes(templateRepository, audit, events).register(this::route);
        new ProtocolRoutes(nodes).register(this::route);
        new AdminNodeRoutes(nodes, nodeFailureMonitor, config.heartbeatTimeout(), audit, events).register(this::route, this::routePrefix);
        new AdminIslandLifecycleRoutes(
            lifecycle,
            islandRepository,
            runtimeRepository,
            snapshotRepository,
            audit,
            events,
            this::requestIslandDelete
        ).register(this::route, this::routePrefix);
    }

    private static long redisCacheFailures(NodeRegistry nodes, RouteTicketStore tickets, RouteSessionStore sessions, IslandRepository islands, IslandMetadataRepository metadata, PlayerProfileRepository playerProfiles, IslandPermissionRuleRepository permissionRules, IslandRoleRepository roles, IslandRuntimeRepository runtimes, RankingRepository rankings, IslandLevelRepository levels, IslandBankRepository bank, IslandLimitRepository limits, IslandMissionRepository missions, IslandUpgradeRepository upgrades, IslandTemplateRepository templates, IslandSnapshotRepository snapshots, IslandLogRepository islandLogs, RedisCacheAdmin redisCacheAdmin, RedisActivationLock activationLock, RedisPlayerCreationLock playerCreationLock, AuditLogger audit) {
        long failures = 0L;
        if (nodes instanceof CachingNodeRegistry cachingNodes) {
            failures += cachingNodes.failuresTotal();
        }
        if (tickets instanceof CachingRouteTicketStore cachingTickets) {
            failures += cachingTickets.failuresTotal();
        }
        if (sessions instanceof RedisRouteSessionStore redisSessions) {
            failures += redisSessions.failuresTotal();
        }
        if (islands instanceof CachingIslandRepository cachingIslands) {
            failures += cachingIslands.failuresTotal();
        }
        if (metadata instanceof CachingIslandMetadataRepository cachingMetadata) {
            failures += cachingMetadata.failuresTotal();
        }
        if (playerProfiles instanceof CachingPlayerProfileRepository cachingProfiles) {
            failures += cachingProfiles.failuresTotal();
        }
        if (permissionRules instanceof CachingIslandPermissionRuleRepository cachingPermissionRules) {
            failures += cachingPermissionRules.failuresTotal();
        }
        if (roles instanceof CachingIslandRoleRepository cachingRoles) {
            failures += cachingRoles.failuresTotal();
        }
        if (runtimes instanceof CachingIslandRuntimeRepository cachingRuntimes) {
            failures += cachingRuntimes.failuresTotal();
        }
        if (rankings instanceof CachingRankingRepository cachingRankings) {
            failures += cachingRankings.failuresTotal();
        }
        if (levels instanceof CachingIslandLevelRepository cachingLevels) {
            failures += cachingLevels.failuresTotal();
        }
        if (bank instanceof CachingIslandBankRepository cachingBank) {
            failures += cachingBank.failuresTotal();
        }
        if (limits instanceof CachingIslandLimitRepository cachingLimits) {
            failures += cachingLimits.failuresTotal();
        }
        if (missions instanceof CachingIslandMissionRepository cachingMissions) {
            failures += cachingMissions.failuresTotal();
        }
        if (upgrades instanceof CachingIslandUpgradeRepository cachingUpgrades) {
            failures += cachingUpgrades.failuresTotal();
        }
        if (templates instanceof CachingIslandTemplateRepository cachingTemplates) {
            failures += cachingTemplates.failuresTotal();
        }
        if (snapshots instanceof CachingIslandSnapshotRepository cachingSnapshots) {
            failures += cachingSnapshots.failuresTotal();
        }
        if (islandLogs instanceof CachingIslandLogRepository cachingIslandLogs) {
            failures += cachingIslandLogs.failuresTotal();
        }
        if (redisCacheAdmin != null) {
            failures += redisCacheAdmin.failuresTotal();
        }
        if (activationLock != null) {
            failures += activationLock.failuresTotal();
        }
        if (playerCreationLock != null) {
            failures += playerCreationLock.failuresTotal();
        }
        if (audit instanceof RedisAuditLogger redisAudit) {
            failures += redisAudit.failuresTotal();
        }
        return failures;
    }

    private static RollbackTarget migrationRollbackTarget(CoreServiceConfig config, DataSource dataSource) {
        if (!config.jdbcRepositories()) {
            return null;
        }
        List<RollbackTarget> targets = new ArrayList<>();
        targets.add(new JdbcMigrationRollbackTarget(dataSource));
        IslandStorage storage = migrationRollbackStorage(config);
        if (storage != null) {
            targets.add(new StorageRollbackTarget(islandId -> {
                try {
                    storage.deleteIsland(islandId);
                } catch (IOException exception) {
                    throw new IllegalStateException("failed to delete island storage " + islandId, exception);
                }
            }));
        }
        return targets.size() == 1 ? targets.get(0) : new CompositeRollbackTarget(targets);
    }

    private static IslandStorage migrationRollbackStorage(CoreServiceConfig config) {
        if ("LOCAL".equalsIgnoreCase(config.storageType())) {
            return new LocalIslandStorage(Path.of(config.storageLocalPath()));
        }
        if ("S3".equalsIgnoreCase(config.storageType())) {
            return new S3IslandStorage(config.storageEndpoint(), config.storageBucket(), config.storageRegion(), config.storageAccessKey(), config.storageSecretKey(), config.storageBearerToken());
        }
        return null;
    }

    private static String storageBackendSafety(CoreServiceConfig config) {
        if (config == null || config.storageType() == null || config.storageType().isBlank()) {
            return "unknown-storage-backend";
        }
        if ("S3".equalsIgnoreCase(config.storageType())) {
            return "shared-object-storage-safe-for-multi-node-island-pools";
        }
        if ("LOCAL".equalsIgnoreCase(config.storageType())) {
            return "local-storage-requires-shared-filesystem-mount-for-multi-node-island-pools";
        }
        return "unsupported-storage-backend";
    }

    private static String configSummaryJson(CoreServiceConfig config, NodeRegistry nodes) {
        kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy snapshotPolicy = config.snapshotRetentionPolicy().normalized();
        return "{"
            + "\"repositoryMode\":\"" + escape(config.repositoryMode()) + "\","
            + "\"jobQueueMode\":\"" + escape(config.jobQueueMode()) + "\","
            + "\"eventBusMode\":\"" + escape(config.eventBusMode()) + "\","
            + "\"effectiveRepositoryMode\":\"" + (config.jdbcRepositories() ? "JDBC" : "IN_MEMORY") + "\","
            + "\"effectiveJobQueueMode\":\"" + (config.jdbcJobs() ? "JDBC" : config.redisJobs() ? "REDIS" : "IN_MEMORY") + "\","
            + "\"effectiveEventBusMode\":\"" + (config.redisEvents() ? "REDIS" : "IN_MEMORY") + "\","
            + "\"coreJdbcRepositoriesEnabled\":" + config.jdbcRepositories() + ","
            + "\"coreJdbcJobsEnabled\":" + config.jdbcJobs() + ","
            + "\"configuredDatabaseType\":\"" + escape(config.configuredDatabaseType()) + "\","
            + "\"configuredDatabaseTypeSource\":\"" + escape(CoreServiceConfig.configuredDatabaseTypeSource()) + "\","
            + "\"runtimeMode\":\"" + escape(config.runtimeMode()) + "\","
            + "\"databaseBackend\":\"" + escape(jdbcBackend(config.jdbcUrl())) + "\","
            + "\"jdbcUrlSource\":\"" + escape(CoreServiceConfig.configuredJdbcUrlSource()) + "\","
            + "\"effectiveJdbcSettingsType\":\"" + escape(CoreServiceConfig.configuredJdbcSettingsType()) + "\","
            + "\"effectiveJdbcSettingsSource\":\"" + escape(CoreServiceConfig.configuredJdbcSettingsSource()) + "\","
            + "\"coreJdbcSupported\":" + coreJdbcSupported(config.jdbcUrl()) + ","
            + "\"coreJdbcSupportedBackends\":\"POSTGRESQL,MYSQL,MARIADB\","
            + "\"coreSetupFallbackBackends\":\"POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC\","
            + "\"coreSetupFallbackRequireSharedBeforeLocal\":" + config.setupDatabaseFallbackRequireSharedBeforeLocal() + ","
            + "\"coreSetupFallbackLocalLast\":" + config.setupDatabaseFallbackLocalLast() + ","
            + "\"coreSetupFallbackProductionSafeOrder\":\"" + escape(config.setupDatabaseFallbackProductionSafeOrder()) + "\","
            + "\"coreSetupDatabaseAutoSchema\":" + config.setupDatabaseAutoSchema() + ","
            + "\"coreSetupDatabaseAutoSchemaPolicy\":\"explicit-opt-in-postgresql-mysql-mariadb-bootstrap\","
            + "\"coreSetupDatabaseAutoSchemaResource\":\"postgresql=/db/migration/V1..V54,mysql-mariadb=/db/mysql/V1__cloudislands_mysql_schema.sql\","
            + "\"coreSetupDatabaseAutoSchemaHistoryTable\":\"cloudislands_schema_bootstrap\","
            + "\"coreSetupDatabaseAutoSchemaRetryPolicy\":\"ignore-existing-schema-objects-and-mark-bootstrap-after-complete-apply\","
            + "\"coreSetupDatabaseAutoSchemaGuardPolicy\":\"generated-columns-enforce-active-unique-guards-for-mysql-mariadb\","
            + "\"coreSetupFallbackEnabled\":" + config.setupDatabaseFallbackEnabled() + ","
            + "\"coreSetupAllowInMemoryFallback\":" + config.setupDatabaseAllowInMemoryFallback() + ","
            + "\"coreSetupFallbackEffective\":" + coreJdbcFallbackActive(config) + ","
            + "\"coreSetupFallbackSafetyForced\":" + coreSetupFallbackSafetyForced(config) + ","
            + "\"coreSetupFallbackPolicy\":\"" + escape(coreSetupFallbackPolicy(config)) + "\","
            + "\"coreSetupFallbackOrder\":\"" + escape(config.setupDatabaseFallbackOrder()) + "\","
            + "\"coreSetupFallbackMode\":\"" + escape(coreSetupFallbackMode(config)) + "\","
            + "\"coreSetupDatabaseRequestedBackend\":\"" + escape(config.setupDatabaseRequestedBackend()) + "\","
            + "\"coreSetupDatabaseEffectiveAuthority\":\"" + escape(config.setupDatabaseEffectiveAuthority()) + "\","
            + "\"coreSetupDatabaseEffectiveBackend\":\"" + escape(config.setupDatabaseEffectiveBackend()) + "\","
            + "\"coreSetupDatabaseFallbackTarget\":\"" + escape(config.setupDatabaseFallbackTarget()) + "\","
            + "\"coreSetupDatabasePostgresqlFallbackConfigured\":" + config.setupDatabasePostgresqlFallbackConfigured() + ","
            + "\"coreSetupDatabaseMysqlFallbackConfigured\":" + config.setupDatabaseMysqlFallbackConfigured() + ","
            + "\"coreSetupDatabaseMariadbFallbackConfigured\":" + config.setupDatabaseMariadbFallbackConfigured() + ","
            + "\"coreSetupDatabaseCoreApiFallbackConfigured\":" + config.setupDatabaseCoreApiFallbackConfigured() + ","
            + "\"coreSetupDatabaseFallbackReason\":\"" + escape(config.setupDatabaseFallbackReason()) + "\","
            + "\"coreSetupDatabaseFallbackSummary\":\"" + escape(config.setupDatabaseFallbackSummary()) + "\","
            + "\"coreSetupDatabaseFallbackCandidateChain\":\"" + escape(coreSetupFallbackCandidateChain(config)) + "\","
            + "\"coreSetupDatabaseFallbackReadyBackends\":\"" + escape(coreSetupFallbackReadyBackends(config)) + "\","
            + "\"coreSetupDatabaseFallbackMissingBackends\":\"" + escape(coreSetupFallbackMissingBackends(config)) + "\","
            + "\"coreSetupDatabaseFallbackDecision\":\"" + escape(coreSetupFallbackDecision(config)) + "\","
            + "\"coreSetupDatabaseDurable\":" + config.setupDatabaseDurable() + ","
            + "\"coreSetupDatabaseProductionDurable\":" + config.setupDatabaseProductionDurable() + ","
            + "\"coreSetupDatabaseReady\":" + config.setupDatabaseReady() + ","
            + "\"coreSetupDatabaseFallbackSafetyForced\":" + config.setupDatabaseFallbackSafetyForced() + ","
            + "\"coreSetupDatabaseFallbackSafetyMode\":\"" + escape(config.setupDatabaseFallbackSafetyMode()) + "\","
            + "\"coreSetupDatabaseOperationalModes\":\"POSTGRESQL=CORE_JDBC,MYSQL=CORE_JDBC,MARIADB=CORE_JDBC,CORE_API=CLIENT_MODE_NO_CORE_SERVICE_SELF_STORAGE\","
            + "\"coreSetupDatabasePrimaryAuthority\":\"POSTGRESQL_MYSQL_MARIADB_FOR_CORE_STATE_CORE_API_FOR_CLIENT_ADDON_STATE\","
            + "\"coreSetupDatabaseMysqlPolicy\":\"native-core-jdbc-through-setup.database.mysql-or-jdbc-url\","
            + "\"coreSetupDatabaseMariadbPolicy\":\"native-core-jdbc-through-setup.database.mariadb-or-jdbc-url\","
            + "\"coreSetupDatabaseCoreApiPolicy\":\"client-addon-state-marker-core-self-storage-requires-durable-jdbc-or-explicit-non-production-in-memory-fallback\","
            + "\"coreSetupDatabaseSafeFallbackWarning\":\"IN_MEMORY_CORE_FALLBACK_IS_NON_DURABLE_AND_NOT_READY_FOR_PRODUCTION\","
            + "\"coreSetupDatabaseConfigLoader\":\"yaml-nested-dotted-path\","
            + "\"coreSetupDatabaseResolvedPathExamples\":\"setup.database.type,setup.database.postgresql.jdbc-url,setup.database.postgresql.username,setup.database.mysql.host,setup.database.mysql.password,setup.database.mariadb.pool-size,setup.database.core-api.enabled\","
            + "\"coreSetupDatabaseConfigShapes\":\"setup.database.*,setup.database-*,setup.database.fallback.order(list-or-comma-string)\","
            + "\"coreSetupDatabaseTypedShapes\":\"setup.database.postgresql.*,setup.database.mysql.*,setup.database.mariadb.*,setup.database.core-api.*\","
            + "\"coreSetupDatabaseTypedCredentialKeys\":\"username,password,pool-size\","
            + "\"coreSetupDatabaseTypedHostMode\":\"requires-type-or-typed-url-inference\","
            + "\"coreSetupDatabaseTypedProbeOrder\":\"postgresql,mysql,mariadb\","
            + "\"coreSetupDatabaseCoreApiMode\":\"enabled-marker-no-core-jdbc\","
            + "\"coreSetupDatabaseCoreApiBaseUrl\":\"" + escape(config.setupDatabaseCoreApiBaseUrl()) + "\","
            + "\"coreSetupDatabaseCoreApiAuthTokenConfigured\":" + config.setupDatabaseCoreApiAuthTokenConfigured() + ","
            + "\"coreSetupDatabaseCoreApiAdminTokenConfigured\":" + config.setupDatabaseCoreApiAdminTokenConfigured() + ","
            + "\"coreSetupDatabaseCoreApiTimeoutMs\":" + config.setupDatabaseCoreApiTimeoutMillis() + ","
            + "\"coreSetupDatabaseCoreApiClientMode\":" + config.setupDatabaseCoreApiClientMode() + ","
            + "\"coreSetupDatabaseCoreApiClientReady\":" + config.setupDatabaseCoreApiClientReady() + ","
            + "\"coreSetupDatabaseFallbackReadiness\":\"" + escape(config.setupDatabaseFallbackReadiness()) + "\","
            + "\"coreSetupDatabaseCoreApiConfigPaths\":\"setup.database.core-api.base-url,setup.database.core-api.url,setup.database.core-api.auth-token,setup.database.core-api.admin-token,setup.database.core-api.timeout-ms,setup.core-api.*,setup-core-api.*,core-api.*\","
            + "\"coreSetupDatabaseEnv\":\"CI_DATABASE_TYPE,CI_JDBC_URL,CI_DB_USERNAME,CI_DB_PASSWORD,CI_DB_POOL_SIZE,CI_DB_AUTO_SCHEMA,CI_DB_FALLBACK_ENABLED,CI_DB_FALLBACK_ORDER,CI_SETUP_CORE_API_BASE_URL,CI_SETUP_CORE_API_AUTH_TOKEN,CI_SETUP_CORE_API_ADMIN_TOKEN,CI_SETUP_CORE_API_TIMEOUT_MS\","
            + "\"coreSetupDatabasePrecedence\":\"env,nested-setup-database,legacy-flat-setup,database-default\","
            + "\"coreSetupDatabaseNameAliases\":\"setup.database.name,setup.database.database,setup.database-name\","
            + "\"nodeAllocatorPolicy\":\"pool-filter-unique-velocity-server-fresh-heartbeat-storage-ready-template-version-capacity-then-weighted-score\","
            + "\"nodeAllocatorScoreWeights\":\"players=0.25,activeIslands=0.15,mspt=0.25,activationQueue=0.15,chunkLoad=0.10,memory=0.05,recentFailure=0.05\","
            + "\"nodeAllocatorHardRules\":\"READY-or-policy-allowed-SOFT_FULL,heartbeat-fresh,storage-available,hard-player-cap,max-active-islands,max-activation-queue,template-supported,min-node-version,unique-velocity-server-name,non-default-node-identity\","
            + "\"nodeAllocatorFiveSixNodePolicy\":\"no-fixed-node-count-limit-add-island-nodes-with-unique-node-id-unique-velocity-server-name-shared-storage\","
            + "\"coreSetupDatabaseJdbcAliases\":\"CI_JDBC_URL,setup.database.jdbc-url,setup.jdbc-url,database.jdbc-url\","
            + "\"coreSetupDatabaseTypeInference\":\"CI_DATABASE_TYPE,setup.database.type,setup.database-type,setup.database.core-api.enabled,CI_JDBC_URL,setup.database.jdbc-url,setup.jdbc-url,setup.database.postgresql.jdbc-url,setup.database.postgresql.url,setup.database.mysql.jdbc-url,setup.database.mysql.url,setup.database.mariadb.jdbc-url,setup.database.mariadb.url,typed-host-name,database.jdbc-url\","
            + "\"observabilityMetricsPolicy\":\"prometheus-exporter-covers-node-island-route-permission-job-storage-database-redis-metrics\","
            + "\"observabilityRequiredMetrics\":\"cloudislands_nodes_online,cloudislands_node_players,cloudislands_node_mspt,cloudislands_node_active_islands,cloudislands_node_activation_queue,cloudislands_island_activation_seconds,cloudislands_island_save_seconds,cloudislands_island_snapshot_seconds,cloudislands_route_ticket_created_total,cloudislands_route_ticket_failed_total,cloudislands_permission_checks_total,cloudislands_permission_cache_hit_ratio,cloudislands_jobs_pending,cloudislands_jobs_failed_total,cloudislands_jobs_retry_total,cloudislands_storage_upload_seconds,cloudislands_storage_download_seconds,cloudislands_database_query_seconds,cloudislands_redis_latency_seconds\","
            + "\"observabilityRequiredDashboardPanels\":\"nodes-online,node-players,node-mspt,active-islands,activation-seconds,island-save-failures,route-failures,redis-latency,database-pool-usage,object-storage-failure-ratio\","
            + "\"observabilityDashboardPolicy\":\"grafana-dashboard-must-show-node-load-activation-save-route-redis-db-and-object-storage-health\","
            + "\"coreJdbcFallbackReason\":\"" + escape(coreJdbcFallbackReason(config)) + "\","
            + "\"coreJdbcFallbackActive\":" + coreJdbcFallbackActive(config) + ","
            + "\"coreJdbcFallbackStatus\":\"" + escape(coreJdbcFallbackStatus(config)) + "\","
            + "\"addonStateBulkSaveApi\":true,"
            + "\"addonStateBulkSaveGlobalEndpoint\":\"" + AddonStateBulkSaveRequest.GLOBAL_LEGACY_ENDPOINT + "\","
            + "\"addonStateBulkSaveIslandEndpoint\":\"" + AddonStateBulkSaveRequest.ISLAND_LEGACY_ENDPOINT + "\","
            + "\"addonStateTableKeyValueBulkSaveGlobalEndpoint\":\"" + AddonStateBulkSaveRequest.GLOBAL_ENDPOINT + "\","
            + "\"addonStateTableKeyValueBulkSaveIslandEndpoint\":\"" + AddonStateBulkSaveRequest.ISLAND_ENDPOINT + "\","
            + "\"addonStateTableKeyValueBulkSaveGlobalAlias\":\"" + AddonStateBulkSaveRequest.GLOBAL_BULK_SAVE_ALIAS + "\","
            + "\"addonStateTableKeyValueBulkSaveIslandAlias\":\"" + AddonStateBulkSaveRequest.ISLAND_BULK_SAVE_ALIAS + "\","
            + "\"addonStateTableKeyValueBulkGlobalEndpoint\":\"" + AddonStateBulkSaveRequest.GLOBAL_BULK_ALIAS + "\","
            + "\"addonStateTableKeyValueBulkIslandEndpoint\":\"" + AddonStateBulkSaveRequest.ISLAND_BULK_ALIAS + "\","
            + "\"addonStateTableKeyValueBulkSaveGlobalAliases\":\"" + String.join(",", AddonStateBulkSaveRequest.GLOBAL_ENDPOINTS) + "\","
            + "\"addonStateTableKeyValueBulkSaveIslandAliases\":\"" + String.join(",", AddonStateBulkSaveRequest.ISLAND_ENDPOINTS) + "\","
            + "\"addonStateTableKeyValueBulkLoadGlobalEndpoint\":\"" + AddonStateBulkLoadRequest.GLOBAL_ENDPOINT + "\","
            + "\"addonStateTableKeyValueBulkLoadIslandEndpoint\":\"" + AddonStateBulkLoadRequest.ISLAND_ENDPOINT + "\","
            + "\"addonStateTableKeyValueBulkLoadGlobalAliases\":\"" + String.join(",", AddonStateBulkLoadRequest.GLOBAL_ENDPOINTS) + "\","
            + "\"addonStateTableKeyValueBulkLoadIslandAliases\":\"" + String.join(",", AddonStateBulkLoadRequest.ISLAND_ENDPOINTS) + "\","
            + "\"addonStateTableLoadGlobalEndpoint\":\"" + AddonStateBulkLoadRequest.GLOBAL_TABLE_LOAD_ALIAS + "\","
            + "\"addonStateTableLoadIslandEndpoint\":\"" + AddonStateBulkLoadRequest.ISLAND_TABLE_LOAD_ALIAS + "\","
            + "\"addonStateTableBulkGlobalEndpoint\":\"" + AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_ENDPOINT + "\","
            + "\"addonStateTableBulkIslandEndpoint\":\"" + AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_ENDPOINT + "\","
            + "\"addonStateTableBulkSetGlobalEndpoint\":\"" + AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_SET_ENDPOINT + "\","
            + "\"addonStateTableBulkSetIslandEndpoint\":\"" + AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_SET_ENDPOINT + "\","
            + "\"addonStateTableKeyValueBulkSavePayload\":\"addonId,islandId(optional),values,tables\","
            + "\"addonStateTableKeyValueBulkLoadPayload\":\"addonId,islandId(optional),table\","
            + "\"addonStateTableKeyValueBulkSaveStorageMode\":\"table-prefix-flattened-key-value\","
            + "\"addonStateTableKeyValueBulkSaveRepositoryApi\":\"AddonStateRepository.tableKeyValueBulkSave,AddonStateRepository.tableKeyValueBulkSaveIsland,AddonStateRepository.tableBulk,AddonStateRepository.tableBulkIsland,AddonStateRepository.tableKeyValueBulkLoad,AddonStateRepository.tableKeyValueBulkLoadIsland,AddonStateBulkSaveRequest,AddonStateBulkLoadRequest,IslandAddonService.tableBulkState,IslandAddonService.tableBulkIslandState,IslandAddonService.tableKeyValueBulkSaveState,IslandAddonService.tableKeyValueBulkSaveIslandState,IslandAddonService.tableKeyValueBulkLoadState,IslandAddonService.tableKeyValueBulkLoadIslandState,IslandAddonService.tableLoadState,IslandAddonService.tableLoadIslandState,CoreApiClient.tableKeyValueBulkSaveAddonState,CoreApiClient.tableKeyValueBulkSaveAddonIslandState,CoreApiClient.tableBulkAddonState,CoreApiClient.tableBulkAddonIslandState,CoreApiClient.tableKeyValueBulkLoadAddonState,CoreApiClient.tableKeyValueBulkLoadAddonIslandState,CoreApiClient.tableLoadAddonState,CoreApiClient.tableLoadAddonIslandState\","
            + "\"addonStateTableKeyPrefix\":\"" + AddonStateRepository.TABLE_STATE_KEY_SHAPE + "\","
            + "\"addonStateMaxAddonIdLength\":" + AddonStateRepository.MAX_ADDON_ID_LENGTH + ","
            + "\"addonStateMaxKeyLength\":" + AddonStateRepository.MAX_KEY_LENGTH + ","
            + "\"addonStateMaxValueLength\":" + AddonStateRepository.MAX_VALUE_LENGTH + ","
            + "\"addonStateMaxKeysPerAddon\":" + AddonStateRepository.MAX_KEYS_PER_ADDON + ","
            + "\"addonStateTableKeyValueBulkSaveFallback\":\"local-cache-on-core-api-failure\","
            + "\"addonStateTableKeyValueBulkLoadFallback\":\"local-cache-or-empty-table-on-core-api-failure\","
            + "\"databasePoolSize\":" + config.databasePoolSize() + ","
            + "\"storageType\":\"" + escape(config.storageType()) + "\","
            + "\"storageSharedBackend\":" + ("S3".equalsIgnoreCase(config.storageType())) + ","
            + "\"storageMultiNodeSafe\":" + ("S3".equalsIgnoreCase(config.storageType())) + ","
            + "\"storageBackendSafety\":\"" + escape(storageBackendSafety(config)) + "\","
            + "\"storageLocalMultiNodePolicy\":\"LOCAL requires the same shared filesystem mount on every Island node; otherwise use S3 or MinIO\","
            + "\"storageS3MultiNodePolicy\":\"S3 or MinIO is the recommended shared object storage for 5/6 Island node pools\","
            + "\"storageLayout\":\"islands/{islandUuid}/manifest.json,latest,snapshots/{snapshotNo}/bundle.tar.zst,checksums.sha256,backups,recovery\","
            + "\"storageLatestPointer\":\"islands/{islandUuid}/latest\","
            + "\"storageSnapshotManifest\":\"islands/{islandUuid}/snapshots/{snapshotNo}/manifest.json\","
            + "\"storageBundleObject\":\"islands/{islandUuid}/snapshots/{snapshotNo}/bundle.tar.zst\","
            + "\"storageChecksumFile\":\"islands/{islandUuid}/snapshots/{snapshotNo}/checksums.sha256\","
            + "\"storageDeleteBackupPath\":\"islands/{islandUuid}/backups/delete-{snapshotNo}\","
            + "\"storageRecoveryPath\":\"islands/{islandUuid}/recovery\","
            + "\"storagePortabilityPolicy\":\"bundle-and-manifest-are-island-uuid-scoped-node-independent\","
            + "\"storageRestoreManifestRequired\":true,"
            + "\"storageRestoreChecksumPolicy\":\"verify-manifest-checksum-for-latest-snapshot-and-storage-path\","
            + "\"storageRestorePortableRequired\":true,"
            + "\"storageRestoreSupportedFormats\":\"checksum=SHA-256,compression=zstd\","
            + "\"failureHandlingNodeDownPolicy\":\"heartbeat-timeout>node-down>block-new-routes>mark-active-islands-recovery-required>fallback-players-lobby>snapshot-check>recover-on-other-node-or-quarantine\","
            + "\"failureHandlingRecoveryRequiredPolicy\":\"" + kr.lunaf.cloudislands.common.runtime.IslandRuntimeStatePolicy.RECOVERY_REQUIRED_POLICY + "\","
            + "\"failureHandlingCoreApiDownAllowed\":\"active-island-play,local-cache-protection,basic-local-teleport-fallback\","
            + "\"failureHandlingCoreApiDownLimited\":\"new-island-create,inactive-island-activation,island-route,member-change,flag-change\","
            + "\"failureHandlingCoreApiDownPlayerMessage\":\"현재 섬 서비스 일부 기능이 점검 중입니다.\","
            + "\"failureHandlingRedisDownAllowed\":\"database-direct-read-degraded-mode-without-data-loss\","
            + "\"failureHandlingRedisDownLimited\":\"cache-performance,event-propagation,job-queue-processing,heartbeat-freshness\","
            + "\"failureHandlingRedisDownRecommendation\":\"run-redis-high-availability-in-production\","
            + "\"failureHandlingObjectStorageDownAllowed\":\"already-active-island-play-continues-on-node-local-world\","
            + "\"failureHandlingObjectStorageDownLimited\":\"new-activation,island-save,snapshot,recovery\","
            + "\"failureHandlingObjectStorageRetryPolicy\":\"queue-failed-save-and-snapshot-operations-for-retry-while-primary-storage-is-unavailable\","
            + "\"failureHandlingQuarantinePolicy\":\"" + kr.lunaf.cloudislands.common.runtime.IslandRuntimeStatePolicy.QUARANTINE_POLICY + "\","
            + "\"failureHandlingRecoveryStateSummary\":\"" + kr.lunaf.cloudislands.common.runtime.IslandRuntimeStatePolicy.recoveryStateSummary() + "\","
            + "\"islandResourceModel\":\"global-resource\","
            + "\"islandPortableBundle\":true,"
            + "\"islandServerPinned\":false,"
            + "\"islandExecutionModel\":\"dynamic-island-node-pool\","
            + "\"islandNodeRole\":\"runtime-execution-node-only\","
            + "\"islandRoutingModel\":\"route-ticket-to-active-or-best-node\","
            + "\"createIslandRequestFlow\":\"velocity-command>core-createIsland>db-transaction-lock>node-allocator>create-island-job>agent-claim>template-restore>cell-allocate>runtime-active>route-ticket-ready>velocity-connect>paper-consume-ticket>spawn-teleport\","
            + "\"createIslandDuplicateGuard\":\"redis-player-creation-lock-plus-jdbc-player-profile-for-update\","
            + "\"createIslandJdbcLockPolicy\":\"upsert-player-profile-select-for-update-then-owner-island-select-for-update\","
            + "\"createIslandTransactionScope\":\"islands-row-owner-member-runtime-primary-island\","
            + "\"createIslandJobPolicy\":\"commit-before-job-publish-error-state-on-job-queue-failure\","
            + "\"createIslandInitialTicketState\":\"PREPARING-until-paper-agent-completes-create-job\","
            + "\"homeRequestFlow\":\"velocity-command>core-createHomeRoute>runtime-check>active-or-activate-best-node>route-ticket-ready>velocity-connect>paper-teleport\","
            + "\"visitRequestFlow\":\"velocity-command>target-island-lookup>public-ban-permission-check>active-or-activate>visitor-ticket>velocity-connect>visitor-spawn-teleport\","
            + "\"routePlayerLoadingUi\":\"actionbar-and-bossbar-progress-without-node-name\","
            + "\"routePlayerFailureCodes\":\"" + escape(kr.lunaf.cloudislands.common.routing.RouteFailurePolicy.PUBLIC_FAILURE_CODES) + "\","
            + "\"routePlayerFailureMessages\":\"" + escape(kr.lunaf.cloudislands.common.routing.RouteFailurePolicy.publicMessageSummary()) + "\","
            + "\"routePublicMessagePolicy\":\"" + escape(kr.lunaf.cloudislands.common.routing.RouteFailurePolicy.PUBLIC_MESSAGE_POLICY) + "\","
            + "\"routeDebugReasonPolicy\":\"" + escape(kr.lunaf.cloudislands.common.routing.RouteFailurePolicy.DEBUG_REASON_POLICY) + "\","
            + "\"routeVisitRejectionPolicy\":\"" + escape(kr.lunaf.cloudislands.common.routing.RouteFailurePolicy.VISIT_REJECTION_POLICY) + "\","
            + "\"routeTransferFailurePolicy\":\"clear-ticket-session-and-show-public-message-or-return-lobby\","
            + "\"softFullRoutingPolicy\":\"new-and-inactive-islands-use-ready-node-active-owner-member-reserve-current-node-visitors-queue-or-limit\","
            + "\"newActivationSoftFullPolicy\":\"" + escape(config.softFullPolicy()) + "\","
            + "\"newActivationSoftFullAvoided\":" + avoidsSoftFullNewActivations(config.softFullPolicy()) + ","
            + "\"newActivationHardFullPolicy\":\"" + escape(config.hardFullPolicy()) + "\","
            + "\"newActivationHardFullAllowed\":" + allowsHardFullNewActivations(config.hardFullPolicy()) + ","
            + "\"softFullNewActivationBehavior\":\"ready-node-preferred-soft-full-avoided-unless-policy-allows\","
            + "\"softFullExistingRouteBehavior\":\"active-owner-member-routes-can-use-current-soft-full-node-visitors-queue-or-limit\","
            + "\"moduleLayout\":\"" + escape(kr.lunaf.cloudislands.common.packaging.CloudIslandsModuleLayoutPolicy.requiredModuleSummary()) + "\","
            + "\"coreModuleLayout\":\"" + escape(kr.lunaf.cloudislands.common.packaging.CloudIslandsModuleLayoutPolicy.requiredModuleSummary()) + "\","
            + "\"addonModuleLayout\":\"" + escape(kr.lunaf.cloudislands.common.packaging.CloudIslandsModuleLayoutPolicy.optionalExtensionModuleSummary()) + "\","
            + "\"moduleResponsibilityLayout\":\"" + escape(kr.lunaf.cloudislands.common.packaging.CloudIslandsModuleLayoutPolicy.moduleResponsibilitySummary()) + "\","
            + "\"moduleRuntimeSurfaceLayout\":\"" + escape(kr.lunaf.cloudislands.common.packaging.CloudIslandsModuleLayoutPolicy.moduleRuntimeSurfaceSummary()) + "\","
            + "\"distributionLayout\":\"" + escape(kr.lunaf.cloudislands.common.packaging.CloudIslandsModuleLayoutPolicy.distributionArtifactSummary()) + "\","
            + "\"distributionTaskLayout\":\"" + escape(kr.lunaf.cloudislands.common.packaging.CloudIslandsModuleLayoutPolicy.distributionTaskSummary()) + "\","
            + "\"distributionMarkdownPackagingGuard\":\"verifyMarkdownDocsExcludedFromArtifacts-before-build-and-distBundle\","
            + "\"addonRegistryPolicy\":\"paper-addon-registers-core-stores-snapshot-only\","
            + "\"addonStateOwnershipPolicy\":\"core-persists-addon-key-value-state-without-addon-business-logic\","
            + "\"addonRemovalSafetyPolicy\":\"missing-addon-metadata-or-state-must-not-block-island-lifecycle\","
            + "\"addonStateFailureIsolationPolicy\":\"addon-state-storage-outages-return-503-without-affecting-island-lifecycle\","
            + "\"addonExtensionModel\":\"optional-external-plugin-using-cloudislands-api\","
            + "\"addonApiLookupPolicy\":\"cloudislands-provider-first-bukkit-servicesmanager-fallback\","
            + "\"addonApiContractVersion\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.CONTRACT_VERSION) + "\","
            + "\"addonApiContractCompatibility\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.metadataCompatibilityStatus(kr.lunaf.cloudislands.api.CloudIslandsApiContract.metadata())) + "\","
            + "\"addonApiContractCompatible\":" + kr.lunaf.cloudislands.api.CloudIslandsApiContract.compatibleMetadata(kr.lunaf.cloudislands.api.CloudIslandsApiContract.metadata()) + ","
            + "\"addonApiRequiredMetadataKeys\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.requiredMetadataKeysCsv()) + "\","
            + "\"addonApiReadPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.READ_POLICY) + "\","
            + "\"addonApiWriteAuthority\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.WRITE_AUTHORITY) + "\","
            + "\"addonApiSyncEventPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.SYNC_EVENT_POLICY) + "\","
            + "\"addonApiStoragePolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.ADDON_STORAGE_POLICY) + "\","
            + "\"addonApiRemovalPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.ADDON_REMOVAL_POLICY) + "\","
            + "\"addonApiReconnectPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.ADDON_RECONNECT_POLICY) + "\","
            + "\"addonJavaPluginApiPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.JAVA_PLUGIN_API_POLICY) + "\","
            + "\"addonInternalApiPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.INTERNAL_API_POLICY) + "\","
            + "\"addonEventApiPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.EVENT_API_POLICY) + "\","
            + "\"addonCoreAuthPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.CORE_AUTH_POLICY) + "\","
            + "\"addonAdminEndpointPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.ADMIN_ENDPOINT_POLICY) + "\","
            + "\"addonNetworkExposurePolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.NETWORK_EXPOSURE_POLICY) + "\","
            + "\"addonSecurityPostureSummary\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.SECURITY_POSTURE_SUMMARY) + "\","
            + "\"addonTopologyPrivacyPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.TOPOLOGY_PRIVACY_POLICY) + "\","
            + "\"addonConsistencyAuthorityPolicy\":\"" + escape(kr.lunaf.cloudislands.api.CloudIslandsApiContract.CONSISTENCY_AUTHORITY_POLICY) + "\","
            + "\"addonEventDeliveryPolicy\":\"core-global-events-to-paper-poller-to-cloudislands-addon-and-bukkit-events\","
            + "\"addonEventCoverage\":\"pre-create,create,pre-activate,activate,deactivate,migrate,delete,delete-backup-failed,restore,reset,recovery,runtime,pre-visit,visit,invite,member-join,member-left,member-role,member-change,ownership,rename,access,visitor-ban,visitor-kick,flag,permission-check,permission-change,role-catalog,biome,home,warp-create,warp-delete,warp-change,bank,chat,mission,blocks,block-value,level,worth,upgrade,limit,snapshot,template,addon-state,node,core-cache,core-reload,route-ticket\","
            + "\"addonEventBackfillPolicy\":\"paper-poller-uses-listEventsSince-with-sequence-gap-cache-invalidation\","
            + "\"satisPackaging\":\"official-external-addon\","
            + "\"satisCoreCoupling\":\"optional-addon-no-core-runtime-dependency\","
            + "\"satisAddonRemovalPolicy\":\"core-boots-and-islands-load-without-satis-jar\","
            + "\"satisDataRetentionPolicy\":\"addon-state-preserved-when-disabled-or-removed\","
            + "\"satisCoreBootRequiresAddon\":false,"
            + "\"satisCommandOwner\":\"optional-satis-paper-addon\","
            + "\"satisCrossNodeStatePolicy\":\"addon-state-must-be-stored-in-core-api-or-shared-database-not-paper-node-memory\","
            + "\"satisIslandMovePolicy\":\"when-island-moves-between-island-nodes-addon-remaps-state-to-current-active-world-and-cell\","
            + "\"satisFeatureDisablePolicy\":\"disabled-features-register-no-commands-gui-listeners-tasks-or-data-writes-and-preserve-existing-data\","
            + "\"satisSuperiorSkyblock2Policy\":\"no-runtime-dependency-use-cloudislands-public-api-and-addon-spi-only\","
            + "\"satisOperationScenarios\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.operationScenarioSummary()) + "\","
            + "\"satisRecommendedModeReasons\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.recommendedModeReasonSummary()) + "\","
            + "\"satisComponentBoundaries\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.componentBoundarySummary()) + "\","
            + "\"satisFeatureOffRuntimeBlocks\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.featureOffRuntimeBlockSummary()) + "\","
            + "\"satisStateStorageConfig\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.stateStorageConfigSummary()) + "\","
            + "\"satisPlayerExperiencePolicy\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.playerExperienceBoundarySummary()) + "\","
            + "\"satisOfficialFeaturePackPolicy\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.officialFeaturePackBoundarySummary()) + "\","
            + "\"satisNodeMoveRemapFlow\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.nodeMoveRemapStepSummary()) + "\","
            + "\"satisFailureRecoveryFlow\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.failureRecoveryStepSummary()) + "\","
            + "\"satisAddonReconnectFlow\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.addonReconnectStepSummary()) + "\","
            + "\"satisCompletionCriteria\":\"" + escape(kr.lunaf.cloudislands.common.feature.SatisIntegrationPolicy.completionCriteriaSummary()) + "\","
            + "\"satisRecoveryPolicy\":\"use-last-confirmed-addon-state-after-node-failure-and-avoid-duplicate-ticks-with-core-fencing\","
            + "\"satisAddonAbsentPolicy\":\"cloudislands-core-and-island-lifecycle-do-not-require-cloudislands-satis-installed\","
            + "\"satisDisabledRuntimePolicy\":\"disabled-satis-keeps-core-create-route-protect-save-restore-working-without-registering-satis-runtime-components\","
            + "\"satisReinstallPolicy\":\"preserved-addon-state-can-be-reconnected-when-satis-addon-is-installed-again\","
            + "\"satisStateAuthorityPolicy\":\"portable-addon-state-is-authoritative-in-core-api-bulk-table-key-value-or-configured-shared-database\","
            + "\"satisMultiNodeSafe\":true,"
            + "\"satisNodeCountPolicy\":\"island-node-count-does-not-change-satis-state-identity-or-storage-authority\","
            + "\"velocitySatisCommandPolicy\":\"no-direct-satis-command-handler-route-only\","
            + "\"paperSatisCommandPolicy\":\"addon-registers-own-commands-when-enabled\","
            + "\"finalRequestFlowKeys\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowKeys() + "\","
            + "\"finalRequestFlowIslandCreate\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("island-create") + "\","
            + "\"finalRequestFlowIslandHome\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("island-home") + "\","
            + "\"finalRequestFlowIslandVisit\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("island-visit") + "\","
            + "\"finalRequestFlowSoftFullRouting\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("soft-full-routing") + "\","
            + "\"islandPool\":\"" + escape(config.islandPool()) + "\","
            + "\"islandPoolNodeCount\":" + islandPoolNodeCount(config, nodes) + ","
            + "\"islandPoolRouteCandidateCount\":" + islandPoolRouteCandidateCount(config, nodes) + ","
            + "\"islandPoolRouteCandidateRecommendedMinimum\":" + islandPoolRouteCandidateRecommendedMinimum(config, nodes) + ","
            + "\"islandPoolRouteCandidateMinimumStatus\":\"" + escape(islandPoolRouteCandidateMinimumStatus(config, nodes)) + "\","
            + "\"islandPoolRouteCandidateNodeIds\":\"" + escape(islandPoolRouteCandidateNodeIds(config, nodes)) + "\","
            + "\"islandPoolScaleStatus\":\"" + escape(islandPoolScaleStatus(config, nodes)) + "\","
            + "\"islandPoolScaleModel\":\"dynamic-node-pool-by-node-id\","
            + "\"islandPoolElasticLimitPolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.SCALE_POLICY + "\","
            + "\"islandPoolScaleGuidance\":\"add-island-nodes-with-unique-node-id-unique-velocity-server-name-shared-storage\","
            + "\"islandPoolHorizontalScalePolicy\":\"no-hardcoded-island-node-count-unique-node-id-unique-velocity-server-name-shared-storage-required\","
            + "\"islandPoolFiveSixNodePolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.FIVE_SIX_NODE_POLICY + "\","
            + "\"islandPoolScaleReadinessPolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.SCALE_READINESS_POLICY + "\","
            + "\"islandPoolScaleReadinessSummary\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.scaleReadinessSummary() + "\","
            + "\"islandPoolFiveSixNodeHealthy\":" + islandPoolFiveSixNodeHealthy(config, nodes) + ","
            + "\"islandPoolFiveSixNodeStatus\":\"" + escape(islandPoolFiveSixNodeStatus(config, nodes)) + "\","
            + "\"islandPlacementPolicy\":\"deterministic-uuid-shard-cell\","
            + "\"islandPlacementShardCount\":" + IslandPlacement.SHARD_COUNT + ","
            + "\"islandPlacementCellsPerAxis\":" + IslandPlacement.CELLS_PER_AXIS + ","
            + "\"islandPlacementCollisionPolicy\":\"uuid-derived-cell-with-runtime-occupied-cell-probing-db-unique-guard-fencing-and-node-lease\","
            + "\"islandNodeHardRules\":\"pool-match,ready-or-soft-full,fresh-heartbeat,hard-cap-open,activation-queue-open,object-storage-available,template-supported,min-node-version,not-default-identity\","
            + "\"islandNodeScoreWeights\":\"players=0.25,activeIslands=0.15,mspt=0.25,activationQueue=0.15,chunkLoad=0.10,memory=0.05,recentFailure=0.05\","
            + "\"islandNodeSchemaColumns\":\"id,pool,velocity_server_name,node_version,state,soft_player_cap,hard_player_cap,reserved_slots,max_active_islands,players,active_islands,mspt,heap_used_mb,heap_max_mb,activation_queue,max_activation_queue,chunk_load_pressure,recent_failure_penalty,object_storage_available,supported_templates,last_heartbeat\","
            + "\"islandNodeExistingRoutePolicy\":\"active-island-stays-on-current-node-unless-node-missing-stale-down-or-admin-migration\","
            + "\"islandNodeVisitorSoftFullPolicy\":\"visitors-queue-or-retry-on-soft-full-active-node-members-use-reserved-slots\","
            + "\"redisRolePolicy\":\"" + escape(kr.lunaf.cloudislands.common.cache.CacheStrategyPolicy.REDIS_ROLE) + "\","
            + "\"redisKeyScope\":\"" + escape(kr.lunaf.cloudislands.common.cache.CacheStrategyPolicy.REDIS_KEY_SCOPE) + "\","
            + "\"redisTtlSummary\":\"" + escape(kr.lunaf.cloudislands.common.cache.CacheStrategyPolicy.REDIS_TTL_SUMMARY) + "\","
            + "\"redisConsistencyGuard\":\"" + escape(kr.lunaf.cloudislands.common.cache.CacheStrategyPolicy.CONSISTENCY_GUARD) + "\","
            + "\"fencingWritePolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.FencingToken.WRITE_POLICY) + "\","
            + "\"fencingRedisLockPolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.FencingToken.REDIS_LOCK_POLICY) + "\","
            + "\"nodeProtocolMinSupported\":" + MIN_NODE_PROTOCOL_VERSION + ","
            + "\"nodeProtocolCurrent\":" + MAX_NODE_PROTOCOL_VERSION + ","
            + "\"nodeProtocolNegotiationPolicy\":\"" + kr.lunaf.cloudislands.protocol.ProtocolVersion.NEGOTIATION_POLICY + "\","
            + "\"nodeProtocolHeartbeatField\":\"" + kr.lunaf.cloudislands.protocol.ProtocolVersion.HEARTBEAT_FIELD + "\","
            + "\"routingFailureDetailKeys\":\"pool,nodeCount,readyOrSoftFullNodeCount,storageReadyNodeCount,primaryStorageHealthyNodeCount,storageSaveRetryBacklogNodeCount,storageSaveRetryBacklogTotal,hardCapOpenNodeCount,activeIslandOpenNodeCount,queueOpenNodeCount,defaultIdentityRiskNodeCount,duplicateVelocityServerNameNodeCount,routeCandidateEstimateNodeCount,routeCandidateRecommendedMinimum,routeCandidateShortfall,routeCandidateEstimatePolicy,elasticLimitPolicy,blockReason,physicalNodeNamesExposed\","
            + "\"islandPoolMultiNodeReady\":" + islandPoolMultiNodeReady(config, nodes) + ","
            + "\"islandPoolDegraded\":" + islandPoolDegraded(config, nodes) + ","
            + "\"islandPoolRouteCandidateShortfall\":" + islandPoolRouteCandidateShortfall(config, nodes) + ","
            + "\"islandPoolRouteCandidateBlockSummary\":\"" + escape(islandPoolRouteCandidateBlockSummary(config, nodes)) + "\","
            + "\"islandPoolBlockedNodeIds\":\"" + escape(islandPoolBlockedNodeIds(config, nodes)) + "\","
            + "\"islandPoolDuplicateVelocityServerNameNodeCount\":" + islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) + ","
            + "\"islandPoolDefaultNodeIdentityRiskCount\":" + islandPoolDefaultNodeIdentityRiskCount(config, nodes) + ","
            + "\"softFullPolicy\":\"" + escape(config.softFullPolicy()) + "\","
            + "\"hardFullPolicy\":\"" + escape(config.hardFullPolicy()) + "\","
            + "\"softFullNewActivationAvoided\":" + avoidsSoftFullNewActivations(config.softFullPolicy()) + ","
            + "\"hardFullNewActivationAllowed\":" + allowsHardFullNewActivations(config.hardFullPolicy()) + ","
            + "\"nodeAllocatorNewActivationFallback\":\"READY nodes first, SOFT_FULL skipped by default so Island-2/next ready node receives new work when Island-1 is soft-full\","
            + "\"migrationPolicy\":\"" + escape(config.migrationPolicy()) + "\","
            + "\"superiorSkyblock2MigrationEnabled\":" + config.superiorSkyblock2MigrationEnabled() + ","
            + "\"superiorSkyblock2MigrationInputOnly\":true,"
            + "\"superiorSkyblock2RuntimeDependency\":false,"
            + "\"superiorSkyblock2RuntimePolicy\":\"migration-input-only-no-runtime-hooks\","
            + "\"superiorSkyblock2ReplacementFeatureCount\":" + kr.lunaf.cloudislands.common.feature.SuperiorSkyblockReplacementFeaturePolicy.requiredFeatureCount() + ","
            + "\"superiorSkyblock2ReplacementFeatures\":\"" + escape(kr.lunaf.cloudislands.common.feature.SuperiorSkyblockReplacementFeaturePolicy.requiredFeatureKeys()) + "\","
            + "\"superiorSkyblock2MigrationTargets\":\"" + escape(kr.lunaf.cloudislands.common.feature.SuperiorSkyblockReplacementFeaturePolicy.migrationTargetSummary()) + "\","
            + "\"superiorSkyblock2MigrationFlow\":\"" + escape(kr.lunaf.cloudislands.common.feature.SuperiorSkyblockReplacementFeaturePolicy.migrationStepSummary()) + "\","
            + "\"superiorSkyblock2MigrationCommands\":\"" + escape(kr.lunaf.cloudislands.common.feature.SuperiorSkyblockReplacementFeaturePolicy.migrationCommandSummary()) + "\","
            + "\"superiorSkyblock2MigrationEndpoints\":\"scan,status,dryrun,extract,import,verify,rollback\","
            + "\"superiorSkyblock2MigrationStatusEndpoint\":\"/v1/admin/migrations/superiorskyblock2/status\","
            + "\"superiorSkyblock2MigrationRollbackPolicy\":\"last-successful-import-only-consume-plan-after-success\","
            + "\"routeTicketTtlSeconds\":" + config.routeTicketTtl().toSeconds() + ","
            + "\"routePreparingTicketTtlSeconds\":" + config.routePreparingTicketTtl().toSeconds() + ","
            + "\"routeTicketOneTimeConsume\":" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.ONE_TIME_CONSUME + ","
            + "\"routeTicketNonceRequired\":" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.NONCE_REQUIRED + ","
            + "\"routeTicketTargetNodeRequired\":" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.TARGET_NODE_REQUIRED + ","
            + "\"routeTicketOneTimePolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.ONE_TIME_POLICY + "\","
            + "\"routeTicketNoncePolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.NONCE_POLICY + "\","
            + "\"routeTicketArrivalConsumePolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.ARRIVAL_CONSUME_POLICY + "\","
            + "\"routeTicketDirectAccessPolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.DIRECT_ACCESS_POLICY + "\","
            + "\"routeTicketReplayPolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.REPLAY_POLICY + "\","
            + "\"routeTicketConsumeEndpoint\":\"/v1/routes/consume\","
            + "\"routeSessionConsumeEndpoint\":\"/v1/routes/session/consume\","
            + "\"routeSessionPreLoginGuard\":\"paper-async-prelogin-find-session-and-playerjoin-consume-session\","
            + "\"heartbeatTimeoutSeconds\":" + config.heartbeatTimeout().toSeconds() + ","
            + "\"leaseDurationSeconds\":" + config.leaseDuration().toSeconds() + ","
            + "\"cacheTierPolicy\":\"L1=paper-velocity-local-memory,L2=redis,L3=postgresql-mysql-mariadb-core-jdbc\","
            + "\"cacheAuthoritativeStorePolicy\":\"database-or-core-api-is-authority-redis-and-local-memory-are-discardable\","
            + "\"redisCacheTtlPolicy\":\"route-ticket=30s,player-island=300s,island-summary=60s,permissions=30s,locks=10-60s\","
            + "\"redisCacheTtlMillis\":\"serverHeartbeat=" + kr.lunaf.cloudislands.common.cache.RedisTtls.SERVER_HEARTBEAT_MILLIS
                + ",routeTicket=" + kr.lunaf.cloudislands.common.cache.RedisTtls.ROUTE_TICKET_MILLIS
                + ",playerIsland=" + kr.lunaf.cloudislands.common.cache.RedisTtls.PLAYER_ISLAND_MILLIS
                + ",playerProfile=" + kr.lunaf.cloudislands.common.cache.RedisTtls.PLAYER_PROFILE_MILLIS
                + ",islandSummary=" + kr.lunaf.cloudislands.common.cache.RedisTtls.ISLAND_SUMMARY_MILLIS
                + ",islandMetadata=" + kr.lunaf.cloudislands.common.cache.RedisTtls.ISLAND_METADATA_MILLIS
                + ",islandRuntime=" + kr.lunaf.cloudislands.common.cache.RedisTtls.ISLAND_RUNTIME_MILLIS
                + ",islandPermissions=" + kr.lunaf.cloudislands.common.cache.RedisTtls.ISLAND_PERMISSIONS_MILLIS
                + ",lockMin=" + kr.lunaf.cloudislands.common.cache.RedisTtls.LOCK_MIN_MILLIS
                + ",lockMax=" + kr.lunaf.cloudislands.common.cache.RedisTtls.LOCK_MAX_MILLIS + "\","
            + "\"redisKeyPolicy\":\"ci:server:{nodeId}:*,ci:player:{uuid}:*,ci:island:{islandId}:*,ci:lock:*,ci:stream:*\","
            + "\"cacheInvalidationSource\":\"core-write-transaction-then-global-event\","
            + "\"cacheInvalidationEvents\":\"IslandMemberChanged,IslandFlagChanged,IslandPermissionChanged,IslandWarpChanged,IslandRuntimeChanged,IslandDeleted\","
            + "\"cacheInvalidationEventFields\":\"cacheTargets,cacheKeys,cachePolicy\","
            + "\"cacheInvalidationFanout\":\"core-event-stream>paper-event-poller>velocity-route-cache-if-affected>local-cache-delete\","
            + "\"cacheInvalidationTargets\":\"player_uuid->island_id,island_id->summary,island_id->runtime,island_id->members,island_id->permissions,island_id->flags,island_id->warps,node_id->heartbeat\","
            + "\"cacheInvalidationRedisPatterns\":\"ci:player:*:island,ci:player:*:profile,ci:player-name:*:profile,ci:addon:*:state,ci:island:*:summary,ci:island:*:runtime,ci:island:*:members,ci:island:*:permissions,ci:island:*:flags,ci:island:*:warps,ci:island:*:addon-state,ci:island:*:route-tickets,ci:rankings:*\","
            + "\"addonStateGlobalCacheKey\":\"ci:addon:{addonId}:state\","
            + "\"addonStateIslandCacheKey\":\"ci:island:{islandId}:addon-state\","
            + "\"addonStateCacheInvalidationApi\":\"CacheInvalidationPlan.redisKeysFor(eventType,islandId,addonId)\","
            + "\"cacheRedisDownPolicy\":\"degraded-db-direct-read-no-data-loss-event-propagation-delayed-jobs-and-heartbeats-limited\","
            + "\"cacheCoreApiDownPolicy\":\"active-island-local-protection-continues-new-route-and-writes-limited\","
            + "\"redisStreamPolicy\":\"jobs,events,audit-append-only-observability\","
            + "\"globalEventTypes\":\"IslandCreated,IslandDeleted,IslandActivated,IslandDeactivated,IslandMigrated,IslandMemberChanged,IslandFlagChanged,IslandLevelUpdated,IslandSnapshotCreated,NodeStateChanged,RouteTicketCreated,RouteSessionPublished,RouteTicketConsumed,RouteTicketFailed,RouteTicketCleared\","
            + "\"globalEventTypeKeys\":\"" + escape(globalEventTypeKeys()) + "\","
            + "\"globalEventRecoveryKeys\":\"ISLAND_RUNTIME_CHANGED,ISLAND_RECOVERY_REQUIRED,ISLAND_REPAIRED,NODE_STATE_CHANGED\","
            + "\"globalEventAddonKeys\":\"ADDON_STATE_CHANGED\","
            + "\"routeMetricsTargetServerName\":true,"
            + "\"routeMetricsTargetServerNameEvents\":\"RouteTicketCreated,RouteSessionPublished,RouteTicketConsumed,RouteTicketFailed\","
            + "\"routeMetricsRequestedNode\":true,"
            + "\"routeMetricsRequestedNodeEvents\":\"RouteTicketFailed\","
            + "\"distributedLockPolicy\":\"redis-fast-lock-plus-postgresql-row-lock\","
            + "\"fencingTokenPolicy\":\"island_runtime.fencing_token-rejects-stale-node-writes\","
            + "\"jobCompletionContract\":\"" + kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPolicy.CONTRACT + "\","
            + "\"jobCompletionFencingTokenKey\":\"" + kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPolicy.FENCING_TOKEN_KEY + "\","
            + "\"jobCompletionStaleReasons\":\"" + kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPolicy.STALE_FENCING_TOKEN + "," + kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPolicy.STALE_NODE_COMPLETION + "," + kr.lunaf.cloudislands.protocol.job.IslandJobCompletionPolicy.RUNTIME_NOT_ACCEPTING_COMPLETION + "\","
            + "\"staleWritePolicy\":\"current-fencing-token-required-before-snapshot-or-runtime-write\","
            + "\"snapshotKeepLatest\":" + config.snapshotKeepLatest() + ","
            + "\"snapshotKeepHourly\":" + snapshotPolicy.keepHourly() + ","
            + "\"snapshotKeepDaily\":" + snapshotPolicy.keepDaily() + ","
            + "\"snapshotKeepWeekly\":" + snapshotPolicy.keepWeekly() + ","
            + "\"snapshotKeepManual\":" + snapshotPolicy.keepManual() + ","
            + "\"snapshotCompress\":" + snapshotPolicy.compress() + ","
            + "\"snapshotChecksumAlgorithm\":\"" + escape(snapshotPolicy.checksumAlgorithm()) + "\","
            + "\"snapshotRetentionMode\":\"hourly-daily-weekly-manual\","
            + "\"snapshotAutomaticTriggerPolicy\":\"" + escape(kr.lunaf.cloudislands.storage.snapshot.SnapshotOperationPolicy.AUTOMATIC_TRIGGER_POLICY) + "\","
            + "\"snapshotRequiredTriggerReasons\":\"" + escape(kr.lunaf.cloudislands.storage.snapshot.SnapshotOperationPolicy.automaticTriggerReasonSummary()) + "\","
            + "\"snapshotManualTriggerReason\":\"MANUAL\","
            + "\"snapshotPreRestoreReason\":\"BEFORE_RESTORE\","
            + "\"snapshotRestorePipeline\":\"" + escape(kr.lunaf.cloudislands.storage.snapshot.SnapshotOperationPolicy.RESTORE_PIPELINE_POLICY) + "\","
            + "\"snapshotRestoringLockState\":\"" + escape(kr.lunaf.cloudislands.storage.snapshot.SnapshotOperationPolicy.RESTORING_LOCK_STATE) + "\","
            + "\"snapshotRuntimeResetPolicy\":\"" + escape(kr.lunaf.cloudislands.storage.snapshot.SnapshotOperationPolicy.RUNTIME_RESET_POLICY) + "\","
            + "\"snapshotRollbackSteps\":\"" + escape(kr.lunaf.cloudislands.storage.snapshot.SnapshotOperationPolicy.rollbackStepSummary()) + "\","
            + "\"coreApiAuthPolicy\":\"token-or-mtls-required\","
            + "\"coreApiTokenConfigured\":" + (config.coreToken() != null && !config.coreToken().isBlank()) + ","
            + "\"coreApiMtlsRequired\":" + config.requireMtls() + ","
            + "\"coreApiAuthConfigured\":" + coreApiAuthConfigured(config) + ","
            + "\"coreApiAuthLockoutRisk\":" + !coreApiAuthConfigured(config) + ","
            + "\"adminPermissionPolicy\":\"separate-admin-permission-per-endpoint\","
            + "\"auditLogPolicy\":\"record-admin-player-and-system-actions\","
            + "\"controlChannelPolicy\":\"http-or-grpc-plus-redis-streams\","
            + "\"pluginMessagingPolicy\":\"not-used-for-critical-island-control\","
            + "\"requiredSecurityControls\":\"" + escape(String.join(",", kr.lunaf.cloudislands.common.security.BackendAccessPolicy.requiredSecurityControls())) + "\","
            + "\"velocityForwardingSecurityPolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.BackendAccessPolicy.MODERN_FORWARDING_POLICY) + "\","
            + "\"paperDirectAccessSecurityPolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.BackendAccessPolicy.PAPER_DIRECT_ACCESS_POLICY) + "\","
            + "\"coreApiSecurityPolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.BackendAccessPolicy.CORE_API_AUTH_POLICY) + "\","
            + "\"infrastructureExposureSecurityPolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.BackendAccessPolicy.INFRASTRUCTURE_EXPOSURE_POLICY) + "\","
            + "\"pluginMessagingSecurityPolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.BackendAccessPolicy.PLUGIN_MESSAGING_POLICY) + "\","
            + "\"pluginMessagingAllowedUse\":\"emergency-proxy-assist-only\","
            + "\"pluginMessagingForbiddenUse\":\"island-create-delete-save-migrate-routing-authority\","
            + "\"paperAgentRolePolicy\":\"LOBBY handles menus-and-query-flow,ISLAND_NODE handles-world-runtime-protection-save-teleport\","
            + "\"paperLobbyRolePolicy\":\"no-island-world-execution-no-runtime-lease-no-shard-cell-ownership\","
            + "\"paperIslandNodeRolePolicy\":\"activate-save-snapshot-shard-cell-preload-protection-route-ticket-consume\","
            + "\"velocityCommandOwnershipPolicy\":\"/is-and-/섬-route-entry-owned-by-velocity-before-paper-fallback\","
            + "\"paperCommandFallbackPolicy\":\"paper-agent-keeps-local-gui-protection-and-bungee-connect-fallback-only\","
            + "\"createIslandFlowPolicy\":\"velocity-command-core-transaction-player-lock-node-allocator-create-job-agent-claim-template-restore-cell-runtime-active-route-ticket-ready-connect-teleport\","
            + "\"homeRouteFlowPolicy\":\"velocity-command-core-runtime-check-active-node-or-best-node-activation-route-ticket-ready-connect-paper-teleport\","
            + "\"visitRouteFlowPolicy\":\"target-island-lookup-public-ban-permission-check-active-or-activate-visitor-ticket-connect-visitor-spawn\","
            + "\"softFullRoutingPolicy\":\"new-and-inactive-islands-avoid-soft-full-nodes-active-members-may-use-reserved-slots-visitors-queue-or-deny\","
            + "\"newActivationSoftFullPolicy\":\"" + escape(config.softFullPolicy()) + "\","
            + "\"newActivationSoftFullAvoided\":" + avoidsSoftFullNewActivations(config.softFullPolicy()) + ","
            + "\"newActivationHardFullPolicy\":\"" + escape(config.hardFullPolicy()) + "\","
            + "\"newActivationHardFullAllowed\":" + allowsHardFullNewActivations(config.hardFullPolicy()) + ","
            + "\"portableIslandPolicy\":\"islands-are-global-portable-bundles-not-fixed-to-a-server-node\","
            + "\"portableIslandResourceModel\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.RESOURCE_MODEL + "\","
            + "\"portableIslandBundleModel\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.BUNDLE_MODEL + "\","
            + "\"portableIslandNodeModel\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.NODE_MODEL + "\","
            + "\"portableIslandPlayerVisibilityPolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.PLAYER_VISIBILITY_POLICY + "\","
            + "\"portableIslandScalePolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.SCALE_POLICY + "\","
            + "\"portableIslandFiveSixNodePolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.FIVE_SIX_NODE_POLICY + "\","
            + "\"portableIslandScaleReadinessPolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.SCALE_READINESS_POLICY + "\","
            + "\"portableIslandSoftFullPolicy\":\"" + kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.SOFT_FULL_POLICY + "\","
            + "\"portableIslandDesignEffects\":\"" + escape(kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.designEffectSummary()) + "\","
            + "\"portableIslandDefinition\":\"" + escape(kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy.ONE_LINE_DEFINITION) + "\","
            + "\"nodeScaleOutPolicy\":\"add-island-nodes-with-unique-node-id-and-velocity-server-name-then-allocator-can-route-new-or-inactive-islands-there\","
            + "\"rankingUpdatePolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService.UPDATE_POLICY) + "\","
            + "\"blockValuePolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService.BLOCK_VALUE_POLICY) + "\","
            + "\"rankingFullScanPolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService.FULL_SCAN_POLICY) + "\","
            + "\"rankingCachePolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService.CACHE_POLICY) + "\","
            + "\"rankingDirtyBatchLimit\":" + kr.lunaf.cloudislands.coreservice.ranking.DirtyRankingRecalculationTask.BATCH_LIMIT + ","
            + "\"upgradePolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy.CONFIG_DRIVEN_POLICY) + "\","
            + "\"upgradeTypePolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy.SUPPORTED_TYPE_POLICY) + "\","
            + "\"upgradeEffectPolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy.EFFECT_APPLICATION_POLICY) + "\","
            + "\"upgradePurchasePolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeService.PURCHASE_POLICY) + "\","
            + "\"upgradeEconomyPolicy\":\"" + escape(kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeService.ECONOMY_ABSTRACTION_POLICY) + "\","
            + "\"paperUpgradeEconomyPolicy\":\"paper-agent-uses-economy-bridge-for-player-facing-upgrade-purchase\","
            + "\"generatorPolicy\":\"paper-agent-local-generator-rules-by-island-upgrade-level\","
            + "\"superiorSkyblock2ReplacementPolicy\":\"built-in-cloudislands-modules-replace-superiorskyblock2-runtime-dependency\","
            + "\"superiorSkyblock2ReplacementFeatures\":\"create,template-select,delete,reset,home,multiple-homes,visit,random-visit,public-private,visitor-ban,visitor-kick,member-invite,member-kick,roles-permissions,custom-roles,flags,warps,ranking,level,worth,block-values,upgrades,size-expansion,border,biome,bank,island-chat,team-chat,missions,challenges,generator-upgrades,spawner-limits,hopper-limits,entity-limits,redstone-limits,island-logs,admin-recovery,snapshots-rollback,migration,server-drain,distributed-api,web-api,external-java-api\","
            + "\"superiorSkyblock2ReplacementFeatureGate\":\"core-config-and-addon-feature-flags-can-disable-optional-satis-compatible-features\","
            + "\"infrastructureExposurePolicy\":\"redis-postgresql-object-storage-private-only\","
            + "\"publicBindRiskPolicy\":\"require-ip-allowlist-or-private-bind\","
            + "\"adminApiEnabled\":" + config.adminApiEnabled() + ","
            + "\"requireMtls\":" + config.requireMtls() + ","
            + "\"ipAllowlistEnabled\":" + (config.ipAllowlist() != null && !config.ipAllowlist().isBlank()) + ","
            + "\"requiredSecurityControls\":\"" + escape(String.join(",", kr.lunaf.cloudislands.common.security.BackendAccessPolicy.requiredSecurityControls())) + "\","
            + "\"pluginMessagingSecurityPolicy\":\"" + escape(kr.lunaf.cloudislands.common.security.BackendAccessPolicy.PLUGIN_MESSAGING_POLICY) + "\","
            + "\"rateLimitRequests\":" + config.rateLimitRequests() + ","
            + "\"rateLimitWindowSeconds\":" + config.rateLimitWindow().toSeconds()
            + "}";
    }

    private static long islandPoolNodeCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        return nodes.snapshot().stream()
            .filter(node -> inIslandPool(config, node))
            .count();
    }

    private static long islandPoolRouteCandidateCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        java.util.List<NodeLoad> snapshot = nodes.snapshot();
        java.util.Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        java.time.Instant now = java.time.Instant.now();
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .filter(node -> islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts).isBlank())
            .count();
    }

    private static String islandPoolRouteCandidateNodeIds(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return "";
        }
        java.util.List<NodeLoad> snapshot = nodes.snapshot();
        java.util.Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        java.time.Instant now = java.time.Instant.now();
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .filter(node -> islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts).isBlank())
            .map(NodeLoad::nodeId)
            .filter(id -> id != null && !id.isBlank())
            .sorted()
            .collect(java.util.stream.Collectors.joining(","));
    }

    private static String islandPoolScaleStatus(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        long candidates = islandPoolRouteCandidateCount(config, nodes);
        if (nodeCount <= 0L) {
            return "NO_POOL_NODES";
        }
        if (nodeCount == 1L) {
            return candidates > 0L ? "SINGLE_NODE_READY" : "SINGLE_NODE_BLOCKED";
        }
        if (candidates <= 0L) {
            return "MULTI_NODE_BLOCKED";
        }
        if (candidates == 1L) {
            return "MULTI_NODE_DEGRADED";
        }
        return "MULTI_NODE_READY";
    }

    private static boolean islandPoolDegraded(CoreServiceConfig config, NodeRegistry nodes) {
        return islandPoolNodeCount(config, nodes) > 1L && islandPoolRouteCandidateCount(config, nodes) == 1L;
    }

    private static boolean islandPoolMultiNodeReady(CoreServiceConfig config, NodeRegistry nodes) {
        return islandPoolNodeCount(config, nodes) > 1L
            && islandPoolRouteCandidateCount(config, nodes) > 1L
            && islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) == 0L
            && islandPoolDefaultNodeIdentityRiskCount(config, nodes) == 0L;
    }

    private static long islandPoolRouteCandidateRecommendedMinimum(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        if (nodeCount <= 0L) {
            return 0L;
        }
        if (nodeCount == 1L) {
            return 1L;
        }
        if (nodeCount < 5L) {
            return 2L;
        }
        if (nodeCount <= 6L) {
            return nodeCount;
        }
        return Math.min(nodeCount, 6L);
    }

    private static String islandPoolRouteCandidateMinimumStatus(CoreServiceConfig config, NodeRegistry nodes) {
        long minimum = islandPoolRouteCandidateRecommendedMinimum(config, nodes);
        long candidates = islandPoolRouteCandidateCount(config, nodes);
        if (minimum <= 0L) {
            return "NO_POOL_NODES";
        }
        if (candidates >= minimum) {
            return "OK candidates=" + candidates + "/" + minimum;
        }
        return "SHORTFALL candidates=" + candidates + "/" + minimum
            + " blocked=" + islandPoolBlockedNodeIds(config, nodes);
    }

    private static boolean islandPoolFiveSixNodeHealthy(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        if (nodeCount < 5L || nodeCount > 6L) {
            return false;
        }
        return islandPoolRouteCandidateCount(config, nodes) >= islandPoolRouteCandidateRecommendedMinimum(config, nodes)
            && islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) == 0L
            && islandPoolDefaultNodeIdentityRiskCount(config, nodes) == 0L;
    }

    private static String islandPoolFiveSixNodeStatus(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        long candidates = islandPoolRouteCandidateCount(config, nodes);
        if (nodeCount < 5L) {
            return "NOT_5_6_NODE_POOL";
        }
        if ((nodeCount == 5L || nodeCount == 6L)
                && candidates == nodeCount
                && islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) == 0L
                && islandPoolDefaultNodeIdentityRiskCount(config, nodes) == 0L) {
            return "READY";
        }
        if (nodeCount > 6L && candidates == nodeCount) {
            return "READY_ABOVE_6_NODES";
        }
        return "DEGRADED candidates=" + candidates + "/" + nodeCount
            + " blocked=" + islandPoolBlockedNodeIds(config, nodes);
    }

    private static long islandPoolRouteCandidateShortfall(CoreServiceConfig config, NodeRegistry nodes) {
        return Math.max(0L, islandPoolNodeCount(config, nodes) - islandPoolRouteCandidateCount(config, nodes));
    }

    private static String islandPoolRouteCandidateBlockSummary(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return "none";
        }
        java.util.List<NodeLoad> snapshot = nodes.snapshot();
        java.util.Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        java.time.Instant now = java.time.Instant.now();
        java.util.Map<String, Long> blocked = new java.util.TreeMap<>();
        snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(node -> islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts))
            .filter(reason -> reason != null && !reason.isBlank())
            .forEach(reason -> blocked.merge(reason, 1L, Long::sum));
        if (blocked.isEmpty()) {
            return "none";
        }
        return blocked.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(java.util.stream.Collectors.joining(","));
    }

    private static String globalEventTypeKeys() {
        return java.util.Arrays.stream(CloudIslandEventType.values())
            .map(Enum::name)
            .collect(java.util.stream.Collectors.joining(","));
    }

    private static String islandPoolBlockedNodeIds(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return "";
        }
        java.util.List<NodeLoad> snapshot = nodes.snapshot();
        java.util.Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        java.time.Instant now = java.time.Instant.now();
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(node -> {
                String reason = islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts);
                if (reason == null || reason.isBlank()) {
                    return "";
                }
                String id = node.nodeId() == null || node.nodeId().isBlank() ? "unknown" : node.nodeId();
                return id + ":" + reason;
            })
            .filter(value -> !value.isBlank())
            .sorted()
            .collect(java.util.stream.Collectors.joining(","));
    }

    private static long islandPoolDuplicateVelocityServerNameNodeCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        java.util.List<NodeLoad> snapshot = nodes.snapshot();
        java.util.Map<String, Long> serverCounts = islandPoolVelocityServerCounts(config, snapshot);
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(CloudIslandsCoreApplication::velocityServerNameKey)
            .filter(server -> !server.isBlank() && serverCounts.getOrDefault(server, 0L) > 1L)
            .count();
    }

    private static long islandPoolDefaultNodeIdentityRiskCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        return nodes.snapshot().stream()
            .filter(node -> inIslandPool(config, node))
            .filter(NodeLoad::defaultNodeIdentityRisk)
            .count();
    }

    private static boolean inIslandPool(CoreServiceConfig config, NodeLoad node) {
        return node != null && node.inPool(islandPool(config));
    }

    private static String islandPool(CoreServiceConfig config) {
        return config == null || config.islandPool() == null || config.islandPool().isBlank() ? "island" : config.islandPool();
    }

    private static java.util.Map<String, Long> islandPoolVelocityServerCounts(CoreServiceConfig config, java.util.List<NodeLoad> snapshot) {
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(CloudIslandsCoreApplication::velocityServerNameKey)
            .filter(server -> !server.isBlank())
            .collect(java.util.stream.Collectors.groupingBy(server -> server, java.util.stream.Collectors.counting()));
    }

    private static String islandPoolRouteCandidateBlockReason(CoreServiceConfig config, NodeLoad node, java.time.Instant now, java.util.Map<String, Long> velocityServerCounts) {
        String blockReason = node.allocationBlockReason(now, config.heartbeatTimeout());
        if (!blockReason.isBlank()) {
            return blockReason;
        }
        String server = velocityServerNameKey(node);
        if (!server.isBlank() && velocityServerCounts.getOrDefault(server, 0L) > 1L) {
            return "DUPLICATE_VELOCITY_SERVER_NAME";
        }
        return "";
    }

    private static String velocityServerNameKey(NodeLoad node) {
        return node == null || node.velocityServerName() == null ? "" : node.velocityServerName().trim().toLowerCase(Locale.ROOT);
    }

    public void start() {
        server.start();
        nodeFailureMonitor.start();
        routeTicketExpiryMonitor.start();
        jobRecoveryMonitor.start();
        rankingRecalculationTask.start();
    }

    public static void main(String[] args) throws IOException {
        CoreServiceConfig config = CoreServiceConfig.fromEnvironment();
        if (args.length > 0) {
            config = config.withPort(Integer.parseInt(args[0]));
        }
        new CloudIslandsCoreApplication(config).start();
    }

    private void route(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            String key = exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allow(key)) {
                auditSecurityReject("RATE_LIMITED", path, exchange);
                write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
                return;
            }
            if (!healthProbePath(path) && !coreApiAuthenticated(exchange)) {
                String rejectCode = coreApiAuthRejectCode();
                auditSecurityReject(rejectCode, path, exchange);
                write(exchange, 401, ApiResponses.error(rejectCode, coreApiAuthRejectMessage(rejectCode)));
                return;
            }
            if (!ipAllowlist.allowed(exchange)) {
                auditSecurityReject("IP_NOT_ALLOWED", path, exchange);
                write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
                return;
            }
            if (!adminGuard.allowed(path, exchange)) {
                auditSecurityReject("ADMIN_PERMISSION_DENIED", path, exchange);
                write(exchange, 403, ApiResponses.error("ADMIN_PERMISSION_DENIED", "Admin permission is required"));
                return;
            }
            try {
                handler.handle(exchange);
            } catch (IllegalStateException exception) {
                if (path.startsWith("/v1/addons/")) {
                    LOGGER.warning("CloudIslands addon state endpoint failed without affecting island lifecycle: " + exception.getMessage());
                    write(exchange, 503, ApiResponses.error("ADDON_STATE_UNAVAILABLE", "Addon state storage is temporarily unavailable"));
                    return;
                }
                throw exception;
            }
        });
    }

    private void routePrefix(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            String key = exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allow(key)) {
                auditSecurityReject("RATE_LIMITED", exchange.getRequestURI().getPath(), exchange);
                write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
                return;
            }
            if (!coreApiAuthenticated(exchange)) {
                String rejectCode = coreApiAuthRejectCode();
                auditSecurityReject(rejectCode, exchange.getRequestURI().getPath(), exchange);
                write(exchange, 401, ApiResponses.error(rejectCode, coreApiAuthRejectMessage(rejectCode)));
                return;
            }
            if (!ipAllowlist.allowed(exchange)) {
                auditSecurityReject("IP_NOT_ALLOWED", exchange.getRequestURI().getPath(), exchange);
                write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
                return;
            }
            if (!adminGuard.allowed(exchange.getRequestURI().getPath(), exchange)) {
                auditSecurityReject("ADMIN_PERMISSION_DENIED", exchange.getRequestURI().getPath(), exchange);
                write(exchange, 403, ApiResponses.error("ADMIN_PERMISSION_DENIED", "Admin permission is required"));
                return;
            }
            handler.handle(exchange);
        });
    }

    private boolean coreApiAuthenticated(HttpExchange exchange) {
        return tokenGuard.allowed(exchange) || mtlsGuard.verified(exchange);
    }

    private static boolean healthProbePath(String path) {
        return "/live".equals(path) || "/ready".equals(path) || "/health".equals(path);
    }

    private String coreApiAuthRejectCode() {
        return mtlsGuard.required() ? "MTLS_REQUIRED" : "UNAUTHORIZED";
    }

    private String coreApiAuthRejectMessage(String rejectCode) {
        return "MTLS_REQUIRED".equals(rejectCode)
            ? "mTLS verification or API token authentication is required"
            : "Missing or invalid API token";
    }

    private static void lifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result) throws IOException {
        write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\"}");
    }

    private static void restoreLifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result, long snapshotNo, String storagePath) throws IOException {
        write(exchange, result.accepted() ? 202 : 409,
            "{\"accepted\":" + result.accepted()
                + ",\"code\":\"" + result.code() + "\""
                + ",\"snapshotNo\":" + snapshotNo
                + ",\"storagePath\":\"" + escape(storagePath == null ? "" : storagePath) + "\""
                + ",\"restoreManifestRequired\":" + IslandLifecycleWorkflow.RESTORE_MANIFEST_REQUIRED
                + ",\"restoreChecksumPolicy\":\"" + IslandLifecycleWorkflow.RESTORE_CHECKSUM_POLICY + "\""
                + ",\"restorePortableRequired\":" + IslandLifecycleWorkflow.RESTORE_PORTABLE_REQUIRED
                + ",\"restoreSupportedFormats\":\"" + IslandLifecycleWorkflow.RESTORE_SUPPORTED_FORMATS + "\""
                + "}");
    }

    private void auditSecurityReject(String reason, String path, HttpExchange exchange) {
        recordSecurityReject(reason);
        if (audit == null) {
            return;
        }
        try {
            String remote = exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null
                ? "unknown"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
            audit.log(new UUID(0L, 0L), "SECURITY", "SECURITY_REJECT", "HTTP", path == null ? "" : path, Map.of(
                "reason", reason == null ? "" : reason,
                "method", exchange.getRequestMethod() == null ? "" : exchange.getRequestMethod(),
                "remote", remote
            ));
        } catch (RuntimeException ignored) {
            // Security rejection audit must not change the original response.
        }
    }

    private void recordSecurityReject(String reason) {
        securityRejectsTotal.incrementAndGet();
        String normalized = reason == null ? "" : reason;
        switch (normalized) {
            case "RATE_LIMITED" -> securityRejectsRateLimited.incrementAndGet();
            case "UNAUTHORIZED" -> securityRejectsUnauthorized.incrementAndGet();
            case "MTLS_REQUIRED" -> securityRejectsMtlsRequired.incrementAndGet();
            case "IP_NOT_ALLOWED" -> securityRejectsIpNotAllowed.incrementAndGet();
            case "ADMIN_PERMISSION_DENIED" -> securityRejectsAdminPermissionDenied.incrementAndGet();
            default -> {
            }
        }
    }

    private boolean requestIslandDelete(UUID islandId, UUID ownerUuid, UUID requesterUuid, String reason) {
        java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
        if (island.isEmpty() || !island.get().ownerUuid().equals(ownerUuid)) {
            return false;
        }
        islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETE_REQUESTED);
        boolean deletedImmediately = publishDeleteJobOrMarkDeleted(islandId, ownerUuid, reason);
        if (deletedImmediately) {
            playerProfiles.clearPrimaryIsland(ownerUuid);
            events.publish(CloudIslandEventType.ISLAND_DELETED.name(), Map.of("islandId", islandId.toString(), "requesterUuid", requesterUuid.toString()));
        }
        return true;
    }

    private boolean publishDeleteJobOrMarkDeleted(UUID islandId, UUID ownerUuid, String reason) {
        java.util.Optional<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> runtime = runtimeRepository.find(islandId);
        String targetNode = runtime.map(kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot::activeNode).orElse("");
        if (targetNode != null && !targetNode.isBlank()) {
            String fencingToken = runtime.map(value -> Long.toString(value.fencingToken())).orElse("0");
            islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DEACTIVATING);
            runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DEACTIVATING);
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.DELETE_ISLAND, islandId, targetNode, 50, Map.of("reason", "BEFORE_DELETE", "deleteReason", reason, "ownerUuid", ownerUuid.toString(), "fencingToken", fencingToken), java.time.Instant.now()));
            events.publish(CloudIslandEventType.ISLAND_DELETE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "targetNode", targetNode, "reason", reason, "snapshotReason", "BEFORE_DELETE"));
            return false;
        }
        islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.BACKUP_BEFORE_DELETE);
        runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.BACKUP_BEFORE_DELETE);
        if (!backupInactiveStorageBeforeDelete(islandId, reason)) {
            islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.RECOVERY_REQUIRED);
            runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.RECOVERY_REQUIRED);
            return false;
        }
        islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETING);
        runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETING);
        return islandRepository.markDeleted(islandId, ownerUuid);
    }

    private boolean backupInactiveStorageBeforeDelete(UUID islandId, String reason) {
        if (deleteStorage == null) {
            return true;
        }
        try {
            long snapshotNo = System.currentTimeMillis();
            IslandStorage.StoredBundle storedBundle = deleteStorage.writeDeleteBackupFromLatest(islandId, snapshotNo, "BEFORE_DELETE");
            String storagePath = storedBundle.storagePath() == null || storedBundle.storagePath().isBlank()
                ? "islands/" + islandId + "/backups/delete-" + String.format("%06d", snapshotNo) + "/bundle.tar.zst"
                : storedBundle.storagePath();
            recordSnapshotAndPublish(islandId, snapshotNo, storagePath, "BEFORE_DELETE", storedBundle.checksum(), storedBundle.sizeBytes(), "");
            deleteStorage.deleteLiveState(islandId);
            return true;
        } catch (IOException exception) {
            events.publish(CloudIslandEventType.ISLAND_DELETE_BACKUP_FAILED.name(), Map.of("islandId", islandId.toString(), "reason", reason, "error", exception.getMessage() == null ? "" : exception.getMessage()));
            return false;
        }
    }

    private int recordSnapshotAndPublish(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId) {
        return recordSnapshotAndPublish(islandId, snapshotNo, storagePath, reason, checksum, sizeBytes, nodeId, 0L);
    }

    private int recordSnapshotAndPublish(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
        snapshotRepository.record(islandId, snapshotNo, storagePath, reason, null, checksum, sizeBytes);
        int pruned = snapshotRepository.prune(islandId, snapshotRetentionPolicy);
        events.publish(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(), Map.of(
            "islandId", islandId.toString(),
            "snapshotNo", Long.toString(snapshotNo),
            "reason", reason == null ? "" : reason,
            "storagePath", storagePath == null ? "" : storagePath,
            "checksum", checksum == null ? "" : checksum,
            "sizeBytes", Long.toString(sizeBytes),
            "nodeId", nodeId == null ? "" : nodeId,
            "fencingToken", Long.toString(fencingToken),
            "pruned", Integer.toString(pruned)
        ));
        return pruned;
    }

    private static String islandJson(IslandSnapshot island) {
        return "{\"islandId\":\"" + island.islandId()
            + "\",\"ownerUuid\":\"" + island.ownerUuid()
            + "\",\"name\":\"" + escape(island.name())
            + "\",\"state\":\"" + island.state()
            + "\",\"size\":" + island.size()
            + ",\"border\":" + island.size()
            + ",\"level\":" + island.level()
            + ",\"worth\":\"" + escape(island.worth())
            + "\",\"publicAccess\":" + island.publicAccess()
            + ",\"createdAt\":\"" + island.createdAt()
            + "\",\"updatedAt\":\"" + island.updatedAt()
            + "\"}";
    }

    private static String islandsJson(java.util.List<IslandSnapshot> islands) {
        StringBuilder builder = new StringBuilder("{\"islands\":[");
        boolean first = true;
        for (IslandSnapshot island : islands) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(islandJson(island));
        }
        return builder.append("]}").toString();
    }

    private static String playerProfileJson(kr.lunaf.cloudislands.api.model.PlayerIslandProfile profile) {
        return "{\"playerUuid\":\"" + profile.playerUuid()
            + "\",\"lastName\":\"" + escape(profile.lastName())
            + "\",\"primaryIslandId\":" + profile.primaryIslandId().map(value -> "\"" + value + "\"").orElse("null")
            + ",\"lastSeenAt\":\"" + profile.lastSeenAt()
            + "\"}";
    }

    private static String runtimeJson(kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime) {
        return "{\"islandId\":\"" + runtime.islandId()
            + "\",\"state\":\"" + runtime.state()
            + "\",\"activeNode\":" + nullable(runtime.activeNode())
            + ",\"activeWorld\":" + nullable(runtime.activeWorld())
            + ",\"cellX\":" + (runtime.cellX() == null ? "null" : runtime.cellX())
            + ",\"cellZ\":" + (runtime.cellZ() == null ? "null" : runtime.cellZ())
            + ",\"leaseOwner\":" + nullable(runtime.leaseOwner())
            + ",\"fencingToken\":" + runtime.fencingToken()
            + ",\"activatedAt\":" + nullable(runtime.activatedAt() == null ? null : runtime.activatedAt().toString())
            + ",\"lastHeartbeat\":" + nullable(runtime.lastHeartbeat() == null ? null : runtime.lastHeartbeat().toString())
            + "}";
    }

    private static String nodeIslandsJson(String nodeId, java.util.List<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> runtimes) {
        StringBuilder builder = new StringBuilder("{\"nodeId\":\"").append(escape(nodeId)).append("\",\"count\":").append(runtimes.size()).append(",\"islands\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot runtime : runtimes) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(runtimeJson(runtime));
        }
        return builder.append("]}").toString();
    }

    private static String nullable(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }

    private static BigDecimal amount(String body) {
        try {
            return new BigDecimal(JsonFields.text(body, "amount", Double.toString(JsonFields.decimal(body, "amount", 0.0D))));
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static String bankJson(kr.lunaf.cloudislands.api.model.IslandBankSnapshot bank) {
        return "{\"islandId\":\"" + bank.islandId() + "\",\"balance\":\"" + escape(bank.balance()) + "\",\"updatedAt\":\"" + bank.updatedAt() + "\"}";
    }

    private static String levelJson(kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot snapshot) {
        return "{\"islandId\":\"" + snapshot.islandId() + "\",\"level\":" + snapshot.level() + ",\"worth\":\"" + snapshot.worth().toPlainString() + "\",\"calculatedAt\":\"" + snapshot.updatedAt() + "\"}";
    }

    private static String rankingsJson(java.util.List<kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot> rankings) {
        StringBuilder builder = new StringBuilder("{\"rankings\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot ranking : rankings) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(levelJson(ranking));
        }
        return builder.append("]}").toString();
    }

    private static String upgradeRulesJson(java.util.List<UpgradeRule> rules) {
        StringBuilder builder = new StringBuilder("{\"rules\":[");
        boolean first = true;
        for (UpgradeRule rule : rules) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"upgradeKey\":\"").append(rule.upgradeKey()).append("\",")
                .append("\"type\":\"").append(rule.type()).append("\",")
                .append("\"maxLevel\":").append(rule.maxLevel()).append(',')
                .append("\"baseCost\":\"").append(rule.baseCost().toPlainString()).append("\",")
                .append("\"multiplier\":\"").append(rule.multiplier().toPlainString()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String blockValuesJson(Map<String, RankingRecalculationService.BlockValue> values) {
        StringBuilder builder = new StringBuilder("{\"values\":[");
        boolean first = true;
        for (Map.Entry<String, RankingRecalculationService.BlockValue> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            RankingRecalculationService.BlockValue value = entry.getValue();
            builder.append('{')
                .append("\"materialKey\":\"").append(escape(entry.getKey())).append("\",")
                .append("\"worth\":\"").append(value.worth().toPlainString()).append("\",")
                .append("\"levelPoints\":").append(value.levelPoints()).append(',')
                .append("\"limit\":").append(value.limit())
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static Map<String, Long> parseCountsPayload(String payload) {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        if (payload == null || payload.isBlank()) {
            return counts;
        }
        for (String entry : payload.split("\\|")) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }
            try {
                long amount = Long.parseLong(entry.substring(separator + 1));
                if (amount > 0L) {
                    counts.put(entry.substring(0, separator), amount);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return counts;
    }

    private static String missionsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> missions) {
        StringBuilder builder = new StringBuilder("{\"missions\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission : missions) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(missionJson(mission));
        }
        return builder.append("]}").toString();
    }

    private static String missionJson(kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission) {
        return "{\"islandId\":\"" + mission.islandId()
            + "\",\"missionKey\":\"" + escape(mission.missionKey())
            + "\",\"kind\":\"" + escape(mission.kind())
            + "\",\"title\":\"" + escape(mission.title())
            + "\",\"progress\":" + mission.progress()
            + ",\"goal\":" + mission.goal()
            + ",\"completed\":" + mission.completed()
            + ",\"reward\":\"" + escape(mission.reward())
            + "\",\"updatedAt\":\"" + mission.updatedAt()
            + "\"}";
    }

    private static String limitsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandLimitSnapshot> limits) {
        StringBuilder builder = new StringBuilder("{\"limits\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit : limits) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(limitJson(limit));
        }
        return builder.append("]}").toString();
    }

    private static String limitJson(kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit) {
        return "{\"islandId\":\"" + limit.islandId()
            + "\",\"limitKey\":\"" + escape(limit.limitKey())
            + "\",\"value\":" + limit.value()
            + ",\"updatedBy\":\"" + limit.updatedBy()
            + "\",\"updatedAt\":\"" + limit.updatedAt()
            + "\"}";
    }

    private static String upgradesJson(java.util.List<kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot> upgrades) {
        StringBuilder builder = new StringBuilder("{\"upgrades\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot upgrade : upgrades) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(upgradeJson(upgrade));
        }
        return builder.append("]}").toString();
    }

    private static String snapshotsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandSnapshotRecord> snapshots) {
        StringBuilder builder = new StringBuilder("{\"snapshots\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandSnapshotRecord snapshot : snapshots) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"snapshotId\":\"").append(snapshot.snapshotId()).append("\",")
                .append("\"islandId\":\"").append(snapshot.islandId()).append("\",")
                .append("\"snapshotNo\":").append(snapshot.snapshotNo()).append(',')
                .append("\"storagePath\":\"").append(escape(snapshot.storagePath())).append("\",")
                .append("\"reason\":\"").append(escape(snapshot.reason())).append("\",")
                .append("\"createdBy\":\"").append(snapshot.createdBy()).append("\",")
                .append("\"checksum\":\"").append(escape(snapshot.checksum())).append("\",")
                .append("\"sizeBytes\":").append(snapshot.sizeBytes()).append(',')
                .append("\"createdAt\":\"").append(snapshot.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String upgradePurchaseJson(UpgradePurchaseResult result) {
        return "{\"accepted\":" + result.accepted()
            + ",\"code\":\"" + result.code() + "\""
            + ",\"cost\":\"" + result.cost().toPlainString() + "\""
            + ",\"upgrade\":" + (result.snapshot() == null ? "null" : upgradeJson(result.snapshot()))
            + "}";
    }

    private static String upgradeJson(kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot upgrade) {
        return "{\"islandId\":\"" + upgrade.islandId()
            + "\",\"upgradeKey\":\"" + escape(upgrade.upgradeKey())
            + "\",\"type\":\"" + upgrade.type()
            + "\",\"level\":" + upgrade.level()
            + ",\"updatedAt\":\"" + upgrade.updatedAt()
            + "\"}";
    }

    private static boolean requireIslandPermission(HttpExchange exchange, IslandRepository islandRepository, IslandMetadataRepository metadataRepository, IslandPermissionRuleRepository permissionRules, GlobalEventPublisher events, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        CachedPermissionSet permissions = DefaultIslandPermissions.create();
        for (var rule : permissionRules.list(islandId)) {
            permissions.put(rule.role(), rule.permission(), rule.allowed());
        }
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissions.allowed(member.role(), permission));
        boolean accepted = owner || allowed;
        events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHECKED.name(), Map.of(
            "islandId", islandId.toString(),
            "playerUuid", actorUuid.toString(),
            "permission", permission.name(),
            "allowed", Boolean.toString(accepted)
        ));
        if (accepted) {
            return true;
        }
        write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island permission " + permission.name() + " is required"));
        return false;
    }

    private static boolean requireMember(HttpExchange exchange, IslandRepository islandRepository, IslandMetadataRepository metadataRepository, UUID islandId, UUID actorUuid) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        boolean member = metadataRepository.members(islandId).stream()
            .anyMatch(record -> record.playerUuid().equals(actorUuid) && record.role() != IslandRole.VISITOR && record.role() != IslandRole.BANNED);
        if (owner || member) {
            return true;
        }
        write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island member permission is required"));
        return false;
    }

    private static void applyUpgradeLimit(IslandLimitRepository limits, GlobalEventPublisher events, UUID islandId, UUID actorUuid, UpgradeRule rule, UpgradeType type, int level) {
        java.util.OptionalLong configuredValue = rule == null ? java.util.OptionalLong.empty() : rule.limitValueForLevel(level);
        IslandLimitSnapshot snapshot = switch (type) {
            case ISLAND_SIZE -> limits.set(islandId, "SIZE", configuredValue.orElse(100L + Math.max(0L, level - 1L) * 50L), actorUuid);
            case MAX_MEMBERS -> limits.set(islandId, "MEMBERS", configuredValue.orElse(3L + Math.max(0L, level - 1L) * 2L), actorUuid);
            case MAX_WARPS -> limits.set(islandId, "WARPS", configuredValue.orElse(Math.max(1L, level)), actorUuid);
            case HOPPER_LIMIT -> limits.set(islandId, "HOPPER", configuredValue.orElse(Math.max(1L, level) * 50L), actorUuid);
            case SPAWNER_LIMIT -> limits.set(islandId, "SPAWNER", configuredValue.orElse(Math.max(1L, level) * 25L), actorUuid);
            case MOB_LIMIT -> limits.set(islandId, "ENTITY", configuredValue.orElse(Math.max(1L, level) * 200L), actorUuid);
            case REDSTONE_LIMIT -> limits.set(islandId, "REDSTONE", configuredValue.orElse(Math.max(1L, level) * 512L), actorUuid);
            case BANK_LIMIT -> limits.set(islandId, "BANK", configuredValue.orElse(Math.max(1L, level) * 100000L), actorUuid);
            case GENERATOR_LEVEL, CROP_GROWTH, FLY_ACCESS -> null;
        };
        if (snapshot != null) {
            events.publish(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name(), Map.of("islandId", islandId.toString(), "limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
        }
    }

    private static void applyUpgradeFlag(IslandMetadataRepository metadata, GlobalEventPublisher events, UUID islandId, UpgradeType type) {
        if (type != UpgradeType.FLY_ACCESS) {
            return;
        }
        metadata.setFlag(islandId, IslandFlag.FLY, "true");
        events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "flag", IslandFlag.FLY.name(), "value", "true"));
    }

    private static long limitValue(IslandLimitRepository limits, UUID islandId, String limitKey, long fallback) {
        return limits.list(islandId).stream()
            .filter(limit -> limit.limitKey().equalsIgnoreCase(limitKey))
            .findFirst()
            .map(IslandLimitSnapshot::value)
            .orElse(fallback);
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value == null || value.isBlank() ? "0" : value);
        } catch (RuntimeException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String adminSaveReason(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "save" : reason;
        return safeReason.toUpperCase(java.util.Locale.ROOT).contains("ADMIN_SAVE") ? safeReason : "ADMIN_SAVE:" + safeReason;
    }

    private static String adminSnapshotReason(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "snapshot" : reason;
        return safeReason.toUpperCase(java.util.Locale.ROOT).contains("MANUAL") ? safeReason : "MANUAL:" + safeReason;
    }

    private static void restoreIslandSnapshot(com.sun.net.httpserver.HttpExchange exchange, AuditLogger audit, IslandLifecycleWorkflow lifecycle, IslandSnapshotRepository snapshotRepository, UUID islandId, long snapshotNo) throws java.io.IOException {
        java.util.Optional<kr.lunaf.cloudislands.api.model.IslandSnapshotRecord> snapshot = snapshotRepository.find(islandId, snapshotNo);
        if (snapshotNo <= 0L || snapshot.isEmpty()) {
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_RESTORE_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", "false", "code", "SNAPSHOT_NOT_FOUND", "snapshotNo", Long.toString(snapshotNo)));
            write(exchange, 404, ApiResponses.error("SNAPSHOT_NOT_FOUND", "Snapshot was not found"));
            return;
        }
        IslandLifecycleWorkflow.Result result = lifecycle.restore(islandId, snapshotNo, snapshot.get().storagePath());
        audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_RESTORE_REQUEST", "ISLAND", islandId.toString(), Map.of(
            "accepted", Boolean.toString(result.accepted()),
            "code", result.code(),
            "snapshotNo", Long.toString(snapshotNo),
            "storagePath", snapshot.get().storagePath() == null ? "" : snapshot.get().storagePath(),
            "restoreManifestRequired", IslandLifecycleWorkflow.RESTORE_MANIFEST_REQUIRED,
            "restoreChecksumPolicy", IslandLifecycleWorkflow.RESTORE_CHECKSUM_POLICY,
            "restorePortableRequired", IslandLifecycleWorkflow.RESTORE_PORTABLE_REQUIRED,
            "restoreSupportedFormats", IslandLifecycleWorkflow.RESTORE_SUPPORTED_FORMATS
        ));
        restoreLifecycle(exchange, result, snapshotNo, snapshot.get().storagePath());
    }

    private static void rollbackIslandSnapshot(com.sun.net.httpserver.HttpExchange exchange, AuditLogger audit, IslandLifecycleWorkflow lifecycle, IslandSnapshotRepository snapshotRepository, UUID islandId, long snapshotNo) throws java.io.IOException {
        java.util.Optional<kr.lunaf.cloudislands.api.model.IslandSnapshotRecord> snapshot = snapshotRepository.find(islandId, snapshotNo);
        if (snapshotNo <= 0L || snapshot.isEmpty()) {
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_ROLLBACK_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", "false", "code", "SNAPSHOT_NOT_FOUND", "snapshotNo", Long.toString(snapshotNo)));
            write(exchange, 404, ApiResponses.error("SNAPSHOT_NOT_FOUND", "Snapshot was not found"));
            return;
        }
        IslandLifecycleWorkflow.Result result = lifecycle.restore(islandId, snapshotNo, snapshot.get().storagePath());
        audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_ROLLBACK_REQUEST", "ISLAND", islandId.toString(), Map.of(
            "accepted", Boolean.toString(result.accepted()),
            "code", result.code(),
            "snapshotNo", Long.toString(snapshotNo),
            "storagePath", snapshot.get().storagePath() == null ? "" : snapshot.get().storagePath(),
            "restoreManifestRequired", IslandLifecycleWorkflow.RESTORE_MANIFEST_REQUIRED,
            "restoreChecksumPolicy", IslandLifecycleWorkflow.RESTORE_CHECKSUM_POLICY,
            "restorePortableRequired", IslandLifecycleWorkflow.RESTORE_PORTABLE_REQUIRED,
            "restoreSupportedFormats", IslandLifecycleWorkflow.RESTORE_SUPPORTED_FORMATS
        ));
        restoreLifecycle(exchange, result, snapshotNo, snapshot.get().storagePath());
    }

    private static String membersJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandMemberSnapshot> members) {
        StringBuilder builder = new StringBuilder("{\"members\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandMemberSnapshot member : members) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(member.islandId()).append("\",")
                .append("\"playerUuid\":\"").append(member.playerUuid()).append("\",")
                .append("\"role\":\"").append(member.role()).append("\",")
                .append("\"joinedAt\":\"").append(member.joinedAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String islandLogsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandLogRecord> logs) {
        StringBuilder builder = new StringBuilder("{\"logs\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandLogRecord log : logs) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"logId\":\"").append(log.logId()).append("\",")
                .append("\"islandId\":\"").append(log.islandId()).append("\",")
                .append("\"actorUuid\":\"").append(log.actorUuid()).append("\",")
                .append("\"action\":\"").append(escape(log.action())).append("\",")
                .append("\"payload\":").append(stringMapJson(log.payload())).append(',')
                .append("\"createdAt\":\"").append(log.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String bansJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandBanSnapshot> bans) {
        StringBuilder builder = new StringBuilder("{\"bans\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandBanSnapshot ban : bans) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(ban.islandId()).append("\",")
                .append("\"bannedUuid\":\"").append(ban.bannedUuid()).append("\",")
                .append("\"actorUuid\":\"").append(ban.actorUuid()).append("\",")
                .append("\"reason\":\"").append(escape(ban.reason())).append("\",")
                .append("\"createdAt\":\"").append(ban.createdAt()).append("\",")
                .append("\"expiresAt\":").append(ban.expiresAt() == null ? "null" : "\"" + ban.expiresAt() + "\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String stringMapJson(Map<String, String> payload) {
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

    private static Map<String, String> tableStateValues(String table, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        String safeTable = safeBulkTableName(table);
        if (safeTable.isBlank()) {
            return Map.of();
        }
        java.util.HashMap<String, String> state = new java.util.HashMap<>();
        values.forEach((key, value) -> {
            String safeKey = safeBulkTableKey(key);
            if (!safeKey.isBlank() && tableStateKeyLength(safeTable, safeKey) <= AddonStateRepository.MAX_KEY_LENGTH) {
                state.put(tableStateKey(safeTable, safeKey), safeBulkValue(value));
            }
        });
        return Map.copyOf(state);
    }

    private static Map<String, String> tableKeyValueBulkStateValues(String valuesTable, Map<String, String> values, Map<String, Map<String, String>> tables) {
        java.util.HashMap<String, String> state = new java.util.HashMap<>();
        if (valuesTable == null || valuesTable.isBlank()) {
            if (values != null) {
                values.forEach((key, value) -> {
                    String safeKey = safeBulkRootKey(key);
                    if (!safeKey.isBlank()) {
                        state.put(safeKey, safeBulkValue(value));
                    }
                });
            }
        } else {
            state.putAll(tableStateValues(valuesTable, values));
        }
        if (tables != null) {
            tables.forEach((table, tableValues) -> state.putAll(tableStateValues(table, tableValues)));
        }
        return Map.copyOf(state);
    }

    private static Map<String, String> tableKeyValueBulkStateValues(Map<String, String> values, Map<String, Map<String, String>> tables, String valuesTable) {
        return tableKeyValueBulkStateValues(valuesTable, values, tables);
    }

    private static int tableKeyValueBulkTableKeyCount(Map<String, String> values, Map<String, Map<String, String>> tables, String valuesTable) {
        int count = tableKeyCount(tables);
        if (valuesTable != null && !valuesTable.isBlank()) {
            count += tableStateValues(valuesTable, values).size();
        }
        return count;
    }

    private static int tableKeyValueBulkTableCount(Map<String, String> values, Map<String, Map<String, String>> tables, String valuesTable) {
        java.util.LinkedHashSet<String> tableNames = new java.util.LinkedHashSet<>();
        if (tables != null) {
            tables.keySet().forEach(table -> {
                Map<String, String> tableValues = tables.get(table);
                if (tableValues != null && !tableValues.isEmpty() && table != null && !table.isBlank()
                    && !tableStateValues(table, tableValues).isEmpty()) {
                    tableNames.add(safeTableName(table));
                }
            });
        }
        if (valuesTable != null && !valuesTable.isBlank() && !tableStateValues(valuesTable, values).isEmpty()) {
            tableNames.add(safeTableName(valuesTable));
        }
        return tableNames.size();
    }

    private static int rootValueKeyCount(Map<String, String> values, String valuesTable) {
        if (valuesTable != null && !valuesTable.isBlank()) {
            return 0;
        }
        return values == null ? 0 : values.size();
    }

    private static int tableKeyCount(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        return tables.entrySet().stream()
            .mapToInt(entry -> tableStateValues(entry.getKey(), entry.getValue()).size())
            .sum();
    }

    private static String tableStatePrefix(String table) {
        return AddonStateRepository.TABLE_STATE_KEY_PREFIX + safeTableName(table) + "/";
    }

    private static String tableStateKey(String table, String key) {
        String value = tableStatePrefix(table) + safeTableKey(key);
        if (value.length() > AddonStateRepository.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Addon state table key is too long");
        }
        return value;
    }

    private static int tableStateKeyLength(String table, String key) {
        return AddonStateRepository.TABLE_STATE_KEY_PREFIX.length() + table.length() + 1 + key.length();
    }

    private static String safeBulkTableName(String table) {
        try {
            return safeTableName(table);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static String safeTableName(String table) {
        String value = table == null ? "" : table.trim();
        if (value.startsWith(AddonStateRepository.TABLE_STATE_KEY_PREFIX)) {
            value = value.substring(AddonStateRepository.TABLE_STATE_KEY_PREFIX.length());
        }
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Addon state table is required");
        }
        if (value.contains("/")) {
            throw new IllegalArgumentException("Addon state table cannot contain /");
        }
        if (value.length() > AddonStateRepository.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Addon state table is too long");
        }
        return value;
    }

    private static String safeTableKey(String key) {
        String value = key == null ? "" : key.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Addon state table key is required");
        }
        return value;
    }

    private static String safeBulkTableKey(String key) {
        return key == null ? "" : key.trim();
    }

    private static String safeBulkRootKey(String key) {
        String value = key == null ? "" : key.trim();
        return value.length() > AddonStateRepository.MAX_KEY_LENGTH
            ? value.substring(0, AddonStateRepository.MAX_KEY_LENGTH)
            : value;
    }

    private static String safeBulkValue(String value) {
        String safe = value == null ? "" : value;
        return safe.length() > AddonStateRepository.MAX_VALUE_LENGTH
            ? safe.substring(0, AddonStateRepository.MAX_VALUE_LENGTH)
            : safe;
    }

    private static String permissionsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot> rules) {
        StringBuilder builder = new StringBuilder("{\"rules\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot rule : rules) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(rule.islandId()).append("\",")
                .append("\"role\":\"").append(rule.role().name()).append("\",")
                .append("\"permission\":\"").append(rule.permission().name()).append("\",")
                .append("\"allowed\":").append(rule.allowed())
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String rolesJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandRoleSnapshot> roles) {
        StringBuilder builder = new StringBuilder("{\"roles\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandRoleSnapshot role : roles) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(roleJson(role));
        }
        return builder.append("]}").toString();
    }

    private static String roleJson(kr.lunaf.cloudislands.api.model.IslandRoleSnapshot role) {
        return "{\"islandId\":\"" + role.islandId()
            + "\",\"role\":\"" + role.role().name()
            + "\",\"weight\":" + role.weight()
            + ",\"displayName\":\"" + escape(role.displayName())
            + "\"}";
    }

    private static String flagsJson(kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot flags) {
        StringBuilder builder = new StringBuilder("{\"islandId\":\"").append(flags.islandId()).append("\",\"flags\":{");
        boolean first = true;
        for (Map.Entry<IslandFlag, String> entry : flags.values().entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(entry.getKey().name()).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append("}}").toString();
    }

    private static String biomeJson(kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot biome) {
        return "{\"islandId\":\"" + biome.islandId()
            + "\",\"biomeKey\":\"" + escape(biome.biomeKey())
            + "\",\"updatedBy\":\"" + biome.updatedBy()
            + "\",\"updatedAt\":\"" + biome.updatedAt()
            + "\"}";
    }

    private static String homesJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandHomeSnapshot> homes) {
        StringBuilder builder = new StringBuilder("{\"homes\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandHomeSnapshot home : homes) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            IslandLocation location = home.location();
            builder.append('{')
                .append("\"islandId\":\"").append(home.islandId()).append("\",")
                .append("\"name\":\"").append(escape(home.name())).append("\",")
                .append("\"worldName\":\"").append(escape(location.worldName())).append("\",")
                .append("\"localX\":").append(location.localX()).append(',')
                .append("\"localY\":").append(location.localY()).append(',')
                .append("\"localZ\":").append(location.localZ()).append(',')
                .append("\"yaw\":").append(location.yaw()).append(',')
                .append("\"pitch\":").append(location.pitch()).append(',')
                .append("\"createdBy\":\"").append(home.createdBy()).append("\",")
                .append("\"createdAt\":\"").append(home.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String warpsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandWarpSnapshot> warps) {
        StringBuilder builder = new StringBuilder("{\"warps\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandWarpSnapshot warp : warps) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            IslandLocation location = warp.location();
            builder.append('{')
                .append("\"islandId\":\"").append(warp.islandId()).append("\",")
                .append("\"name\":\"").append(escape(warp.name())).append("\",")
                .append("\"localX\":").append(location.localX()).append(',')
                .append("\"localY\":").append(location.localY()).append(',')
                .append("\"localZ\":").append(location.localZ()).append(',')
                .append("\"yaw\":").append(location.yaw()).append(',')
                .append("\"pitch\":").append(location.pitch()).append(',')
                .append("\"publicAccess\":").append(warp.publicAccess()).append(',')
                .append("\"createdBy\":\"").append(warp.createdBy()).append("\",")
                .append("\"createdAt\":\"").append(warp.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean avoidsSoftFullNewActivations(String policy) {
        return policy == null
            || policy.isBlank()
            || policy.equalsIgnoreCase("AVOID_NEW_ACTIVATIONS")
            || policy.equalsIgnoreCase("READY_ONLY");
    }

    private static boolean allowsHardFullNewActivations(String policy) {
        return policy != null
            && (policy.equalsIgnoreCase("ALLOW_NEW_ACTIVATIONS")
                || policy.equalsIgnoreCase("ALLOW")
                || policy.equalsIgnoreCase("IGNORE_HARD_FULL"));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return CoreHttpResponses.readBody(exchange);
    }

    private static int queryInteger(HttpExchange exchange, String key, int fallback, int min, int max) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return Math.max(min, Math.min(fallback, max));
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || !part.substring(0, separator).equals(key)) {
                continue;
            }
            try {
                return Math.max(min, Math.min(Integer.parseInt(part.substring(separator + 1)), max));
            } catch (NumberFormatException ignored) {
                return Math.max(min, Math.min(fallback, max));
            }
        }
        return Math.max(min, Math.min(fallback, max));
    }

    private static UUID queryUuid(HttpExchange exchange, String key, UUID fallback) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return fallback;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || !part.substring(0, separator).equals(key)) {
                continue;
            }
            try {
                return UUID.fromString(part.substring(separator + 1));
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static UUID uuidPath(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        write(exchange, status, body, "application/json; charset=utf-8");
    }

    private static void write(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        CoreHttpResponses.write(exchange, status, body, contentType);
    }
}
