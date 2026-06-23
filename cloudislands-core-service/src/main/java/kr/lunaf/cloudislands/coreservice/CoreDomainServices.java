package kr.lunaf.cloudislands.coreservice;

import kr.lunaf.cloudislands.coreservice.job.JobCompletionService;
import kr.lunaf.cloudislands.coreservice.metrics.PrometheusMetricsRenderer;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeService;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.coreservice.workflow.CreateIslandWorkflow;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;

public record CoreDomainServices(
    RoutingOrchestrator routing,
    CreateIslandWorkflow createIsland,
    IslandLifecycleWorkflow islandLifecycle,
    MigrationAdminService migrationAdmin,
    JobCompletionService jobCompletion,
    PrometheusMetricsRenderer metrics,
    RankingRecalculationService levelRecalculation,
    UpgradePolicy upgradePolicy,
    IslandUpgradeService upgradeService,
    CoreIslandDeleteService islandDeleteService
) {}
