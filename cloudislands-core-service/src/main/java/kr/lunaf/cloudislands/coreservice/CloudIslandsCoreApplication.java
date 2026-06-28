package kr.lunaf.cloudislands.coreservice;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.coreservice.addon.AddonStateRepository;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.cache.RedisCacheAdmin;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.db.MeteredDataSource;
import kr.lunaf.cloudislands.coreservice.event.FailFastGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.OutboxGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.RedisStreamEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRequestExecutor;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRouteRegistrar;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminIslandLifecycleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminGeneratorRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminNodeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminRuntimeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AddonRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AuditRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.CoreConfigRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.EventRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.GeneratorRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.HealthRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandBankRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandBlockLevelRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandCatalogRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandCommunicationRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandMemberRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandPlayerLifecycleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandQueryRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandReviewRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandSettingsRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandSnapshotRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandUpgradeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandVisitorRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandWarpRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.IslandWarehouseRoutes;
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
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.job.JobCompletionOutboxDispatcher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.generator.IslandGeneratorRepository;
import kr.lunaf.cloudislands.coreservice.metrics.CoreMetricsFactory;
import kr.lunaf.cloudislands.coreservice.metrics.PrometheusMetricsRenderer;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.DirtyRankingRecalculationTask;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRepository;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.review.IslandReviewRepository;
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionStore;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.coreservice.upgrade.ConfigUpgradePolicy;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeService;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.coreservice.warehouse.IslandWarehouseRepository;
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import kr.lunaf.cloudislands.migration.rollback.CompositeRollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService.RollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.StorageRollbackTarget;
import kr.lunaf.cloudislands.migration.rollback.jdbc.JdbcMigrationRollbackTarget;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.LocalIslandStorage;
import kr.lunaf.cloudislands.storage.s3.S3IslandStorage;

public final class CloudIslandsCoreApplication {
    private static final Logger LOGGER = Logger.getLogger(CloudIslandsCoreApplication.class.getName());
    private final CoreLifecycle coreLifecycle;
    private final CoreIslandDeleteService islandDeleteService;
    private final int snapshotKeepLatest;

    public CloudIslandsCoreApplication(int port) throws IOException {
        this(CoreServiceConfig.fromEnvironment().withPort(port));
    }

    public CloudIslandsCoreApplication(CoreServiceConfig config) throws IOException {
        Clock clock = Clock.systemUTC();
        CoreInfrastructure infrastructure = CoreBootstrap.infrastructure(config, clock, LOGGER);
        CoreRepositories repositories = CoreBootstrap.repositories(config, clock, infrastructure);
        CoreHttpRouteRegistrar routeRegistrar = infrastructure.routeRegistrar();
        CoreHttpRouteRegistrar adminRouteRegistrar = infrastructure.adminRouteRegistrar();
        DataSource dataSource = infrastructure.dataSource();
        MeteredDataSource meteredDataSource = infrastructure.meteredDataSource();
        boolean coreJdbcActive = infrastructure.coreJdbcActive();
        RedisStreamEventPublisher redisEventPublisher = infrastructure.redisEventPublisher();
        RedisCacheAdmin redisCacheAdmin = infrastructure.redisCacheAdmin();
        RedisActivationLock activationLock = infrastructure.activationLock();
        RedisPlayerCreationLock playerCreationLock = infrastructure.playerCreationLock();
        NodeRegistry nodes = repositories.nodes();
        NodeAllocator allocator = repositories.allocator();
        RouteTicketStore tickets = repositories.tickets();
        RouteSessionStore sessions = repositories.sessions();
        IslandJobQueue jobs = repositories.jobs();
        InMemoryGlobalEventPublisher inMemoryEvents = repositories.inMemoryEvents();
        GlobalEventPublisher immediateEvents = repositories.events();
        IslandRepository islandRepository = repositories.islandRepository();
        IslandMetadataRepository metadataRepository = repositories.metadataRepository();
        PlayerProfileRepository playerProfiles = repositories.playerProfiles();
        IslandPermissionRuleRepository permissionRules = repositories.permissionRules();
        IslandRoleRepository roleRepository = repositories.roleRepository();
        IslandRuntimeRepository runtimeRepository = repositories.runtimeRepository();
        IslandSnapshotRepository snapshotRepository = repositories.snapshotRepository();
        RankingRepository rankingRepository = repositories.rankingRepository();
        IslandLevelRepository levelRepository = repositories.levelRepository();
        IslandUpgradeRepository upgradeRepository = repositories.upgradeRepository();
        IslandBankRepository bankRepository = repositories.bankRepository();
        IslandMissionRepository missionRepository = repositories.missionRepository();
        IslandGeneratorRepository generatorRepository = repositories.generatorRepository();
        IslandLimitRepository limitRepository = repositories.limitRepository();
        IslandTemplateRepository templateRepository = repositories.templateRepository();
        AddonStateRepository addonStates = repositories.addonStates();
        IslandReviewRepository reviewRepository = repositories.reviewRepository();
        IslandWarehouseRepository warehouseRepository = repositories.warehouseRepository();
        AuditLogger audit = repositories.audit();
        IslandLogRepository islandLogs = repositories.islandLogs();
        IslandStorage deleteStorage = migrationRollbackStorage(config);
        this.snapshotKeepLatest = Math.max(1, config.snapshotKeepLatest());
        kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy snapshotRetentionPolicy = config.snapshotRetentionPolicy();
        kr.lunaf.cloudislands.coreservice.ranking.ConfigBlockValues.load(config.blockValuesFile()).forEach(levelRepository::putBlockValue);
        kr.lunaf.cloudislands.coreservice.job.JobCompletionReceiptStore completionReceipts = config.jdbcRepositories()
            ? new kr.lunaf.cloudislands.coreservice.job.JdbcJobCompletionReceiptStore(dataSource)
            : new kr.lunaf.cloudislands.coreservice.job.InMemoryJobCompletionReceiptStore();
        kr.lunaf.cloudislands.coreservice.job.JobCompletionOutboxStore completionOutbox = config.jdbcRepositories()
            ? new kr.lunaf.cloudislands.coreservice.job.JdbcJobCompletionOutboxStore(dataSource)
            : new kr.lunaf.cloudislands.coreservice.job.InMemoryJobCompletionOutboxStore();
        GlobalEventPublisher completionEvents = redisEventPublisher == null
            ? immediateEvents
            : new FailFastGlobalEventPublisher(List.of(redisEventPublisher.rethrowingPublisher(), inMemoryEvents));
        JobCompletionOutboxDispatcher completionOutboxDispatcher = new JobCompletionOutboxDispatcher(completionOutbox, completionEvents);
        GlobalEventPublisher events = config.jdbcRepositories()
            ? new OutboxGlobalEventPublisher(completionOutbox)
            : immediateEvents;
        RankingRecalculationService levelRecalculation = new RankingRecalculationService(rankingRepository, events, config.levelFormulaExpression(), config.worthFormulaType());
        DirtyRankingRecalculationTask rankingRecalculationTask = new DirtyRankingRecalculationTask(rankingRepository, levelRepository, metadataRepository, levelRecalculation);
        UpgradePolicy upgradePolicy = ConfigUpgradePolicy.load(config.upgradesFile());
        IslandUpgradeService upgradeService = new IslandUpgradeService(upgradeRepository, bankRepository, upgradePolicy);
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
        kr.lunaf.cloudislands.coreservice.job.JobCompletionService jobCompletion = new kr.lunaf.cloudislands.coreservice.job.JobCompletionService(runtimeRepository, completionEvents, snapshotRepository, tickets, jobs, islandRepository, playerProfiles, config.routeTicketTtl(), config.snapshotRetentionPolicy(), activationLock, completionReceipts, completionOutbox);
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
            routeRegistrar::securityRejectsTotal,
            routeRegistrar::securityRejectsRateLimited,
            routeRegistrar::securityRejectsUnauthorized,
            routeRegistrar::securityRejectsMtlsRequired,
            routeRegistrar::securityRejectsIpNotAllowed,
            routeRegistrar::securityRejectsAdminPermissionDenied
        );
        NodeFailureMonitor nodeFailureMonitor = new NodeFailureMonitor(nodes, runtimeRepository, islandRepository, events, config.heartbeatTimeout(), tickets, sessions, snapshotRepository, lifecycle);
        RouteTicketExpiryMonitor routeTicketExpiryMonitor = new RouteTicketExpiryMonitor(tickets, events, config.routeTicketTtl());
        JobRecoveryMonitor jobRecoveryMonitor = new JobRecoveryMonitor(jobs, Duration.ofSeconds(60), config.leaseDuration().toMillis(), 16);
        HttpServer server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        CoreHttpRequestExecutor httpExecutor = new CoreHttpRequestExecutor(config.httpWorkerThreads(), config.httpQueueCapacity(), config.httpKeepAlive());
        HttpServer adminServer = adminRouteRegistrar == null ? null : HttpServer.create(new InetSocketAddress(config.adminBind(), config.adminPort()), 0);
        CoreHttpRequestExecutor adminHttpExecutor = adminRouteRegistrar == null ? null : new CoreHttpRequestExecutor(config.httpWorkerThreads(), config.httpQueueCapacity(), config.httpKeepAlive());
        server.setExecutor(httpExecutor);
        if (adminServer != null) {
            adminServer.setExecutor(adminHttpExecutor);
        }
        routeRegistrar.attach(server);
        if (adminRouteRegistrar != null) {
            adminRouteRegistrar.attach(adminServer);
        }
        CoreHttpRuntime httpRuntime = new CoreHttpRuntime(server, adminServer, httpExecutor, adminHttpExecutor, config.httpShutdownGrace(), routeRegistrar, adminRouteRegistrar);
        CoreBackgroundTasks backgroundTasks = new CoreBackgroundTasks(nodeFailureMonitor, routeTicketExpiryMonitor, jobRecoveryMonitor, completionOutboxDispatcher, rankingRecalculationTask);
        this.coreLifecycle = new CoreLifecycle(httpRuntime, backgroundTasks);
        this.islandDeleteService = new CoreIslandDeleteService(deleteStorage, islandRepository, playerProfiles, runtimeRepository, jobs, events, snapshotRepository, snapshotRetentionPolicy);
        CoreDomainServices domainServices = new CoreDomainServices(
            routing,
            createIsland,
            lifecycle,
            migrationAdmin,
            jobCompletion,
            metrics,
            levelRecalculation,
            upgradePolicy,
            upgradeService,
            islandDeleteService
        );
        CoreRouteRegistry route = new CoreRouteRegistry() {
            @Override
            public void route(String path, com.sun.net.httpserver.HttpHandler handler) {
                routeRegistrar.route(path, handler);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.route(path, handler);
                }
            }

            @Override
            public void routeMethods(String path, com.sun.net.httpserver.HttpHandler handler, String... methods) {
                routeRegistrar.routeMethods(path, handler, methods);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.routeMethods(path, handler, methods);
                }
            }
        };
        CoreRouteRegistry routePrefix = new CoreRouteRegistry() {
            @Override
            public void route(String path, com.sun.net.httpserver.HttpHandler handler) {
                routeRegistrar.routePrefix(path, handler);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.routePrefix(path, handler);
                }
            }

            @Override
            public void routeMethods(String path, com.sun.net.httpserver.HttpHandler handler, String... methods) {
                routeRegistrar.routePrefixMethods(path, handler, methods);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.routePrefixMethods(path, handler, methods);
                }
            }
        };
        new HealthRoutes(config, domainServices.metrics()::render, readinessProbes(config, dataSource, deleteStorage, nodes)).register(route);
        new CoreConfigRoutes(config, nodes).register(route);
        new NodeRoutes(nodes, config.heartbeatTimeout(), runtimeRepository).register(route);
        new JobRoutes(jobs, domainServices.jobCompletion(), audit).register(route);
        new EventRoutes(inMemoryEvents).register(route);
        new AuditRoutes(audit).register(route);
        new AddonRoutes(addonStates, audit, events).register(route);
        new ProgressionRoutes(rankingRepository, domainServices.upgradePolicy(), levelRepository, missionRepository, limitRepository, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(route);
        new GeneratorRoutes(generatorRepository, upgradeRepository, islandRepository).register(route);
        new PermissionRoleRoutes(islandRepository, metadataRepository, permissionRules, roleRepository, islandLogs, audit, events).register(route);
        new IslandBankRoutes(bankRepository, limitRepository, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(route);
        new IslandBlockLevelRoutes(levelRepository, rankingRepository, domainServices.levelRecalculation(), islandRepository, metadataRepository, permissionRules, audit, events).register(route);
        new IslandUpgradeRoutes(upgradeRepository, domainServices.upgradeService(), domainServices.upgradePolicy(), bankRepository, limitRepository, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(route);
        new IslandCommunicationRoutes(islandLogs, islandRepository, metadataRepository, playerProfiles, events).register(route);
        new IslandSnapshotRoutes(snapshotRepository, runtimeRepository, snapshotRetentionPolicy, events).register(route);
        new IslandMemberRoutes(islandRepository, metadataRepository, limitRepository, permissionRules, playerProfiles, islandLogs, audit, events).register(route);
        new IslandVisitorRoutes(islandRepository, metadataRepository, limitRepository, permissionRules, islandLogs, audit, events).register(route);
        new IslandSettingsRoutes(islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(route);
        new IslandWarpRoutes(islandRepository, metadataRepository, limitRepository, permissionRules, islandLogs, audit, events).register(route);
        new IslandReviewRoutes(reviewRepository, islandRepository, islandLogs, audit, events).register(route);
        new IslandWarehouseRoutes(warehouseRepository, islandRepository, metadataRepository, permissionRules, islandLogs, audit, events).register(route);
        new IslandCatalogRoutes(islandRepository, metadataRepository, domainServices.createIsland(), islandLogs, audit).register(route);
        new IslandPlayerLifecycleRoutes(islandRepository, metadataRepository, permissionRules, domainServices.islandLifecycle(), domainServices.islandDeleteService()::requestIslandDelete, islandLogs, audit, events).register(route);
        new IslandQueryRoutes(
            islandRepository,
            metadataRepository,
            runtimeRepository,
            levelRepository,
            domainServices.levelRecalculation(),
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
            domainServices.islandDeleteService()::requestIslandDelete
        ).register(routePrefix);
        new RoutePreparationRoutes(domainServices.routing()).register(route);
        new RouteTicketRoutes(routing, tickets, sessions, audit, events).register(route);
        new AdminGeneratorRoutes(generatorRepository, audit, events).register(route);
        new AdminRuntimeRoutes(sessions, tickets, redisCacheAdmin, audit, events).register(route);
        new SuperiorSkyblock2MigrationRoutes(config.superiorSkyblock2MigrationEnabled(), domainServices.migrationAdmin(), audit).register(route);
        new PlayerProfileRoutes(playerProfiles, audit).register(route);
        new TemplateRoutes(templateRepository, audit, events).register(route);
        new ProtocolRoutes(nodes).register(route);
        new AdminNodeRoutes(nodes, nodeFailureMonitor, config.heartbeatTimeout(), audit, events).register(route, routePrefix);
        new AdminIslandLifecycleRoutes(
            domainServices.islandLifecycle(),
            islandRepository,
            runtimeRepository,
            snapshotRepository,
            audit,
            events,
            domainServices.islandDeleteService()::requestIslandDelete
        ).register(route, routePrefix);
    }

    private static List<HealthRoutes.ReadinessProbe> readinessProbes(CoreServiceConfig config, DataSource dataSource, IslandStorage storage, NodeRegistry nodes) {
        return List.of(
            new HealthRoutes.ReadinessProbe("database-query", () -> databaseProbe(dataSource)),
            new HealthRoutes.ReadinessProbe("redis", () -> redisProbe(config)),
            new HealthRoutes.ReadinessProbe("object-storage", () -> storageProbe(storage)),
            new HealthRoutes.ReadinessProbe("job-queue", () -> jobQueueProbe(config, dataSource)),
            new HealthRoutes.ReadinessProbe("island-node-heartbeat", () -> nodeHeartbeatProbe(config, nodes))
        );
    }

    private static HealthRoutes.ProbeResult databaseProbe(DataSource dataSource) {
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return HealthRoutes.ProbeResult.up("select-1-ok");
        } catch (java.sql.SQLException exception) {
            return HealthRoutes.ProbeResult.down("database-query-failed");
        }
    }

    private static HealthRoutes.ProbeResult redisProbe(CoreServiceConfig config) {
        if (!config.redisEvents() && !config.redisJobs()) {
            return HealthRoutes.ProbeResult.up("redis-not-configured");
        }
        try (RedisRespConnection redis = new RedisRespConnection(config.redisUri())) {
            String response = redis.command("PING");
            return "PONG".equalsIgnoreCase(response)
                ? HealthRoutes.ProbeResult.up("ping-pong")
                : HealthRoutes.ProbeResult.down("unexpected-ping-response");
        } catch (IOException | RuntimeException exception) {
            return HealthRoutes.ProbeResult.down("redis-unreachable");
        }
    }

    private static HealthRoutes.ProbeResult storageProbe(IslandStorage storage) {
        if (storage == null) {
            return HealthRoutes.ProbeResult.down("storage-not-configured");
        }
        try {
            return storage.available()
                ? HealthRoutes.ProbeResult.up("available")
                : HealthRoutes.ProbeResult.down("unavailable");
        } catch (IOException | RuntimeException exception) {
            return HealthRoutes.ProbeResult.down("storage-check-failed");
        }
    }

    private static HealthRoutes.ProbeResult jobQueueProbe(CoreServiceConfig config, DataSource dataSource) {
        if (config.redisJobs()) {
            return redisProbe(config).ready()
                ? HealthRoutes.ProbeResult.up("redis-job-queue-reachable")
                : HealthRoutes.ProbeResult.down("redis-job-queue-unreachable");
        }
        if (config.jdbcJobs()) {
            return databaseProbe(dataSource).ready()
                ? HealthRoutes.ProbeResult.up("jdbc-job-queue-reachable")
                : HealthRoutes.ProbeResult.down("jdbc-job-queue-unreachable");
        }
        return HealthRoutes.ProbeResult.down("job-queue-not-durable");
    }

    private static HealthRoutes.ProbeResult nodeHeartbeatProbe(CoreServiceConfig config, NodeRegistry nodes) {
        java.time.Instant now = java.time.Instant.now();
        long routeCandidates = nodes.snapshot().stream()
            .filter(node -> node.inPool(config.islandPool()))
            .filter(node -> node.eligible(now, config.heartbeatTimeout()))
            .count();
        return routeCandidates > 0L
            ? HealthRoutes.ProbeResult.up("route-candidates=" + routeCandidates)
            : HealthRoutes.ProbeResult.down("no-fresh-route-candidate");
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
        coreLifecycle.start();
    }

    public void stop() {
        coreLifecycle.stop();
    }

    public static void main(String[] args) throws IOException {
        CoreServiceConfig config = CoreServiceConfig.fromEnvironment();
        if (args.length > 0) {
            config = config.withPort(Integer.parseInt(args[0]));
        }
        CloudIslandsCoreApplication application = new CloudIslandsCoreApplication(config);
        Runtime.getRuntime().addShutdownHook(new Thread(application::stop, "cloudislands-core-shutdown"));
        application.start();
    }

}
