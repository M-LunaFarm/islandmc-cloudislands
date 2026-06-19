package kr.lunaf.cloudislands.coreservice;

import static kr.lunaf.cloudislands.coreservice.config.CoreNetworkExposure.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
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
import kr.lunaf.cloudislands.coreservice.http.routes.AdminIslandLifecycleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminRuntimeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.CoreConfigRoutes;
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
import kr.lunaf.cloudislands.coreservice.metrics.CoreMetricsFactory;
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
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import kr.lunaf.cloudislands.migration.rollback.CompositeRollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService.RollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.StorageRollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.jdbc.JdbcMigrationRollbackTarget;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.LocalIslandStorage;
import kr.lunaf.cloudislands.storage.s3.S3IslandStorage;
import kr.lunaf.cloudislands.coreservice.addon.AddonStateRepository;
import kr.lunaf.cloudislands.coreservice.addon.InMemoryAddonStateRepository;
import kr.lunaf.cloudislands.coreservice.addon.JdbcAddonStateRepository;

public final class CloudIslandsCoreApplication {
    private static final Logger LOGGER = Logger.getLogger(CloudIslandsCoreApplication.class.getName());
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
        PrometheusMetricsRenderer metrics = CoreMetricsFactory.create(
            config,
            coreJdbcActive,
            meteredDataSource,
            nodes,
            jobs,
            tickets,
            sessions,
            islandRepository,
            metadataRepository,
            playerProfiles,
            permissionRules,
            roleRepository,
            runtimeRepository,
            rankingRepository,
            levelRepository,
            bankRepository,
            limitRepository,
            missionRepository,
            upgradeRepository,
            templateRepository,
            snapshotRepository,
            islandLogs,
            redisCacheAdmin,
            activationLock,
            playerCreationLock,
            audit,
            inMemoryEvents,
            redisEventPublisher,
            rankingRecalculationTask,
            securityRejectsTotal::get,
            securityRejectsRateLimited::get,
            securityRejectsUnauthorized::get,
            securityRejectsMtlsRequired::get,
            securityRejectsIpNotAllowed::get,
            securityRejectsAdminPermissionDenied::get
        );
        this.nodeFailureMonitor = new NodeFailureMonitor(nodes, runtimeRepository, islandRepository, events, config.heartbeatTimeout(), tickets, sessions, snapshotRepository, lifecycle);
        this.routeTicketExpiryMonitor = new RouteTicketExpiryMonitor(tickets, events, config.routeTicketTtl());
        this.jobRecoveryMonitor = new JobRecoveryMonitor(jobs, Duration.ofSeconds(60), config.leaseDuration().toMillis(), 16);
        this.server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        new HealthRoutes(config, metrics::render).register(this::route);
        new CoreConfigRoutes(config, nodes).register(this::route);
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

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
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
