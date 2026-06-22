package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.EmptyIslandSaveTask;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.paper.activation.IslandDeactivationHandler;
import kr.lunaf.cloudislands.paper.activation.IslandSaveService;
import kr.lunaf.cloudislands.paper.activation.PeriodicIslandSaveTask;
import kr.lunaf.cloudislands.paper.activation.ShardWorldManager;
import kr.lunaf.cloudislands.paper.cache.PermissionCacheSyncService;
import kr.lunaf.cloudislands.paper.cache.PermissionEventPoller;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.generator.CropGrowthLevelCache;
import kr.lunaf.cloudislands.paper.integration.IntegrationLifecycleHooks;
import kr.lunaf.cloudislands.paper.job.CoreBackedIslandJobSource;
import kr.lunaf.cloudislands.paper.job.PaperIslandJobWorker;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.level.PeriodicIslandLevelScanTask;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;
import kr.lunaf.cloudislands.paper.world.ShardWorldPreloader;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.paper.world.bundle.ExternalTarBundleExtractor;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.export.ExternalTarIslandBundleExporter;
import kr.lunaf.cloudislands.storage.IslandStorage;

final class PaperIslandNodeRuntime {
    private PaperIslandNodeRuntime() {
    }

    static void start(
        CloudIslandsPaperPlugin plugin,
        CoreApiClient client,
        String nodeId,
        IslandStorage storage,
        IslandLimitCache limitCache,
        PaperRuntimeConfig config
    ) {
        CropGrowthLevelCache cropGrowthLevels = new CropGrowthLevelCache(client);
        String fallbackServerName = config.routing().fallbackServerName();
        ShardWorldManager shardWorldManager = new ShardWorldManager(
            config.worker().shardWorldPrefix(),
            config.worker().shardCount(),
            config.worker().cellSize()
        );
        plugin.activeIslands = new ActiveIslandRegistry();
        plugin.agent.routeTickets().setActiveIslands(plugin.activeIslands);
        IntegrationLifecycleHooks integrationHooks = IntegrationLifecycleHooks.fromRegistry(plugin.integrationRegistry, nodeId);
        IslandSaveService saveService = new IslandSaveService(
            storage,
            new ExternalTarIslandBundleExporter(plugin.getServer().getWorldContainer().toPath(), integrationHooks),
            plugin.getDataFolder().toPath().resolve("exports"),
            config.snapshots()
        );
        IslandActivationJobHandler activationHandler = new IslandActivationJobHandler(
            storage,
            shardWorldManager,
            plugin.agent.protection(),
            new IslandWorldRestorer(
                storage,
                plugin.getDataFolder().toPath().resolve("staging"),
                new BundleRestorePlanner(new ExternalTarBundleExtractor()),
                integrationHooks
            ),
            new ShardWorldPreloader(plugin),
            config.worker().activationPreloadRadius(),
            new FileBackedCellTransfer(plugin.getServer().getWorldContainer().toPath()),
            plugin.activeIslands,
            saveService,
            config.worker().defaultIslandSize(),
            integrationHooks
        );
        IslandDeactivationHandler deactivationHandler = new IslandDeactivationHandler(
            plugin.activeIslands,
            shardWorldManager,
            plugin.agent.protection(),
            saveService,
            integrationHooks
        );
        PermissionCacheSyncService permissionSync = new PermissionCacheSyncService(plugin, client, plugin.agent.permissionCache());
        plugin.jobWorker = new PaperIslandJobWorker(
            plugin,
            new CoreBackedIslandJobSource(client),
            activationHandler,
            deactivationHandler,
            plugin.activeIslands,
            permissionSync,
            nodeId
        );
        plugin.permissionEventPoller = new PermissionEventPoller(
            plugin,
            client,
            permissionSync,
            plugin.generatorLevels,
            cropGrowthLevels,
            limitCache,
            plugin.agent.protection(),
            nodeId,
            fallbackServerName,
            plugin.messages,
            plugin.agent.cacheInvalidator()
        );
        plugin.periodicSaveTask = new PeriodicIslandSaveTask(plugin, plugin.activeIslands, saveService, client, nodeId);
        plugin.emptyIslandSaveTask = new EmptyIslandSaveTask(plugin, plugin.activeIslands, plugin.agent.protection(), saveService, client);
        plugin.periodicLevelScanTask = new PeriodicIslandLevelScanTask(
            plugin,
            plugin.activeIslands,
            new IslandLevelScanService(plugin, () -> plugin.activeIslands, client)
        );
        plugin.permissionEventPoller.start(config.protection().cacheEventPollTicks());
        plugin.lifecycle.started("permission-event-poller", plugin.permissionEventPoller::stop);
        plugin.jobWorker.start(config.worker().activationWorkerIntervalTicks());
        plugin.lifecycle.started("job-worker", plugin.jobWorker::stop);
        plugin.periodicSaveTask.start(config.worker().periodicSaveSeconds());
        plugin.lifecycle.started("periodic-save", plugin.periodicSaveTask::stop);
        plugin.emptyIslandSaveTask.start(config.worker().saveOnEmptyAfterSeconds());
        plugin.lifecycle.started("empty-save", plugin.emptyIslandSaveTask::stop);
        plugin.periodicLevelScanTask.start(config.worker().levelScanIntervalSeconds());
        plugin.lifecycle.started("level-scan", plugin.periodicLevelScanTask::stop);
    }
}
