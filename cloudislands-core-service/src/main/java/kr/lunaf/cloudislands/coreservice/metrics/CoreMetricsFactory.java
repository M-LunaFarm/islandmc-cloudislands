package kr.lunaf.cloudislands.coreservice.metrics;

import static kr.lunaf.cloudislands.coreservice.config.CoreNetworkExposure.internalHost;
import static kr.lunaf.cloudislands.coreservice.config.CoreNetworkExposure.jdbcHost;
import static kr.lunaf.cloudislands.coreservice.config.CoreNetworkExposure.publicBind;
import static kr.lunaf.cloudislands.coreservice.config.CoreSetupSummary.coreJdbcFallbackActive;

import java.util.function.LongSupplier;
import kr.lunaf.cloudislands.coreservice.CachingNodeRegistry;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.RedisActivationLock;
import kr.lunaf.cloudislands.coreservice.RedisPlayerCreationLock;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.audit.RedisAuditLogger;
import kr.lunaf.cloudislands.coreservice.bank.CachingIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.bank.IslandBankRepository;
import kr.lunaf.cloudislands.coreservice.cache.RedisCacheAdmin;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.db.MeteredDataSource;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.event.RedisStreamEventPublisher;
import kr.lunaf.cloudislands.coreservice.islandlog.CachingIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.limit.CachingIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.mission.CachingIslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.CachingIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.CachingPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.ranking.CachingIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.CachingRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.DirtyRankingRecalculationTask;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.CachingIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.role.CachingIslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;
import kr.lunaf.cloudislands.coreservice.session.RedisRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionStore;
import kr.lunaf.cloudislands.coreservice.snapshot.CachingIslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.template.CachingIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.ticket.CachingRouteTicketStore;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.coreservice.upgrade.CachingIslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeRepository;

public final class CoreMetricsFactory {
    private CoreMetricsFactory() {
    }

    public static PrometheusMetricsRenderer create(
            CoreServiceConfig config,
            boolean coreJdbcActive,
            MeteredDataSource meteredDataSource,
            NodeRegistry nodes,
            IslandJobQueue jobs,
            RouteTicketStore tickets,
            RouteSessionStore sessions,
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            PlayerProfileRepository playerProfiles,
            IslandPermissionRuleRepository permissionRules,
            IslandRoleRepository roleRepository,
            IslandRuntimeRepository runtimeRepository,
            RankingRepository rankingRepository,
            IslandLevelRepository levelRepository,
            IslandBankRepository bankRepository,
            IslandLimitRepository limitRepository,
            IslandMissionRepository missionRepository,
            IslandUpgradeRepository upgradeRepository,
            IslandTemplateRepository templateRepository,
            IslandSnapshotRepository snapshotRepository,
            IslandLogRepository islandLogs,
            RedisCacheAdmin redisCacheAdmin,
            RedisActivationLock activationLock,
            RedisPlayerCreationLock playerCreationLock,
            AuditLogger audit,
            InMemoryGlobalEventPublisher inMemoryEvents,
            RedisStreamEventPublisher redisEventPublisher,
            DirtyRankingRecalculationTask rankingRecalculationTask,
            LongSupplier securityRejectsTotal,
            LongSupplier securityRejectsRateLimited,
            LongSupplier securityRejectsUnauthorized,
            LongSupplier securityRejectsMtlsRequired,
            LongSupplier securityRejectsIpNotAllowed,
            LongSupplier securityRejectsAdminPermissionDenied) {
        return new PrometheusMetricsRenderer(
            nodes,
            jobs,
            tickets,
            runtimeRepository,
            inMemoryEvents,
            config.heartbeatTimeout(),
            meteredDataSource::lastQuerySeconds,
            meteredDataSource::activeConnections,
            meteredDataSource::openedConnections,
            meteredDataSource::connectionFailures,
            meteredDataSource::queryFailures,
            () -> redisEventPublisher == null ? 0L : redisEventPublisher.failuresTotal(),
            () -> redisCacheFailures(nodes, tickets, sessions, islandRepository, metadataRepository, playerProfiles,
                permissionRules, roleRepository, runtimeRepository, rankingRepository, levelRepository,
                bankRepository, limitRepository, missionRepository, upgradeRepository, templateRepository,
                snapshotRepository, islandLogs, redisCacheAdmin, activationLock, playerCreationLock, audit),
            () -> config.databasePoolSize(),
            () -> coreJdbcFallbackActive(config),
            config::setupDatabaseDurable,
            config::setupDatabaseRequestedBackend,
            config::setupDatabaseEffectiveAuthority,
            config::setupDatabaseFallbackTarget,
            () -> config.coreToken() != null && !config.coreToken().isBlank(),
            () -> config.adminToken() != null && !config.adminToken().isBlank(),
            config::adminApiEnabled,
            config::requireMtls,
            () -> config.ipAllowlist() != null && !config.ipAllowlist().isBlank(),
            () -> publicBind(config.bind()) && (config.ipAllowlist() == null || config.ipAllowlist().isBlank()),
            () -> config.redisUri() != null && !internalHost(config.redisUri().getHost()),
            () -> coreJdbcActive && !internalHost(jdbcHost(config.jdbcUrl())),
            () -> "S3".equalsIgnoreCase(config.storageType()) && config.storageEndpoint() != null && !internalHost(config.storageEndpoint().getHost()),
            () -> "S3".equalsIgnoreCase(config.storageType()) && config.storageEndpoint() != null && "http".equalsIgnoreCase(config.storageEndpoint().getScheme()) && !internalHost(config.storageEndpoint().getHost()),
            () -> config.rateLimitRequests(),
            () -> config.rateLimitWindow().toSeconds(),
            rankingRepository::dirtyCount,
            rankingRecalculationTask::drainedTotal,
            rankingRecalculationTask::recalculatedTotal,
            rankingRecalculationTask::failuresTotal,
            rankingRecalculationTask::lastBatchSize,
            securityRejectsTotal,
            securityRejectsRateLimited,
            securityRejectsUnauthorized,
            securityRejectsMtlsRequired,
            securityRejectsIpNotAllowed,
            securityRejectsAdminPermissionDenied
        );
    }

    private static long redisCacheFailures(
            NodeRegistry nodes,
            RouteTicketStore tickets,
            RouteSessionStore sessions,
            IslandRepository islands,
            IslandMetadataRepository metadata,
            PlayerProfileRepository playerProfiles,
            IslandPermissionRuleRepository permissionRules,
            IslandRoleRepository roles,
            IslandRuntimeRepository runtimes,
            RankingRepository rankings,
            IslandLevelRepository levels,
            IslandBankRepository bank,
            IslandLimitRepository limits,
            IslandMissionRepository missions,
            IslandUpgradeRepository upgrades,
            IslandTemplateRepository templates,
            IslandSnapshotRepository snapshots,
            IslandLogRepository islandLogs,
            RedisCacheAdmin redisCacheAdmin,
            RedisActivationLock activationLock,
            RedisPlayerCreationLock playerCreationLock,
            AuditLogger audit) {
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
}
