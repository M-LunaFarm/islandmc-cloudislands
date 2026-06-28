package kr.lunaf.cloudislands.coreservice;

import static kr.lunaf.cloudislands.coreservice.config.CoreNetworkExposure.logSecurityPosture;

import java.time.Clock;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.addon.AddonStateRepository;
import kr.lunaf.cloudislands.coreservice.addon.InMemoryAddonStateRepository;
import kr.lunaf.cloudislands.coreservice.addon.JdbcAddonStateRepository;
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
import kr.lunaf.cloudislands.coreservice.generator.CachingIslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.generator.InMemoryIslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.generator.IslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.generator.JdbcIslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRouteRegistrar;
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
import kr.lunaf.cloudislands.coreservice.ranking.CachingIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.CachingRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.JdbcIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.JdbcRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRepository;
import kr.lunaf.cloudislands.coreservice.redis.RedisStreamWriterAdapter;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.JdbcIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.JdbcIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.JdbcIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.review.InMemoryIslandReviewRepository;
import kr.lunaf.cloudislands.coreservice.review.IslandReviewRepository;
import kr.lunaf.cloudislands.coreservice.review.JdbcIslandReviewRepository;
import kr.lunaf.cloudislands.coreservice.role.CachingIslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.role.InMemoryIslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.role.JdbcIslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.CoreApiAuthGuard;
import kr.lunaf.cloudislands.coreservice.security.CoreAuthMode;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.ForwardedClientIpResolver;
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
import kr.lunaf.cloudislands.coreservice.upgrade.InMemoryIslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.JdbcIslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.warehouse.InMemoryIslandWarehouseRepository;
import kr.lunaf.cloudislands.coreservice.warehouse.IslandWarehouseRepository;
import kr.lunaf.cloudislands.coreservice.warehouse.JdbcIslandWarehouseRepository;

final class CoreBootstrap {
    private CoreBootstrap() {
    }

    static CoreInfrastructure infrastructure(CoreServiceConfig config, Clock clock, Logger logger) {
        CoreAuthMode authMode = config.authMode();
        CoreApiAuthGuard authGuard = new CoreApiAuthGuard(
            authMode,
            new ApiTokenGuard(config.coreToken(), config.nodeCredentialBindings()),
            new MtlsHeaderGuard(authMode.acceptsMtls(), config.mtlsVerifiedHeader(), config.mtlsVerifiedValue(), config.mtlsTrustedProxies())
        );
        CoreHttpRouteRegistrar routeRegistrar = routeRegistrar(config, clock, authGuard, config.publicAdminApiEnabled());
        CoreHttpRouteRegistrar adminRouteRegistrar = config.adminListenerActive()
            ? routeRegistrar(config, clock, authGuard, true)
            : null;

        logSecurityPosture(logger, config);
        config.validateStartupSecurity();

        MeteredDataSource meteredDataSource = new MeteredDataSource(new BoundedDataSource(
            new DriverManagerDataSource(config.jdbcUrl(), config.databaseUsername(), config.databasePassword()),
            config.databasePoolSize()
        ));
        DataSource dataSource = new JdbcDialectDataSource(meteredDataSource);
        boolean coreJdbcActive = config.jdbcRepositories() || config.jdbcJobs();
        if (coreJdbcActive && config.setupDatabaseAutoSchema()) {
            logger.info("CloudIslands database schema bootstrap applied=" + JdbcSchemaBootstrap.apply(dataSource));
        }
        RedisStreamWriterAdapter redisEventWriter = config.redisEvents() ? new RedisStreamWriterAdapter(config.redisUri()) : null;
        RedisStreamEventPublisher redisEventPublisher = redisEventWriter == null ? null : new RedisStreamEventPublisher(redisEventWriter);
        RedisCacheAdmin redisCacheAdmin = config.redisEvents() || config.redisJobs() ? new RedisCacheAdmin(config.redisUri()) : null;
        RedisActivationLock activationLock = config.redisEvents() || config.redisJobs()
            ? new RedisActivationLock(config.redisUri(), config.routePreparingTicketTtl(), config.redisLockLocalFallbackEnabled())
            : null;
        RedisPlayerCreationLock playerCreationLock = config.redisEvents() || config.redisJobs()
            ? new RedisPlayerCreationLock(config.redisUri(), config.routePreparingTicketTtl(), config.redisLockLocalFallbackEnabled())
            : null;
        return new CoreInfrastructure(
            authGuard,
            routeRegistrar,
            adminRouteRegistrar,
            meteredDataSource,
            dataSource,
            coreJdbcActive,
            redisEventWriter,
            redisEventPublisher,
            redisCacheAdmin,
            activationLock,
            playerCreationLock
        );
    }

    static CoreRepositories repositories(CoreServiceConfig config, Clock clock, CoreInfrastructure infrastructure) {
        DataSource dataSource = infrastructure.dataSource();
        boolean redisEnabled = config.redisEvents() || config.redisJobs();
        NodeRegistry baseNodes = config.jdbcRepositories() ? new JdbcNodeRegistry(dataSource) : new InMemoryNodeRegistry();
        NodeRegistry nodes = redisEnabled
            ? new CachingNodeRegistry(baseNodes, config.redisUri(), config.heartbeatTimeout())
            : baseNodes;
        NodeAllocator allocator = new NodeAllocator(config.heartbeatTimeout(), config.softFullPolicy(), config.hardFullPolicy());
        RouteTicketStore baseTickets = config.jdbcRepositories() ? new JdbcRouteTicketStore(dataSource, clock) : new InMemoryRouteTicketStore(clock);
        RouteTicketStore tickets = redisEnabled
            ? new CachingRouteTicketStore(baseTickets, config.redisUri())
            : baseTickets;
        RouteSessionStore sessions = redisEnabled
            ? new RedisRouteSessionStore(config.redisUri())
            : new InMemoryRouteSessionStore(clock);
        IslandJobQueue jobs = config.jdbcJobs()
            ? new JdbcIslandJobQueue(dataSource, clock, config.leaseDuration())
            : config.redisJobs()
                ? new RedisIslandJobQueue(config.redisUri(), config.leaseDuration())
                : new InMemoryIslandJobPublisher();
        InMemoryGlobalEventPublisher inMemoryEvents = new InMemoryGlobalEventPublisher();
        GlobalEventPublisher events = infrastructure.redisEventPublisher() != null
            ? new CompositeGlobalEventPublisher(List.of(inMemoryEvents, infrastructure.redisEventPublisher()))
            : inMemoryEvents;

        IslandRepository baseIslandRepository = config.jdbcRepositories() ? new JdbcIslandRepository(dataSource) : new InMemoryIslandRepository();
        IslandRepository islandRepository = redisEnabled
            ? new CachingIslandRepository(baseIslandRepository, config.redisUri())
            : baseIslandRepository;
        IslandMetadataRepository baseMetadataRepository = config.jdbcRepositories() ? new JdbcIslandMetadataRepository(dataSource) : new InMemoryIslandMetadataRepository();
        IslandMetadataRepository metadataRepository = redisEnabled
            ? new CachingIslandMetadataRepository(baseMetadataRepository, config.redisUri())
            : baseMetadataRepository;
        PlayerProfileRepository basePlayerProfiles = config.jdbcRepositories() ? new JdbcPlayerProfileRepository(dataSource) : new InMemoryPlayerProfileRepository();
        PlayerProfileRepository playerProfiles = redisEnabled
            ? new CachingPlayerProfileRepository(basePlayerProfiles, config.redisUri())
            : basePlayerProfiles;
        IslandPermissionRuleRepository basePermissionRules = config.jdbcRepositories() ? new JdbcIslandPermissionRuleRepository(dataSource) : new InMemoryIslandPermissionRuleRepository();
        IslandPermissionRuleRepository permissionRules = redisEnabled
            ? new CachingIslandPermissionRuleRepository(basePermissionRules, config.redisUri())
            : basePermissionRules;
        IslandRoleRepository baseRoleRepository = config.jdbcRepositories() ? new JdbcIslandRoleRepository(dataSource) : new InMemoryIslandRoleRepository();
        IslandRoleRepository roleRepository = redisEnabled
            ? new CachingIslandRoleRepository(baseRoleRepository, config.redisUri())
            : baseRoleRepository;
        IslandRuntimeRepository baseRuntimeRepository = config.jdbcRepositories() ? new JdbcIslandRuntimeRepository(dataSource) : new InMemoryIslandRuntimeRepository();
        IslandRuntimeRepository runtimeRepository = redisEnabled
            ? new CachingIslandRuntimeRepository(baseRuntimeRepository, config.redisUri())
            : baseRuntimeRepository;
        IslandSnapshotRepository baseSnapshotRepository = config.jdbcRepositories() ? new JdbcIslandSnapshotRepository(dataSource) : new InMemoryIslandSnapshotRepository();
        IslandSnapshotRepository snapshotRepository = redisEnabled
            ? new CachingIslandSnapshotRepository(baseSnapshotRepository, config.redisUri())
            : baseSnapshotRepository;
        RankingRepository baseRankingRepository = config.jdbcRepositories() ? new JdbcRankingRepository(dataSource) : new InMemoryRankingRepository();
        RankingRepository rankingRepository = redisEnabled
            ? new CachingRankingRepository(baseRankingRepository, config.redisUri())
            : baseRankingRepository;
        IslandLevelRepository baseLevelRepository = config.jdbcRepositories() ? new JdbcIslandLevelRepository(dataSource) : new InMemoryIslandLevelRepository();
        IslandLevelRepository levelRepository = redisEnabled
            ? new CachingIslandLevelRepository(baseLevelRepository, config.redisUri())
            : baseLevelRepository;
        IslandUpgradeRepository baseUpgradeRepository = config.jdbcRepositories() ? new JdbcIslandUpgradeRepository(dataSource) : new InMemoryIslandUpgradeRepository();
        IslandUpgradeRepository upgradeRepository = redisEnabled
            ? new CachingIslandUpgradeRepository(baseUpgradeRepository, config.redisUri())
            : baseUpgradeRepository;
        IslandBankRepository baseBankRepository = config.jdbcRepositories() ? new JdbcIslandBankRepository(dataSource) : new InMemoryIslandBankRepository();
        IslandBankRepository bankRepository = redisEnabled
            ? new CachingIslandBankRepository(baseBankRepository, config.redisUri())
            : baseBankRepository;
        IslandMissionRepository baseMissionRepository = config.jdbcRepositories() ? new JdbcIslandMissionRepository(dataSource) : new InMemoryIslandMissionRepository();
        IslandMissionRepository missionRepository = redisEnabled
            ? new CachingIslandMissionRepository(baseMissionRepository, config.redisUri())
            : baseMissionRepository;
        IslandGeneratorRepository baseGeneratorRepository = config.jdbcRepositories() ? new JdbcIslandGeneratorRepository(dataSource) : new InMemoryIslandGeneratorRepository();
        IslandGeneratorRepository generatorRepository = redisEnabled
            ? new CachingIslandGeneratorRepository(baseGeneratorRepository, config.redisUri())
            : baseGeneratorRepository;
        IslandLimitRepository baseLimitRepository = config.jdbcRepositories() ? new JdbcIslandLimitRepository(dataSource) : new InMemoryIslandLimitRepository();
        IslandLimitRepository limitRepository = redisEnabled
            ? new CachingIslandLimitRepository(baseLimitRepository, config.redisUri())
            : baseLimitRepository;
        IslandTemplateRepository baseTemplateRepository = config.jdbcRepositories() ? new JdbcIslandTemplateRepository(dataSource) : new InMemoryIslandTemplateRepository();
        IslandTemplateRepository templateRepository = redisEnabled
            ? new CachingIslandTemplateRepository(baseTemplateRepository, config.redisUri())
            : baseTemplateRepository;
        AddonStateRepository addonStates = config.jdbcRepositories() ? new JdbcAddonStateRepository(dataSource) : new InMemoryAddonStateRepository();
        IslandReviewRepository reviewRepository = config.jdbcRepositories() ? new JdbcIslandReviewRepository(dataSource) : new InMemoryIslandReviewRepository();
        IslandWarehouseRepository warehouseRepository = config.jdbcRepositories() ? new JdbcIslandWarehouseRepository(dataSource) : new InMemoryIslandWarehouseRepository();
        AuditLogger baseAudit = config.jdbcRepositories() ? new JdbcAuditLogger(dataSource) : new InMemoryAuditLogger();
        AuditLogger audit = infrastructure.redisEventWriter() == null
            ? baseAudit
            : new RedisAuditLogger(baseAudit, infrastructure.redisEventWriter(), RedisKeys.auditStream());
        infrastructure.routeRegistrar().setAudit(audit);
        IslandLogRepository baseIslandLogs = config.jdbcRepositories() ? new JdbcIslandLogRepository(dataSource) : new InMemoryIslandLogRepository();
        IslandLogRepository islandLogs = redisEnabled
            ? new CachingIslandLogRepository(baseIslandLogs, config.redisUri())
            : baseIslandLogs;
        return new CoreRepositories(
            nodes,
            allocator,
            tickets,
            sessions,
            jobs,
            inMemoryEvents,
            events,
            islandRepository,
            metadataRepository,
            playerProfiles,
            permissionRules,
            roleRepository,
            runtimeRepository,
            snapshotRepository,
            rankingRepository,
            levelRepository,
            upgradeRepository,
            bankRepository,
            missionRepository,
            generatorRepository,
            limitRepository,
            templateRepository,
            addonStates,
            reviewRepository,
            warehouseRepository,
            audit,
            islandLogs
        );
    }

    private static CoreHttpRouteRegistrar routeRegistrar(
        CoreServiceConfig config,
        Clock clock,
        CoreApiAuthGuard authGuard,
        boolean publicAdminApiEnabled
    ) {
        return new CoreHttpRouteRegistrar(
            new FixedWindowRateLimiter(clock, config.rateLimitRequests(), config.rateLimitWindow().toMillis()),
            authGuard,
            new ForwardedClientIpResolver(config.mtlsTrustedProxies()),
            new IpAllowlist(config.ipAllowlist()),
            new AdminEndpointGuard(config.adminToken(), config.adminApiEnabled(), publicAdminApiEnabled, config.adminPermissions())
        );
    }
}
