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
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.audit.JdbcAuditLogger;
import kr.lunaf.cloudislands.coreservice.bank.InMemoryIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.bank.JdbcIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.db.DriverManagerDataSource;
import kr.lunaf.cloudislands.coreservice.event.CompositeGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.RedisStreamEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.JdbcIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.JdbcIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.limit.JdbcIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.metrics.PrometheusMetricsRenderer;
import kr.lunaf.cloudislands.coreservice.mission.InMemoryIslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.mission.JdbcIslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.permission.JdbcIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.profile.JdbcPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.JdbcIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.JdbcRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRepository;
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
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.IpAllowlist;
import kr.lunaf.cloudislands.coreservice.session.InMemoryRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.snapshot.InMemoryIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.JdbcIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot;
import kr.lunaf.cloudislands.coreservice.template.JdbcIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.ticket.JdbcRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
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

public final class CloudIslandsCoreApplication {
    private final HttpServer server;
    private final ApiTokenGuard tokenGuard;
    private final FixedWindowRateLimiter rateLimiter;
    private final AdminEndpointGuard adminGuard;
    private final IpAllowlist ipAllowlist;
    private final NodeFailureMonitor nodeFailureMonitor;

    public CloudIslandsCoreApplication(int port) throws IOException {
        this(CoreServiceConfig.fromEnvironment().withPort(port));
    }

    public CloudIslandsCoreApplication(CoreServiceConfig config) throws IOException {
        Clock clock = Clock.systemUTC();
        this.tokenGuard = new ApiTokenGuard(config.coreToken());
        this.rateLimiter = new FixedWindowRateLimiter(clock, 240, 60_000L);
        this.adminGuard = new AdminEndpointGuard(config.adminToken());
        this.ipAllowlist = new IpAllowlist(config.ipAllowlist());
        DataSource dataSource = new DriverManagerDataSource(config.jdbcUrl(), config.databaseUsername(), config.databasePassword());
        NodeRegistry nodes = config.jdbcRepositories() ? new JdbcNodeRegistry(dataSource) : new InMemoryNodeRegistry();
        NodeAllocator allocator = new NodeAllocator(config.heartbeatTimeout());
        RouteTicketStore tickets = config.jdbcRepositories() ? new JdbcRouteTicketStore(dataSource, clock) : new InMemoryRouteTicketStore(clock);
        InMemoryRouteSessionStore sessions = new InMemoryRouteSessionStore(clock);
        IslandJobQueue jobs = config.jdbcJobs() ? new JdbcIslandJobQueue(dataSource, clock, config.leaseDuration()) : config.redisJobs() ? new RedisIslandJobQueue(config.redisUri()) : new InMemoryIslandJobPublisher();
        InMemoryGlobalEventPublisher inMemoryEvents = new InMemoryGlobalEventPublisher();
        GlobalEventPublisher events = config.redisEvents()
            ? new CompositeGlobalEventPublisher(java.util.List.of(inMemoryEvents, new RedisStreamEventPublisher(new RedisStreamWriterAdapter(config.redisUri()))))
            : inMemoryEvents;
        IslandRepository islandRepository = config.jdbcRepositories() ? new JdbcIslandRepository(dataSource) : new InMemoryIslandRepository();
        IslandMetadataRepository metadataRepository = config.jdbcRepositories() ? new JdbcIslandMetadataRepository(dataSource) : new InMemoryIslandMetadataRepository();
        PlayerProfileRepository playerProfiles = config.jdbcRepositories() ? new JdbcPlayerProfileRepository(dataSource) : new InMemoryPlayerProfileRepository();
        IslandPermissionRuleRepository permissionRules = config.jdbcRepositories() ? new JdbcIslandPermissionRuleRepository(dataSource) : new InMemoryIslandPermissionRuleRepository();
        IslandRuntimeRepository runtimeRepository = config.jdbcRepositories() ? new JdbcIslandRuntimeRepository(dataSource) : new InMemoryIslandRuntimeRepository();
        IslandSnapshotRepository snapshotRepository = config.jdbcRepositories() ? new JdbcIslandSnapshotRepository(dataSource) : new InMemoryIslandSnapshotRepository();
        RankingRepository rankingRepository = config.jdbcRepositories() ? new JdbcRankingRepository(dataSource) : new InMemoryRankingRepository();
        IslandLevelRepository levelRepository = config.jdbcRepositories() ? new JdbcIslandLevelRepository(dataSource) : new InMemoryIslandLevelRepository();
        kr.lunaf.cloudislands.coreservice.ranking.ConfigBlockValues.load(config.blockValuesFile()).forEach(levelRepository::putBlockValue);
        RankingRecalculationService levelRecalculation = new RankingRecalculationService(rankingRepository, events);
        IslandUpgradeRepository upgradeRepository = config.jdbcRepositories() ? new JdbcIslandUpgradeRepository(dataSource) : new InMemoryIslandUpgradeRepository();
        UpgradePolicy upgradePolicy = ConfigUpgradePolicy.load(config.upgradesFile());
        IslandBankRepository bankRepository = config.jdbcRepositories() ? new JdbcIslandBankRepository(dataSource) : new InMemoryIslandBankRepository();
        IslandUpgradeService upgradeService = new IslandUpgradeService(upgradeRepository, bankRepository, upgradePolicy);
        IslandMissionRepository missionRepository = config.jdbcRepositories() ? new JdbcIslandMissionRepository(dataSource) : new InMemoryIslandMissionRepository();
        IslandLimitRepository limitRepository = config.jdbcRepositories() ? new JdbcIslandLimitRepository(dataSource) : new InMemoryIslandLimitRepository();
        IslandTemplateRepository templateRepository = config.jdbcRepositories() ? new JdbcIslandTemplateRepository(dataSource) : new InMemoryIslandTemplateRepository();
        AuditLogger audit = config.jdbcRepositories() ? new JdbcAuditLogger(dataSource) : new InMemoryAuditLogger();
        IslandLogRepository islandLogs = config.jdbcRepositories() ? new JdbcIslandLogRepository(dataSource) : new InMemoryIslandLogRepository();
        InMemoryAuditLogger inMemoryAudit = audit instanceof InMemoryAuditLogger logger ? logger : new InMemoryAuditLogger();
        java.util.function.Supplier<String> auditJson = audit instanceof JdbcAuditLogger jdbcAudit ? () -> jdbcAudit.toJson(100) : inMemoryAudit::toJson;
        RoutingOrchestrator routing = new RoutingOrchestrator(nodes, allocator, tickets, islandRepository, metadataRepository, runtimeRepository, templateRepository, jobs, events);
        CreateIslandWorkflow createIsland = new CreateIslandWorkflow(islandRepository, metadataRepository, playerProfiles, templateRepository, nodes, allocator, jobs, events, tickets);
        IslandLifecycleWorkflow lifecycle = new IslandLifecycleWorkflow(runtimeRepository, islandRepository, templateRepository, nodes, allocator, jobs, events);
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
            migrationRollbackTarget(config, dataSource)
        );
        kr.lunaf.cloudislands.coreservice.job.JobCompletionService jobCompletion = new kr.lunaf.cloudislands.coreservice.job.JobCompletionService(runtimeRepository, events, snapshotRepository, tickets, jobs);
        PrometheusMetricsRenderer metrics = new PrometheusMetricsRenderer(nodes, jobs, inMemoryEvents, config.heartbeatTimeout());
        this.nodeFailureMonitor = new NodeFailureMonitor(nodes, runtimeRepository, events, config.heartbeatTimeout());
        this.server = HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 0);
        route("/health", exchange -> write(exchange, 200, "{\"status\":\"UP\"}"));
        route("/metrics", exchange -> write(exchange, 200, metrics.render(), "text/plain; version=0.0.4; charset=utf-8"));
        route("/v1/nodes", exchange -> write(exchange, 200, nodes.toJson()));
        route("/v1/nodes/info", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            write(exchange, nodes.find(nodeId).isPresent() ? 200 : 404, nodes.find(nodeId).map(NodeRegistry::toJson).orElseGet(() -> ApiResponses.error("NODE_NOT_FOUND", "Node was not found")));
        });
        route("/v1/jobs", exchange -> write(exchange, 200, jobsJson(jobs)));
        route("/v1/events", exchange -> write(exchange, 200, inMemoryEvents.toJson()));
        route("/v1/audit", exchange -> write(exchange, 200, auditJson.get()));
        route("/v1/rankings/level", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, rankingsJson(rankingRepository.topByLevel(JsonFields.integer(body, "limit", 10))));
        });
        route("/v1/rankings/worth", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, rankingsJson(rankingRepository.topByWorth(JsonFields.integer(body, "limit", 10))));
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
            if (!requireMember(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> completed = missionRepository.complete(islandId, actorUuid, missionKey);
            completed.ifPresent(snapshot -> {
                audit.log(actorUuid, "PLAYER", "ISLAND_MISSION_COMPLETE", "ISLAND", islandId.toString(), Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
                islandLogs.append(islandId, actorUuid, "ISLAND_MISSION_COMPLETE", Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind(), "reward", snapshot.reward()));
                events.publish(CloudIslandEventType.ISLAND_MISSION_COMPLETED.name(), Map.of("islandId", islandId.toString(), "missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
            });
            write(exchange, completed.isPresent() ? 202 : 404, completed.map(CloudIslandsCoreApplication::missionJson).orElseGet(() -> ApiResponses.error("MISSION_NOT_FOUND", "Mission was not found")));
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (method.equalsIgnoreCase("GET") && !tail.contains("/")) {
                UUID islandId = uuidPath(tail);
                java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
                write(exchange, island.isPresent() ? 200 : 404, island.map(CloudIslandsCoreApplication::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
                return;
            }
            if (method.equalsIgnoreCase("DELETE") && !tail.contains("/")) {
                UUID islandId = uuidPath(tail);
                java.util.Optional<IslandSnapshot> island = islandRepository.findById(islandId);
                boolean deleted = island.isPresent() && islandRepository.markDeleted(islandId, island.get().ownerUuid());
                if (deleted) {
                    playerProfiles.clearPrimaryIsland(island.get().ownerUuid());
                    if (publishDeleteJobOrMarkDeleted(islandId, "api-delete")) {
                        events.publish(CloudIslandEventType.ISLAND_DELETED.name(), Map.of("islandId", islandId.toString()));
                    }
                }
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
            if (method.equalsIgnoreCase("GET") && tail.endsWith("/island")) {
                UUID playerUuid = uuidPath(tail.substring(0, tail.length() - "/island".length()));
                java.util.Optional<IslandSnapshot> island = islandRepository.findByOwner(playerUuid);
                write(exchange, island.isPresent() ? 200 : 404, island.map(CloudIslandsCoreApplication::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            permissionRules.put(islandId, role, permission, allowed);
            audit.log(actorUuid, "PLAYER", "ISLAND_PERMISSION_SET", "ISLAND", islandId.toString(), Map.of("role", role.name(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
            islandLogs.append(islandId, actorUuid, "ISLAND_PERMISSION_SET", Map.of("role", role.name(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
            events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", role.name(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
            write(exchange, 202, ApiResponses.ok(true));
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
            java.util.Map<String, String> payload = JsonFields.object(body, "payload");
            jobs.findClaimed(jobId).map(job -> new kr.lunaf.cloudislands.protocol.job.IslandJob(job.jobId(), job.type(), job.islandId(), job.targetNode(), job.priority(), java.util.Map.copyOf(new java.util.HashMap<String, String>() {{ putAll(job.payload()); putAll(payload); }}), job.createdAt())).ifPresent(jobCompletion::completed);
            jobs.complete(JsonFields.text(body, "nodeId", ""), jobId);
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/jobs/fail", exchange -> {
            String body = readBody(exchange);
            UUID jobId = JsonFields.uuid(body, "jobId", new UUID(0L, 0L));
            String error = JsonFields.text(body, "error", "unknown");
            jobs.findClaimed(jobId).ifPresent(job -> jobCompletion.failed(job, error));
            jobs.fail(JsonFields.text(body, "nodeId", ""), jobId, error);
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
        route("/v1/admin/jobs/list", exchange -> write(exchange, 200, jobsJson(jobs)));
        route("/v1/admin/jobs/retry", exchange -> {
            String body = readBody(exchange);
            UUID jobId = JsonFields.uuid(body, "jobId", new UUID(0L, 0L));
            boolean retried;
            if (jobs instanceof InMemoryIslandJobPublisher memoryJobs) {
                retried = memoryJobs.retry(jobId);
                audit.log(new UUID(0L, 0L), "ADMIN", "JOB_RETRY", "JOB", jobId.toString(), Map.of("retried", Boolean.toString(retried)));
                write(exchange, retried ? 202 : 404, retried ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_RETRIED", "Job was not found or cannot be retried"));
            } else if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
                retried = jdbcJobs.retry(jobId);
                audit.log(new UUID(0L, 0L), "ADMIN", "JOB_RETRY", "JOB", jobId.toString(), Map.of("retried", Boolean.toString(retried)));
                write(exchange, retried ? 202 : 404, retried ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_RETRIED", "Job was not found or cannot be retried"));
            } else {
                write(exchange, 409, ApiResponses.error("JOB_RETRY_UNAVAILABLE", "Job retry is only available for in-memory or JDBC queue mode"));
            }
        });
        route("/v1/admin/jobs/cancel", exchange -> {
            String body = readBody(exchange);
            UUID jobId = JsonFields.uuid(body, "jobId", new UUID(0L, 0L));
            boolean canceled;
            if (jobs instanceof InMemoryIslandJobPublisher memoryJobs) {
                canceled = memoryJobs.cancel(jobId);
                audit.log(new UUID(0L, 0L), "ADMIN", "JOB_CANCEL", "JOB", jobId.toString(), Map.of("canceled", Boolean.toString(canceled)));
                write(exchange, canceled ? 202 : 404, canceled ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_CANCELED", "Job was not found or cannot be canceled"));
            } else if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
                canceled = jdbcJobs.cancel(jobId);
                audit.log(new UUID(0L, 0L), "ADMIN", "JOB_CANCEL", "JOB", jobId.toString(), Map.of("canceled", Boolean.toString(canceled)));
                write(exchange, canceled ? 202 : 404, canceled ? ApiResponses.ok(true) : ApiResponses.error("JOB_NOT_CANCELED", "Job was not found or cannot be canceled"));
            } else {
                write(exchange, 409, ApiResponses.error("JOB_CANCEL_UNAVAILABLE", "Job cancel is only available for in-memory or JDBC queue mode"));
            }
        });
        route("/v1/routes/home", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareHomeRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.text(body, "homeName", "default")));
        });
        route("/v1/routes/visit", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareVisitRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.uuid(body, "islandId", new UUID(0L, 0L))));
        });
        route("/v1/routes/random", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareRandomVisitRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L))));
        });
        route("/v1/routes/warp", exchange -> {
            String body = readBody(exchange);
            routeResult(exchange, routing.prepareWarpRoute(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "warpName", "default")));
        });
        route("/v1/routes/session", exchange -> {
            String body = readBody(exchange);
            sessions.put(new RouteTicket(JsonFields.uuid(body, "ticketId", UUID.randomUUID()), JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), kr.lunaf.cloudislands.api.model.RouteAction.HOME, new UUID(0L, 0L), JsonFields.text(body, "targetNode", ""), "ci_shard_001", kr.lunaf.cloudislands.api.model.RouteTicketState.READY, java.time.Instant.parse(JsonFields.text(body, "expiresAt", java.time.Instant.now().plusSeconds(30).toString())), JsonFields.text(body, "nonce", ""), Map.of("targetServerName", JsonFields.text(body, "targetServerName", ""))));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/routes/session/consume", exchange -> {
            String body = readBody(exchange);
            PlayerRouteSession session = sessions.consume(JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L)), JsonFields.text(body, "nodeId", "")).orElse(null);
            write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
        });
        route("/v1/routes/ticket-status", exchange -> {
            String body = readBody(exchange);
            UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String nonce = JsonFields.text(body, "nonce", "");
            RouteTicket ticket = tickets.find(ticketId).orElse(null);
            boolean allowed = ticket != null && ticket.playerUuid().equals(playerUuid) && ticket.nonce().equals(nonce);
            write(exchange, allowed ? 200 : 404, allowed ? RoutingOrchestrator.toJson(ticket) : ApiResponses.error("ROUTE_TICKET_NOT_FOUND", "Route ticket was not found"));
        });
        route("/v1/routes/consume", exchange -> write(exchange, 200, routing.consumeTicketJson(readBody(exchange))));
        route("/v1/admin/routes/debug", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            if (playerUuid.equals(new UUID(0L, 0L))) {
                write(exchange, 200, "{\"sessions\":" + sessions.toJson() + ",\"tickets\":" + tickets.toJson() + "}");
            } else {
                PlayerRouteSession session = sessions.findAny(playerUuid).orElse(null);
                write(exchange, session == null ? 404 : 200, session == null ? ApiResponses.error("ROUTE_SESSION_NOT_FOUND", "Route session was not found") : sessionJson(session));
            }
        });
        route("/v1/admin/routes/ticket", exchange -> {
            String body = readBody(exchange);
            UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
            RouteTicket ticket = tickets.find(ticketId).orElse(null);
            write(exchange, ticket == null ? 404 : 200, ticket == null ? ApiResponses.error("ROUTE_TICKET_NOT_FOUND", "Route ticket was not found") : RoutingOrchestrator.toJson(ticket));
        });
        route("/v1/admin/routes/clear", exchange -> {
            String body = readBody(exchange);
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            UUID ticketId = JsonFields.uuid(body, "ticketId", new UUID(0L, 0L));
            boolean clearedSession = !playerUuid.equals(new UUID(0L, 0L)) && sessions.clear(playerUuid);
            boolean clearedTicket = !ticketId.equals(new UUID(0L, 0L)) && tickets.clear(ticketId);
            write(exchange, 202, "{\"clearedSession\":" + clearedSession + ",\"clearedTicket\":" + clearedTicket + "}");
        });
        route("/v1/admin/cache/clear", exchange -> {
            int clearedSessions = sessions.clearAll();
            int clearedTickets = tickets.clearAll();
            audit.log(new UUID(0L, 0L), "ADMIN", "CACHE_CLEAR", "CORE", "route-cache", Map.of("sessions", Integer.toString(clearedSessions), "tickets", Integer.toString(clearedTickets)));
            events.publish(CloudIslandEventType.CORE_CACHE_CLEARED.name(), Map.of("scope", "route-cache", "sessions", Integer.toString(clearedSessions), "tickets", Integer.toString(clearedTickets)));
            write(exchange, 202, "{\"clearedSessions\":" + clearedSessions + ",\"clearedTickets\":" + clearedTickets + "}");
        });
        route("/v1/admin/reload", exchange -> {
            int clearedSessions = sessions.clearAll();
            int clearedTickets = tickets.clearAll();
            audit.log(new UUID(0L, 0L), "ADMIN", "CORE_RELOAD", "CORE", "runtime", Map.of("clearedSessions", Integer.toString(clearedSessions), "clearedTickets", Integer.toString(clearedTickets)));
            events.publish(CloudIslandEventType.CORE_RELOADED.name(), Map.of("clearedSessions", Integer.toString(clearedSessions), "clearedTickets", Integer.toString(clearedTickets)));
            write(exchange, 202, "{\"reloaded\":true,\"clearedSessions\":" + clearedSessions + ",\"clearedTickets\":" + clearedTickets + "}");
        });
        route("/v1/admin/migrations/superiorskyblock2/scan", exchange -> {
            String body = readBody(exchange);
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_SCAN", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.scan(JsonFields.text(body, "path", "plugins/SuperiorSkyblock2")));
        });
        route("/v1/admin/migrations/superiorskyblock2/dryrun", exchange -> {
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_DRYRUN", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.dryRun());
        });
        route("/v1/admin/migrations/superiorskyblock2/import", exchange -> {
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_IMPORT", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.importLastPlan());
        });
        route("/v1/admin/migrations/superiorskyblock2/verify", exchange -> {
            audit.log(new UUID(0L, 0L), "ADMIN", "MIGRATION_VERIFY", "MIGRATION", "superiorskyblock2", Map.of());
            write(exchange, 202, migrationAdmin.verify());
        });
        route("/v1/admin/migrations/superiorskyblock2/rollback", exchange -> {
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
            nodes.heartbeat(heartbeat(readBody(exchange)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/admin/nodes/drain", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            boolean changed = nodes.drain(nodeId);
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_DRAIN", "NODE", nodeId, Map.of());
            write(exchange, changed ? 202 : 404, ApiResponses.ok(changed));
        });
        route("/v1/admin/nodes/undrain", exchange -> {
            String body = readBody(exchange);
            String nodeId = JsonFields.text(body, "nodeId", "");
            boolean changed = nodes.undrain(nodeId);
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_UNDRAIN", "NODE", nodeId, Map.of());
            write(exchange, changed ? 202 : 404, ApiResponses.ok(changed));
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
            boolean changed = nodes.drain(nodeId);
            if (changed) {
                events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "SHUTDOWN_SAFE", "reason", reason));
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
                audit.log(new UUID(0L, 0L), "ADMIN", "NODE_DRAIN", "NODE", nodeId, Map.of());
                write(exchange, changed ? 202 : 404, ApiResponses.ok(changed));
                return;
            }
            if (tail.endsWith("/undrain")) {
                String nodeId = tail.substring(0, tail.length() - "/undrain".length());
                boolean changed = nodes.undrain(nodeId);
                audit.log(new UUID(0L, 0L), "ADMIN", "NODE_UNDRAIN", "NODE", nodeId, Map.of());
                write(exchange, changed ? 202 : 404, ApiResponses.ok(changed));
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
                boolean changed = nodes.drain(nodeId);
                if (changed) {
                    events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId, "state", "SHUTDOWN_SAFE", "reason", "admin-request"));
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
            java.util.List<String> downNodes = nodeId.isBlank() ? nodes.markStaleDown(config.heartbeatTimeout()) : java.util.List.of(nodeId);
            for (String downNode : downNodes) {
                affected += runtimeRepository.markRecoveryRequiredForNode(downNode);
            }
            String nodesJson = "[\"" + String.join("\",\"", downNodes.stream().map(value -> value.replace("\"", "'")).toList()) + "\"]";
            audit.log(new UUID(0L, 0L), "ADMIN", "NODE_SWEEP", "NODE", nodeId.isBlank() ? "*" : nodeId, Map.of("recoveryRequired", Integer.toString(affected), "nodes", String.join(",", downNodes)));
            events.publish(CloudIslandEventType.NODE_STATE_CHANGED.name(), Map.of("nodeId", nodeId.isBlank() ? "*" : nodeId, "state", "SWEEP", "recoveryRequired", Integer.toString(affected), "nodes", String.join(",", downNodes)));
            write(exchange, 202, "{\"nodes\":" + nodesJson + ",\"recoveryRequired\":" + affected + "}");
        });
        route("/v1/admin/islands/activate", exchange -> lifecycle(exchange, lifecycle.activate(JsonFields.uuid(readBody(exchange), "islandId", new UUID(0L, 0L)))));
        route("/v1/admin/islands/deactivate", exchange -> lifecycle(exchange, lifecycle.deactivate(JsonFields.uuid(readBody(exchange), "islandId", new UUID(0L, 0L)))));
        route("/v1/admin/islands/migrate", exchange -> {
            String body = readBody(exchange);
            lifecycle(exchange, lifecycle.migrate(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "targetNode", "")));
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
                lifecycle(exchange, lifecycle.activate(uuidPath(tail.substring(0, tail.length() - "/activate".length()))));
                return;
            }
            if (tail.endsWith("/deactivate")) {
                lifecycle(exchange, lifecycle.deactivate(uuidPath(tail.substring(0, tail.length() - "/deactivate".length()))));
                return;
            }
            if (tail.endsWith("/migrate")) {
                String body = readBody(exchange);
                lifecycle(exchange, lifecycle.migrate(uuidPath(tail.substring(0, tail.length() - "/migrate".length())), JsonFields.text(body, "targetNode", "")));
                return;
            }
            write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
        });
        route("/v1/admin/islands/snapshot", exchange -> {
            String body = readBody(exchange);
            lifecycle(exchange, lifecycle.snapshot(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "reason", "MANUAL")));
        });
        route("/v1/admin/islands/restore", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            long snapshotNo = JsonFields.longValue(body, "snapshotNo", 0L);
            if (snapshotNo <= 0L || snapshotRepository.find(islandId, snapshotNo).isEmpty()) {
                write(exchange, 404, ApiResponses.error("SNAPSHOT_NOT_FOUND", "Snapshot was not found"));
            } else {
                lifecycle(exchange, lifecycle.restore(islandId, snapshotNo));
            }
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
            boolean deleted = island.isPresent() && islandRepository.markDeleted(islandId, island.get().ownerUuid());
            if (deleted) {
                playerProfiles.clearPrimaryIsland(island.get().ownerUuid());
            }
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of("deleted", Boolean.toString(deleted)));
            if (deleted) {
                if (publishDeleteJobOrMarkDeleted(islandId, "admin-delete")) {
                    events.publish(CloudIslandEventType.ISLAND_DELETED.name(), Map.of("islandId", islandId.toString()));
                }
            }
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
            audit.log(new UUID(0L, 0L), "ADMIN", "ISLAND_REPAIR", "ISLAND", islandId.toString(), Map.of("reason", reason));
            events.publish(CloudIslandEventType.ISLAND_REPAIRED.name(), Map.of("islandId", islandId.toString(), "reason", reason));
            write(exchange, 202, runtimeJson(runtime));
        });
        route("/v1/islands/snapshots", exchange -> {
            String body = readBody(exchange);
            write(exchange, 200, snapshotsJson(snapshotRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.integer(body, "limit", 20))));
        });
        route("/v1/admin/block-values", exchange -> {
            String body = readBody(exchange);
            String materialKey = JsonFields.text(body, "materialKey", "minecraft:stone");
            BigDecimal worth = new BigDecimal(JsonFields.text(body, "worth", Double.toString(JsonFields.decimal(body, "worth", 0.0D))));
            long levelPoints = JsonFields.longValue(body, "levelPoints", 0L);
            long limit = JsonFields.longValue(body, "limit", 0L);
            levelRepository.putBlockValue(materialKey, new RankingRecalculationService.BlockValue(worth, levelPoints, limit));
            audit.log(JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L)), "ADMIN", "BLOCK_VALUE_SET", "MATERIAL", materialKey, Map.of("worth", worth.toPlainString(), "levelPoints", Long.toString(levelPoints)));
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
        route("/v1/islands/level/recalculate", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            UpgradePurchaseResult result = upgradeService.purchase(islandId, upgradeKey);
            audit.log(actorUuid, "PLAYER", "ISLAND_UPGRADE_PURCHASE", "ISLAND", islandId.toString(), Map.of("upgradeKey", upgradeKey, "code", result.code(), "cost", result.cost().toPlainString()));
            islandLogs.append(islandId, actorUuid, "ISLAND_UPGRADE_PURCHASE", Map.of("upgradeKey", upgradeKey, "code", result.code(), "cost", result.cost().toPlainString()));
            if (result.accepted()) {
                events.publish(CloudIslandEventType.ISLAND_UPGRADE.name(), Map.of("islandId", islandId.toString(), "upgradeKey", upgradeKey, "level", Integer.toString(result.snapshot().level())));
                if (result.cost().signum() > 0) {
                    String balance = bankRepository.balance(islandId).balance();
                    events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "operation", "UPGRADE_PURCHASE", "amount", result.cost().toPlainString(), "balance", balance));
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
            String normalizedChannel = channel.equals("TEAM") ? "TEAM" : "ISLAND";
            islandLogs.append(islandId, actorUuid, "ISLAND_CHAT", Map.of("channel", normalizedChannel, "message", message));
            events.publish(CloudIslandEventType.ISLAND_CHAT_SENT.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "channel", normalizedChannel, "message", message));
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
            if (!requireMember(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            if (amount.signum() <= 0) {
                write(exchange, 409, "{\"accepted\":false,\"code\":\"INVALID_AMOUNT\",\"bank\":" + bankJson(bankRepository.balance(islandId)) + "}");
                return;
            }
            var snapshot = bankRepository.deposit(islandId, amount);
            audit.log(actorUuid, "PLAYER", "ISLAND_BANK_DEPOSIT", "ISLAND", islandId.toString(), Map.of("amount", amount.toPlainString(), "balance", snapshot.balance()));
            islandLogs.append(islandId, actorUuid, "ISLAND_BANK_DEPOSIT", Map.of("amount", amount.toPlainString(), "balance", snapshot.balance()));
            events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "operation", "DEPOSIT", "amount", amount.toPlainString(), "balance", snapshot.balance()));
            write(exchange, 202, bankJson(snapshot));
        });
        route("/v1/islands/bank/withdraw", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            BigDecimal amount = amount(body);
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            var result = bankRepository.withdraw(islandId, amount);
            audit.log(actorUuid, "PLAYER", "ISLAND_BANK_WITHDRAW", "ISLAND", islandId.toString(), Map.of("amount", amount.toPlainString(), "code", result.code(), "balance", result.snapshot().balance()));
            islandLogs.append(islandId, actorUuid, "ISLAND_BANK_WITHDRAW", Map.of("amount", amount.toPlainString(), "code", result.code(), "balance", result.snapshot().balance()));
            if (result.accepted()) {
                events.publish(CloudIslandEventType.ISLAND_BANK_CHANGED.name(), Map.of("islandId", islandId.toString(), "operation", "WITHDRAW", "amount", amount.toPlainString(), "balance", result.snapshot().balance()));
            }
            write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\",\"bank\":" + bankJson(result.snapshot()) + "}");
        });
        route("/v1/islands/delete", exchange -> {
            String body = readBody(exchange);
            UUID requesterUuid = JsonFields.uuid(body, "requesterUuid", new UUID(0L, 0L));
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            boolean deleted = islandRepository.markDeleted(islandId, requesterUuid);
            if (deleted) {
                playerProfiles.clearPrimaryIsland(requesterUuid);
            }
            if (deleted) {
                boolean deletedImmediately = publishDeleteJobOrMarkDeleted(islandId, "player-delete");
                audit.log(requesterUuid, "PLAYER", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of());
                islandLogs.append(islandId, requesterUuid, "ISLAND_DELETE", Map.of());
                if (deletedImmediately) {
                    events.publish(CloudIslandEventType.ISLAND_DELETED.name(), Map.of("islandId", islandId.toString(), "requesterUuid", requesterUuid.toString()));
                }
            }
            write(exchange, deleted ? 202 : 403, deleteResultJson(new DeleteIslandResult(deleted, deleted ? "DELETED" : "NOT_OWNER_OR_MISSING", islandId)));
        });
        route("/v1/islands/reset", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String reason = JsonFields.text(body, "reason", "player-reset");
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, inviterUuid)) {
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
            boolean accepted = metadataRepository.acceptInvite(inviteId, playerUuid);
            audit.log(playerUuid, "PLAYER", "ISLAND_INVITE_ACCEPT", "INVITE", inviteId.toString(), Map.of("accepted", Boolean.toString(accepted)));
            events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString(), "accepted", Boolean.toString(accepted)));
            if (accepted) {
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
            events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString(), "declined", Boolean.toString(declined)));
            write(exchange, declined ? 202 : 409, declined ? ApiResponses.ok(true) : ApiResponses.error("INVITE_UNAVAILABLE", "Invite is missing or not pending"));
        });
        route("/v1/islands/bans/set", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            UUID playerUuid = JsonFields.uuid(body, "playerUuid", new UUID(0L, 0L));
            String reason = JsonFields.text(body, "reason", "island ban");
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            metadataRepository.pardonVisitor(islandId, playerUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_VISITOR_PARDON", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
            islandLogs.append(islandId, actorUuid, "ISLAND_VISITOR_PARDON", Map.of("playerUuid", playerUuid.toString()));
            events.publish(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "banned", Boolean.toString(false)));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/lock", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            boolean locked = JsonFields.bool(body, "locked", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            metadataRepository.setLocked(islandId, locked);
            audit.log(actorUuid, "PLAYER", "ISLAND_LOCK_SET", "ISLAND", islandId.toString(), Map.of("locked", Boolean.toString(locked)));
            islandLogs.append(islandId, actorUuid, "ISLAND_LOCK_SET", Map.of("locked", Boolean.toString(locked)));
            events.publish(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name(), Map.of("islandId", islandId.toString(), "locked", Boolean.toString(locked)));
            write(exchange, 202, ApiResponses.ok(true));
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 27), 54));
            write(exchange, 200, warpsJson(metadataRepository.publicWarps(limit)));
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            metadataRepository.upsertWarp(islandId, name, location(body), publicAccess, actorUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_WARP_SET", "ISLAND", islandId.toString(), Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            islandLogs.append(islandId, actorUuid, "ISLAND_WARP_SET", Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/warps/delete", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "default").toLowerCase();
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            metadataRepository.deleteWarp(islandId, name);
            audit.log(actorUuid, "PLAYER", "ISLAND_WARP_DELETE", "ISLAND", islandId.toString(), Map.of("name", name));
            islandLogs.append(islandId, actorUuid, "ISLAND_WARP_DELETE", Map.of("name", name));
            events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/warps/access", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            String name = JsonFields.text(body, "name", "default").toLowerCase();
            boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            if (metadataRepository.warp(islandId, name).isEmpty()) {
                write(exchange, 404, ApiResponses.error("WARP_NOT_FOUND", "Island warp was not found"));
                return;
            }
            metadataRepository.setWarpPublicAccess(islandId, name, publicAccess);
            audit.log(actorUuid, "PLAYER", "ISLAND_WARP_ACCESS_SET", "ISLAND", islandId.toString(), Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            islandLogs.append(islandId, actorUuid, "ISLAND_WARP_ACCESS_SET", Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
            events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name));
            write(exchange, 202, ApiResponses.ok(true));
        });
        route("/v1/islands/access", exchange -> {
            String body = readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            if (!requireManager(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
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
            int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 27), 54));
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
            return new S3IslandStorage(config.storageEndpoint(), config.storageBucket(), config.storageBearerToken());
        }
        return null;
    }

    public void start() {
        server.start();
        nodeFailureMonitor.start();
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
                write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
                return;
            }
            if (!path.equals("/health") && !tokenGuard.allowed(exchange)) {
                write(exchange, 401, ApiResponses.error("UNAUTHORIZED", "Missing or invalid API token"));
                return;
            }
            if (!ipAllowlist.allowed(exchange)) {
                write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
                return;
            }
            if (!adminGuard.allowed(path, exchange)) {
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
                write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
                return;
            }
            if (!tokenGuard.allowed(exchange)) {
                write(exchange, 401, ApiResponses.error("UNAUTHORIZED", "Missing or invalid API token"));
                return;
            }
            if (!ipAllowlist.allowed(exchange)) {
                write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
                return;
            }
            if (!adminGuard.allowed(exchange.getRequestURI().getPath(), exchange)) {
                write(exchange, 403, ApiResponses.error("ADMIN_PERMISSION_DENIED", "Admin permission is required"));
                return;
            }
            handler.handle(exchange);
        });
    }

    private static void lifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result) throws IOException {
        write(exchange, result.accepted() ? 202 : 409, "{\"accepted\":" + result.accepted() + ",\"code\":\"" + result.code() + "\"}");
    }

    private static void routeResult(HttpExchange exchange, RoutePreparationResult result) throws IOException {
        write(exchange, result.status(), result.body());
    }

    private boolean publishDeleteJobOrMarkDeleted(UUID islandId, String reason) {
        java.util.Optional<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> runtime = runtimeRepository.find(islandId);
        String targetNode = runtime.map(kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot::activeNode).orElse("");
        if (targetNode != null && !targetNode.isBlank()) {
            runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETE_REQUESTED);
            jobs.publish(new IslandJob(UUID.randomUUID(), IslandJobType.DELETE_ISLAND, islandId, targetNode, 50, Map.of("reason", reason), java.time.Instant.now()));
            events.publish(CloudIslandEventType.ISLAND_DELETE_REQUESTED.name(), Map.of("islandId", islandId.toString(), "targetNode", targetNode, "reason", reason));
            return false;
        }
        runtimeRepository.setState(islandId, kr.lunaf.cloudislands.api.model.IslandState.DELETED);
        return true;
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

    private static boolean requireManager(HttpExchange exchange, IslandRepository islandRepository, IslandMetadataRepository metadataRepository, UUID islandId, UUID actorUuid) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && (member.role() == IslandRole.OWNER || member.role() == IslandRole.CO_OWNER));
        if (owner || allowed) {
            return true;
        }
        write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island manager permission is required"));
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
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append("}").toString();
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

    private static NodeHeartbeatRequest heartbeat(String body) {
        return new NodeHeartbeatRequest(JsonFields.text(body, "nodeId", "unknown"), JsonFields.text(body, "pool", "island"), JsonFields.text(body, "velocityServerName", JsonFields.text(body, "nodeId", "unknown")), JsonFields.text(body, "nodeVersion", ""), JsonFields.enumValue(NodeState.class, body, "state", NodeState.READY), JsonFields.integer(body, "players", 0), JsonFields.integer(body, "activeIslands", 0), JsonFields.decimal(body, "mspt", 20.0D), JsonFields.integer(body, "activationQueue", 0), JsonFields.longValue(body, "heapUsedMb", 0L), JsonFields.longValue(body, "heapMaxMb", 1L), JsonFields.bool(body, "storageAvailable", true), JsonFields.text(body, "supportedTemplates", "*"));
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
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
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
