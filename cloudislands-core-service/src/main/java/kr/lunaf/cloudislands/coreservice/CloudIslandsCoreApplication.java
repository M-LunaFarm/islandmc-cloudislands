package kr.lunaf.cloudislands.coreservice;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;
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
import kr.lunaf.cloudislands.coreservice.db.MeteredDataSource;
import kr.lunaf.cloudislands.coreservice.event.CompositeGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.RedisStreamEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
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
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot;
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
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;
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
        this.adminGuard = new AdminEndpointGuard(config.adminToken(), config.adminApiEnabled());
        this.ipAllowlist = new IpAllowlist(config.ipAllowlist());
        this.mtlsGuard = new MtlsHeaderGuard(config.requireMtls(), config.mtlsVerifiedHeader(), config.mtlsVerifiedValue());
        logSecurityPosture(config);
        this.deleteStorage = migrationRollbackStorage(config);
        MeteredDataSource meteredDataSource = new MeteredDataSource(new BoundedDataSource(new DriverManagerDataSource(config.jdbcUrl(), config.databaseUsername(), config.databasePassword()), config.databasePoolSize()));
        DataSource dataSource = meteredDataSource;
        boolean coreJdbcActive = config.jdbcRepositories() || config.jdbcJobs();
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
        RedisCacheAdmin redisCacheAdmin = config.redisEvents() || config.redisJobs() ? new RedisCacheAdmin(config.redisUri()) : null;
        RedisActivationLock activationLock = config.redisEvents() || config.redisJobs() ? new RedisActivationLock(config.redisUri(), config.routePreparingTicketTtl()) : null;
        RedisPlayerCreationLock playerCreationLock = config.redisEvents() || config.redisJobs() ? new RedisPlayerCreationLock(config.redisUri(), config.routePreparingTicketTtl()) : null;
        GlobalEventPublisher events = config.redisEvents()
            ? new CompositeGlobalEventPublisher(java.util.List.of(inMemoryEvents, new RedisStreamEventPublisher(redisEventWriter)))
            : inMemoryEvents;
        IslandRepository baseIslandRepository = config.jdbcRepositories() ? new JdbcIslandRepository(dataSource) : new InMemoryIslandRepository();
        IslandRepository islandRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandRepository(baseIslandRepository, config.redisUri())
            : baseIslandRepository;
        IslandMetadataRepository baseMetadataRepository = config.jdbcRepositories() ? new JdbcIslandMetadataRepository(dataSource) : new InMemoryIslandMetadataRepository();
        IslandMetadataRepository metadataRepository = config.redisEvents() || config.redisJobs()
            ? new CachingIslandMetadataRepository(baseMetadataRepository, config.redisUri())
            : baseMetadataRepository;
        PlayerProfileRepository basePlayerProfiles = config.jdbcRepositories() ? new JdbcPlayerProfileRepository(dataSource) : new InMemoryPlayerProfileRepository();
        PlayerProfileRepository playerProfiles = config.redisEvents() || config.redisJobs()
            ? new CachingPlayerProfileRepository(basePlayerProfiles, config.redisUri())
            : basePlayerProfiles;
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
        RankingRecalculationService levelRecalculation = new RankingRecalculationService(rankingRepository, events);
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
        java.util.function.IntFunction<String> auditJson = audit::toJson;
        RoutingOrchestrator routing = new RoutingOrchestrator(nodes, allocator, tickets, islandRepository, metadataRepository, runtimeRepository, templateRepository, jobs, events, config.islandPool(), config.routeTicketTtl(), config.routePreparingTicketTtl(), activationLock);
        CreateIslandWorkflow createIsland = new CreateIslandWorkflow(islandRepository, metadataRepository, playerProfiles, templateRepository, nodes, allocator, jobs, events, tickets, config.islandPool(), config.routePreparingTicketTtl(), playerCreationLock);
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
        PrometheusMetricsRenderer metrics = new PrometheusMetricsRenderer(nodes, jobs, tickets, runtimeRepository, inMemoryEvents, config.heartbeatTimeout(), meteredDataSource::lastQuerySeconds, meteredDataSource::activeConnections, meteredDataSource::openedConnections, meteredDataSource::connectionFailures, meteredDataSource::queryFailures, () -> redisEventWriter == null ? 0L : redisEventWriter.failuresTotal(), () -> redisCacheFailures(nodes, tickets, sessions, islandRepository, metadataRepository, playerProfiles, permissionRules, roleRepository, runtimeRepository, rankingRepository, levelRepository, bankRepository, limitRepository, missionRepository, upgradeRepository, templateRepository, snapshotRepository, islandLogs, redisCacheAdmin, activationLock, playerCreationLock, audit), () -> config.databasePoolSize(), () -> coreJdbcFallbackActive(config), () -> config.coreToken() != null && !config.coreToken().isBlank(), () -> config.adminToken() != null && !config.adminToken().isBlank(), config::adminApiEnabled, config::requireMtls, () -> config.ipAllowlist() != null && !config.ipAllowlist().isBlank(), () -> publicBind(config.bind()) && (config.ipAllowlist() == null || config.ipAllowlist().isBlank()), () -> config.redisUri() != null && !internalHost(config.redisUri().getHost()), () -> coreJdbcActive && !internalHost(jdbcHost(config.jdbcUrl())), () -> "S3".equalsIgnoreCase(config.storageType()) && config.storageEndpoint() != null && !internalHost(config.storageEndpoint().getHost()), () -> "S3".equalsIgnoreCase(config.storageType()) && config.storageEndpoint() != null && "http".equalsIgnoreCase(config.storageEndpoint().getScheme()) && !internalHost(config.storageEndpoint().getHost()), () -> config.rateLimitRequests(), () -> config.rateLimitWindow().toSeconds(), this.rankingRecalculationTask::drainedTotal, this.rankingRecalculationTask::recalculatedTotal, this.rankingRecalculationTask::failuresTotal, this.rankingRecalculationTask::lastBatchSize, securityRejectsTotal::get, securityRejectsRateLimited::get, securityRejectsUnauthorized::get, securityRejectsMtlsRequired::get, securityRejectsIpNotAllowed::get, securityRejectsAdminPermissionDenied::get);
        this.nodeFailureMonitor = new NodeFailureMonitor(nodes, runtimeRepository, islandRepository, events, config.heartbeatTimeout(), tickets, sessions, snapshotRepository, lifecycle);
        this.routeTicketExpiryMonitor = new RouteTicketExpiryMonitor(tickets, events, config.routeTicketTtl());
        this.jobRecoveryMonitor = new JobRecoveryMonitor(jobs, Duration.ofSeconds(60), config.leaseDuration().toMillis(), 16);
        this.server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        route("/health", exchange -> write(exchange, 200, "{\"status\":\"UP\"}"));
        route("/metrics", exchange -> write(exchange, 200, metrics.render(), "text/plain; version=0.0.4; charset=utf-8"));
        route("/v1/admin/config", exchange -> write(exchange, 200, configSummaryJson(config, nodes)));
        route("/v1/admin/storage", exchange -> write(exchange, 200, nodes.toJson(config.heartbeatTimeout())));
        route("/v1/nodes", exchange -> write(exchange, 200, nodes.toJson(config.heartbeatTimeout())));
        route("/v1/nodes/info", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            write(exchange, nodes.find(nodeId).isPresent() ? 200 : 404, nodes.find(nodeId).map(NodeRegistry::toJson).orElseGet(() -> ApiResponses.error("NODE_NOT_FOUND", "Node was not found")));
        });
        route("/v1/nodes/islands", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 50), 200));
            if (nodes.find(nodeId).isEmpty()) {
                write(exchange, 404, ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
                return;
            }
            write(exchange, 200, nodeIslandsJson(nodeId, runtimeRepository.listByNode(nodeId, limit)));
        });
        route("/v1/admin/nodes/list", exchange -> write(exchange, 200, nodes.toJson(config.heartbeatTimeout())));
        route("/v1/admin/nodes/info", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            write(exchange, nodes.find(nodeId).isPresent() ? 200 : 404, nodes.find(nodeId).map(NodeRegistry::toJson).orElseGet(() -> ApiResponses.error("NODE_NOT_FOUND", "Node was not found")));
        });
        route("/v1/admin/nodes/islands", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 50), 200));
            if (nodes.find(nodeId).isEmpty()) {
                write(exchange, 404, ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
                return;
            }
            write(exchange, 200, nodeIslandsJson(nodeId, runtimeRepository.listByNode(nodeId, limit)));
        });
        route("/v1/jobs", exchange -> write(exchange, 200, jobsJson(jobs)));
        route("/v1/events", exchange -> {
            String body = readBody(exchange);
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 512), 4096));
            long sinceSeq = Math.max(0L, JsonFields.longValue(body, "sinceSeq", 0L));
            write(exchange, 200, inMemoryEvents.toJson(limit, sinceSeq));
        });
        route("/v1/audit", exchange -> {
            String body = readBody(exchange);
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 100), 500));
            write(exchange, 200, auditJson.apply(limit));
        });
        route("/v1/admin/audit", exchange -> {
            String body = readBody(exchange);
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 100), 500));
            write(exchange, 200, auditJson.apply(limit));
        });
        route("/v1/admin/audit/list", exchange -> {
            String body = readBody(exchange);
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 100), 500));
            write(exchange, 200, auditJson.apply(limit));
        });
        route("/v1/admin/addons/state/summary", exchange -> write(exchange, 200, addonStateSummaryJson(addonStates.globalStateCounts(), addonStates.islandStateCounts())));
        route("/v1/addons/state", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            if (addonId.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                write(exchange, 200, addonStateJson(addonStates.list(addonId)));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/set", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String key = JsonFields.text(body, "key", "");
            String value = JsonFields.text(body, "value", "");
            if (addonId.isBlank() || key.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.put(addonId, key, value);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_SET", "ADDON", addonId, Map.of("key", key));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "SET", "key", key));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/bulk", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.put(addonId, stateValues);
                String operation = tables.isEmpty() ? "BULK_SET" : "BULK_SAVE";
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_" + operation, "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", operation, "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/save", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.put(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/table-key-value/bulk-save", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.put(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/table/key-value/bulk-save", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.put(addonId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/table/bulk", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            if (addonId.isBlank() || table.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                Map<String, String> tableValues = tableStateValues(safeTable, values);
                Map<String, String> state = addonStates.put(addonId, tableValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_TABLE_STATE_BULK_SET", "ADDON", addonId, Map.of("table", safeTable, "keys", Integer.toString(tableValues.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "TABLE_BULK_SET", "table", safeTable, "keys", Integer.toString(tableValues.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/table/replace", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            if (addonId.isBlank() || table.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> tableValues = tableStateValues(safeTable, values);
                Map<String, String> state = addonStates.replacePrefix(addonId, prefix, tableValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_TABLE_STATE_REPLACE", "ADDON", addonId, Map.of("table", safeTable, "keys", Integer.toString(tableValues.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "TABLE_REPLACE", "table", safeTable, "keys", Integer.toString(tableValues.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/table/clear", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || table.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> updated = addonStates.removePrefix(addonId, prefix);
                audit.log(new UUID(0L, 0L), "API", "ADDON_TABLE_STATE_CLEAR", "ADDON", addonId, Map.of("table", safeTable));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "TABLE_CLEAR", "table", safeTable));
                write(exchange, 202, addonStateJson(updated));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/remove", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            String key = JsonFields.text(body, "key", "");
            if (addonId.isBlank() || key.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.remove(addonId, key);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_REMOVE", "ADDON", addonId, Map.of("key", key));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "REMOVE", "key", key));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/state/clear", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            if (addonId.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id is required"));
                return;
            }
            try {
                addonStates.clear(addonId);
                audit.log(new UUID(0L, 0L), "API", "ADDON_STATE_CLEAR", "ADDON", addonId, Map.of());
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "operation", "CLEAR"));
                write(exchange, 202, addonStateJson(Map.of()));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                write(exchange, 200, addonStateJson(addonStates.listIsland(addonId, islandId)));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/set", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String key = JsonFields.text(body, "key", "");
            String value = JsonFields.text(body, "value", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || key.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.putIsland(addonId, islandId, key, value);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_SET", "ADDON", addonId, Map.of("islandId", islandId.toString(), "key", key));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "SET", "key", key));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/bulk", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.putIsland(addonId, islandId, stateValues);
                String operation = tables.isEmpty() ? "BULK_SET" : "BULK_SAVE";
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_" + operation, "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", operation, "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/save", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.putIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/table-key-value/bulk-save", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.putIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/table/key-value/bulk-save", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, String> values = JsonFields.object(body, "values");
            Map<String, Map<String, String>> tables = JsonFields.objectMap(body, "tables");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                Map<String, String> stateValues = new java.util.HashMap<>(values);
                tables.forEach((table, tableValues) -> stateValues.putAll(tableStateValues(table, tableValues)));
                int tableKeys = tableKeyCount(tables);
                Map<String, String> state = addonStates.putIsland(addonId, islandId, stateValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_TABLE_KEY_VALUE_BULK_SAVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_KEY_VALUE_BULK_SAVE", "keys", Integer.toString(stateValues.size()), "valueKeys", Integer.toString(values.size()), "tableKeys", Integer.toString(tableKeys), "tables", Integer.toString(tables.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/table/bulk", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || table.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                Map<String, String> tableValues = tableStateValues(safeTable, values);
                Map<String, String> state = addonStates.putIsland(addonId, islandId, tableValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_TABLE_STATE_BULK_SET", "ADDON", addonId, Map.of("islandId", islandId.toString(), "table", safeTable, "keys", Integer.toString(tableValues.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_BULK_SET", "table", safeTable, "keys", Integer.toString(tableValues.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/table/replace", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            Map<String, String> values = JsonFields.object(body, "values");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || table.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> tableValues = tableStateValues(safeTable, values);
                Map<String, String> state = addonStates.replaceIslandPrefix(addonId, islandId, prefix, tableValues);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_TABLE_STATE_REPLACE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "table", safeTable, "keys", Integer.toString(tableValues.size())));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_REPLACE", "table", safeTable, "keys", Integer.toString(tableValues.size())));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/table/clear", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String table = JsonFields.text(body, "table", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || table.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and table are required"));
                return;
            }
            try {
                String safeTable = safeTableName(table);
                String prefix = tableStatePrefix(safeTable);
                Map<String, String> updated = addonStates.removeIslandPrefix(addonId, islandId, prefix);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_TABLE_STATE_CLEAR", "ADDON", addonId, Map.of("islandId", islandId.toString(), "table", safeTable));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "TABLE_CLEAR", "table", safeTable));
                write(exchange, 202, addonStateJson(updated));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/remove", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String key = JsonFields.text(body, "key", "");
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L)) || key.isBlank()) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id, island id, and key are required"));
                return;
            }
            try {
                Map<String, String> state = addonStates.removeIsland(addonId, islandId, key);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_REMOVE", "ADDON", addonId, Map.of("islandId", islandId.toString(), "key", key));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "REMOVE", "key", key));
                write(exchange, 202, addonStateJson(state));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/addons/islands/state/clear", exchange -> {
            String body = readBody(exchange);
            String addonId = JsonFields.text(body, "addonId", "");
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            if (addonId.isBlank() || islandId.equals(new UUID(0L, 0L))) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", "Addon id and island id are required"));
                return;
            }
            try {
                addonStates.clearIsland(addonId, islandId);
                audit.log(new UUID(0L, 0L), "API", "ADDON_ISLAND_STATE_CLEAR", "ADDON", addonId, Map.of("islandId", islandId.toString()));
                events.publish(CloudIslandEventType.ADDON_STATE_CHANGED.name(), Map.of("addonId", addonId, "islandId", islandId.toString(), "operation", "CLEAR"));
                write(exchange, 202, addonStateJson(Map.of()));
            } catch (IllegalArgumentException exception) {
                write(exchange, 400, ApiResponses.error("INVALID_ADDON_STATE", exception.getMessage()));
            }
        });
        route("/v1/rankings/level", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, rankingsJson(rankingRepository.topByLevel(queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 10), 1, 100))));
        });
        route("/v1/rankings/worth", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, rankingsJson(rankingRepository.topByWorth(queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 10), 1, 100))));
        });
        route("/v1/upgrades/rules", exchange -> write(exchange, 200, upgradeRulesJson(upgradePolicy.list())));
        route("/v1/admin/block-values/list", exchange -> write(exchange, 200, blockValuesJson(levelRepository.blockValues())));
        route("/v1/islands/missions", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, missionsJson(missionRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "kind", "MISSION"))));
        });
        route("/v1/islands/missions/complete", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String missionKey = JsonFields.text(body, "missionKey", "");
            String kind = JsonFields.text(body, "kind", "MISSION");
            if (!requireMember(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> completed = missionRepository.complete(islandId, actorUuid, missionKey, kind);
            completed.ifPresent(snapshot -> {
                audit.log(actorUuid, "PLAYER", "ISLAND_MISSION_COMPLETE", "ISLAND", islandId.toString(), Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
                islandLogs.append(islandId, actorUuid, "ISLAND_MISSION_COMPLETE", Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind(), "reward", snapshot.reward()));
                events.publish(CloudIslandEventType.ISLAND_MISSION_COMPLETED.name(), Map.of("islandId", islandId.toString(), "missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
            });
            write(exchange, completed.isPresent() ? 202 : 404, completed.map(CloudIslandsCoreApplication::missionJson).orElseGet(() -> ApiResponses.error("MISSION_NOT_FOUND", "Mission was not found")));
        });
        route("/v1/islands/missions/progress", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String missionKey = JsonFields.text(body, "missionKey", "");
            String kind = JsonFields.text(body, "kind", "MISSION");
            long amount = Math.max(0L, JsonFields.longValue(body, "amount", 1L));
            if (!requireMember(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> progressed = missionRepository.progress(islandId, actorUuid, missionKey, kind, amount);
            progressed.ifPresent(snapshot -> events.publish(CloudIslandEventType.ISLAND_MISSION_PROGRESS.name(), Map.of(
                "islandId", islandId.toString(),
                "missionKey", snapshot.missionKey(),
                "kind", snapshot.kind(),
                "progress", Long.toString(snapshot.progress()),
                "goal", Long.toString(snapshot.goal()),
                "amount", Long.toString(amount),
                "completed", Boolean.toString(snapshot.completed())
            )));
            progressed.filter(kr.lunaf.cloudislands.api.model.IslandMissionSnapshot::completed).ifPresent(snapshot -> {
                audit.log(actorUuid, "PLAYER", "ISLAND_MISSION_COMPLETE", "ISLAND", islandId.toString(), Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
                islandLogs.append(islandId, actorUuid, "ISLAND_MISSION_COMPLETE", Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind(), "reward", snapshot.reward()));
                events.publish(CloudIslandEventType.ISLAND_MISSION_COMPLETED.name(), Map.of("islandId", islandId.toString(), "missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
            });
            write(exchange, progressed.isPresent() ? 202 : 404, progressed.map(CloudIslandsCoreApplication::missionJson).orElseGet(() -> ApiResponses.error("MISSION_NOT_FOUND", "Mission was not found")));
        });
        route("/v1/islands/limits", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, limitsJson(limitRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/limits/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String limitKey = JsonFields.text(body, "limitKey", "HOPPER");
            long value = JsonFields.longValue(body, "value", 0L);
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_UPGRADES)) {
                return;
            }
            kr.lunaf.cloudislands.api.model.IslandLimitSnapshot snapshot = limitRepository.set(islandId, limitKey, value, actorUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_LIMIT_SET", "ISLAND", islandId.toString(), Map.of("limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
            islandLogs.append(islandId, actorUuid, "ISLAND_LIMIT_SET", Map.of("limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
            events.publish(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name(), Map.of("islandId", islandId.toString(), "limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
            write(exchange, 202, limitJson(snapshot));
        });
        route("/v1/islands/info", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID ownerUuid = JsonFields.uuid(body, "ownerUuid", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "");
            java.util.Optional<IslandSnapshot> island = islandId.equals(new UUID(0L, 0L))
                ? ownerUuid.equals(new UUID(0L, 0L)) ? islandRepository.findByName(name) : islandRepository.findByOwner(ownerUuid)
                : islandRepository.findById(islandId);
            write(exchange, island.isPresent() ? 200 : 404, island.map(CloudIslandsCoreApplication::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
        });
        routePrefix("/v1/islands/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String tail = path.substring("/v1/islands/".length());
            if (method.equalsIgnoreCase("GET") && tail.startsWith("by-owner/")) {
                UUID ownerUuid = uuidPath(tail.substring("by-owner/".length()));
                java.util.Optional<IslandSnapshot> island = islandRepository.findByOwner(ownerUuid);
                write(exchange, island.isPresent() ? 200 : 404, island.map(CloudIslandsCoreApplication::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/members")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/members".length()));
                write(exchange, 200, membersJson(metadataRepository.members(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/runtime")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/runtime".length()));
                java.util.Optional<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> runtime = runtimeRepository.find(islandId);
                write(exchange, runtime.isPresent() ? 200 : 404, runtime.map(CloudIslandsCoreApplication::runtimeJson).orElseGet(() -> ApiResponses.error("ISLAND_RUNTIME_NOT_FOUND", "Island runtime was not found")));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/flags")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/flags".length()));
                write(exchange, 200, flagsJson(metadataRepository.flags(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/permissions")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/permissions".length()));
                write(exchange, 200, permissionsJson(permissionRules.list(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/roles")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/roles".length()));
                write(exchange, 200, rolesJson(roleRepository.list(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/bans")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/bans".length()));
                write(exchange, 200, bansJson(metadataRepository.bans(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/biome")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/biome".length()));
                write(exchange, 200, biomeJson(metadataRepository.biome(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/homes")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/homes".length()));
                write(exchange, 200, homesJson(metadataRepository.homes(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/warps")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/warps".length()));
                write(exchange, 200, warpsJson(metadataRepository.warps(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/limits")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/limits".length()));
                write(exchange, 200, limitsJson(limitRepository.list(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/upgrades")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/upgrades".length()));
                write(exchange, 200, upgradesJson(upgradeRepository.list(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/bank")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/bank".length()));
                write(exchange, 200, bankJson(bankRepository.balance(islandId)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/missions")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/missions".length()));
                write(exchange, 200, missionsJson(missionRepository.list(islandId, "MISSION")));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/snapshots")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/snapshots".length()));
                write(exchange, 200, snapshotsJson(snapshotRepository.list(islandId, 20)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/logs")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/logs".length()));
                write(exchange, 200, islandLogsJson(islandLogs.list(islandId, 30)));
                return;
            }
            if (method.equalsIgnoreCase("GET") && !tail.contains("/")) {
                UUID islandId = uuidPath(tail);
                java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
                write(exchange, island.isPresent() ? 200 : 404, island.map(CloudIslandsCoreApplication::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
                return;
            }
            if (method.equalsIgnoreCase("DELETE") && !tail.contains("/")) {
                UUID islandId = uuidPath(tail);
                java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
                UUID requesterUuid = queryUuid(exchange, "requesterUuid", island.map(IslandSnapshot::ownerUuid).orElse(new UUID(0L, 0L)));
                boolean deleted = island.isPresent() && requestIslandDelete(islandId, requesterUuid, island.get().ownerUuid(), "api-delete");
                audit.log(new UUID(0L, 0L), "API", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of("deleted", Boolean.toString(deleted)));
                write(exchange, deleted ? 202 : 404, deleted ? ApiResponses.ok(true) : ApiResponses.error("ISLAND_NOT_DELETED", "Island was not found or could not be deleted"));
                return;
            }
            write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
        });
        routePrefix("/v1/players/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String tail = path.substring("/v1/players/".length());
            if (method.equalsIgnoreCase("GET") && !tail.contains("/")) {
                UUID playerUuid = uuidPath(tail);
                java.util.Optional<kr.lunaf.cloudislands.api.model.PlayerIslandProfile> profile = java.util.Optional.of(playerProfiles.find(playerUuid));
                write(exchange, profile.isPresent() ? 200 : 404, profile.map(CloudIslandsCoreApplication::playerProfileJson).orElseGet(() -> ApiResponses.error("PLAYER_NOT_FOUND", "Player was not found")));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/profile")) {
                UUID playerUuid = uuidPath(tail.substring(0, tail.length() - "/profile".length()));
                java.util.Optional<kr.lunaf.cloudislands.api.model.PlayerIslandProfile> profile = java.util.Optional.of(playerProfiles.find(playerUuid));
                write(exchange, profile.isPresent() ? 200 : 404, profile.map(CloudIslandsCoreApplication::playerProfileJson).orElseGet(() -> ApiResponses.error("PLAYER_NOT_FOUND", "Player was not found")));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/island")) {
                UUID playerUuid = uuidPath(tail.substring(0, tail.length() - "/island".length()));
                java.util.Optional<IslandSnapshot> island = islandRepository.findByOwner(playerUuid);
                write(exchange, island.isPresent() ? 200 : 404, island.map(CloudIslandsCoreApplication::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
                return;
            }
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/islands")) {
                UUID playerUuid = uuidPath(tail.substring(0, tail.length() - "/islands".length()));
                java.util.ArrayList<IslandSnapshot> islands = new java.util.ArrayList<>();
                for (kr.lunaf.cloudislands.api.model.IslandMemberSnapshot member : metadataRepository.islandsForMember(playerUuid)) {
                    islandRepository.findById(member.islandId()).ifPresent(islands::add);
                }
                write(exchange, 200, islandsJson(islands));
                return;
            }
            write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
        });
        route("/v1/islands/permissions", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, permissionsJson(permissionRules.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/permissions/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            IslandRole role = JsonFields.enumValue(IslandRole.class, body, "role", IslandRole.MEMBER);
            IslandPermission permission = JsonFields.enumValue(IslandPermission.class, body, "permission", IslandPermission.BUILD);
            boolean allowed = JsonFields.bool(body, "allowed", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
                return;
            }
            permissionRules.put(islandId, role, permission, allowed);
            audit.log(actorUuid, "PLAYER", "ISLAND_PERMISSION_SET", "ISLAND", islandId.toString(), Map.of("role", role.name(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
            islandLogs.append(islandId, actorUuid, "ISLAND_PERMISSION_SET", Map.of("role", role.name(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
            events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", role.name(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/roles", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, rolesJson(roleRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/roles/upsert", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            IslandRole role = JsonFields.enumValue(IslandRole.class, body, "role", IslandRole.CUSTOM_1);
            if (role == IslandRole.OWNER || !role.islandMemberRole()) {
                write(exchange, 409, ApiResponses.error("ROLE_NOT_EDITABLE", "Only island member roles can be customized"));
                return;
            }
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
                return;
            }
            int weight = JsonFields.integer(body, "weight", role.ordinal());
            String displayName = JsonFields.text(body, "displayName", role.name());
            kr.lunaf.cloudislands.api.model.IslandRoleSnapshot snapshot = roleRepository.upsert(islandId, role, weight, displayName);
            audit.log(actorUuid, "PLAYER", "ISLAND_ROLE_UPSERT", "ISLAND", islandId.toString(), Map.of("role", role.name(), "weight", Integer.toString(weight), "displayName", displayName));
            islandLogs.append(islandId, actorUuid, "ISLAND_ROLE_UPSERT", Map.of("role", role.name(), "weight", Integer.toString(weight), "displayName", displayName));
            events.publish(CloudIslandEventType.ISLAND_ROLE_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", role.name(), "operation", "ROLE_UPSERT"));
            write(exchange, 202, roleJson(snapshot));
        });
        route("/v1/islands/roles/reset", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            IslandRole role = JsonFields.enumValue(IslandRole.class, body, "role", IslandRole.CUSTOM_1);
            if (role == IslandRole.OWNER || !role.islandMemberRole()) {
                write(exchange, 409, ApiResponses.error("ROLE_NOT_EDITABLE", "Only island member roles can be reset"));
                return;
            }
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
                return;
            }
            boolean removed = roleRepository.reset(islandId, role);
            audit.log(actorUuid, "PLAYER", "ISLAND_ROLE_RESET", "ISLAND", islandId.toString(), Map.of("role", role.name(), "removed", Boolean.toString(removed)));
            islandLogs.append(islandId, actorUuid, "ISLAND_ROLE_RESET", Map.of("role", role.name(), "removed", Boolean.toString(removed)));
            events.publish(CloudIslandEventType.ISLAND_ROLE_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", role.name(), "operation", "ROLE_RESET"));
            write(exchange, 202, "{\"accepted\":true,\"code\":\"ROLE_RESET\",\"role\":\"" + role.name() + "\",\"removed\":" + removed + "}");
        });
        route("/v1/jobs/claim", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            java.util.List<kr.lunaf.cloudislands.protocol.job.IslandJob> claimed = jobs.claim(nodeId, supportedJobTypes(JsonFields.text(body, "supportedTypes", "")), JsonFields.integer(body, "maxJobs", 4));
            write(exchange, 200, kr.lunaf.cloudislands.protocol.job.json.IslandJobJson.writeArray(claimed));
        });
        route("/v1/jobs/complete", exchange -> {
            String body = readBody(exchange);
            UUID jobId = JsonFields.uuid(body, "jobId", new UUID(0L, 0L));
            String nodeId = JsonFields.text(body, "nodeId", "");
            java.util.Map<String, String> payload = JsonFields.object(body, "payload");
            java.util.Optional<kr.lunaf.cloudislands.protocol.job.IslandJob> claimed = jobs.findClaimed(jobId)
                .map(job -> new kr.lunaf.cloudislands.protocol.job.IslandJob(job.jobId(), job.type(), job.islandId(), job.targetNode(), job.priority(), java.util.Map.copyOf(new java.util.HashMap<String, String>() {{ putAll(job.payload()); putAll(payload); }}), job.createdAt()));
            if (claimed.isEmpty() || !jobs.complete(nodeId, jobId)) {
                write(exchange, 409, ApiResponses.error("JOB_CLAIM_MISMATCH", "Job is not claimed by this node"));
                return;
            }
            claimed.ifPresent(jobCompletion::completed);
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/jobs/fail", exchange -> {
            String body = readBody(exchange);
            UUID jobId = JsonFields.uuid(body, "jobId", new UUID(0L, 0L));
            String nodeId = JsonFields.text(body, "nodeId", "");
            String error = JsonFields.text(body, "error", "unknown");
            java.util.Optional<kr.lunaf.cloudislands.protocol.job.IslandJob> claimed = jobs.findClaimed(jobId);
            if (claimed.isEmpty() || !jobs.fail(nodeId, jobId, error)) {
                write(exchange, 409, ApiResponses.error("JOB_CLAIM_MISMATCH", "Job is not claimed by this node"));
                return;
            }
            claimed.ifPresent(job -> jobCompletion.failed(job, error));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/jobs/recover", exchange -> {
            String body = readBody(exchange);
            if (jobs instanceof kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue redisJobs) {
                String recovered = redisJobs.recoverPending(
                    JsonFields.text(body, "nodeId", "recovery"),
                    JsonFields.longValue(body, "minIdleMillis", 60000L),
                    JsonFields.integer(body, "maxJobs", 16)
                );
                write(exchange, 202, "{\"recovered\":\"" + recovered.replace("\"", "'") + "\"}");
            } else if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
                String recovered = jdbcJobs.recoverPending(
                    JsonFields.text(body, "nodeId", "recovery"),
                    JsonFields.longValue(body, "minIdleMillis", 60000L),
                    JsonFields.integer(body, "maxJobs", 16)
                );
                write(exchange, 202, "{\"recovered\":" + recovered + "}");
            } else {
                write(exchange, 409, ApiResponses.error("RECOVERY_UNAVAILABLE", "Job recovery is only available when CI_JOB_QUEUE_MODE=REDIS or JDBC"));
            }
        });
        route("/v1/admin/jobs/recover", exchange -> {
            String body = readBody(exchange);
            if (jobs instanceof kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue redisJobs) {
                String recovered = redisJobs.recoverPending(
                    JsonFields.text(body, "nodeId", "recovery"),
                    JsonFields.longValue(body, "minIdleMillis", 60000L),
                    JsonFields.integer(body, "maxJobs", 16)
                );
                write(exchange, 202, "{\"recovered\":\"" + recovered.replace("\"", "'") + "\"}");
            } else if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
                String recovered = jdbcJobs.recoverPending(
                    JsonFields.text(body, "nodeId", "recovery"),
                    JsonFields.longValue(body, "minIdleMillis", 60000L),
                    JsonFields.integer(body, "maxJobs", 16)
                );
                write(exchange, 202, "{\"recovered\":" + recovered + "}");
            } else {
                write(exchange, 409, ApiResponses.error("RECOVERY_UNAVAILABLE", "Job recovery is only available when CI_JOB_QUEUE_MODE=REDIS or JDBC"));
            }
        });
        route("/v1/admin/jobs/list", exchange -> write(exchange, 200, jobsJson(jobs)));
        route("/v1/admin/jobs/retry", exchange -> {
            String body = readBody(exchange);
            UUID jobId = JsonFields.uuid(body, "jobId", new UUID(0L, 0L));
            boolean retried = jobs.retry(jobId);
            audit.log(new UUID(0L, 0L), "ADMIN", "JOB_RETRY", "JOB", jobId.toString(), Map.of("retried", Boolean.toString(retried)));
            write(exchange, retried ? 202 : 404, retried ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_RETRIED", "Job was not found or cannot be retried"));
        });
        route("/v1/admin/jobs/cancel", exchange -> {
            String body = readBody(exchange);
            UUID jobId = JsonFields.uuid(body, "jobId", new UUID(0L, 0L));
            boolean canceled = jobs.cancel(jobId);
            audit.log(new UUID(0L, 0L), "ADMIN", "JOB_CANCEL", "JOB", jobId.toString(), Map.of("canceled", Boolean.toString(canceled)));
            write(exchange, canceled ? 202 : 404, canceled ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_CANCELED", "Job was not found or cannot be canceled"));
        });
        route("/v1/routes/home", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareHomeRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.text(body, "homeName", "default")));
        });
        route("/v1/routes/visit", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String islandName = JsonFields.text(body, "islandName", "");
            if (!islandName.isBlank()) {
                routeResult(exchange, routing.prepareVisitRouteByName(playerUuid, islandName));
                return;
            }
            UUID ownerUuid = JsonFields.uuid(body, "ownerUuid", new UUID(0L, 0L));
            if (!ownerUuid.equals(new UUID(0L, 0L))) {
                routeResult(exchange, routing.prepareVisitRouteByOwner(playerUuid, ownerUuid));
                return;
            }
            routeResult(exchange, routing.prepareVisitRoute(playerUuid, JsonFields.uuid(body, "islandId", new UUID(0L, 0L))));
        });
        route("/v1/routes/random", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareRandomVisitRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L))));
        });
        route("/v1/routes/warp", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareWarpRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "warpName", "default")));
        });
        route("/v1/routes/migration-return", exchange -> {
            String body = readBody(exchange);
            java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("localX", Double.toString(JsonFields.decimal(body, "localX", 0.5D)));
            payload.put("localY", Double.toString(JsonFields.decimal(body, "localY", 100.0D)));
            payload.put("localZ", Double.toString(JsonFields.decimal(body, "localZ", 0.5D)));
            payload.put("yaw", Float.toString((float) JsonFields.decimal(body, "yaw", 180.0D)));
            payload.put("pitch", Float.toString((float) JsonFields.decimal(body, "pitch", 0.0D)));
            routeResult(exchange, routing.prepareMigrationReturnRoute(
                JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)),
                JsonFields.uuid(body, "islandId", new UUID(0L, 0L)),
                JsonFields.text(body, "targetNode", ""),
                payload
            ));
        });
        route("/v1/routes/session", exchange -> {
            String body = readBody(exchange);
            UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String targetNode = JsonFields.text(body, "targetNode", "");
            String nonce = JsonFields.text(body, "nonce", "");
            RouteTicket ticket = tickets.find(ticketId).orElse(null);
            if (ticket == null) {
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                    "ticketId", ticketId.toString(),
                    "playerUuid", playerUuid.toString(),
                    "targetNode", targetNode,
                    "reason", "SESSION_TICKET_NOT_FOUND"
                ));
                write(exchange, 404, ApiResponses.error("TICKET_NOT_FOUND", "Route ticket was not found"));
                return;
            }
            if (ticket.state() != kr.lunaf.cloudislands.api.model.RouteTicketState.READY) {
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                    "ticketId", ticket.ticketId().toString(),
                    "playerUuid", ticket.playerUuid().toString(),
                    "islandId", ticket.islandId().toString(),
                    "action", ticket.action().name(),
                    "targetNode", ticket.targetNode(),
                    "reason", "SESSION_TICKET_NOT_READY"
                ));
                write(exchange, 409, ApiResponses.error("TICKET_NOT_READY", "Route ticket is not ready"));
                return;
            }
            if (ticket.expiresAt().isBefore(java.time.Instant.now())) {
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                    "ticketId", ticket.ticketId().toString(),
                    "playerUuid", ticket.playerUuid().toString(),
                    "islandId", ticket.islandId().toString(),
                    "action", ticket.action().name(),
                    "targetNode", ticket.targetNode(),
                    "reason", "SESSION_TICKET_EXPIRED"
                ));
                write(exchange, 409, ApiResponses.error("TICKET_EXPIRED", "Route ticket has expired"));
                return;
            }
            if (!ticket.playerUuid().equals(playerUuid) || !ticket.targetNode().equals(targetNode) || !ticket.nonce().equals(nonce)) {
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                    "ticketId", ticket.ticketId().toString(),
                    "playerUuid", playerUuid.toString(),
                    "islandId", ticket.islandId().toString(),
                    "action", ticket.action().name(),
                    "targetNode", targetNode,
                    "reason", "SESSION_TICKET_MISMATCH"
                ));
                write(exchange, 403, ApiResponses.error("TICKET_MISMATCH", "Route ticket fields do not match"));
                return;
            }
            sessions.put(ticket);
            audit.log(ticket.playerUuid(), "PLAYER", "ROUTE_SESSION_PUBLISH", "ROUTE", ticket.ticketId().toString(), Map.of(
                "islandId", ticket.islandId().toString(),
                "action", ticket.action().name(),
                "targetNode", ticket.targetNode(),
                "targetServerName", ticket.payload().getOrDefault("targetServerName", ticket.targetNode())
            ));
            events.publish(CloudIslandEventType.ROUTE_SESSION_PUBLISHED.name(), Map.of(
                "ticketId", ticket.ticketId().toString(),
                "playerUuid", ticket.playerUuid().toString(),
                "islandId", ticket.islandId().toString(),
                "action", ticket.action().name(),
                "targetNode", ticket.targetNode()
            ));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/routes/session/find", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String nodeId = JsonFields.text(body, "nodeId", "");
            PlayerRouteSession session = sessions.find(playerUuid, nodeId).orElse(null);
            if (session == null) {
                PlayerRouteSession existing = sessions.findAny(playerUuid).orElse(null);
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), existing == null
                    ? Map.of(
                        "playerUuid", playerUuid.toString(),
                        "targetNode", nodeId,
                        "reason", "SESSION_LOOKUP_NOT_FOUND"
                    )
                    : Map.of(
                        "playerUuid", playerUuid.toString(),
                        "ticketId", existing.ticketId().toString(),
                        "targetNode", existing.targetNode(),
                        "requestedNode", nodeId,
                        "reason", "SESSION_LOOKUP_NODE_MISMATCH"
                    ));
            }
            write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
        });
        route("/v1/routes/session/find-any", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            PlayerRouteSession session = sessions.findAny(playerUuid).orElse(null);
            write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
        });
        route("/v1/routes/session/consume", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String nodeId = JsonFields.text(body, "nodeId", "");
            boolean reportMissing = JsonFields.bool(body, "reportMissing", true);
            PlayerRouteSession session = sessions.consume(playerUuid, nodeId).orElse(null);
            if (session != null) {
                audit.log(session.playerUuid(), "PLAYER", "ROUTE_SESSION_CONSUME", "ROUTE", session.ticketId().toString(), Map.of(
                    "targetNode", session.targetNode(),
                    "targetServerName", session.targetServerName()
                ));
            }
            if (session == null && reportMissing) {
                PlayerRouteSession existing = sessions.findAny(playerUuid).orElse(null);
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), existing == null
                    ? Map.of(
                        "playerUuid", playerUuid.toString(),
                        "targetNode", nodeId,
                        "reason", "SESSION_NOT_FOUND"
                    )
                    : Map.of(
                        "playerUuid", playerUuid.toString(),
                        "ticketId", existing.ticketId().toString(),
                        "targetNode", existing.targetNode(),
                        "requestedNode", nodeId,
                        "reason", "SESSION_NODE_MISMATCH"
                    ));
                audit.log(playerUuid, "PLAYER", "ROUTE_SESSION_CONSUME_FAILED", "ROUTE", existing == null ? "" : existing.ticketId().toString(), existing == null
                    ? Map.of("targetNode", nodeId, "reason", "SESSION_NOT_FOUND")
                    : Map.of("targetNode", existing.targetNode(), "requestedNode", nodeId, "reason", "SESSION_NODE_MISMATCH"));
            }
            write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
        });
        route("/v1/routes/ticket-status", exchange -> {
            String body = readBody(exchange);
            UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String nonce = JsonFields.text(body, "nonce", "");
            RouteTicket ticket = tickets.find(ticketId).orElse(null);
            boolean allowed = ticket != null && ticket.playerUuid().equals(playerUuid) && ticket.nonce().equals(nonce);
            if (!allowed) {
                events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                    "ticketId", ticketId.toString(),
                    "playerUuid", playerUuid.toString(),
                    "islandId", ticket == null ? "" : ticket.islandId().toString(),
                    "action", ticket == null ? "" : ticket.action().name(),
                    "targetNode", ticket == null ? "" : ticket.targetNode(),
                    "reason", "VERIFY_FAILED"
                ));
            }
            write(exchange, allowed ? 200 : 404, allowed ? RoutingOrchestrator.toJson(ticket) : ApiResponses.error("ROUTE_TICKET_NOT_FOUND", "Route ticket was not found"));
        });
        route("/v1/routes/consume", exchange -> write(exchange, 200, routing.consumeTicketJson(readBody(exchange))));
        route("/v1/admin/routes/debug", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            if (playerUuid.equals(new UUID(0L, 0L))) {
                write(exchange, 200, "{\"sessions\":" + maskRouteNonces(routeSessionsJson(sessions)) + ",\"tickets\":" + maskRouteNonces(tickets.toJson()) + "}");
            } else {
                PlayerRouteSession session = findAnyRouteSession(sessions, playerUuid).orElse(null);
                RouteTicket ticket = tickets.findLatestForPlayer(playerUuid).orElse(null);
                boolean found = session != null || ticket != null;
                write(exchange, found ? 200 : 404, found ? routeDebugJson(playerUuid, session, ticket) : ApiResponses.error("ROUTE_ROUTE_NOT_FOUND", "Route session or ticket was not found"));
            }
        });
        route("/v1/admin/routes/ticket", exchange -> {
            String body = readBody(exchange);
            UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            RouteTicket ticket = ticketId.equals(new UUID(0L, 0L))
                ? tickets.findLatestForPlayer(playerUuid).orElse(null)
                : tickets.find(ticketId).orElse(null);
            write(exchange, ticket == null ? 404 : 200, ticket == null ? ApiResponses.error("ROUTE_TICKET_NOT_FOUND", "Route ticket was not found") : maskRouteNonces(RoutingOrchestrator.toJson(ticket)));
        });
        route("/v1/admin/routes/clear", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
            String reason = JsonFields.text(body, "reason", "MANUAL_CLEAR");
            boolean clearedSession = !playerUuid.equals(new UUID(0L, 0L)) && sessions.clear(playerUuid);
            UUID clearTicketId = ticketId.equals(new UUID(0L, 0L)) && !playerUuid.equals(new UUID(0L, 0L))
                ? tickets.findLatestForPlayer(playerUuid).map(RouteTicket::ticketId).orElse(new UUID(0L, 0L))
                : ticketId;
            boolean clearedTicket = !clearTicketId.equals(new UUID(0L, 0L)) && tickets.clear(clearTicketId);
            String clearReason = reason == null || reason.isBlank() ? "MANUAL_CLEAR" : reason;
            audit.log(new UUID(0L, 0L), "MANUAL_CLEAR".equals(clearReason) ? "ADMIN" : "SYSTEM", "ROUTE_CLEAR", "ROUTE", playerUuid.toString(), Map.of(
                "ticketId", clearTicketId.toString(),
                "reason", clearReason,
                "clearedSession", Boolean.toString(clearedSession),
                "clearedTicket", Boolean.toString(clearedTicket)
            ));
            events.publish(CloudIslandEventType.ROUTE_TICKET_CLEARED.name(), Map.of(
                "playerUuid", playerUuid.toString(),
                "ticketId", clearTicketId.toString(),
                "reason", clearReason,
                "clearedSession", Boolean.toString(clearedSession),
                "clearedTicket", Boolean.toString(clearedTicket)
            ));
            write(exchange, 202, "{\"clearedSession\":" + clearedSession + ",\"clearedTicket\":" + clearedTicket + ",\"reason\":\"" + escape(clearReason) + "\"}");
        });
        route("/v1/admin/cache/clear", exchange -> {
            int clearedSessions = sessions.clearAll();
            int clearedTickets = tickets.clearAll();
            int clearedRedisKeys = redisCacheAdmin == null ? 0 : redisCacheAdmin.clearApplicationCaches();
            audit.log(new UUID(0L, 0L), "ADMIN", "CACHE_CLEAR", "CORE", "application-cache", Map.of("sessions", Integer.toString(clearedSessions), "tickets", Integer.toString(clearedTickets), "redisKeys", Integer.toString(clearedRedisKeys)));
            events.publish(CloudIslandEventType.CORE_CACHE_CLEARED.name(), Map.of("scope", "application-cache", "sessions", Integer.toString(clearedSessions), "tickets", Integer.toString(clearedTickets), "redisKeys", Integer.toString(clearedRedisKeys)));
            write(exchange, 202, "{\"clearedSessions\":" + clearedSessions + ",\"clearedTickets\":" + clearedTickets + ",\"clearedRedisKeys\":" + clearedRedisKeys + "}");
        });
        route("/v1/admin/reload", exchange -> {
            int clearedSessions = sessions.clearAll();
            int clearedTickets = tickets.clearAll();
            int clearedRedisKeys = redisCacheAdmin == null ? 0 : redisCacheAdmin.clearApplicationCaches();
            audit.log(new UUID(0L, 0L), "ADMIN", "CORE_RELOAD", "CORE", "runtime", Map.of("clearedSessions", Integer.toString(clearedSessions), "clearedTickets", Integer.toString(clearedTickets), "clearedRedisKeys", Integer.toString(clearedRedisKeys)));
            events.publish(CloudIslandEventType.CORE_RELOADED.name(), Map.of("clearedSessions", Integer.toString(clearedSessions), "clearedTickets", Integer.toString(clearedTickets), "clearedRedisKeys", Integer.toString(clearedRedisKeys)));
            write(exchange, 202, "{\"reloaded\":true,\"clearedSessions\":" + clearedSessions + ",\"clearedTickets\":" + clearedTickets + ",\"clearedRedisKeys\":" + clearedRedisKeys + "}");
        });
        route("/v1/admin/migrations/superiorskyblock2/scan", exchange -> {
            if (!config.superiorSkyblock2MigrationEnabled()) {
                write(exchange, 403, migrationDisabledJson());
                return;
            }
            String body = readBody(exchange);
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_SCAN", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.scan(JsonFields.text(body, "path", "plugins/SuperiorSkyblock2")));
        });
        route("/v1/admin/migrations/superiorskyblock2/dryrun", exchange -> {
            if (!config.superiorSkyblock2MigrationEnabled()) {
                write(exchange, 403, migrationDisabledJson());
                return;
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_DRYRUN", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.dryRun());
        });
        route("/v1/admin/migrations/superiorskyblock2/extract", exchange -> {
            if (!config.superiorSkyblock2MigrationEnabled()) {
                write(exchange, 403, migrationDisabledJson());
                return;
            }
            String body = readBody(exchange);
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_EXTRACT", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.extractWorldBundles(JsonFields.text(body, "path", "")));
        });
        route("/v1/admin/migrations/superiorskyblock2/import", exchange -> {
            if (!config.superiorSkyblock2MigrationEnabled()) {
                write(exchange, 403, migrationDisabledJson());
                return;
            }
            String body = readBody(exchange);
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_IMPORT", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.importLastPlan(JsonFields.text(body, "approval", "")));
        });
        route("/v1/admin/migrations/superiorskyblock2/verify", exchange -> {
            if (!config.superiorSkyblock2MigrationEnabled()) {
                write(exchange, 403, migrationDisabledJson());
                return;
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_VERIFY", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.verify());
        });
        route("/v1/admin/migrations/superiorskyblock2/rollback", exchange -> {
            if (!config.superiorSkyblock2MigrationEnabled()) {
                write(exchange, 403, migrationDisabledJson());
                return;
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_ROLLBACK", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.rollbackLastImport());
        });
        route("/v1/admin/players/info", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, playerProfileJson(playerProfiles.find(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)))));
        });
        route("/v1/players/info", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String lastName = JsonFields.text(body, "lastName", "");
            java.util.Optional<kr.lunaf.cloudislands.api.model.PlayerIslandProfile> profile = playerUuid.equals(new UUID(0L, 0L))
                ? playerProfiles.findByLastName(lastName)
                : java.util.Optional.of(playerProfiles.find(playerUuid));
            write(exchange, profile.isPresent() ? 200 : 404, profile.map(CloudIslandsCoreApplication::playerProfileJson).orElseGet(() -> ApiResponses.error("PLAYER_NOT_FOUND", "Player was not found")));
        });
        route("/v1/players/touch", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String lastName = JsonFields.text(body, "lastName", "");
            write(exchange, 202, playerProfileJson(playerProfiles.touch(playerUuid, lastName)));
        });
        route("/v1/admin/players/setisland", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            audit.log(new UUID(0L, 0L), "ADMIN", "PLAYER_SET_ISLAND", "PLAYER", playerUuid.toString(), Map.of("islandId", islandId.toString()));
            write(exchange, 202, playerProfileJson(playerProfiles.setPrimaryIsland(playerUuid, islandId)));
        });
        route("/v1/admin/players/clearisland", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            audit.log(new UUID(0L, 0L), "ADMIN", "PLAYER_CLEAR_ISLAND", "PLAYER", playerUuid.toString(), Map.of());
            write(exchange, 202, playerProfileJson(playerProfiles.clearPrimaryIsland(playerUuid)));
        });
        route("/v1/admin/templates/list", exchange -> write(exchange, 200, templatesJson(templateRepository.list())));
        route("/v1/admin/templates/upsert", exchange -> {
            String body = readBody(exchange);
            String templateId = JsonFields.text(body, "templateId", JsonFields.text(body, "id", "default"));
            IslandTemplateSnapshot snapshot = templateRepository.upsert(
                templateId,
                JsonFields.text(body, "displayName", templateId),
                JsonFields.bool(body, "enabled", true),
                JsonFields.text(body, "minNodeVersion", "")
            );
            audit.log(new UUID(0L, 0L), "ADMIN", "TEMPLATE_UPSERT", "TEMPLATE", snapshot.id(), Map.of("enabled", Boolean.toString(snapshot.enabled()), "minNodeVersion", snapshot.minNodeVersion()));
            events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", snapshot.id(), "enabled", Boolean.toString(snapshot.enabled()), "minNodeVersion", snapshot.minNodeVersion(), "operation", "UPSERT"));
            write(exchange, 202, templateJson(snapshot));
        });
        route("/v1/admin/templates/enable", exchange -> {
            String body = readBody(exchange);
            String templateId = JsonFields.text(body, "templateId", JsonFields.text(body, "id", "default"));
            boolean changed = templateRepository.setEnabled(templateId, true);
            audit.log(new UUID(0L, 0L), "ADMIN", "TEMPLATE_ENABLE", "TEMPLATE", templateId, Map.of("changed", Boolean.toString(changed)));
            if (changed) {
                events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", templateId, "enabled", Boolean.toString(true), "operation", "ENABLE"));
            }
            write(exchange, changed ? 202 : 404, changed ? templateRepository.find(templateId).map(CloudIslandsCoreApplication::templateJson).orElseGet(() -> ApiResponses.ok(true)) : ApiResponses.error("TEMPLATE_NOT_FOUND", "Island template was not found"));
        });
        route("/v1/admin/templates/disable", exchange -> {
            String body = readBody(exchange);
            String templateId = JsonFields.text(body, "templateId", JsonFields.text(body, "id", "default"));
            boolean changed = templateRepository.setEnabled(templateId, false);
            audit.log(new UUID(0L, 0L), "ADMIN", "TEMPLATE_DISABLE", "TEMPLATE", templateId, Map.of("changed", Boolean.toString(changed)));
            if (changed) {
                events.publish(CloudIslandEventType.ISLAND_TEMPLATE_CHANGED.name(), Map.of("templateId", templateId, "enabled", Boolean.toString(false), "operation", "DISABLE"));
            }
            write(exchange, changed ? 202 : 404, changed ? templateRepository.find(templateId).map(CloudIslandsCoreApplication::templateJson).orElseGet(() -> ApiResponses.ok(true)) : ApiResponses.error("TEMPLATE_NOT_FOUND", "Island template was not found"));
        });
        route("/v1/nodes/heartbeat", exchange -> {
            NodeHeartbeatRequest heartbeat = heartbeat(readBody(exchange));
            if (heartbeat.protocolVersion() < MIN_NODE_PROTOCOL_VERSION || heartbeat.protocolVersion() > MAX_NODE_PROTOCOL_VERSION) {
                write(exchange, 426, ApiResponses.error("PROTOCOL_VERSION_UNSUPPORTED", "Node protocol version is not supported"));
                return;
            }
            nodes.heartbeat(heartbeat);
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/admin/nodes/drain", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            boolean changed = nodes.drain(nodeId);
            if (changed) {
                events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "DRAINING"));
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_DRAIN", "NODE", nodeId, Map.of());
            write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
        });
        route("/v1/admin/nodes/undrain", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            boolean changed = nodes.undrain(nodeId);
            if (changed) {
                events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "READY"));
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_UNDRAIN", "NODE", nodeId, Map.of());
            write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
        });
        route("/v1/admin/nodes/kickall", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            String reason = JsonFields.text(body, "reason", "admin-request");
            boolean found = nodes.find(nodeId).isPresent();
            if (found) {
                events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "KICKALL", "reason", reason));
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_KICKALL", "NODE", nodeId, Map.of("reason", reason));
            write(exchange, found ? 202 : 404, found ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
        });
        route("/v1/admin/nodes/shutdown-safe", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            String reason = JsonFields.text(body, "reason", "admin-request");
            boolean changed = nodes.shutdownSafe(nodeId);
            if (changed) {
                events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "SHUTTING_DOWN", "operation", "SHUTDOWN_SAFE", "reason", reason));
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_SHUTDOWN_SAFE", "NODE", nodeId, Map.of("reason", reason));
            write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
        });
        routePrefix("/v1/admin/nodes/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String tail = path.substring("/v1/admin/nodes/".length());
            if (!method.equalsIgnoreCase("POST")) {
                write(exchange, 405, ApiResponses.error("METHOD_NOT_ALLOWED", "Use POST for admin node lifecycle operations"));
                return;
            }
            if (tail.endsWith("/drain")) {
                String nodeId = tail.substring(0, tail.length() - "/drain".length());
                boolean changed = nodes.drain(nodeId);
                if (changed) {
                    events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "DRAINING"));
                }
                audit.log(new UUID(0L, 0L), "ADMIN", "NODE_DRAIN", "NODE", nodeId, Map.of());
                write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
                return;
            }
            if (tail.endsWith("/undrain")) {
                String nodeId = tail.substring(0, tail.length() - "/undrain".length());
                boolean changed = nodes.undrain(nodeId);
                if (changed) {
                    events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "READY"));
                }
                audit.log(new UUID(0L, 0L), "ADMIN", "NODE_UNDRAIN", "NODE", nodeId, Map.of());
                write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
                return;
            }
            if (tail.endsWith("/kickall")) {
                String nodeId = tail.substring(0, tail.length() - "/kickall".length());
                boolean found = nodes.find(nodeId).isPresent();
                if (found) {
                    events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "KICKALL", "reason", "admin-request"));
                }
                audit.log(new UUID(0L, 0L), "ADMIN", "NODE_KICKALL", "NODE", nodeId, Map.of("reason", "admin-request"));
                write(exchange, found ? 202 : 404, found ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
                return;
            }
            if (tail.endsWith("/shutdown-safe")) {
                String nodeId = tail.substring(0, tail.length() - "/shutdown-safe".length());
                boolean changed = nodes.shutdownSafe(nodeId);
                if (changed) {
                    events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "SHUTTING_DOWN", "operation", "SHUTDOWN_SAFE", "reason", "admin-request"));
                }
                audit.log(new UUID(0L, 0L), "ADMIN", "NODE_SHUTDOWN_SAFE", "NODE", nodeId, Map.of("reason", "admin-request"));
                write(exchange, changed ? 202 : 404, changed ? ApiResponses.ok(true) : ApiResponses.error("NODE_NOT_FOUND", "Node was not found"));
                return;
            }
            write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
        });
        route("/v1/admin/nodes/sweep", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            int affected = 0;
            int recoveryQueued = 0;
            java.util.List<String> downNodes = nodeId.isBlank() ? nodes.markStaleDown(config.heartbeatTimeout()) : java.util.List.of(nodeId);
            for (String downNode : downNodes) {
                affected += nodeFailureMonitor.markRecoveryRequiredForNode(downNode);
                recoveryQueued += nodeFailureMonitor.recoverOrQuarantineNodeIslands(downNode);
            }
            String nodesJson = "[\"" + String.join("\",\"", downNodes.stream().map(value -> value.replace("\"", "'")).toList()) + "\"]";
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_SWEEP", "NODE", nodeId.isBlank() ? "*" : nodeId, Map.of("recoveryRequired", Integer.toString(affected), "recoveryQueued", Integer.toString(recoveryQueued), "nodes", String.join(",", downNodes)));
            events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId.isBlank() ? "*" : nodeId, "state", "SWEEP", "recoveryRequired", Integer.toString(affected), "recoveryQueued", Integer.toString(recoveryQueued), "nodes", String.join(",", downNodes)));
            write(exchange, 202, "{\"nodes\":" + nodesJson + ",\"recoveryRequired\":" + affected + ",\"recoveryQueued\":" + recoveryQueued + "}");
        });
        route("/v1/admin/islands/activate", exchange -> {
            UUID islandId = JsonFields.uuid(readBody(exchange), "islandId", new UUID(0L, 0L));
            IslandLifecycleWorkflow.Result result = lifecycle.activate(islandId);
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_ACTIVATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code()));
            lifecycle(exchange, result);
        });
        route("/v1/admin/islands/deactivate", exchange -> {
            UUID islandId = JsonFields.uuid(readBody(exchange), "islandId", new UUID(0L, 0L));
            IslandLifecycleWorkflow.Result result = lifecycle.deactivate(islandId);
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_DEACTIVATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code()));
            lifecycle(exchange, result);
        });
        route("/v1/admin/islands/migrate", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String targetNode = JsonFields.text(body, "targetNode", "");
            IslandLifecycleWorkflow.Result result = lifecycle.migrate(islandId, targetNode);
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_MIGRATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "targetNode", targetNode));
            lifecycle(exchange, result);
        });
        routePrefix("/v1/admin/islands/", exchange -> {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String tail = path.substring("/v1/admin/islands/".length());
            if (!method.equalsIgnoreCase("POST")) {
                write(exchange, 405, ApiResponses.error("METHOD_NOT_ALLOWED", "Use POST for admin island lifecycle operations"));
                return;
            }
            if (tail.endsWith("/activate")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/activate".length()));
                IslandLifecycleWorkflow.Result result = lifecycle.activate(islandId);
                audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_ACTIVATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code()));
                lifecycle(exchange, result);
                return;
            }
            if (tail.endsWith("/deactivate")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/deactivate".length()));
                IslandLifecycleWorkflow.Result result = lifecycle.deactivate(islandId);
                audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_DEACTIVATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code()));
                lifecycle(exchange, result);
                return;
            }
            if (tail.endsWith("/migrate")) {
                String body = readBody(exchange);
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/migrate".length()));
                String targetNode = JsonFields.text(body, "targetNode", "");
                IslandLifecycleWorkflow.Result result = lifecycle.migrate(islandId, targetNode);
                audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_MIGRATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "targetNode", targetNode));
                lifecycle(exchange, result);
                return;
            }
            if (tail.endsWith("/save")) {
                String body = readBody(exchange);
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/save".length()));
                String reason = adminSaveReason(JsonFields.text(body, "reason", "ADMIN_SAVE"));
                IslandLifecycleWorkflow.Result result = lifecycle.snapshot(islandId, reason);
                audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_SAVE_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "reason", reason));
                lifecycle(exchange, result);
                return;
            }
            if (tail.endsWith("/snapshot")) {
                String body = readBody(exchange);
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/snapshot".length()));
                String reason = adminSnapshotReason(JsonFields.text(body, "reason", "MANUAL"));
                IslandLifecycleWorkflow.Result result = lifecycle.snapshot(islandId, reason);
                audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_SNAPSHOT_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "reason", reason));
                lifecycle(exchange, result);
                return;
            }
            if (tail.endsWith("/restore")) {
                String body = readBody(exchange);
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/restore".length()));
                long snapshotNo = JsonFields.longValue(body, "snapshotNo", 0L);
                restoreIslandSnapshot(exchange, audit, lifecycle, snapshotRepository, islandId, snapshotNo);
                return;
            }
            if (tail.endsWith("/rollback")) {
                String body = readBody(exchange);
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/rollback".length()));
                long snapshotNo = JsonFields.longValue(body, "snapshotNo", 0L);
                rollbackIslandSnapshot(exchange, audit, lifecycle, snapshotRepository, islandId, snapshotNo);
                return;
            }
            if (tail.endsWith("/quarantine")) {
                String body = readBody(exchange);
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/quarantine".length()));
                lifecycle(exchange, lifecycle.quarantine(islandId, JsonFields.text(body, "reason", "admin")));
                return;
            }
            if (tail.endsWith("/delete")) {
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/delete".length()));
                java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
                boolean deleted = island.isPresent() && requestIslandDelete(islandId, island.get().ownerUuid(), island.get().ownerUuid(), "admin-delete");
                audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of("deleted", Boolean.toString(deleted)));
                write(exchange, deleted ? 202 : 404, deleted ? ApiResponses.ok(true) : ApiResponses.error("ISLAND_NOT_DELETED", "Island was not found or could not be deleted"));
                return;
            }
            if (tail.endsWith("/repair")) {
                String body = readBody(exchange);
                UUID islandId = uuidPath(tail.substring(0, tail.length() - "/repair".length()));
                String reason = JsonFields.text(body, "reason", "admin");
                if (islandRepository.findById(islandId).isEmpty()) {
                    write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
                    return;
                }
                var runtime = runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.INACTIVE_READY);
                islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.INACTIVE_READY);
                audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_REPAIR", "ISLAND", islandId.toString(), Map.of("reason", reason));
                events.publish(CloudIslandEventType.ISLAND_REPAIRED.name(), Map.of("islandId", islandId.toString(), "reason", reason, "state", runtime.state().name()));
                events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name(), "reason", "REPAIRED"));
                write(exchange, 202, runtimeJson(runtime));
                return;
            }
            write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
        });
        route("/v1/admin/islands/save", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String reason = adminSaveReason(JsonFields.text(body, "reason", "ADMIN_SAVE"));
            IslandLifecycleWorkflow.Result result = lifecycle.snapshot(islandId, reason);
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_SAVE_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "reason", reason));
            lifecycle(exchange, result);
        });
        route("/v1/admin/islands/snapshot", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String reason = adminSnapshotReason(JsonFields.text(body, "reason", "MANUAL"));
            IslandLifecycleWorkflow.Result result = lifecycle.snapshot(islandId, reason);
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_SNAPSHOT_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "reason", reason));
            lifecycle(exchange, result);
        });
        route("/v1/admin/islands/restore", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            long snapshotNo = JsonFields.longValue(body, "snapshotNo", 0L);
            restoreIslandSnapshot(exchange, audit, lifecycle, snapshotRepository, islandId, snapshotNo);
        });
        route("/v1/admin/islands/rollback", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            long snapshotNo = JsonFields.longValue(body, "snapshotNo", 0L);
            rollbackIslandSnapshot(exchange, audit, lifecycle, snapshotRepository, islandId, snapshotNo);
        });
        route("/v1/admin/islands/quarantine", exchange -> {
            String body = readBody(exchange);
            lifecycle(exchange, lifecycle.quarantine(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "reason", "admin")));
        });
        route("/v1/admin/islands/info", exchange -> {
            String body = readBody(exchange);
            UUID lookupUuid = JsonFields.uuid(body, "lookupUuid", new UUID(0L, 0L));
            java.util.Optional<IslandSnapshot> island = islandRepository.findById(lookupUuid).or(() -> islandRepository.findByOwner(lookupUuid));
            write(exchange, island.isPresent() ? 200 : 404, island.map(CloudIslandsCoreApplication::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
        });
        route("/v1/admin/islands/where", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> runtime = runtimeRepository.find(islandId);
            write(exchange, runtime.isPresent() ? 200 : 404, runtime.map(CloudIslandsCoreApplication::runtimeJson).orElseGet(() -> ApiResponses.error("ISLAND_RUNTIME_NOT_FOUND", "Island runtime was not found")));
        });
        route("/v1/admin/islands/tp", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareAdminTeleportRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.uuid(body, "islandId", new UUID(0L, 0L))));
        });
        route("/v1/admin/islands/delete", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
            boolean deleted = island.isPresent() && requestIslandDelete(islandId, island.get().ownerUuid(), island.get().ownerUuid(), "admin-delete");
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of("deleted", Boolean.toString(deleted)));
            write(exchange, deleted ? 202 : 404, deleted ? ApiResponses.ok(true) : ApiResponses.error("ISLAND_NOT_DELETED", "Island was not found or could not be deleted"));
        });
        route("/v1/admin/islands/repair", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String reason = JsonFields.text(body, "reason", "admin");
            if (islandRepository.findById(islandId).isEmpty()) {
                write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
                return;
            }
            var runtime = runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.INACTIVE_READY);
            islandRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.INACTIVE_READY);
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_REPAIR", "ISLAND", islandId.toString(), Map.of("reason", reason));
            events.publish(CloudIslandEventType.ISLAND_REPAIRED.name(), Map.of("islandId", islandId.toString(), "reason", reason, "state", runtime.state().name()));
            events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name(), "reason", "REPAIRED"));
            write(exchange, 202, runtimeJson(runtime));
        });
        route("/v1/islands/snapshots", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, snapshotsJson(snapshotRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.integer(body, "limit", 20))));
        });
        route("/v1/islands/snapshots/record", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            long snapshotNo = JsonFields.longValue(body, "snapshotNo", 0L);
            if (snapshotNo <= 0L) {
                write(exchange, 409, ApiResponses.error("INVALID_SNAPSHOT", "Snapshot number is required"));
                return;
            }
            String storagePath = JsonFields.text(body, "storagePath", "islands/" + islandId + "/snapshots/" + String.format("%06d", snapshotNo) + "/bundle.tar.zst");
            String reason = JsonFields.text(body, "reason", "AUTO");
            String checksum = JsonFields.text(body, "checksum", "");
            long sizeBytes = JsonFields.longValue(body, "sizeBytes", 0L);
            String nodeId = JsonFields.text(body, "nodeId", "");
            var runtime = runtimeRepository.find(islandId).orElse(null);
            if (runtime == null || runtime.activeNode() == null || !runtime.activeNode().equals(nodeId)) {
                write(exchange, 403, ApiResponses.error("SNAPSHOT_NODE_MISMATCH", "Snapshot must be recorded by the active island node"));
                return;
            }
            int pruned = recordSnapshotAndPublish(islandId, snapshotNo, storagePath, reason, checksum, sizeBytes, nodeId);
            write(exchange, 202, "{\"accepted\":true,\"snapshotNo\":" + snapshotNo + ",\"storagePath\":\"" + escape(storagePath) + "\",\"checksum\":\"" + escape(checksum) + "\",\"sizeBytes\":" + sizeBytes + ",\"pruned\":" + pruned + "}");
        });
        route("/v1/admin/block-values", exchange -> {
            String body = readBody(exchange);
            String materialKey = JsonFields.text(body, "materialKey", "minecraft:stone");
            BigDecimal worth = new BigDecimal(JsonFields.text(body, "worth", Double.toString(JsonFields.decimal(body, "worth", 0.0D))));
            long levelPoints = JsonFields.longValue(body, "levelPoints", 0L);
            long limit = JsonFields.longValue(body, "limit", 0L);
            levelRepository.putBlockValue(materialKey, new RankingRecalculationService.BlockValue(worth, levelPoints, limit));
            audit.log(JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L)), "ADMIN", "BLOCK_VALUE_SET", "MATERIAL", materialKey, Map.of("worth", worth.toPlainString(), "levelPoints", Long.toString(levelPoints)));
            events.publish(CloudIslandEventType.ISLAND_BLOCK_VALUE_CHANGED.name(), Map.of("materialKey", materialKey, "worth", worth.toPlainString(), "levelPoints", Long.toString(levelPoints), "limit", Long.toString(limit), "cacheTargets", "BLOCKS,LEVEL,SUMMARY"));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/blocks/delta", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String materialKey = JsonFields.text(body, "materialKey", "minecraft:air");
            long delta = JsonFields.longValue(body, "delta", 0L);
            levelRepository.addBlockDelta(islandId, materialKey, delta);
            rankingRepository.markDirty(islandId);
            events.publish(CloudIslandEventType.ISLAND_BLOCKS_CHANGED.name(), Map.of("islandId", islandId.toString(), "materialKey", materialKey, "delta", Long.toString(delta)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/blocks/replace", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            Map<String, Long> counts = parseCountsPayload(JsonFields.text(body, "counts", ""));
            levelRepository.replaceBlockCounts(islandId, counts);
            rankingRepository.markDirty(islandId);
            events.publish(CloudIslandEventType.ISLAND_BLOCKS_CHANGED.name(), Map.of("islandId", islandId.toString(), "materialKey", "*", "delta", "rescan"));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/level/recalculate", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.START_LEVEL_CALC)) {
                return;
            }
            var snapshot = levelRecalculation.recalculate(islandId, levelRepository.blockCounts(islandId), levelRepository.blockValues(), metadataRepository.members(islandId).size());
            write(exchange, 202, levelJson(snapshot));
        });
        route("/v1/islands/upgrades", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, upgradesJson(upgradeRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/upgrades/purchase", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String upgradeKey = JsonFields.text(body, "upgradeKey", "size").toLowerCase();
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_UPGRADES)) {
                return;
            }
            UpgradePurchaseResult result = upgradeService.purchase(islandId, upgradeKey);
            audit.log(actorUuid, "PLAYER", "ISLAND_UPGRADE_PURCHASE", "ISLAND", islandId.toString(), Map.of("upgradeKey", upgradeKey, "code", result.code(), "cost", result.cost().toPlainString()));
            islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_PURCHASE", Map.of("upgradeKey", upgradeKey, "code", result.code(), "cost", result.cost().toPlainString()));
            if (result.accepted()) {
                events.publish(CloudIslandEventType.ISLAND_UPGRADE.name(), Map.of("islandId", islandId.toString(), "upgradeKey", upgradeKey, "level", Integer.toString(result.snapshot().level())));
                applyUpgradeLimit(limitRepository, events, islandId, actorUuid, upgradePolicy.rule(upgradeKey), result.snapshot().type(), result.snapshot().level());
                applyUpgradeFlag(metadataRepository, events, islandId, result.snapshot().type());
                if (result.cost().signum() > 0) {
                    String balance = bankRepository.balance(islandId).balance();
                    events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "operation", "UPGRADE_PURCHASE", "amount", result.cost().toPlainString(), "balance", balance));
                }
            }
            write(exchange, result.accepted() ? 202 : 409, upgradePurchaseJson(result));
        });
        route("/v1/islands/logs", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, islandLogsJson(islandLogs.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.integer(body, "limit", 30))));
        });
        route("/v1/islands/chat", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String channel = JsonFields.text(body, "channel", "ISLAND").toUpperCase();
            String message = JsonFields.text(body, "message", "");
            if (!requireMember(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            if (message.isBlank()) {
                write(exchange, 400, ApiResponses.error("EMPTY_CHAT_MESSAGE", "Chat message is empty"));
                return;
            }
            String normalizedChannel = channel.equals("TEAM") ? "TEAM" : "ISLAND";
            String actorName = playerProfiles.find(actorUuid).lastName();
            islandLogs.append(islandId, actorUuid, "ISLAND_CHAT", Map.of("channel", normalizedChannel, "message", message));
            java.util.Map<String, String> payload = new java.util.LinkedHashMap<>();
            payload.put("islandId", islandId.toString());
            payload.put("actorUuid", actorUuid.toString());
            payload.put("actorName", actorName == null || actorName.isBlank() ? actorUuid.toString() : actorName);
            payload.put("channel", normalizedChannel);
            payload.put("message", message);
            if (normalizedChannel.equals("TEAM")) {
                payload.put("recipients", String.join(",", metadataRepository.members(islandId).stream().map(member -> member.playerUuid().toString()).toList()));
            }
            events.publish(CloudIslandEventType.ISLAND_CHAT_SENT.name(), payload);
            write(exchange, 202, "{\"accepted\":true,\"channel\":\"" + normalizedChannel + "\",\"message\":\"" + escape(message) + "\"}");
        });
        route("/v1/islands/bank", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, bankJson(bankRepository.balance(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/bank/deposit", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            BigDecimal amount = amount(body);
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.DEPOSIT_BANK)) {
                return;
            }
            if (amount.signum() <= 0) {
                write(exchange, 409, "{\"accepted\":false,\"code\":\"INVALID_AMOUNT\",\"bank\":" + bankJson(bankRepository.balance(islandId)) + "}");
                return;
            }
            long bankLimit = limitValue(limitRepository, islandId, "BANK", Long.MAX_VALUE);
            var result = bankRepository.deposit(islandId, amount, bankLimit == Long.MAX_VALUE ? null : BigDecimal.valueOf(bankLimit));
            if (!result.accepted()) {
                write(exchange, 409, "{\"accepted\":false,\"code\":\"" + result.code() + "\",\"bank\":" + bankJson(result.snapshot()) + "}");
                return;
            }
            var snapshot = result.snapshot();
            audit.log(actorUuid, "PLAYER", "ISLAND_BANK_DEPOSIT", "ISLAND", islandId.toString(), Map.of("amount", amount.toPlainString(), "balance", snapshot.balance()));
            islandLogs.append(islandId, actorUuid, "ISLAND_BANK_DEPOSIT", Map.of("amount", amount.toPlainString(), "balance", snapshot.balance()));
            events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "operation", "DEPOSIT", "amount", amount.toPlainString(), "balance", snapshot.balance()));
            write(exchange, 202, bankJson(snapshot));
        });
        route("/v1/islands/bank/withdraw", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            BigDecimal amount = amount(body);
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.WITHDRAW_BANK)) {
                return;
            }
            var result = bankRepository.withdraw(islandId, amount);
            audit.log(actorUuid, "PLAYER", "ISLAND_BANK_WITHDRAW", "ISLAND", islandId.toString(), Map.of("amount", amount.toPlainString(), "code", result.code(), "balance", result.snapshot().balance()));
            islandLogs.append(islandId, actorUuid, "ISLAND_BANK_WITHDRAW", Map.of("amount", amount.toPlainString(), "code", result.code(), "balance", result.snapshot().balance()));
            if (result.accepted()) {
                events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "operation", "WITHDRAW", "amount", amount.toPlainString(), "balance", result.snapshot().balance()));
            }
            write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\",\"bank\":" + bankJson(result.snapshot()) + "}");
        });
        route("/v1/islands/delete", exchange -> {
            String body = readBody(exchange);
            UUID requesterUuid = JsonFields.uuid(body, "requesterUuid", new UUID(0L, 0L));
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            boolean deleted = requestIslandDelete(islandId, requesterUuid, requesterUuid, "player-delete");
            if (deleted) {
                audit.log(requesterUuid, "PLAYER", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of());
                islandLogs.append(islandId, requesterUuid, "ISLAND_DELETE", Map.of());
            }
            write(exchange, deleted ? 202 : 403, deleteResultJson(new DeleteIslandResult(deleted, deleted ? "DELETED" : "NOT_OWNER_OR_MISSING", islandId)));
        });
        route("/v1/islands/reset", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String reason = JsonFields.text(body, "reason", "player-reset");
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_MEMBERS)) {
                return;
            }
            IslandLifecycleWorkflow.Result result = lifecycle.reset(islandId, reason);
            if (result.accepted()) {
                audit.log(actorUuid, "PLAYER", "ISLAND_RESET", "ISLAND", islandId.toString(), Map.of("reason", reason));
                islandLogs.append(islandId, actorUuid, "ISLAND_RESET", Map.of("reason", reason));
            }
            lifecycle(exchange, result);
        });
        route("/v1/islands/members", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, membersJson(metadataRepository.members(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/players/islands", exchange -> {
            String body = readBody(exchange);
            java.util.ArrayList<IslandSnapshot> islands = new java.util.ArrayList<>();
            for (kr.lunaf.cloudislands.api.model.IslandMemberSnapshot member : metadataRepository.islandsForMember(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)))) {
                islandRepository.findById(member.islandId()).ifPresent(islands::add);
            }
            write(exchange, 200, islandsJson(islands));
        });
        route("/v1/islands/members/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            IslandRole role = JsonFields.enumValue(IslandRole.class, body, "role", IslandRole.MEMBER);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
                return;
            }
            java.util.List<kr.lunaf.cloudislands.api.model.IslandMemberSnapshot> members = metadataRepository.members(islandId);
            IslandRole currentRole = memberRole(members, playerUuid);
            if (role == IslandRole.VISITOR || role == IslandRole.BANNED) {
                write(exchange, 409, ApiResponses.error("MEMBER_ROLE_UNAVAILABLE", "Visitor and banned roles are not managed as island members"));
                return;
            }
            if (role == IslandRole.OWNER || currentRole == IslandRole.OWNER) {
                write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island ownership must be changed through ownership transfer"));
                return;
            }
            boolean existingMember = currentRole != null;
            if (!existingMember && members.size() >= limitValue(limitRepository, islandId, "MEMBERS", 3L)) {
                write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
                return;
            }
            metadataRepository.upsertMember(islandId, playerUuid, role);
            audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_SET", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "role", role.name()));
            islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_SET", Map.of("playerUuid", playerUuid.toString(), "role", role.name()));
            events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", role.name()));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/transfer", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            UUID targetUuid = JsonFields.uuid(body, "targetUuid", new UUID(0L, 0L));
            boolean transferred = islandRepository.transferOwnership(islandId, actorUuid, targetUuid);
            if (transferred) {
                metadataRepository.upsertMember(islandId, actorUuid, IslandRole.CO_OWNER);
                metadataRepository.upsertMember(islandId, targetUuid, IslandRole.OWNER);
                playerProfiles.clearPrimaryIsland(actorUuid);
                playerProfiles.setPrimaryIsland(targetUuid, islandId);
            }
            audit.log(actorUuid, "PLAYER", "ISLAND_OWNERSHIP_TRANSFER", "ISLAND", islandId.toString(), Map.of("targetUuid", targetUuid.toString(), "transferred", Boolean.toString(transferred)));
            islandLogs.append(islandId, actorUuid, "ISLAND_OWNERSHIP_TRANSFER", Map.of("targetUuid", targetUuid.toString(), "transferred", Boolean.toString(transferred)));
            if (transferred) {
                events.publish(CloudIslandEventType.ISLAND_OWNERSHIP_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "targetUuid", targetUuid.toString()));
                events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "targetUuid", targetUuid.toString()));
            }
            write(exchange, transferred ? 202 : 409, transferred ? ApiResponses.ok(true) : ApiResponses.error("OWNERSHIP_TRANSFER_DENIED", "Only the current owner can transfer to a player without an island"));
        });
        route("/v1/islands/members/remove", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_MEMBERS)) {
                return;
            }
            if (memberRole(metadataRepository.members(islandId), playerUuid) == IslandRole.OWNER) {
                write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner cannot be removed as a member"));
                return;
            }
            metadataRepository.removeMember(islandId, playerUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_REMOVE", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
            islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_REMOVE", Map.of("playerUuid", playerUuid.toString()));
            events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString()));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/invites", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID inviterUuid = JsonFields.uuid(body, "inviterUuid", new UUID(0L, 0L));
            UUID targetUuid = JsonFields.uuid(body, "targetUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, inviterUuid, IslandPermission.MANAGE_MEMBERS)) {
                return;
            }
            boolean existingMember = metadataRepository.members(islandId).stream().anyMatch(member -> member.playerUuid().equals(targetUuid));
            if (existingMember) {
                write(exchange, 409, ApiResponses.error("ALREADY_MEMBER", "Player is already an island member"));
                return;
            }
            if (!existingMember && metadataRepository.members(islandId).size() >= limitValue(limitRepository, islandId, "MEMBERS", 3L)) {
                write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
                return;
            }
            var invite = metadataRepository.createInvite(islandId, inviterUuid, targetUuid);
            audit.log(inviterUuid, "PLAYER", "ISLAND_INVITE_CREATE", "ISLAND", islandId.toString(), Map.of("targetUuid", targetUuid.toString(), "inviteId", invite.inviteId().toString()));
            islandLogs.append(islandId, inviterUuid, "ISLAND_INVITE_CREATE", Map.of("targetUuid", targetUuid.toString(), "inviteId", invite.inviteId().toString()));
            events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("islandId", islandId.toString(), "targetUuid", targetUuid.toString(), "inviteId", invite.inviteId().toString(), "state", invite.state().name()));
            write(exchange, 202, "{\"accepted\":true,\"inviteId\":\"" + invite.inviteId() + "\",\"islandId\":\"" + invite.islandId() + "\",\"inviterUuid\":\"" + invite.inviterUuid() + "\",\"targetUuid\":\"" + invite.targetUuid() + "\",\"state\":\"" + invite.state() + "\",\"createdAt\":\"" + invite.createdAt() + "\",\"expiresAt\":\"" + invite.expiresAt() + "\"}");
        });
        route("/v1/players/invites", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, invitesJson(metadataRepository.pendingInvites(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)))));
        });
        route("/v1/islands/invites/accept", exchange -> {
            String body = readBody(exchange);
            UUID inviteId = JsonFields.uuid(body, "inviteId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandInviteSnapshot> invite = metadataRepository.pendingInvites(playerUuid).stream().filter(value -> value.inviteId().equals(inviteId)).findFirst();
            String islandId = invite.map(value -> value.islandId().toString()).orElse("");
            if (invite.isPresent()) {
                UUID inviteIslandId = invite.get().islandId();
                boolean existingMember = metadataRepository.members(inviteIslandId).stream().anyMatch(member -> member.playerUuid().equals(playerUuid));
                if (!existingMember && metadataRepository.members(inviteIslandId).size() >= limitValue(limitRepository, inviteIslandId, "MEMBERS", 3L)) {
                    write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
                    return;
                }
            }
            boolean accepted = metadataRepository.acceptInvite(inviteId, playerUuid);
            audit.log(playerUuid, "PLAYER", "ISLAND_INVITE_ACCEPT", "INVITE", inviteId.toString(), Map.of("accepted", Boolean.toString(accepted)));
            events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString(), "accepted", Boolean.toString(accepted)));
            if (accepted) {
                invite.ifPresent(value -> islandLogs.append(value.islandId(), playerUuid, "ISLAND_INVITE_ACCEPT", Map.of("inviteId", inviteId.toString(), "accepted", "true")));
                events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString()));
            }
            write(exchange, accepted ? 202 : 409, accepted ? ApiResponses.ok(true) : ApiResponses.error("INVITE_UNAVAILABLE", "Invite is missing, expired, or not pending"));
        });
        route("/v1/islands/invites/decline", exchange -> {
            String body = readBody(exchange);
            UUID inviteId = JsonFields.uuid(body, "inviteId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandInviteSnapshot> invite = metadataRepository.pendingInvites(playerUuid).stream().filter(value -> value.inviteId().equals(inviteId)).findFirst();
            String islandId = invite.map(value -> value.islandId().toString()).orElse("");
            boolean declined = metadataRepository.declineInvite(inviteId, playerUuid);
            audit.log(playerUuid, "PLAYER", "ISLAND_INVITE_DECLINE", "INVITE", inviteId.toString(), Map.of("declined", Boolean.toString(declined)));
            if (declined) {
                invite.ifPresent(value -> islandLogs.append(value.islandId(), playerUuid, "ISLAND_INVITE_DECLINE", Map.of("inviteId", inviteId.toString(), "declined", "true")));
            }
            events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString(), "declined", Boolean.toString(declined)));
            write(exchange, declined ? 202 : 409, declined ? ApiResponses.ok(true) : ApiResponses.error("INVITE_UNAVAILABLE", "Invite is missing or not pending"));
        });
        route("/v1/islands/bans/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String reason = JsonFields.text(body, "reason", "island ban");
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.BAN_VISITOR)) {
                return;
            }
            IslandRole targetRole = memberRole(metadataRepository.members(islandId), playerUuid);
            if (targetRole != null && targetRole != IslandRole.VISITOR && targetRole != IslandRole.BANNED) {
                write(exchange, 409, ApiResponses.error("VISITOR_BAN_DENIED", "Island members cannot be handled through visitor bans"));
                return;
            }
            metadataRepository.banVisitor(islandId, actorUuid, playerUuid, reason);
            metadataRepository.removeMember(islandId, playerUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_VISITOR_BAN", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "reason", reason));
            islandLogs.append(islandId, actorUuid, "ISLAND_VISITOR_BAN", Map.of("playerUuid", playerUuid.toString(), "reason", reason));
            events.publish(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "banned", Boolean.toString(true)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/bans", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, bansJson(metadataRepository.bans(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/bans/remove", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.BAN_VISITOR)) {
                return;
            }
            metadataRepository.pardonVisitor(islandId, playerUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_VISITOR_PARDON", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
            islandLogs.append(islandId, actorUuid, "ISLAND_VISITOR_PARDON", Map.of("playerUuid", playerUuid.toString()));
            events.publish(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "banned", Boolean.toString(false)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/visitors/kick", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.KICK_VISITOR)) {
                return;
            }
            audit.log(actorUuid, "PLAYER", "ISLAND_VISITOR_KICK", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
            islandLogs.append(islandId, actorUuid, "ISLAND_VISITOR_KICK", Map.of("playerUuid", playerUuid.toString()));
            events.publish(CloudIslandEventType.ISLAND_VISITOR_KICKED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "actorUuid", actorUuid.toString()));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/lock", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            boolean locked = JsonFields.bool(body, "locked", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
                return;
            }
            metadataRepository.setLocked(islandId, locked);
            audit.log(actorUuid, "PLAYER", "ISLAND_LOCK_SET", "ISLAND", islandId.toString(), Map.of("locked", Boolean.toString(locked)));
            islandLogs.append(islandId, actorUuid, "ISLAND_LOCK_SET", Map.of("locked", Boolean.toString(locked)));
            events.publish(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name(), Map.of("islandId", islandId.toString(), "locked", Boolean.toString(locked)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/name", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "").trim();
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
                return;
            }
            if (name.length() < 2 || name.length() > 32 || name.chars().anyMatch(Character::isISOControl)) {
                write(exchange, 400, ApiResponses.error("INVALID_ISLAND_NAME", "Island name must be 2-32 visible characters"));
                return;
            }
            java.util.Optional<IslandSnapshot> duplicate = islandRepository.findByName(name);
            if (duplicate.isPresent() && !duplicate.get().islandId().equals(islandId)) {
                write(exchange, 409, ApiResponses.error("ISLAND_NAME_TAKEN", "Island name is already used"));
                return;
            }
            boolean renamed = islandRepository.rename(islandId, name);
            if (!renamed) {
                write(exchange, 409, ApiResponses.error("ISLAND_RENAME_DENIED", "Island was not renamed"));
                return;
            }
            audit.log(actorUuid, "PLAYER", "ISLAND_RENAME", "ISLAND", islandId.toString(), Map.of("name", name));
            islandLogs.append(islandId, actorUuid, "ISLAND_RENAME", Map.of("name", name));
            events.publish(CloudIslandEventType.ISLAND_RENAMED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "name", name));
            write(exchange, 202, "{\"accepted\":true,\"islandId\":\"" + islandId + "\",\"name\":\"" + escape(name) + "\"}");
        });
        route("/v1/islands/flags", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, flagsJson(metadataRepository.flags(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/biome", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, biomeJson(metadataRepository.biome(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/biome/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String biomeKey = JsonFields.text(body, "biomeKey", "minecraft:plains").toLowerCase();
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.SET_BIOME)) {
                return;
            }
            metadataRepository.setBiome(islandId, biomeKey, actorUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_BIOME_SET", "ISLAND", islandId.toString(), Map.of("biomeKey", biomeKey));
            islandLogs.append(islandId, actorUuid, "ISLAND_BIOME_SET", Map.of("biomeKey", biomeKey));
            events.publish(CloudIslandEventType.ISLAND_BIOME_CHANGED.name(), Map.of("islandId", islandId.toString(), "biomeKey", biomeKey));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/flags/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            IslandFlag flag = JsonFields.enumValue(IslandFlag.class, body, "flag", IslandFlag.VISITOR_INTERACT);
            String value = JsonFields.text(body, "value", "false");
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
                return;
            }
            metadataRepository.setFlag(islandId, flag, value);
            audit.log(actorUuid, "PLAYER", "ISLAND_FLAG_SET", "ISLAND", islandId.toString(), Map.of("flag", flag.name(), "value", value));
            islandLogs.append(islandId, actorUuid, "ISLAND_FLAG_SET", Map.of("flag", flag.name(), "value", value));
            events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "flag", flag.name(), "value", value));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/warps", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, warpsJson(metadataRepository.warps(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/public-warps", exchange -> {
            String body = readBody(exchange);
            int limit = queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 27), 1, 54);
            var visibleWarps = metadataRepository.publicWarps(500).stream()
                .filter(warp -> metadataRepository.isPublicAccess(warp.islandId()))
                .filter(warp -> !metadataRepository.isLocked(warp.islandId()))
                .filter(warp -> islandFlagEnabled(metadataRepository, warp.islandId(), IslandFlag.PUBLIC_WARPS))
                .limit(limit)
                .toList();
            write(exchange, 200, warpsJson(visibleWarps));
        });
        route("/v1/islands/homes", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, homesJson(metadataRepository.homes(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        route("/v1/islands/homes/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "default").toLowerCase();
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.SET_HOME)) {
                return;
            }
            metadataRepository.upsertHome(islandId, name, location(body), actorUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_HOME_SET", "ISLAND", islandId.toString(), Map.of("name", name));
            islandLogs.append(islandId, actorUuid, "ISLAND_HOME_SET", Map.of("name", name));
            events.publish(CloudIslandEventType.ISLAND_HOME_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/warps/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "default").toLowerCase();
            boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_WARPS)) {
                return;
            }
            boolean existingWarp = metadataRepository.warps(islandId).stream().anyMatch(warp -> warp.name().equalsIgnoreCase(name));
            if (!existingWarp && metadataRepository.warps(islandId).size() >= limitValue(limitRepository, islandId, "WARPS", 1L)) {
                write(exchange, 409, ApiResponses.error("WARP_LIMIT", "Island warp limit was reached"));
                return;
            }
            metadataRepository.upsertWarp(islandId, name, location(body), publicAccess, actorUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_WARP_SET", "ISLAND", islandId.toString(), Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            islandLogs.append(islandId, actorUuid, "ISLAND_WARP_SET", Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name, "operation", existingWarp ? "WARP_UPDATE" : "WARP_CREATE"));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/warps/delete", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "default").toLowerCase();
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_WARPS)) {
                return;
            }
            metadataRepository.deleteWarp(islandId, name);
            audit.log(actorUuid, "PLAYER", "ISLAND_WARP_DELETE", "ISLAND", islandId.toString(), Map.of("name", name));
            islandLogs.append(islandId, actorUuid, "ISLAND_WARP_DELETE", Map.of("name", name));
            events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name, "operation", "WARP_DELETE"));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/warps/access", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "default").toLowerCase();
            boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_WARPS)) {
                return;
            }
            if (metadataRepository.warp(islandId, name).isEmpty()) {
                write(exchange, 404, ApiResponses.error("WARP_NOT_FOUND", "Island warp was not found"));
                return;
            }
            metadataRepository.setWarpPublicAccess(islandId, name, publicAccess);
            audit.log(actorUuid, "PLAYER", "ISLAND_WARP_ACCESS_SET", "ISLAND", islandId.toString(), Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            islandLogs.append(islandId, actorUuid, "ISLAND_WARP_ACCESS_SET", Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name, "operation", "WARP_ACCESS_SET", "publicAccess", Boolean.toString(publicAccess)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/access", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
                return;
            }
            metadataRepository.setPublicAccess(islandId, publicAccess);
            audit.log(actorUuid, "PLAYER", "ISLAND_ACCESS_SET", "ISLAND", islandId.toString(), Map.of("publicAccess", Boolean.toString(publicAccess)));
            islandLogs.append(islandId, actorUuid, "ISLAND_ACCESS_SET", Map.of("publicAccess", Boolean.toString(publicAccess)));
            events.publish(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name(), Map.of("islandId", islandId.toString(), "publicAccess", Boolean.toString(publicAccess)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/public", exchange -> {
            String body = readBody(exchange);
            int limit = queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 27), 1, 54);
            java.util.List<IslandSnapshot> islands = metadataRepository.publicIslandIds(limit).stream()
                .map(islandRepository::findById)
                .flatMap(java.util.Optional::stream)
                .sorted(java.util.Comparator.comparingLong(IslandSnapshot::level).reversed().thenComparing(IslandSnapshot::name))
                .toList();
            write(exchange, 200, islandsJson(islands));
        });
        route("/v1/islands", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            CreateIslandResult result = createIsland.create(playerUuid, JsonFields.text(body, "templateId", "default"));
            if (result.accepted() && result.island() != null) {
                metadataRepository.upsertMember(result.island().islandId(), playerUuid, IslandRole.OWNER);
                islandLogs.append(result.island().islandId(), playerUuid, "ISLAND_CREATE", Map.of("templateId", JsonFields.text(body, "templateId", "default")));
            }
            audit.log(playerUuid, "PLAYER", "ISLAND_CREATE", "ISLAND", result.island() == null ? "" : result.island().islandId().toString(), Map.of("code", result.code()));
            String ticketJson = result.ticket() == null ? "null" : RoutingOrchestrator.toJson(result.ticket());
            String islandId = result.island() == null ? "" : result.island().islandId().toString();
            write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\",\"islandId\":\"" + islandId + "\",\"ticket\":" + ticketJson + "}");
        });
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
            + "\"databaseBackend\":\"" + escape(jdbcBackend(config.jdbcUrl())) + "\","
            + "\"jdbcUrlSource\":\"" + escape(CoreServiceConfig.configuredJdbcUrlSource()) + "\","
            + "\"coreJdbcSupported\":" + coreJdbcSupported(config.jdbcUrl()) + ","
            + "\"coreJdbcSupportedBackends\":\"POSTGRESQL\","
            + "\"coreSetupFallbackBackends\":\"MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC\","
            + "\"coreSetupFallbackEnabled\":" + config.setupDatabaseFallbackEnabled() + ","
            + "\"coreSetupFallbackEffective\":" + coreJdbcFallbackActive(config) + ","
            + "\"coreSetupFallbackSafetyForced\":" + coreSetupFallbackSafetyForced(config) + ","
            + "\"coreSetupFallbackPolicy\":\"" + escape(coreSetupFallbackPolicy(config)) + "\","
            + "\"coreSetupFallbackOrder\":\"" + escape(config.setupDatabaseFallbackOrder()) + "\","
            + "\"coreSetupFallbackMode\":\"" + escape(coreSetupFallbackMode(config)) + "\","
            + "\"coreSetupDatabaseOperationalModes\":\"POSTGRESQL=CORE_JDBC,MYSQL=CONFIGURED_SAFE_FALLBACK,MARIADB=CONFIGURED_SAFE_FALLBACK,CORE_API=CLIENT_MODE_NO_CORE_JDBC\","
            + "\"coreSetupDatabaseConfigLoader\":\"yaml-nested-dotted-path\","
            + "\"coreSetupDatabaseResolvedPathExamples\":\"setup.database.type,setup.database.postgresql.jdbc-url,setup.database.postgresql.username,setup.database.mysql.host,setup.database.mysql.password,setup.database.mariadb.pool-size,setup.database.core-api.enabled\","
            + "\"coreSetupDatabaseConfigShapes\":\"setup.database.*,setup.database-*\","
            + "\"coreSetupDatabaseTypedShapes\":\"setup.database.postgresql.*,setup.database.mysql.*,setup.database.mariadb.*,setup.database.core-api.*\","
            + "\"coreSetupDatabaseTypedCredentialKeys\":\"username,password,pool-size\","
            + "\"coreSetupDatabaseTypedHostMode\":\"requires-type-or-typed-url-inference\","
            + "\"coreSetupDatabaseTypedProbeOrder\":\"postgresql,mysql,mariadb\","
            + "\"coreSetupDatabaseCoreApiMode\":\"enabled-marker-no-core-jdbc\","
            + "\"coreSetupDatabaseEnv\":\"CI_DATABASE_TYPE,CI_JDBC_URL,CI_DB_USERNAME,CI_DB_PASSWORD,CI_DB_POOL_SIZE,CI_DB_FALLBACK_ENABLED,CI_DB_FALLBACK_ORDER\","
            + "\"coreSetupDatabasePrecedence\":\"env,nested-setup-database,legacy-flat-setup,database-default\","
            + "\"coreSetupDatabaseNameAliases\":\"setup.database.name,setup.database.database,setup.database-name\","
            + "\"coreSetupDatabaseJdbcAliases\":\"CI_JDBC_URL,setup.database.jdbc-url,setup.jdbc-url,database.jdbc-url\","
            + "\"coreSetupDatabaseTypeInference\":\"CI_DATABASE_TYPE,setup.database.type,setup.database-type,setup.database.core-api.enabled,CI_JDBC_URL,setup.database.jdbc-url,setup.jdbc-url,setup.database.postgresql.jdbc-url,setup.database.postgresql.url,setup.database.mysql.jdbc-url,setup.database.mysql.url,setup.database.mariadb.jdbc-url,setup.database.mariadb.url,typed-host-name,database.jdbc-url\","
            + "\"coreJdbcFallbackReason\":\"" + escape(coreJdbcFallbackReason(config)) + "\","
            + "\"coreJdbcFallbackActive\":" + coreJdbcFallbackActive(config) + ","
            + "\"coreJdbcFallbackStatus\":\"" + escape(coreJdbcFallbackStatus(config)) + "\","
            + "\"addonStateBulkSaveApi\":true,"
            + "\"addonStateBulkSaveGlobalEndpoint\":\"/v1/addons/state/table-key-value/bulk-save\","
            + "\"addonStateBulkSaveIslandEndpoint\":\"/v1/addons/islands/state/table-key-value/bulk-save\","
            + "\"addonStateTableKeyValueBulkSaveGlobalEndpoint\":\"/v1/addons/state/table/key-value/bulk-save\","
            + "\"addonStateTableKeyValueBulkSaveIslandEndpoint\":\"/v1/addons/islands/state/table/key-value/bulk-save\","
            + "\"addonStateTableKeyValueBulkSavePayload\":\"addonId,islandId(optional),values,tables\","
            + "\"addonStateTableKeyValueBulkSaveStorageMode\":\"table-prefix-flattened-key-value\","
            + "\"addonStateTableKeyValueBulkSaveFallback\":\"local-cache-on-core-api-failure\","
            + "\"databasePoolSize\":" + config.databasePoolSize() + ","
            + "\"storageType\":\"" + escape(config.storageType()) + "\","
            + "\"islandResourceModel\":\"global-resource\","
            + "\"islandPortableBundle\":true,"
            + "\"islandServerPinned\":false,"
            + "\"islandExecutionModel\":\"dynamic-island-node-pool\","
            + "\"islandNodeRole\":\"runtime-execution-node-only\","
            + "\"islandRoutingModel\":\"route-ticket-to-active-or-best-node\","
            + "\"createIslandRequestFlow\":\"velocity-command>core-createIsland>db-transaction-lock>node-allocator>create-island-job>agent-claim>template-restore>cell-allocate>runtime-active>route-ticket-ready>velocity-connect>paper-consume-ticket>spawn-teleport\","
            + "\"homeRequestFlow\":\"velocity-command>core-createHomeRoute>runtime-check>active-or-activate-best-node>route-ticket-ready>velocity-connect>paper-teleport\","
            + "\"visitRequestFlow\":\"velocity-command>target-island-lookup>public-ban-permission-check>active-or-activate>visitor-ticket>velocity-connect>visitor-spawn-teleport\","
            + "\"softFullRoutingPolicy\":\"new-and-inactive-islands-use-ready-node-active-owner-member-reserve-current-node-visitors-queue-or-limit\","
            + "\"moduleLayout\":\"api,common,protocol,core-client,core-service,velocity,paper,storage,migration,testkit,bom\","
            + "\"coreModuleLayout\":\"api,common,protocol,core-client,core-service,velocity,paper,storage,migration,testkit,bom\","
            + "\"addonModuleLayout\":\"satis\","
            + "\"distributionLayout\":\"plugins=paper+velocity,addons=satis,services=core-api,tools=migration\","
            + "\"satisPackaging\":\"official-external-addon\","
            + "\"satisCoreCoupling\":\"optional-addon-no-core-runtime-dependency\","
            + "\"satisAddonRemovalPolicy\":\"core-boots-and-islands-load-without-satis-jar\","
            + "\"satisDataRetentionPolicy\":\"addon-state-preserved-when-disabled-or-removed\","
            + "\"satisCoreBootRequiresAddon\":false,"
            + "\"satisCommandOwner\":\"optional-satis-paper-addon\","
            + "\"velocitySatisCommandPolicy\":\"no-direct-satis-command-handler-route-only\","
            + "\"paperSatisCommandPolicy\":\"addon-registers-own-commands-when-enabled\","
            + "\"islandPool\":\"" + escape(config.islandPool()) + "\","
            + "\"islandPoolNodeCount\":" + islandPoolNodeCount(config, nodes) + ","
            + "\"islandPoolRouteCandidateCount\":" + islandPoolRouteCandidateCount(config, nodes) + ","
            + "\"islandPoolScaleStatus\":\"" + escape(islandPoolScaleStatus(config, nodes)) + "\","
            + "\"islandPoolDegraded\":" + islandPoolDegraded(config, nodes) + ","
            + "\"islandPoolDuplicateVelocityServerNameNodeCount\":" + islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) + ","
            + "\"islandPoolDefaultNodeIdentityRiskCount\":" + islandPoolDefaultNodeIdentityRiskCount(config, nodes) + ","
            + "\"softFullPolicy\":\"" + escape(config.softFullPolicy()) + "\","
            + "\"hardFullPolicy\":\"" + escape(config.hardFullPolicy()) + "\","
            + "\"migrationPolicy\":\"" + escape(config.migrationPolicy()) + "\","
            + "\"superiorSkyblock2MigrationEnabled\":" + config.superiorSkyblock2MigrationEnabled() + ","
            + "\"superiorSkyblock2MigrationInputOnly\":true,"
            + "\"superiorSkyblock2RuntimeDependency\":false,"
            + "\"superiorSkyblock2RuntimePolicy\":\"migration-input-only-no-runtime-hooks\","
            + "\"routeTicketTtlSeconds\":" + config.routeTicketTtl().toSeconds() + ","
            + "\"routePreparingTicketTtlSeconds\":" + config.routePreparingTicketTtl().toSeconds() + ","
            + "\"heartbeatTimeoutSeconds\":" + config.heartbeatTimeout().toSeconds() + ","
            + "\"leaseDurationSeconds\":" + config.leaseDuration().toSeconds() + ","
            + "\"redisCacheTtlPolicy\":\"route-ticket=30s,player-island=300s,island-summary=60s,permissions=30s,locks=10-60s\","
            + "\"redisKeyPolicy\":\"ci:server:{nodeId}:*,ci:player:{uuid}:*,ci:island:{islandId}:*,ci:lock:*,ci:stream:*\","
            + "\"redisStreamPolicy\":\"jobs,events,audit-append-only-observability\","
            + "\"distributedLockPolicy\":\"redis-fast-lock-plus-postgresql-row-lock\","
            + "\"fencingTokenPolicy\":\"island_runtime.fencing_token-rejects-stale-node-writes\","
            + "\"staleWritePolicy\":\"current-fencing-token-required-before-snapshot-or-runtime-write\","
            + "\"snapshotKeepLatest\":" + config.snapshotKeepLatest() + ","
            + "\"snapshotKeepHourly\":" + snapshotPolicy.keepHourly() + ","
            + "\"snapshotKeepDaily\":" + snapshotPolicy.keepDaily() + ","
            + "\"snapshotKeepWeekly\":" + snapshotPolicy.keepWeekly() + ","
            + "\"snapshotKeepManual\":" + snapshotPolicy.keepManual() + ","
            + "\"snapshotCompress\":" + snapshotPolicy.compress() + ","
            + "\"snapshotChecksumAlgorithm\":\"" + escape(snapshotPolicy.checksumAlgorithm()) + "\","
            + "\"snapshotRetentionMode\":\"hourly-daily-weekly-manual\","
            + "\"snapshotRestorePipeline\":\"lock-restoring,evacuate-active-players,pre-restore-snapshot,restore-bundle,clear-runtime,reactivate,unlock\","
            + "\"coreApiAuthPolicy\":\"token-or-mtls-required\","
            + "\"adminPermissionPolicy\":\"separate-admin-permission-per-endpoint\","
            + "\"auditLogPolicy\":\"record-admin-player-and-system-actions\","
            + "\"controlChannelPolicy\":\"http-or-grpc-plus-redis-streams\","
            + "\"pluginMessagingPolicy\":\"not-used-for-critical-island-control\","
            + "\"pluginMessagingAllowedUse\":\"emergency-proxy-assist-only\","
            + "\"pluginMessagingForbiddenUse\":\"island-create-delete-save-migrate-routing-authority\","
            + "\"infrastructureExposurePolicy\":\"redis-postgresql-object-storage-private-only\","
            + "\"publicBindRiskPolicy\":\"require-ip-allowlist-or-private-bind\","
            + "\"adminApiEnabled\":" + config.adminApiEnabled() + ","
            + "\"requireMtls\":" + config.requireMtls() + ","
            + "\"ipAllowlistEnabled\":" + (config.ipAllowlist() != null && !config.ipAllowlist().isBlank()) + ","
            + "\"rateLimitRequests\":" + config.rateLimitRequests() + ","
            + "\"rateLimitWindowSeconds\":" + config.rateLimitWindow().toSeconds()
            + "}";
    }

    private static String migrationDisabledJson() {
        return "{\"code\":\"MIGRATION_DISABLED\",\"state\":\"DISABLED\",\"message\":\"SuperiorSkyblock2 migration is disabled by config\"}";
    }

    private static String jdbcBackend(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "NONE";
        }
        String value = jdbcUrl.toLowerCase(Locale.ROOT);
        if (value.startsWith("jdbc:postgresql:")) {
            return "POSTGRESQL";
        }
        if (value.startsWith("jdbc:mysql:")) {
            return "MYSQL";
        }
        if (value.startsWith("jdbc:mariadb:")) {
            return "MARIADB";
        }
        return "UNKNOWN";
    }

    private static boolean coreJdbcSupported(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:");
    }

    private static String coreJdbcFallbackReason(CoreServiceConfig config) {
        String configuredType = config.configuredDatabaseType() == null ? "" : config.configuredDatabaseType();
        if (!configuredType.isBlank() && !configuredType.equals("POSTGRESQL") && !configuredType.equals("UNKNOWN")) {
            return "CORE_JDBC_DISABLED_FOR_" + configuredType;
        }
        if (coreJdbcSupported(config.jdbcUrl())) {
            return "";
        }
        if ("JDBC".equalsIgnoreCase(config.repositoryMode()) || "JDBC".equalsIgnoreCase(config.jobQueueMode())) {
            return "CORE_JDBC_POSTGRESQL_ONLY";
        }
        return "CORE_JDBC_DISABLED_FOR_" + jdbcBackend(config.jdbcUrl());
    }

    private static boolean coreJdbcFallbackActive(CoreServiceConfig config) {
        String reason = coreJdbcFallbackReason(config);
        return reason != null && !reason.isBlank();
    }

    private static boolean coreSetupFallbackSafetyForced(CoreServiceConfig config) {
        return coreJdbcFallbackActive(config) && !config.setupDatabaseFallbackEnabled();
    }

    private static String coreSetupFallbackPolicy(CoreServiceConfig config) {
        if (!coreJdbcFallbackActive(config)) {
            return "native-postgresql-jdbc";
        }
        if (coreSetupFallbackSafetyForced(config)) {
            return "configured-disabled-but-forced-for-startup-safety";
        }
        return "configured-safe-fallback";
    }

    private static String coreSetupFallbackMode(CoreServiceConfig config) {
        if (!coreJdbcFallbackActive(config)) {
            return "NONE";
        }
        if (coreSetupFallbackSafetyForced(config)) {
            return "SAFETY_FORCED_IN_MEMORY_REPOSITORIES_AND_JOBS";
        }
        return "IN_MEMORY_REPOSITORIES_AND_JOBS";
    }

    private static String coreJdbcFallbackStatus(CoreServiceConfig config) {
        if (!coreJdbcFallbackActive(config)) {
            return "native-postgresql-jdbc";
        }
        return coreSetupFallbackPolicy(config)
            + ":repo=" + (config.jdbcRepositories() ? "JDBC" : "IN_MEMORY")
            + ",jobs=" + (config.jdbcJobs() ? "JDBC" : config.redisJobs() ? "REDIS" : "IN_MEMORY")
            + ",order=" + config.setupDatabaseFallbackOrder();
    }

    private static long islandPoolNodeCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        String pool = config.islandPool() == null || config.islandPool().isBlank() ? "island" : config.islandPool();
        return nodes.snapshot().stream()
            .filter(node -> pool.equals(node.pool() == null || node.pool().isBlank() ? "island" : node.pool()))
            .count();
    }

    private static long islandPoolRouteCandidateCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        String pool = config.islandPool() == null || config.islandPool().isBlank() ? "island" : config.islandPool();
        java.time.Instant now = java.time.Instant.now();
        return nodes.snapshot().stream()
            .filter(node -> pool.equals(node.pool() == null || node.pool().isBlank() ? "island" : node.pool()))
            .filter(node -> node.allocationBlockReason(now, config.heartbeatTimeout()).isBlank())
            .count();
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

    private static long islandPoolDuplicateVelocityServerNameNodeCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        String pool = config.islandPool() == null || config.islandPool().isBlank() ? "island" : config.islandPool();
        java.util.Map<String, Long> serverCounts = nodes.snapshot().stream()
            .filter(node -> pool.equals(node.pool() == null || node.pool().isBlank() ? "island" : node.pool()))
            .map(node -> node.velocityServerName() == null ? "" : node.velocityServerName().trim().toLowerCase(Locale.ROOT))
            .filter(server -> !server.isBlank())
            .collect(java.util.stream.Collectors.groupingBy(server -> server, java.util.stream.Collectors.counting()));
        return nodes.snapshot().stream()
            .filter(node -> pool.equals(node.pool() == null || node.pool().isBlank() ? "island" : node.pool()))
            .map(node -> node.velocityServerName() == null ? "" : node.velocityServerName().trim().toLowerCase(Locale.ROOT))
            .filter(server -> !server.isBlank() && serverCounts.getOrDefault(server, 0L) > 1L)
            .count();
    }

    private static long islandPoolDefaultNodeIdentityRiskCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        String pool = config.islandPool() == null || config.islandPool().isBlank() ? "island" : config.islandPool();
        return nodes.snapshot().stream()
            .filter(node -> pool.equals(node.pool() == null || node.pool().isBlank() ? "island" : node.pool()))
            .filter(node -> {
                String nodeId = node.nodeId() == null ? "" : node.nodeId().trim();
                String serverName = node.velocityServerName() == null ? "" : node.velocityServerName().trim();
                return nodeId.equalsIgnoreCase("island-1") || serverName.equalsIgnoreCase("Island-1");
            })
            .count();
    }

    private static void logSecurityPosture(CoreServiceConfig config) {
        if (config.coreToken() == null || config.coreToken().isBlank()) {
            LOGGER.warning("CloudIslands security: Core API token is empty; non-health requests will be rejected");
        }
        if (config.adminApiEnabled() && (config.adminToken() == null || config.adminToken().isBlank())) {
            LOGGER.warning("CloudIslands security: Admin API is enabled but admin token is empty; admin requests will be rejected");
        }
        if (!config.requireMtls()) {
            LOGGER.warning("CloudIslands security: Core API mTLS verification is disabled");
        }
        if ((config.ipAllowlist() == null || config.ipAllowlist().isBlank()) && publicBind(config.bind())) {
            LOGGER.warning("CloudIslands security: Core API is bound to " + config.bind() + " without an IP allowlist");
        }
        String jdbcFallbackReason = coreJdbcFallbackReason(config);
        if (jdbcFallbackReason != null && !jdbcFallbackReason.isBlank()) {
            LOGGER.warning("CloudIslands setup: Core JDBC repositories are disabled: " + jdbcFallbackReason
                + " configuredDatabaseType=" + config.configuredDatabaseType()
                + " jdbcBackend=" + jdbcBackend(config.jdbcUrl()));
        }
        warnIfPublicHost("Redis", config.redisUri() == null ? "" : config.redisUri().getHost());
        if (config.jdbcRepositories() || config.jdbcJobs()) {
            warnIfPublicHost("PostgreSQL", jdbcHost(config.jdbcUrl()));
        }
        if ("S3".equalsIgnoreCase(config.storageType())) {
            String storageHost = config.storageEndpoint() == null ? "" : config.storageEndpoint().getHost();
            warnIfPublicHost("Object storage", storageHost);
            if (config.storageEndpoint() != null && "http".equalsIgnoreCase(config.storageEndpoint().getScheme()) && !internalHost(storageHost)) {
                LOGGER.warning("CloudIslands security: Object storage endpoint uses plain HTTP on a non-internal host");
            }
        }
    }

    private static boolean publicBind(String bind) {
        return bind == null || bind.isBlank() || bind.equals("0.0.0.0") || bind.equals("::");
    }

    private static void warnIfPublicHost(String name, String host) {
        if (host == null || host.isBlank()) {
            return;
        }
        if (!internalHost(host)) {
            LOGGER.warning("CloudIslands security: " + name + " host does not look internal: " + host);
        }
    }

    private static boolean internalHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String value = host.toLowerCase(Locale.ROOT);
        if (value.equals("localhost") || value.endsWith(".local") || value.endsWith(".internal") || value.endsWith(".cluster.local")) {
            return true;
        }
        if (value.startsWith("127.") || value.startsWith("10.") || value.startsWith("192.168.")) {
            return true;
        }
        if (value.startsWith("172.")) {
            String[] parts = value.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }

    private static String jdbcHost(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        int scheme = jdbcUrl.indexOf("://");
        if (scheme < 0) {
            return "";
        }
        int hostStart = scheme + 3;
        int slash = jdbcUrl.indexOf('/', hostStart);
        String authority = slash < 0 ? jdbcUrl.substring(hostStart) : jdbcUrl.substring(hostStart, slash);
        int at = authority.lastIndexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        int colon = authority.indexOf(':');
        return colon < 0 ? authority : authority.substring(0, colon);
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
            if (!path.equals("/health") && !tokenGuard.allowed(exchange)) {
                auditSecurityReject("UNAUTHORIZED", path, exchange);
                write(exchange, 401, ApiResponses.error("UNAUTHORIZED", "Missing or invalid API token"));
                return;
            }
            if (!path.equals("/health") && !mtlsGuard.allowed(exchange)) {
                auditSecurityReject("MTLS_REQUIRED", path, exchange);
                write(exchange, 401, ApiResponses.error("MTLS_REQUIRED", "mTLS verification is required"));
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
            handler.handle(exchange);
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
            if (!tokenGuard.allowed(exchange)) {
                auditSecurityReject("UNAUTHORIZED", exchange.getRequestURI().getPath(), exchange);
                write(exchange, 401, ApiResponses.error("UNAUTHORIZED", "Missing or invalid API token"));
                return;
            }
            if (!mtlsGuard.allowed(exchange)) {
                auditSecurityReject("MTLS_REQUIRED", exchange.getRequestURI().getPath(), exchange);
                write(exchange, 401, ApiResponses.error("MTLS_REQUIRED", "mTLS verification is required"));
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

    private static void lifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result) throws IOException {
        write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\"}");
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

    private static void routeResult(HttpExchange exchange, RoutePreparationResult result) throws IOException {
        write(exchange, result.status(), result.body());
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
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.DELETE_ISLAND, islandId, targetNode, 50, Map.of("reason", reason, "ownerUuid", ownerUuid.toString(), "fencingToken", fencingToken), java.time.Instant.now()));
            events.publish(CloudIslandEventType.ISLAND_DELETE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "targetNode", targetNode, "reason", reason));
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
            IslandStorage.StoredBundle storedBundle = deleteStorage.writeDeleteBackupFromLatest(islandId, snapshotNo, reason == null || reason.isBlank() ? "BEFORE_DELETE" : reason);
            String storagePath = storedBundle.storagePath() == null || storedBundle.storagePath().isBlank()
                ? "islands/" + islandId + "/backups/delete-" + String.format("%06d", snapshotNo) + "/bundle.tar.zst"
                : storedBundle.storagePath();
            recordSnapshotAndPublish(islandId, snapshotNo, storagePath, reason, storedBundle.checksum(), storedBundle.sizeBytes(), "");
            deleteStorage.deleteLiveState(islandId);
            return true;
        } catch (IOException exception) {
            events.publish("ISLAND_DELETE_BACKUP_FAILED", Map.of("islandId", islandId.toString(), "reason", reason, "error", exception.getMessage() == null ? "" : exception.getMessage()));
            return false;
        }
    }

    private int recordSnapshotAndPublish(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId) {
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
            "pruned", Integer.toString(pruned)
        ));
        return pruned;
    }

    private static String jobsJson(IslandJobQueue jobs) {
        if (jobs instanceof InMemoryIslandJobPublisher memoryJobs) {
            return memoryJobs.toJson();
        }
        if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
            return jdbcJobs.toJson();
        }
        return "{\"mode\":\"REDIS\"}";
    }

    private static String deleteResultJson(DeleteIslandResult result) {
        return "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\",\"islandId\":\"" + result.islandId() + "\"}";
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

    private static String templatesJson(java.util.List<IslandTemplateSnapshot> templates) {
        StringBuilder builder = new StringBuilder("{\"templates\":[");
        boolean first = true;
        for (IslandTemplateSnapshot template : templates) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(templateJson(template));
        }
        return builder.append("]}").toString();
    }

    private static String templateJson(IslandTemplateSnapshot template) {
        return "{\"id\":\"" + escape(template.id())
            + "\",\"displayName\":\"" + escape(template.displayName())
            + "\",\"enabled\":" + template.enabled()
            + ",\"minNodeVersion\":\"" + escape(template.minNodeVersion())
            + "\"}";
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

    private static String routeDebugJson(UUID playerUuid, PlayerRouteSession session, RouteTicket ticket) {
        return "{\"playerUuid\":\"" + playerUuid
            + "\",\"session\":" + (session == null ? "null" : maskRouteNonces(sessionJson(session)))
            + ",\"ticket\":" + (ticket == null ? "null" : maskRouteNonces(RoutingOrchestrator.toJson(ticket)))
            + "}";
    }

    private static String maskRouteNonces(String json) {
        String needle = "\"nonce\":\"";
        StringBuilder masked = new StringBuilder(json);
        int start = masked.indexOf(needle);
        while (start >= 0) {
            int valueStart = start + needle.length();
            int valueEnd = masked.indexOf("\"", valueStart);
            if (valueEnd < 0) {
                break;
            }
            masked.replace(valueStart, valueEnd, "hidden");
            start = masked.indexOf(needle, valueStart + "hidden".length());
        }
        if (masked.length() == json.length() && masked.indexOf(needle) < 0) {
            return json;
        }
        return masked.toString();
    }

    private static String routeSessionsJson(RouteSessionStore sessions) {
        if (sessions instanceof InMemoryRouteSessionStore memorySessions) {
            return memorySessions.toJson();
        }
        if (sessions instanceof RedisRouteSessionStore redisSessions) {
            return redisSessions.toJson();
        }
        return "{\"sessions\":[]}";
    }

    private static java.util.Optional<PlayerRouteSession> findAnyRouteSession(RouteSessionStore sessions, UUID playerUuid) {
        if (sessions instanceof InMemoryRouteSessionStore memorySessions) {
            return memorySessions.findAny(playerUuid);
        }
        if (sessions instanceof RedisRouteSessionStore redisSessions) {
            return redisSessions.findAny(playerUuid);
        }
        return java.util.Optional.empty();
    }

    private static String sessionJson(PlayerRouteSession session) {
        return "{\"playerUuid\":\"" + session.playerUuid() + "\",\"ticketId\":\"" + session.ticketId() + "\",\"targetNode\":\"" + session.targetNode() + "\",\"targetServerName\":\"" + session.targetServerName() + "\",\"nonce\":\"" + session.nonce() + "\",\"expiresAt\":\"" + session.expiresAt() + "\"}";
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

    private static java.util.List<kr.lunaf.cloudislands.protocol.job.IslandJobType> supportedJobTypes(String value) {
        if (value == null || value.isBlank()) {
            return java.util.List.of(
                kr.lunaf.cloudislands.protocol.job.IslandJobType.CREATE_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.ACTIVATE_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.SAVE_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.DEACTIVATE_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.SNAPSHOT_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.DELETE_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.MIGRATE_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.RESTORE_ISLAND,
                kr.lunaf.cloudislands.protocol.job.IslandJobType.RESET_ISLAND
            );
        }
        java.util.List<kr.lunaf.cloudislands.protocol.job.IslandJobType> result = new java.util.ArrayList<>();
        for (String part : value.split(",")) {
            try {
                result.add(kr.lunaf.cloudislands.protocol.job.IslandJobType.valueOf(part.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown worker capabilities.
            }
        }
        return result.isEmpty() ? supportedJobTypes("") : java.util.List.copyOf(result);
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

    private static boolean requireIslandPermission(HttpExchange exchange, IslandRepository islandRepository, IslandMetadataRepository metadataRepository, IslandPermissionRuleRepository permissionRules, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        CachedPermissionSet permissions = DefaultIslandPermissions.create();
        for (var rule : permissionRules.list(islandId)) {
            permissions.put(rule.role(), rule.permission(), rule.allowed());
        }
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissions.allowed(member.role(), permission));
        if (owner || allowed) {
            return true;
        }
        write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island permission " + permission.name() + " is required"));
        return false;
    }

    private static IslandRole memberRole(java.util.List<kr.lunaf.cloudislands.api.model.IslandMemberSnapshot> members, UUID playerUuid) {
        return members.stream()
            .filter(member -> member.playerUuid().equals(playerUuid))
            .map(kr.lunaf.cloudislands.api.model.IslandMemberSnapshot::role)
            .findFirst()
            .orElse(null);
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

    private static boolean islandFlagEnabled(IslandMetadataRepository metadataRepository, UUID islandId, IslandFlag flag) {
        String value = metadataRepository.flags(islandId).values().getOrDefault(flag, "false");
        return value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("allow")
            || value.equalsIgnoreCase("allowed")
            || value.equalsIgnoreCase("enabled")
            || value.equalsIgnoreCase("on");
    }

    private static IslandLocation location(String body) {
        return new IslandLocation(
            JsonFields.text(body, "worldName", ""),
            JsonFields.decimal(body, "localX", 0.5D),
            JsonFields.decimal(body, "localY", 100.0D),
            JsonFields.decimal(body, "localZ", 0.5D),
            (float) JsonFields.decimal(body, "yaw", 0.0D),
            (float) JsonFields.decimal(body, "pitch", 0.0D)
        );
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
            "storagePath", snapshot.get().storagePath() == null ? "" : snapshot.get().storagePath()
        ));
        lifecycle(exchange, result);
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
            "storagePath", snapshot.get().storagePath() == null ? "" : snapshot.get().storagePath()
        ));
        lifecycle(exchange, result);
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

    private static String invitesJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandInviteSnapshot> invites) {
        StringBuilder builder = new StringBuilder("{\"invites\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandInviteSnapshot invite : invites) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"inviteId\":\"").append(invite.inviteId()).append("\",")
                .append("\"islandId\":\"").append(invite.islandId()).append("\",")
                .append("\"inviterUuid\":\"").append(invite.inviterUuid()).append("\",")
                .append("\"targetUuid\":\"").append(invite.targetUuid()).append("\",")
                .append("\"state\":\"").append(invite.state()).append("\",")
                .append("\"createdAt\":\"").append(invite.createdAt()).append("\",")
                .append("\"expiresAt\":\"").append(invite.expiresAt()).append("\"")
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
        String safeTable = safeTableName(table);
        java.util.HashMap<String, String> state = new java.util.HashMap<>();
        values.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                state.put(tableStatePrefix(safeTable) + key.trim(), value);
            }
        });
        return Map.copyOf(state);
    }

    private static int tableKeyCount(Map<String, Map<String, String>> tables) {
        if (tables == null || tables.isEmpty()) {
            return 0;
        }
        return tables.values().stream()
            .filter(java.util.Objects::nonNull)
            .mapToInt(Map::size)
            .sum();
    }

    private static String tableStatePrefix(String table) {
        return "table/" + safeTableName(table) + "/";
    }

    private static String safeTableName(String table) {
        String value = table == null ? "" : table.trim();
        if (value.startsWith("table/")) {
            value = value.substring("table/".length());
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
        return value;
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

    private static String addonStateJson(Map<String, String> values) {
        StringBuilder builder = new StringBuilder("{\"values\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : (values == null ? Map.<String, String>of() : values).entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append("}}").toString();
    }

    private static String addonStateSummaryJson(Map<String, Integer> globalCounts, Map<String, Integer> islandCounts) {
        java.util.TreeSet<String> addonIds = new java.util.TreeSet<>();
        addonIds.addAll(globalCounts == null ? Map.<String, Integer>of().keySet() : globalCounts.keySet());
        addonIds.addAll(islandCounts == null ? Map.<String, Integer>of().keySet() : islandCounts.keySet());
        StringBuilder builder = new StringBuilder("{\"addons\":[");
        boolean first = true;
        for (String addonId : addonIds) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            int globalKeys = globalCounts == null ? 0 : globalCounts.getOrDefault(addonId, 0);
            int islandKeys = islandCounts == null ? 0 : islandCounts.getOrDefault(addonId, 0);
            builder.append("{\"addonId\":\"").append(escape(addonId))
                .append("\",\"globalKeys\":").append(globalKeys)
                .append(",\"islandKeys\":").append(islandKeys)
                .append(",\"totalKeys\":").append(globalKeys + islandKeys)
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static NodeHeartbeatRequest heartbeat(String body) {
        return new NodeHeartbeatRequest(JsonFields.integer(body, "protocolVersion", NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION), JsonFields.text(body, "nodeId", "unknown"), JsonFields.text(body, "pool", "island"), JsonFields.text(body, "velocityServerName", JsonFields.text(body, "nodeId", "unknown")), JsonFields.text(body, "nodeVersion", ""), JsonFields.enumValue(NodeState.class, body, "state", NodeState.READY), JsonFields.integer(body, "players", 0), JsonFields.integer(body, "softPlayerCap", 90), JsonFields.integer(body, "hardPlayerCap", 110), JsonFields.integer(body, "reservedSlots", 0), JsonFields.integer(body, "activeIslands", 0), JsonFields.integer(body, "maxActiveIslands", 600), JsonFields.decimal(body, "mspt", 20.0D), JsonFields.integer(body, "activationQueue", 0), JsonFields.integer(body, "maxActivationQueue", 20), JsonFields.decimal(body, "chunkLoadPressure", 0.0D), JsonFields.longValue(body, "heapUsedMb", 0L), JsonFields.longValue(body, "heapMaxMb", 1L), JsonFields.integer(body, "recentFailurePenalty", 0), JsonFields.bool(body, "storageAvailable", true), JsonFields.text(body, "supportedTemplates", "*"));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", java.util.List.of(contentType));
        exchange.getResponseHeaders().put("Cache-Control", java.util.List.of("no-store"));
        exchange.getResponseHeaders().put("Pragma", java.util.List.of("no-cache"));
        exchange.getResponseHeaders().put("X-Content-Type-Options", java.util.List.of("nosniff"));
        exchange.getResponseHeaders().put("X-Frame-Options", java.util.List.of("DENY"));
        exchange.getResponseHeaders().put("Referrer-Policy", java.util.List.of("no-referrer"));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
