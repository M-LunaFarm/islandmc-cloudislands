package kr.lunaf.cloudislands.coreservice;

import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpRouteRegistrar;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminGeneratorRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminIslandLifecycleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminNodeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminRuntimeRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.AdminSupportBundleRoutes;
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
import kr.lunaf.cloudislands.coreservice.http.routes.PermissionRoleRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.PlayerProfileRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.ProgressionRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.ProtocolRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.RoutePreparationRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.RouteTicketRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.SuperiorSkyblock2MigrationRoutes;
import kr.lunaf.cloudislands.coreservice.http.routes.TemplateRoutes;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class CoreRouteModules {
    private CoreRouteModules() {
    }

    public static void register(
        CoreServiceConfig config,
        CoreInfrastructure infrastructure,
        CoreRepositories repositories,
        CoreDomainServices domainServices,
        SnapshotRetentionPolicy snapshotRetentionPolicy,
        NodeFailureMonitor nodeFailureMonitor,
        IslandStorage deleteStorage,
        GlobalEventPublisher events
    ) {
        CoreHttpRouteRegistrar routeRegistrar = infrastructure.routeRegistrar();
        CoreHttpRouteRegistrar adminRouteRegistrar = infrastructure.adminRouteRegistrar();
        CoreRouteRegistry route = mirroredRouteRegistry(routeRegistrar, adminRouteRegistrar);
        CoreRouteRegistry routePrefix = mirroredPrefixRouteRegistry(routeRegistrar, adminRouteRegistrar);

        new HealthRoutes(config, domainServices.metrics()::render, readinessProbes(config, infrastructure.dataSource(), deleteStorage, repositories.nodes())).register(route);
        new CoreConfigRoutes(config, repositories.nodes()).register(route);
        new NodeRoutes(repositories.nodes(), config.heartbeatTimeout(), repositories.runtimeRepository()).register(route);
        new JobRoutes(repositories.jobs(), domainServices.jobCompletion(), repositories.audit()).register(route);
        new EventRoutes(repositories.inMemoryEvents()).register(route);
        new AuditRoutes(repositories.audit()).register(route);
        new AddonRoutes(repositories.addonStates(), repositories.audit(), events).register(route);
        new ProgressionRoutes(repositories.rankingRepository(), domainServices.upgradePolicy(), repositories.levelRepository(), repositories.missionRepository(), repositories.bankRepository(), repositories.limitRepository(), repositories.generatorRepository(), repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new GeneratorRoutes(repositories.generatorRepository(), repositories.upgradeRepository(), repositories.islandRepository()).register(route);
        new PermissionRoleRoutes(repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), repositories.roleRepository(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandBankRoutes(repositories.bankRepository(), repositories.limitRepository(), repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandBlockLevelRoutes(repositories.levelRepository(), repositories.rankingRepository(), domainServices.levelRecalculation(), repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), repositories.audit(), events).register(route);
        new IslandUpgradeRoutes(repositories.upgradeRepository(), domainServices.upgradeService(), domainServices.upgradePolicy(), repositories.bankRepository(), repositories.limitRepository(), repositories.generatorRepository(), repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandCommunicationRoutes(repositories.islandLogs(), repositories.islandRepository(), repositories.metadataRepository(), repositories.playerProfiles(), events).register(route);
        new IslandSnapshotRoutes(repositories.snapshotRepository(), repositories.runtimeRepository(), snapshotRetentionPolicy, events).register(route);
        new IslandMemberRoutes(repositories.islandRepository(), repositories.metadataRepository(), repositories.limitRepository(), repositories.permissionRules(), repositories.playerProfiles(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandVisitorRoutes(repositories.islandRepository(), repositories.metadataRepository(), repositories.limitRepository(), repositories.permissionRules(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandSettingsRoutes(repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandWarpRoutes(repositories.islandRepository(), repositories.metadataRepository(), repositories.limitRepository(), repositories.permissionRules(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandReviewRoutes(repositories.reviewRepository(), repositories.islandRepository(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandWarehouseRoutes(repositories.warehouseRepository(), repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandCatalogRoutes(repositories.islandRepository(), repositories.metadataRepository(), domainServices.createIsland(), repositories.islandLogs(), repositories.audit()).register(route);
        new IslandPlayerLifecycleRoutes(repositories.islandRepository(), repositories.metadataRepository(), repositories.permissionRules(), domainServices.islandLifecycle(), domainServices.islandDeleteService()::requestIslandDelete, repositories.islandLogs(), repositories.audit(), events).register(route);
        new IslandQueryRoutes(
            repositories.islandRepository(),
            repositories.metadataRepository(),
            repositories.runtimeRepository(),
            repositories.levelRepository(),
            domainServices.levelRecalculation(),
            repositories.permissionRules(),
            repositories.roleRepository(),
            repositories.limitRepository(),
            repositories.upgradeRepository(),
            repositories.bankRepository(),
            repositories.missionRepository(),
            repositories.snapshotRepository(),
            repositories.islandLogs(),
            repositories.playerProfiles(),
            repositories.audit(),
            domainServices.islandDeleteService()::requestIslandDelete
        ).register(routePrefix);
        new RoutePreparationRoutes(domainServices.routing()).register(route);
        new RouteTicketRoutes(domainServices.routing(), repositories.tickets(), repositories.sessions(), repositories.audit(), events).register(route);
        new AdminGeneratorRoutes(repositories.generatorRepository(), repositories.audit(), events).register(route);
        new AdminRuntimeRoutes(repositories.sessions(), repositories.tickets(), infrastructure.redisCacheAdmin(), repositories.audit(), events).register(route);
        new AdminSupportBundleRoutes(config, repositories.nodes(), repositories.jobs(), repositories.tickets(), repositories.sessions(), repositories.inMemoryEvents(), infrastructure.redisCacheAdmin(), infrastructure.dataSource(), deleteStorage).register(route);
        new SuperiorSkyblock2MigrationRoutes(config.superiorSkyblock2MigrationEnabled(), domainServices.migrationAdmin(), repositories.audit()).register(route);
        new PlayerProfileRoutes(repositories.playerProfiles(), repositories.audit()).register(route);
        new TemplateRoutes(repositories.templateRepository(), repositories.audit(), events).register(route);
        new ProtocolRoutes(repositories.nodes()).register(route);
        new AdminNodeRoutes(repositories.nodes(), nodeFailureMonitor, config.heartbeatTimeout(), repositories.audit(), events).register(route, routePrefix);
        new AdminIslandLifecycleRoutes(
            domainServices.islandLifecycle(),
            repositories.islandRepository(),
            repositories.runtimeRepository(),
            repositories.snapshotRepository(),
            repositories.audit(),
            events,
            domainServices.islandDeleteService()::requestIslandDelete
        ).register(route, routePrefix);
    }

    private static CoreRouteRegistry mirroredRouteRegistry(CoreHttpRouteRegistrar routeRegistrar, CoreHttpRouteRegistrar adminRouteRegistrar) {
        return new CoreRouteRegistry() {
            @Override
            public void route(String path, HttpHandler handler) {
                routeRegistrar.route(path, handler);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.route(path, handler);
                }
            }

            @Override
            public void routeMethods(String path, HttpHandler handler, String... methods) {
                routeRegistrar.routeMethods(path, handler, methods);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.routeMethods(path, handler, methods);
                }
            }
        };
    }

    private static CoreRouteRegistry mirroredPrefixRouteRegistry(CoreHttpRouteRegistrar routeRegistrar, CoreHttpRouteRegistrar adminRouteRegistrar) {
        return new CoreRouteRegistry() {
            @Override
            public void route(String path, HttpHandler handler) {
                routeRegistrar.routePrefix(path, handler);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.routePrefix(path, handler);
                }
            }

            @Override
            public void routeMethods(String path, HttpHandler handler, String... methods) {
                routeRegistrar.routePrefixMethods(path, handler, methods);
                if (adminRouteRegistrar != null) {
                    adminRouteRegistrar.routePrefixMethods(path, handler, methods);
                }
            }
        };
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
        try (kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection redis = new kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection(config.redisUri())) {
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
}
