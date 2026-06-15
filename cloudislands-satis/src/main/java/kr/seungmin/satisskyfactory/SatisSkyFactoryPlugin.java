package kr.seungmin.satisskyfactory;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddonBootstrap;
import kr.lunaf.cloudislands.api.event.CoreCacheClearEvent;
import kr.lunaf.cloudislands.api.event.CoreReloadEvent;
import kr.lunaf.cloudislands.api.event.IslandActivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandBankChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandBiomeChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandCreatedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivationRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.api.event.IslandFlagChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandHomeChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandLevelRecalculateEvent;
import kr.lunaf.cloudislands.api.event.IslandLimitChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberChangedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigratedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigrationEvent;
import kr.lunaf.cloudislands.api.event.IslandOwnershipChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandPermissionChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandRoleCatalogChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandRuntimeChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotCreateEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotRequestEvent;
import kr.lunaf.cloudislands.api.event.IslandUpgradeEvent;
import kr.lunaf.cloudislands.api.event.IslandVisitEvent;
import kr.lunaf.cloudislands.api.event.IslandWarpChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandWorthChangeEvent;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.seungmin.satisskyfactory.command.FactoryCommand;
import kr.seungmin.satisskyfactory.config.ConfigService;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyModeFactory;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiHolder;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.CloudIslandsSkyblockProvider;
import kr.seungmin.satisskyfactory.hook.PlaceholderHook;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.listener.FactoryLifecycleListener;
import kr.seungmin.satisskyfactory.listener.FactoryGuiListener;
import kr.seungmin.satisskyfactory.listener.MachineListener;
import kr.seungmin.satisskyfactory.logistics.ItemNetworkService;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.CoreApiSatisStateService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import kr.seungmin.satisskyfactory.task.MachineTickService;
import kr.seungmin.satisskyfactory.task.MaintenanceTickService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SatisSkyFactoryPlugin extends JavaPlugin implements CloudIslandsAddon {
    private static final String ADDON_ID = "cloudislands-satis";
    private static final Map<String, String> FEATURE_ALIASES = Map.of(
            "factories", "machines",
            "generators", "resource-nodes",
            "upgrades", "research",
            "missions", "contracts",
            "menus", "gui"
    );
    private ConfigService configs;
    private MessageService messages;
    private DatabaseService database;
    private SkyblockProvider skyblock;
    private EconomyService economy;
    private ItemRegistry itemRegistry;
    private CustomItemFactory itemFactory;
    private MachineDefinitionService machineDefinitions;
    private RecipeService recipes;
    private StorageService storage;
    private FactoryIslandService islands;
    private MachineService machines;
    private IslandBoostService boosts;
    private ResourceNodeService nodes;
    private ItemNetworkService itemNetworks;
    private PowerNetworkService power;
    private MarketService market;
    private ContractService contracts;
    private MaintenanceService maintenance;
    private ResearchService research;
    private FactoryGuiService gui;
    private DirtySaveService dirtySaves;
    private CoreApiSatisStateService coreApiState;
    private MachineTickService ticker;
    private MaintenanceTickService maintenanceTicker;
    private PlaceholderHook placeholderHook;
    private CloudIslandsApi cloudIslandsApi;
    private boolean addonRuntimeEnabled;
    private boolean cloudIslandsApiMissing;
    private boolean commandsRegistered;
    private boolean machineListenerRegistered;
    private boolean guiListenerRegistered;
    private boolean lifecycleListenerRegistered;
    private boolean addonStateReportingWasEnabled;
    private MachineListener machineListener;
    private FactoryGuiListener guiListener;
    private FactoryLifecycleListener lifecycleListener;
    private Map<String, Boolean> effectiveFeatures = Map.of();
    private final Set<UUID> coreHydratedIslands = ConcurrentHashMap.newKeySet();
    private String databaseFallbackReason = "none";
    private String pendingDatabaseConfigFallbackReason = "none";
    private String databaseSettingsFingerprint = "";

    @Override
    public void onEnable() {
        configs = new ConfigService(this);
        configs.load();
        messages = new MessageService(configs);
        addonRuntimeEnabled = false;
        effectiveFeatures = Map.of();
        if (!registerCloudIslandsAddon()) {
            installDisabledCommandHandler("addon-disabled");
            if (cloudIslandsApiMissing) {
                getServer().getScheduler().runTask(this, () -> getServer().getPluginManager().disablePlugin(this));
            }
            return;
        }
        startRuntime();
    }

    private void startRuntime() {
        if (database != null) {
            applyAddonRuntimeState();
            return;
        }
        skyblock = createSkyblockProvider();
        if (!skyblock.enable()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        configureSkyblockHook();

        databaseFallbackReason = "none";
        DatabaseService.Settings settings = databaseSettings();
        appendDatabaseFallbackReason(pendingDatabaseConfigFallbackReason);
        databaseSettingsFingerprint = databaseSettingsFingerprint(settings);
        database = new DatabaseService(this, settings);
        database.open();
        syncDatabaseFallbackReason();
        applyCoreApiDatabaseFallback(settings);
        syncDatabaseFallbackReason();
        warnIfUnsharedDatabaseInCloudIslandsMode();
        warnIfAmbiguousSetupDatabase();
        warnIfUnsafeDatabaseFallbackChain();
        getLogger().info("Satis database backend: " + database.activeBackend() + " (" + database.databaseDescription() + ")");

        economy = EconomyModeFactory.create(this, configs.main());
        itemRegistry = new ItemRegistry();
        machineDefinitions = new MachineDefinitionService();
        itemFactory = new CustomItemFactory(this, machineDefinitions);
        recipes = new RecipeService();
        storage = new StorageService(database, configInt("storage.default-capacity", "limits.default-storage-capacity", 10000));
        islands = new FactoryIslandService(skyblock, database);
        machines = new MachineService(database, machineDefinitions, storage);
        boosts = new IslandBoostService(skyblock);
        boosts.configure(configs.main());
        nodes = new ResourceNodeService(database);
        dirtySaves = new DirtySaveService(this, database);
        configureCoreApiStateWriters();
        storage.dirtySaves(dirtySaves);
        islands.dirtySaves(dirtySaves);
        machines.dirtySaves(dirtySaves);
        nodes.dirtySaves(dirtySaves);
        itemNetworks = new ItemNetworkService(database, machines, machineDefinitions);
        power = new PowerNetworkService(database, machines, machineDefinitions, recipes, storage);
        market = new MarketService(storage, economy, database, itemRegistry, () -> featureEnabled("maintenance"));
        contracts = new ContractService(storage, economy, database, boosts, () -> featureEnabled("maintenance"));
        maintenance = new MaintenanceService(machines, economy, database);
        research = new ResearchService(database, economy, () -> featureEnabled("maintenance"));
        gui = new FactoryGuiService(storage, itemRegistry, machineDefinitions, recipes, islands, research, economy, messages, this::operationalFeatureEnabled);

        loadDefinitions();
        refreshIslandCache();
        refreshMachineCache();
        logFeatureWarnings();
        if (featureEnabled("machines")) {
            rebuildNetworks();
        }

        restartRuntimeTasks();

        registerCommands();
        registerListeners();
        registerPlaceholders();
        getLogger().info("SatisSkyFactory enabled using " + economy.name() + " economy.");
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
            maintenanceTicker = null;
        }
        if (dirtySaves != null) {
            dirtySaves.stop();
        }
        if (placeholderHook != null) {
            placeholderHook.unregister();
            placeholderHook = null;
        }
        unregisterCloudIslandsAddon();
        stopRuntimeActivity();
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public void reloadPluginConfig() {
        configs.load();
        effectiveFeatures = Map.of();
        if (!registerCloudIslandsAddon()) {
            stopRuntimeActivity();
            return;
        }
        reloadDatabaseIfNeeded();
        configureSkyblockHook();
        boosts.configure(configs.main());
        loadDefinitions();
        refreshIslandCache();
        refreshMachineCache();
        logFeatureWarnings();
        if (featureEnabled("machines")) {
            rebuildNetworks();
        }
        restartRuntimeTasks();
        registerCommands();
        registerListeners();
        refreshPlaceholders();
    }

    private void restartRuntimeTasks() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
            maintenanceTicker = null;
        }
        if (dirtySaves != null) {
            dirtySaves.stop();
        }
        if (featureEnabled("machines")) {
            ticker = new MachineTickService(
                    this,
                    machines,
                    machineDefinitions,
                    storage,
                    recipes,
                    research,
                    nodes,
                    power,
                    boosts,
                    islands,
                    configInt("settings.max-machines-per-tick", "settings.max-machines-per-cycle", 300),
                    configs.main().getInt("settings.max-backfill-cycles", 60),
                    configs.main().getBoolean("settings.offline-production.enabled", true),
                    Math.max(0L, configs.main().getLong("settings.offline-production.max-hours", 8)) * 60L * 60L * 1000L,
                    configs.main().getDouble("settings.offline-production.efficiency", 0.35),
                    configInt("resource-nodes.link-radius", "settings.resource-node-link-radius", 3),
                    Set.copyOf(configs.main().getStringList("limits.recovery-machine-types")),
                    maintenanceDouble("maintenance.limited.efficiency", "maintenance.limited-efficiency", 0.5),
                    configs.file("maintenance.yml").getInt("maintenance.limited.max-operating-tier", 2),
                    configs.file("maintenance.yml").getDouble("maintenance.locked.recovery-efficiency", 0.30),
                    configs.file("maintenance.yml").getInt("maintenance.locked.max-operating-tier", 1),
                    configs.file("maintenance.yml").getDouble("maintenance.break-wear", 100.0),
                    activeParticleLimit(configs.main(), configInt("settings.max-machines-per-tick", "settings.max-machines-per-cycle", 300)),
                    () -> featureEnabled("machines"),
                    () -> featureEnabled("maintenance"),
                    () -> operationalFeatureEnabled("resource-nodes")
            );
            ticker.start(configLong("settings.tick-period-ticks", "settings.tick-interval", 20));
        }
        if (featureEnabled("maintenance")) {
            maintenanceTicker = new MaintenanceTickService(this, islands, skyblock, maintenance, () -> featureEnabled("maintenance"));
            maintenanceTicker.start(configLong("settings.maintenance-check-period-ticks", "settings.maintenance-check-interval", 1200));
        }
        if (dataWritesEnabled()) {
            dirtySaves.start(dirtySavePeriodTicks(configs.main()));
        }
    }

    private boolean dataWritesEnabled() {
        return featureEnabled("machines")
                || featureEnabled("storage")
                || operationalFeatureEnabled("resource-nodes")
                || operationalFeatureEnabled("market")
                || operationalFeatureEnabled("contracts")
                || featureEnabled("research")
                || featureEnabled("maintenance");
    }

    private void putRuntimeActivityState(Map<String, String> state) {
        state.put("runtime-commands-registered", Boolean.toString(commandsRegistered));
        state.put("runtime-machine-listener-registered", Boolean.toString(machineListenerRegistered));
        state.put("runtime-gui-listener-registered", Boolean.toString(guiListenerRegistered));
        state.put("runtime-lifecycle-listener-registered", Boolean.toString(lifecycleListenerRegistered));
        state.put("runtime-placeholder-registered", Boolean.toString(placeholderHook != null));
        state.put("runtime-data-writes-enabled", Boolean.toString(dataWritesEnabled()));
        state.put("runtime-lifecycle-state-enabled", Boolean.toString(lifecycleStateEnabled()));
        state.put("runtime-machine-ticker-running", Boolean.toString(ticker != null && ticker.running()));
        state.put("runtime-maintenance-ticker-running", Boolean.toString(maintenanceTicker != null && maintenanceTicker.running()));
        state.put("runtime-dirty-save-running", Boolean.toString(dirtySaves != null && dirtySaves.running()));
        state.put("runtime-machine-ticker-gate", "addonRuntimeEnabled&&features.machines");
        state.put("runtime-maintenance-ticker-gate", "addonRuntimeEnabled&&features.maintenance");
        state.put("runtime-dirty-save-gate", "addonRuntimeEnabled&&any-write-feature-enabled");
        state.put("runtime-duplicate-tick-guard", "ticker-stops-when-addon-or-machine-feature-disabled");
        state.put("runtime-core-api-state-writer", Boolean.toString(coreApiState != null));
        state.put("runtime-registration-policy", "disabled-features-skip-commands-gui-listeners-tasks-and-writes");
        state.put("runtime-disabled-features", disabledRuntimeFeatures());
        state.put("runtime-command-handler-mode", commandsRegistered ? "active" : "disabled-stub");
        state.put("runtime-command-block-reason", runtimeCommandBlockReason());
        state.put("runtime-blocked-components", runtimeBlockedComponents());
        state.put("runtime-disabled-component-policy", "preserve-data-and-return-empty-tabs-or-unregister-listeners");
        state.put("command-list-format", "one-line-per-command");
        state.put("command-list-paging", "factory command list [page],factory admin command list [page]");
        state.put("command-list-page-size", "12");
        state.put("command-list-disabled-policy", "empty-tab-complete-disabled-command-stub");
        putDataWriteGateState(state);
        putLifecycleCoverageState(state);
        putIslandMobilityState(state);
    }

    private void putDataWriteGateState(Map<String, String> state) {
        state.put("data-write-mode", dataWritesEnabled() ? "enabled" : "disabled");
        state.put("write-gate-machines", Boolean.toString(featureEnabled("machines")));
        state.put("write-gate-storage", Boolean.toString(storageDataEnabled()));
        state.put("write-gate-resource-nodes", Boolean.toString(operationalFeatureEnabled("resource-nodes")));
        state.put("write-gate-market", Boolean.toString(operationalFeatureEnabled("market")));
        state.put("write-gate-contracts", Boolean.toString(operationalFeatureEnabled("contracts")));
        state.put("write-gate-research", Boolean.toString(featureEnabled("research")));
        state.put("write-gate-maintenance", Boolean.toString(featureEnabled("maintenance")));
        state.put("write-gate-lifecycle-state", Boolean.toString(lifecycleStateEnabled()));
        state.put("write-gate-lifecycle-listener", Boolean.toString(lifecycleListenerNeeded()));
        state.put("write-gate-addon-state", Boolean.toString(featureEnabled("addon-state") && coreApiAddonStateAvailable()));
        state.put("write-gate-dirty-save", Boolean.toString(dataWritesEnabled()));
    }

    private void putLifecycleCoverageState(Map<String, String> state) {
        state.put("lifecycle-event-source", "CloudIslandsAddon.onCloudEvent");
        state.put("lifecycle-event-coverage", "create,activate,deactivation-request,deactivated,migration-request,migrated,delete,restore,reset,recovery,repair,runtime,visit,member,role,ownership,flag,permission,bank,biome,home,warp,level,worth,upgrade,limit,snapshot");
        state.put("lifecycle-event-actions", "activate-and-migration=synchronize,deactivate-and-snapshot=flush,delete-and-reset=purge,recovery=suspend,member-role-ownership-permission-bank-biome-home-warp-level-worth=synchronize");
        state.put("lifecycle-event-storage-policy", "cloudislands-island-uuid-stable-state-remap-active-world");
    }

    private void putIslandMobilityState(Map<String, String> state) {
        state.put("island-state-key", "cloudislands-island-uuid");
        state.put("island-state-node-bound", "false");
        state.put("island-state-mobility", "portable-across-island-nodes");
        state.put("island-state-migration-policy", "remap-location-only-preserve-island-uuid");
        state.put("island-state-authority", "cloudislands-core-api");
        state.put("island-state-active-world-source", "cloudislands-runtime");
        state.put("island-state-active-runtime-source", "CloudIslands IslandRuntime");
        state.put("island-state-location-remap", "active-world-center-delta");
        state.put("island-state-remap-key", "islandUuid+activeWorld+activeCenter");
        state.put("island-state-failover-policy", "last-confirmed-addon-state-only");
        state.put("island-state-ab-node-scenario", "A-server-to-B-server-preserve-addon-state");
        state.put("island-state-storage-authority", coreApiAddonStateAvailable() ? "cloudislands-addon-state" : databaseScope());
        state.put("island-state-write-fence", "cloudislands-runtime-active-island-only");
        state.put("island-state-duplicate-tick-policy", "single-active-runtime-owner");
        state.put("island-state-reconnect-policy", "reload-by-island-uuid-and-remap-active-world");
        state.put("island-state-node-handoff-policy", "A-node-save-B-node-restore-by-island-uuid");
    }

    private String disabledRuntimeFeatures() {
        java.util.List<String> disabled = new java.util.ArrayList<>();
        featureSnapshot().forEach((feature, enabled) -> {
            if (!operationalFeatureEnabled(feature)) {
                disabled.add(feature);
            }
        });
        java.util.Collections.sort(disabled);
        return String.join(",", disabled);
    }

    private String runtimeCommandBlockReason() {
        if (commandsRegistered) {
            return "none";
        }
        if (!addonRuntimeEnabled) {
            return "addon-disabled";
        }
        if (!featureEnabled("commands")) {
            return "commands-feature-disabled";
        }
        return "command-not-registered";
    }

    private String runtimeBlockedComponents() {
        java.util.List<String> blocked = new java.util.ArrayList<>();
        if (!commandsRegistered) {
            blocked.add("commands:" + runtimeCommandBlockReason());
        }
        if (!machineListenerRegistered) {
            blocked.add("machine-listener:" + (!featureEnabled("machines") ? "machines-feature-disabled" : "not-registered"));
        }
        if (!guiListenerRegistered) {
            blocked.add("gui-listener:" + (!featureEnabled("gui") ? "gui-feature-disabled" : "not-registered"));
        }
        if (!lifecycleListenerRegistered) {
            blocked.add("lifecycle-listener:" + (!lifecycleListenerNeeded() ? "lifecycle-state-disabled" : "not-registered"));
        }
        if (placeholderHook == null) {
            blocked.add("placeholders:" + placeholderBlockReason());
        }
        if (ticker == null || !ticker.running()) {
            blocked.add("machine-ticker:" + (!featureEnabled("machines") ? "machines-feature-disabled" : "not-running"));
        }
        if (maintenanceTicker == null || !maintenanceTicker.running()) {
            blocked.add("maintenance-ticker:" + (!featureEnabled("maintenance") ? "maintenance-feature-disabled" : "not-running"));
        }
        if (dirtySaves == null || !dirtySaves.running()) {
            blocked.add("dirty-save:" + (!dataWritesEnabled() ? "data-writes-disabled" : "not-running"));
        }
        return blocked.isEmpty() ? "none" : String.join(",", blocked);
    }

    private String placeholderBlockReason() {
        if (!featureEnabled("placeholders")) {
            return "placeholders-feature-disabled";
        }
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return "placeholderapi-not-installed";
        }
        return "not-registered";
    }

    private boolean lifecycleStateEnabled() {
        return featureEnabled("machines")
                || featureEnabled("storage")
                || operationalFeatureEnabled("resource-nodes")
                || operationalFeatureEnabled("market")
                || operationalFeatureEnabled("contracts")
                || featureEnabled("research")
                || featureEnabled("maintenance");
    }

    private boolean lifecycleListenerNeeded() {
        return featureEnabled("lifecycle") && lifecycleStateEnabled();
    }

    private boolean storageDataEnabled() {
        return featureEnabled("storage")
                || featureEnabled("machines");
    }

    static long dirtySavePeriodTicks(FileConfiguration config) {
        if (config.contains("database.save-interval-seconds")) {
            return Math.max(1L, config.getLong("database.save-interval-seconds", 60L) * 20L);
        }
        if (config.contains("settings.dirty-save-period-ticks")) {
            return Math.max(1L, config.getLong("settings.dirty-save-period-ticks", 1200L));
        }
        return Math.max(1L, config.getLong("settings.dirty-save-interval", 1200L));
    }

    static int activeParticleLimit(FileConfiguration config, int maxMachinesPerTick) {
        if (!config.getBoolean("visuals.particles", true)) {
            return 0;
        }
        return Math.max(1, Math.min(Math.max(1, maxMachinesPerTick), 64));
    }

    private void configureSkyblockHook() {
        boolean allowSpawnIsland = configBoolean("cloudislands.allow-spawn-island", "settings.allow-spawn-island", false);
        skyblock.configure(
                configBoolean("cloudislands.allow-coop-build", "settings.allow-coop-build", false),
                !allowSpawnIsland && configBoolean("cloudislands.protect-spawn-island", "settings.protect-spawn-island", true),
                configBoolean("cloudislands.require-island-member", "settings.require-island-member", true)
        );
    }

    private SkyblockProvider createSkyblockProvider() {
        String provider = configuredSkyblockProvider();
        if (!"CLOUDISLANDS".equalsIgnoreCase(provider) && !"CLOUD_ISLANDS".equalsIgnoreCase(provider)) {
            getLogger().warning("Ignoring legacy skyblock provider '" + provider + "'. CloudIslands Satis uses the CloudIslands API provider.");
        }
        return new CloudIslandsSkyblockProvider(this);
    }

    private String configuredSkyblockProvider() {
        return configs.main().getString("integration.skyblock-provider", "CLOUDISLANDS");
    }

    private int configInt(String primaryPath, String aliasPath, int fallback) {
        return configs.main().contains(primaryPath)
                ? configs.main().getInt(primaryPath, fallback)
                : configs.main().getInt(aliasPath, fallback);
    }

    private long configLong(String primaryPath, String aliasPath, long fallback) {
        return configs.main().contains(primaryPath)
                ? configs.main().getLong(primaryPath, fallback)
                : configs.main().getLong(aliasPath, fallback);
    }

    private boolean configBoolean(String primaryPath, String aliasPath, boolean fallback) {
        return configs.main().contains(primaryPath)
                ? configs.main().getBoolean(primaryPath, fallback)
                : configs.main().getBoolean(aliasPath, fallback);
    }

    private double maintenanceDouble(String primaryPath, String aliasPath, double fallback) {
        return configs.file("maintenance.yml").contains(primaryPath)
                ? configs.file("maintenance.yml").getDouble(primaryPath, fallback)
                : configs.file("maintenance.yml").getDouble(aliasPath, fallback);
    }

    private void loadDefinitions() {
        if (itemDefinitionsNeeded()) {
            itemRegistry.load(configs.file("items.yml"));
        } else {
            itemRegistry.clear();
        }
        if (featureEnabled("machines")) {
            machineDefinitions.load(configs.file("machines.yml"));
            recipes.load(configs.file("recipes.yml"));
        } else {
            machineDefinitions.clear();
            recipes.clear();
        }
        if (operationalFeatureEnabled("resource-nodes")) {
            nodes.load(configs.file("resource-nodes.yml"));
        } else {
            nodes.clear();
            if (dirtySaves != null) {
                dirtySaves.forgetNodes();
            }
        }
        if (operationalFeatureEnabled("market")) {
            market.load(configs.file("market.yml"), configs.file("maintenance.yml"));
        } else {
            market.clear();
        }
        if (operationalFeatureEnabled("contracts")) {
            contracts.load(configs.file("contracts.yml"));
        } else {
            contracts.clear();
        }
        if (featureEnabled("maintenance")) {
            maintenance.load(configs.file("maintenance.yml"));
        } else {
            maintenance.clear();
        }
        if (featureEnabled("research")) {
            research.load(configs.file("research.yml"), configs.file("maintenance.yml"));
        } else {
            research.clear();
        }
        pruneDisabledDirtyQueues();
    }

    private void pruneDisabledDirtyQueues() {
        if (dirtySaves == null) {
            return;
        }
        if (!storageDataEnabled()) {
            dirtySaves.forgetInventories();
        }
        if (!lifecycleStateEnabled()) {
            dirtySaves.forgetIslands();
        }
    }

    private boolean itemDefinitionsNeeded() {
        return featureEnabled("machines")
                || operationalFeatureEnabled("market")
                || operationalFeatureEnabled("contracts")
                || featureEnabled("gui");
    }

    private void refreshMachineCache() {
        if (featureEnabled("machines")) {
            machines.load();
            if (itemNetworks != null) {
                itemNetworks.activate();
            }
            if (power != null) {
                power.activate();
            }
        } else {
            machines.clear();
            if (dirtySaves != null) {
                dirtySaves.forgetMachines();
            }
            if (itemNetworks != null) {
                itemNetworks.clear();
            }
            if (power != null) {
                power.clear();
            }
        }
    }

    private void refreshIslandCache() {
        if (lifecycleStateEnabled()) {
            islands.load();
            return;
        }
        islands.clear();
        if (dirtySaves != null) {
            dirtySaves.forgetIslands();
        }
    }

    private void registerCommands() {
        if (!featureEnabled("commands")) {
            installDisabledCommandHandler("commands");
            commandsRegistered = false;
            return;
        }
        if (commandsRegistered) {
            return;
        }
        FactoryCommand command = new FactoryCommand(
                islands,
                machines,
                machineDefinitions,
                storage,
                nodes,
                skyblock,
                market,
                contracts,
                maintenance,
                research,
                boosts,
                power,
                gui,
                itemFactory,
                itemRegistry,
                messages,
                database,
                this::operationalFeatureEnabled,
                this::addonMetadata,
                this::addonStateSnapshot,
                this::addonIslandStateSnapshot,
                this::reloadPluginConfig
        );
        PluginCommand factory = getCommand("factory");
        if (factory != null) {
            factory.setExecutor(command);
            factory.setTabCompleter(command);
        }
        PluginCommand sfactory = getCommand("sfactory");
        if (sfactory != null) {
            sfactory.setExecutor(command);
            sfactory.setTabCompleter(command);
        }
        commandsRegistered = true;
    }

    private void installDisabledCommandHandler(String reason) {
        java.util.function.Consumer<PluginCommand> installer = command -> {
            if (command == null) {
                return;
            }
            command.setExecutor((sender, _command, _label, _args) -> {
                String feature = reason == null || reason.isBlank() ? "addon" : reason;
                if (messages != null) {
                    messages.send(sender, "feature-disabled", Map.of("feature", feature));
                } else {
                    sender.sendMessage("CloudIslands Satis is disabled: " + feature);
                }
                return true;
            });
            command.setTabCompleter((_sender, _command, _alias, _args) -> java.util.List.of());
        };
        installer.accept(getCommand("factory"));
        installer.accept(getCommand("sfactory"));
    }

    private void registerListeners() {
        if (!featureEnabled("machines")) {
            machineListenerRegistered = unregisterListener(machineListener, machineListenerRegistered);
            machineListener = null;
        } else if (!machineListenerRegistered) {
            machineListener = new MachineListener(
                    () -> featureEnabled("machines"),
                    () -> operationalFeatureEnabled("resource-nodes"),
                    () -> featureEnabled("maintenance"),
                    () -> featureEnabled("research"),
                    () -> operationalFeatureEnabled("gui"),
                    this,
                    itemFactory,
                    machineDefinitions,
                    machines,
                    skyblock,
                    islands,
                    gui,
                    messages,
                    research,
                    nodes,
                    itemNetworks,
                    power,
                    configs.main(),
                    configs.file("maintenance.yml"),
                    boosts
            );
            getServer().getPluginManager().registerEvents(machineListener, this);
            machineListenerRegistered = true;
        }
        if (!featureEnabled("gui")) {
            closeOpenFactoryGuis();
            guiListenerRegistered = unregisterListener(guiListener, guiListenerRegistered);
            guiListener = null;
        } else if (!guiListenerRegistered) {
            guiListener = new FactoryGuiListener(
                    this::operationalFeatureEnabled,
                    islands,
                    skyblock,
                    contracts,
                    research,
                    gui,
                    machines,
                    recipes,
                    storage,
                    itemRegistry,
                    itemFactory,
                    market,
                    machineDefinitions,
                    maintenance,
                    itemNetworks,
                    power,
                    messages,
                    boosts,
                    this::reloadPluginConfig
            );
            getServer().getPluginManager().registerEvents(guiListener, this);
            guiListenerRegistered = true;
        }
        if (!lifecycleListenerNeeded()) {
            lifecycleListenerRegistered = unregisterListener(lifecycleListener, lifecycleListenerRegistered);
            lifecycleListener = null;
        } else if (!lifecycleListenerRegistered) {
            lifecycleListener = new FactoryLifecycleListener(
                    this::lifecycleListenerNeeded,
                    () -> operationalFeatureEnabled("resource-nodes"),
                    () -> featureEnabled("machines"),
                    () -> featureEnabled("maintenance"),
                    islands,
                    skyblock,
                    nodes,
                    machines,
                    itemNetworks,
                    power,
                    maintenance
            );
            getServer().getPluginManager().registerEvents(lifecycleListener, this);
            lifecycleListenerRegistered = true;
        }
    }

    private boolean unregisterListener(Listener listener, boolean registered) {
        if (listener != null && registered) {
            HandlerList.unregisterAll(listener);
        }
        return false;
    }

    private void closeOpenFactoryGuis() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof FactoryGuiHolder) {
                player.closeInventory();
            }
        }
    }

    private void rebuildNetworks() {
        machines.all().stream()
                .map(machine -> machine.islandUuid())
                .distinct()
                .forEach(islandUuid -> {
                    itemNetworks.rebuildIsland(islandUuid);
                    power.rebuildIsland(islandUuid);
                });
    }

    private void registerPlaceholders() {
        if (!featureEnabled("placeholders")) {
            return;
        }
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        if (placeholderHook != null) {
            return;
        }
        placeholderHook = new PlaceholderHook(this, islands, machines, storage, nodes, power, boosts, research, contracts, this::operationalFeatureEnabled);
        placeholderHook.register();
        getLogger().info("Registered PlaceholderAPI expansion: satisskyfactory");
    }

    private void refreshPlaceholders() {
        if (!featureEnabled("placeholders") || !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            if (placeholderHook != null) {
                placeholderHook.unregister();
                placeholderHook = null;
            }
            return;
        }
        if (placeholderHook == null) {
            registerPlaceholders();
        }
    }

    private void reloadDatabaseIfNeeded() {
        DatabaseService.Settings settings = databaseSettings();
        appendDatabaseFallbackReason(pendingDatabaseConfigFallbackReason);
        String nextFingerprint = databaseSettingsFingerprint(settings);
        if (database != null && nextFingerprint.equals(databaseSettingsFingerprint) && !coreApiFallbackRecovered(settings)) {
            configureCoreApiStateWriters();
            return;
        }
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
            maintenanceTicker = null;
        }
        if (dirtySaves != null) {
            dirtySaves.stop();
            dirtySaves.discard();
        }
        closeOpenFactoryGuis();
        machineListenerRegistered = unregisterListener(machineListener, machineListenerRegistered);
        machineListener = null;
        guiListenerRegistered = unregisterListener(guiListener, guiListenerRegistered);
        guiListener = null;
        lifecycleListenerRegistered = unregisterListener(lifecycleListener, lifecycleListenerRegistered);
        lifecycleListener = null;
        commandsRegistered = false;
        if (database != null) {
            database.coreStateWriter(null);
            database.coreTableWriter(null);
            database.coreGlobalStateWriter(null);
            database.coreGlobalTableWriter(null);
            database.close();
        }
        databaseSettingsFingerprint = nextFingerprint;
        databaseFallbackReason = "none";
        appendDatabaseFallbackReason(pendingDatabaseConfigFallbackReason);
        database = new DatabaseService(this, settings);
        database.open();
        syncDatabaseFallbackReason();
        applyCoreApiDatabaseFallback(settings);
        syncDatabaseFallbackReason();
        warnIfUnsharedDatabaseInCloudIslandsMode();
        warnIfAmbiguousSetupDatabase();
        warnIfUnsafeDatabaseFallbackChain();
        getLogger().info("Reloaded Satis database backend: " + database.activeBackend() + " (" + database.databaseDescription() + ")");
        storage = new StorageService(database, configInt("storage.default-capacity", "limits.default-storage-capacity", 10000));
        islands = new FactoryIslandService(skyblock, database);
        machines = new MachineService(database, machineDefinitions, storage);
        nodes = new ResourceNodeService(database);
        dirtySaves = new DirtySaveService(this, database);
        configureCoreApiStateWriters();
        storage.dirtySaves(dirtySaves);
        islands.dirtySaves(dirtySaves);
        machines.dirtySaves(dirtySaves);
        nodes.dirtySaves(dirtySaves);
        itemNetworks = new ItemNetworkService(database, machines, machineDefinitions);
        power = new PowerNetworkService(database, machines, machineDefinitions, recipes, storage);
        market = new MarketService(storage, economy, database, itemRegistry, () -> featureEnabled("maintenance"));
        contracts = new ContractService(storage, economy, database, boosts, () -> featureEnabled("maintenance"));
        maintenance = new MaintenanceService(machines, economy, database);
        research = new ResearchService(database, economy, () -> featureEnabled("maintenance"));
        gui = new FactoryGuiService(storage, itemRegistry, machineDefinitions, recipes, islands, research, economy, messages, this::operationalFeatureEnabled);
        coreHydratedIslands.clear();
    }

    private boolean coreApiFallbackRecovered(DatabaseService.Settings settings) {
        return settings != null
                && settings.backend() == DatabaseService.StorageBackend.CORE_API
                && database != null
                && database.activeBackend() != DatabaseService.StorageBackend.CORE_API
                && coreApiAddonStateAvailable();
    }

    private void configureCoreApiStateWriters() {
        if (database == null || dirtySaves == null) {
            return;
        }
        database.coreStateWriter(null);
        database.coreTableWriter(null);
        database.coreGlobalStateWriter(null);
        database.coreGlobalTableWriter(null);
        dirtySaves.coreStatePublisher(null);
        dirtySaves.coreStateDeletePublisher(null);
        coreApiState = null;
        if (database.activeBackend() != DatabaseService.StorageBackend.CORE_API || !coreApiAddonStateAvailable()) {
            return;
        }
        coreApiState = new CoreApiSatisStateService(getLogger(), cloudIslandsApi, ADDON_ID, coreApiFlattenedFallbackEnabled());
        database.coreStateWriter(coreApiState::publishRow);
        database.coreTableWriter(coreApiState::publishTable);
        database.coreGlobalStateWriter(coreApiState::publishGlobalRow);
        database.coreGlobalTableWriter(coreApiState::publishGlobalTable);
        coreApiState.hydrateGlobal(database);
        dirtySaves.coreStatePublisher(coreApiState::publishDirtyBatch);
        dirtySaves.coreStateDeletePublisher(delete -> coreApiState.removeRow(delete.islandUuid(), delete.key()));
    }

    private boolean registerCloudIslandsAddon() {
        cloudIslandsApiMissing = false;
        cloudIslandsApi = resolveCloudIslandsApi();
        if (cloudIslandsApi == null) {
            if (requiresCloudIslandsApi()) {
                getLogger().severe("CloudIslands API is required for the configured Satis integration mode.");
                addonRuntimeEnabled = false;
                cloudIslandsApiMissing = true;
                return false;
            }
            addonRuntimeEnabled = true;
            return true;
        }
        try {
            CloudIslandsAddonSnapshot addon = register(cloudIslandsApi).join();
            addonRuntimeEnabled = addon.enabled();
            effectiveFeatures = addon.features();
            addonStateReportingWasEnabled = addonStateReportingEnabled(addon);
            getLogger().info("Registered CloudIslands addon: " + addon.id() + " enabled=" + addon.enabled());
            if (!addon.enabled()) {
                getLogger().info("CloudIslands disabled this addon through the parent config.");
                addonRuntimeEnabled = false;
                effectiveFeatures = Map.of();
                return false;
            }
        } catch (RuntimeException exception) {
            getLogger().warning("Failed to register CloudIslands Satis addon: " + exception.getMessage());
            addonRuntimeEnabled = false;
            effectiveFeatures = Map.of();
            return false;
        }
        return true;
    }

    private CloudIslandsApi resolveCloudIslandsApi() {
        CloudIslandsApi api = CloudIslandsAddonBootstrap.findApi().orElse(null);
        if (api != null) {
            return api;
        }
        return getServer().getServicesManager().load(CloudIslandsApi.class);
    }

    private boolean requiresCloudIslandsApi() {
        return true;
    }

    private void unregisterCloudIslandsAddon() {
        if (cloudIslandsApi == null) {
            return;
        }
        try {
            unregister(cloudIslandsApi).join();
        } catch (RuntimeException exception) {
            getLogger().warning("Failed to unregister CloudIslands Satis addon: " + exception.getMessage());
        }
        cloudIslandsApi = null;
    }

    @Override
    public String addonId() {
        return ADDON_ID;
    }

    @Override
    public String addonDisplayName() {
        return "CloudIslands Satis";
    }

    @Override
    public String addonVersion() {
        return getDescription().getVersion();
    }

    @Override
    public boolean enabledByDefault() {
        boolean enabled = configs.main().contains("satis.enabled")
                ? configs.main().getBoolean("satis.enabled", true)
                : configs.main().getBoolean("integration.enabled", true);
        if (configs.main().contains("addons." + ADDON_ID + ".enabled")) {
            enabled = enabled && configs.main().getBoolean("addons." + ADDON_ID + ".enabled", true);
        }
        return enabled;
    }

    @Override
    public Map<String, Boolean> addonFeatures() {
        return featureSnapshot();
    }

    @Override
    public Map<String, String> addonMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        String scope = databaseScope();
        metadata.put("mode", configs.main().getString("integration.mode", "ADDON"));
        metadata.putAll(cloudIslandsIntegrationMetadata());
        metadata.put("skyblock-provider", "CLOUDISLANDS");
        metadata.put("cloudislands-adapter", Boolean.toString(configs.main().getBoolean("integration.cloudislands-adapter", true)));
        metadata.put("requires-cloudislands-api", Boolean.toString(requiresCloudIslandsApi()));
        metadata.put("cloudislands-api-available", Boolean.toString(cloudIslandsApi != null));
        metadata.put("cloudislands-api-resolution", "bootstrap-or-services-manager");
        metadata.put("runtime-hard-depend-plugin", "CloudIslands");
        metadata.put("standalone-island-management", "false");
        metadata.put("missing-cloudislands-behavior", "disable-plugin");
        metadata.put("satis-enabled-configured", Boolean.toString(enabledByDefault()));
        metadata.put("addon-runtime-enabled", Boolean.toString(addonRuntimeEnabled));
        putRuntimeActivityState(metadata);
        metadata.put("database-scope", scope);
        metadata.put("database-supported-backends", "SQLITE,POSTGRESQL,MYSQL,MARIADB,CORE_API");
        metadata.put("database-configured-backend", configuredDatabaseBackendName());
        metadata.put("database-setup-sections", databaseSetupSectionsMetadata());
        metadata.put("database-setup-auto-selected", Boolean.toString(databaseSetupAutoSelected()));
        metadata.put("database-setup-selected-backend", databaseSetupSelectedBackendMetadata());
        metadata.put("database-setup-warning", databaseSetupWarningMetadata());
        metadata.put("database-active-backend", database == null ? "NOT_OPEN" : database.activeBackend().name());
        metadata.put("database-attempted-backends", databaseAttemptedBackendsMetadata());
        metadata.put("database-attempt-order", databaseBackendAttemptOrderMetadata());
        metadata.put("database-jdbc-target", databaseJdbcTargetMetadata());
        metadata.put("database-fallback-jdbc-target", databaseFallbackJdbcTargetMetadata());
        metadata.put("database-fallback-jdbc-targets", databaseFallbackJdbcTargetsMetadata());
        metadata.put("database-fallback-reason", databaseFallbackReason);
        metadata.put("database-fallback-active", Boolean.toString(databaseFallbackActive()));
        metadata.put("database-fallback-status", databaseFallbackStatus());
        metadata.put("database-fallback-enabled", Boolean.toString(databaseSettings().fallbackEnabled()));
        metadata.put("database-fallback-order", databaseFallbackOrderMetadata());
        metadata.put("database-fallback-shared-backends", databaseFallbackSharedBackendsMetadata());
        metadata.put("database-fallback-shared-safe", Boolean.toString(databaseFallbackSharedSafe()));
        metadata.put("database-fallback-risk", databaseFallbackRisk());
        metadata.put("database-fallback-production-safe", Boolean.toString(databaseFallbackProductionSafe()));
        metadata.put("database-fallback-warning", databaseFallbackWarningMetadata());
        metadata.put("database-fallback-source", databaseFallbackSource());
        metadata.put("database-fallback-env", "CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED,CLOUDISLANDS_SATIS_DB_FALLBACK_ORDER");
        metadata.put("database-config-source", databaseConfigSource());
        metadata.put("database-core-api-marker", Boolean.toString(configs.main().getBoolean("setup.database.core-api.enabled", false)));
        metadata.put("database-core-api-available", Boolean.toString(coreApiAddonStateAvailable()));
        metadata.put("database-core-api-requires", "cloudislands-api,addon-state");
        metadata.put("database-core-api-mode", databaseCoreApiMode());
        metadata.put("database-core-api-endpoint", "table/key-value/bulk-save,table/key-value/bulk");
        metadata.put("database-core-api-local-cache", databaseCoreApiLocalCachePolicy());
        metadata.put("database-core-api-fallback-target", databaseCoreApiFallbackTarget());
        metadata.put("database-core-api-fallback-policy", databaseCoreApiFallbackPolicy());
        metadata.put("database-core-api-flattened-fallback-enabled", Boolean.toString(coreApiFlattenedFallbackEnabled()));
        metadata.put("database-core-api-write-fallback", databaseCoreApiWriteFallbackPolicy());
        metadata.put("database-config-env", "CLOUDISLANDS_SATIS_DATABASE_TYPE,CLOUDISLANDS_SATIS_DB");
        metadata.put("database-jdbc-source", databaseJdbcSource());
        metadata.put("database-jdbc-env", "CLOUDISLANDS_SATIS_JDBC_URL");
        metadata.put("database-credentials-source", databaseCredentialsSource());
        metadata.put("database-credentials-env", "CLOUDISLANDS_SATIS_DB_USERNAME,CLOUDISLANDS_SATIS_DB_PASSWORD");
        metadata.put("database-pool-source", databasePoolSource());
        metadata.put("database-pool-env", "CLOUDISLANDS_SATIS_DB_MAX_POOL_SIZE,CLOUDISLANDS_SATIS_DB_CONNECTION_TIMEOUT_MS");
        metadata.put("database-path", resolveDatabaseFileName());
        metadata.put("database-open", Boolean.toString(database != null));
        metadata.put("database-file", configuredDatabaseFileName());
        metadata.put("database-shared", Boolean.toString(databaseShared()));
        putAddonReconnectPolicy(metadata);
        metadata.put("configured-skyblock-provider", configuredSkyblockProvider());
        metadata.put("effective-skyblock-provider", "CLOUDISLANDS");
        metadata.put("skyblock-provider-policy", "cloudislands-api-only");
        metadata.put("legacy-skyblock-provider-ignored", Boolean.toString(!"CLOUDISLANDS".equalsIgnoreCase(configuredSkyblockProvider()) && !"CLOUD_ISLANDS".equalsIgnoreCase(configuredSkyblockProvider())));
        putIslandMobilityState(metadata);
        metadata.put("satis-state-schema", "3");
        metadata.put("legacy-satismc-import-scan", "factory admin migration scan <sqlitePath>");
        metadata.put("legacy-satismc-import-dryrun", "factory admin migration dryrun <sqlitePath>");
        metadata.put("legacy-satismc-import-verify", "factory admin migration verify <sqlitePath>");
        metadata.put("legacy-satismc-import-import", "factory admin migration import <sqlitePath>");
        metadata.put("legacy-satismc-scan-mode", "scan-dryrun-verify-read-only");
        metadata.put("legacy-satismc-import-mode", "cross-backend-sqlite-copy");
        metadata.put("legacy-satismc-rollback-mode", "manual-restore-from-backup");
        metadata.put("island-position-remap", "center-delta");
        metadata.put("recovery-suspend-mode", "drop-local-dirty-state");
        metadata.put("recovery-resume-source", "core-api-confirmed-state");
        metadata.put("recovery-state-authority", "last-core-confirmed-state-only");
        metadata.put("recovery-stale-write-policy", "discard-local-dirty-state");
        metadata.put("runtime-registration-policy", "disabled-features-skip-commands-gui-listeners-tasks-and-writes");
        metadata.put("runtime-disabled-features", disabledRuntimeFeatures());
        putDataWriteGateState(metadata);
        putAddonStateSyncState(metadata);
        metadata.put("addon-state-bulk-save-api", "true");
        metadata.put("addon-state-bulk-save-global-endpoint", "/v1/addons/state/table-key-value/bulk-save");
        metadata.put("addon-state-bulk-save-island-endpoint", "/v1/addons/islands/state/table-key-value/bulk-save");
        metadata.put("addon-state-table-key-value-bulk-save-global-endpoint", "/v1/addons/state/table/key-value/bulk-save");
        metadata.put("addon-state-table-key-value-bulk-save-island-endpoint", "/v1/addons/islands/state/table/key-value/bulk-save");
        metadata.put("addon-state-table-key-value-bulk-global-endpoint", "/v1/addons/state/table/key-value/bulk");
        metadata.put("addon-state-table-key-value-bulk-island-endpoint", "/v1/addons/islands/state/table/key-value/bulk");
        metadata.put("addon-state-bulk-save-methods", "bulkSaveState,tableKeyValueBulkSaveState,bulkSaveTableKeyValueState,tableKeyValueBulkState,bulkTableKeyValueState,bulkSaveIslandState,tableKeyValueBulkSaveIslandState,bulkSaveIslandTableKeyValueState,tableKeyValueBulkIslandState,bulkIslandTableKeyValueState");
        metadata.put("core-api-table-save-mode", "bulk-save-with-table-prefix-and-configurable-flattened-fallback");
        metadata.put("feature-aliases", featureAliasesMetadata());
        metadata.put("feature-alias-disabled", disabledFeatureAliases());
        metadata.put("feature-dependencies", featureDependenciesMetadata());
        metadata.put("feature-gate-policy", "host-addon-config-and-satis-config-must-all-allow");
        metadata.put("feature-gate-sources", "addons.cloudislands-satis.enabled,satis.enabled,addons.cloudislands-satis.features,satis.features,features(legacy)");
        metadata.put("feature-gate-disabled-by", featureGateDisabledBy());
        metadata.put("feature-gate-runtime-policy", "disabled-features-skip-commands-gui-listeners-tasks-and-writes-preserve-data");
        metadata.put("configured-features", featureState(featureSnapshot()));
        metadata.put("effective-features", operationalFeatureState(featureSnapshot()));
        metadata.put("feature-warnings", featureWarnings());
        metadata.put("operational-features", operationalFeatureState(featureSnapshot()));
        return metadata;
    }

    static Map<String, String> cloudIslandsIntegrationMetadata() {
        return Map.ofEntries(
                Map.entry("origin-project", "satismc"),
                Map.entry("origin-repository", "https://github.com/M-LunaFarm/satismc"),
                Map.entry("addon-packaging", "external-plugin"),
                Map.entry("extension-model", "superiorskyblock-style-addon"),
                Map.entry("removable-addon", "true"),
                Map.entry("superior-migration-input-only", "true"),
                Map.entry("superior-runtime-dependency", "false"),
                Map.entry("cloudislands-api-only", "true"),
                Map.entry("feature-gate-scope", "global-and-per-feature"),
                Map.entry("config-gated", "true"),
                Map.entry("parent-config-aliases", "satis")
        );
    }

    private Map<String, String> addonStateSnapshot() {
        if (!featureEnabled("addon-state")) {
            return Map.of("status", "disabled", "feature", "addon-state");
        }
        if (cloudIslandsApi == null) {
            return addonMetadata();
        }
        try {
            Map<String, String> state = cloudIslandsApi.addons().state(ADDON_ID).join();
            return state == null || state.isEmpty() ? addonMetadata() : state;
        } catch (RuntimeException exception) {
            Map<String, String> fallback = new LinkedHashMap<>(addonMetadata());
            fallback.put("core-state-error", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return fallback;
        }
    }

    private Map<String, String> addonIslandStateSnapshot(UUID islandId) {
        if (!featureEnabled("addon-state")) {
            return Map.of("status", "disabled", "feature", "addon-state");
        }
        if (cloudIslandsApi == null || islandId == null) {
            return Map.of();
        }
        try {
            Map<String, String> state = cloudIslandsApi.addons().islandState(ADDON_ID, islandId).join();
            if (state == null || state.isEmpty()) {
                return Map.of("status", "empty", "island", islandId.toString());
            }
            return state;
        } catch (RuntimeException exception) {
            Map<String, String> fallback = new LinkedHashMap<>();
            fallback.put("status", "unavailable");
            fallback.put("island", islandId.toString());
            fallback.put("core-state-error", exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            return fallback;
        }
    }

    private String featureAliasesMetadata() {
        return FEATURE_ALIASES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String featureDependenciesMetadata() {
        return "resource-nodes:machines,generators:factories,market:storage,contracts:storage,missions:storage";
    }

    private String disabledFeatureAliases() {
        List<String> disabled = new ArrayList<>();
        FEATURE_ALIASES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (configFeatureDefined(entry.getKey()) && !configFeature(entry.getKey())) {
                        disabled.add(entry.getKey() + "->" + entry.getValue());
                    }
                });
        return disabled.isEmpty() ? "none" : String.join(",", disabled);
    }

    private String featureGateDisabledBy() {
        if (configs == null) {
            return "config-not-loaded";
        }
        List<String> disabled = new ArrayList<>();
        if (configs.main().contains("addons." + ADDON_ID + ".enabled") && !configs.main().getBoolean("addons." + ADDON_ID + ".enabled", true)) {
            disabled.add("addon=addons." + ADDON_ID + ".enabled");
        }
        if (configs.main().contains("satis.enabled") && !configs.main().getBoolean("satis.enabled", true)) {
            disabled.add("addon=satis.enabled");
        }
        featureSnapshot().keySet().stream()
                .sorted()
                .forEach(feature -> {
                    List<String> sources = featureDisabledSources(feature);
                    if (!sources.isEmpty()) {
                        disabled.add(feature + "=" + String.join("|", sources));
                    }
                });
        return disabled.isEmpty() ? "none" : String.join(",", disabled);
    }

    private List<String> featureDisabledSources(String feature) {
        List<String> sources = new ArrayList<>();
        addDisabledFeatureSource(sources, "addons." + ADDON_ID + ".features." + feature);
        addDisabledFeatureSource(sources, "satis.features." + feature);
        addDisabledFeatureSource(sources, "features." + feature);
        return sources;
    }

    private void addDisabledFeatureSource(List<String> sources, String path) {
        if (configs.main().contains(path) && !configs.main().getBoolean(path, true)) {
            sources.add(path);
        }
    }

    @Override
    public void onAddonRegistered(CloudIslandsAddonSnapshot snapshot) {
        addonRuntimeEnabled = snapshot.enabled();
        effectiveFeatures = snapshot.features();
        addonStateReportingWasEnabled = addonStateReportingEnabled(snapshot);
        publishAddonState(snapshot, "registered");
    }

    @Override
    public void onAddonReloaded(CloudIslandsAddonSnapshot snapshot) {
        addonRuntimeEnabled = snapshot.enabled();
        effectiveFeatures = snapshot.features();
        addonStateReportingWasEnabled = addonStateReportingEnabled(snapshot);
        publishAddonState(snapshot, "reloaded");
        getLogger().info("Reloaded CloudIslands addon config: " + snapshot.id() + " enabled=" + snapshot.enabled());
        if (!snapshot.enabled()) {
            getLogger().info("CloudIslands disabled this addon during config reload.");
            effectiveFeatures = Map.of();
            stopRuntimeActivity();
            return;
        }
        if (database == null) {
            startRuntime();
            return;
        }
        applyAddonRuntimeState();
    }

    @Override
    public void onCoreCacheCleared(CoreCacheClearEvent event) {
        reloadSatisRuntimeFromCore("core-cache-clear");
    }

    @Override
    public void onCoreReloaded(CoreReloadEvent event) {
        reloadSatisRuntimeFromCore("core-reload");
    }

    private void reloadSatisRuntimeFromCore(String reason) {
        if (cloudIslandsApi != null) {
            cloudIslandsApi.addons().refresh(ADDON_ID)
                    .thenAccept(snapshot -> {
                        if (snapshot.isEmpty()) {
                            applySatisRuntimeFallback(reason);
                        }
                    })
                    .exceptionally(error -> {
                        getLogger().warning("Failed to refresh CloudIslands Satis addon after " + reason + ": " + error.getMessage());
                        applySatisRuntimeFallback(reason);
                        return null;
                    });
            return;
        }
        applySatisRuntimeFallback(reason);
    }

    private void applySatisRuntimeFallback(String reason) {
        getServer().getScheduler().runTask(this, () -> {
            if (!isEnabled() || database == null) {
                return;
            }
            applyAddonRuntimeState();
        });
    }

    private void publishAddonState(CloudIslandsAddonSnapshot snapshot, String reason) {
        if (cloudIslandsApi == null || snapshot == null) {
            return;
        }
        if (!addonStateReportingEnabled(snapshot)) {
            return;
        }
        Map<String, String> state = new LinkedHashMap<>();
        state.put("runtime-enabled", Boolean.toString(snapshot.enabled()));
        state.put("database-shared", Boolean.toString(databaseShared()));
        state.put("database-scope", databaseScope());
        state.put("database-supported-backends", "SQLITE,POSTGRESQL,MYSQL,MARIADB,CORE_API");
        state.put("database-configured-backend", configuredDatabaseBackendName());
        state.put("database-setup-sections", databaseSetupSectionsMetadata());
        state.put("database-setup-auto-selected", Boolean.toString(databaseSetupAutoSelected()));
        state.put("database-setup-selected-backend", databaseSetupSelectedBackendMetadata());
        state.put("database-setup-warning", databaseSetupWarningMetadata());
        state.put("database-active-backend", database == null ? "NOT_OPEN" : database.activeBackend().name());
        state.put("database-attempted-backends", databaseAttemptedBackendsMetadata());
        state.put("database-attempt-order", databaseBackendAttemptOrderMetadata());
        state.put("database-jdbc-target", databaseJdbcTargetMetadata());
        state.put("database-fallback-jdbc-target", databaseFallbackJdbcTargetMetadata());
        state.put("database-fallback-jdbc-targets", databaseFallbackJdbcTargetsMetadata());
        state.put("database-fallback-reason", databaseFallbackReason);
        state.put("database-fallback-active", Boolean.toString(databaseFallbackActive()));
        state.put("database-fallback-status", databaseFallbackStatus());
        state.put("database-fallback-enabled", Boolean.toString(databaseSettings().fallbackEnabled()));
        state.put("database-fallback-order", databaseFallbackOrderMetadata());
        state.put("database-fallback-shared-backends", databaseFallbackSharedBackendsMetadata());
        state.put("database-fallback-shared-safe", Boolean.toString(databaseFallbackSharedSafe()));
        state.put("database-fallback-risk", databaseFallbackRisk());
        state.put("database-fallback-production-safe", Boolean.toString(databaseFallbackProductionSafe()));
        state.put("database-fallback-warning", databaseFallbackWarningMetadata());
        state.put("database-config-source", databaseConfigSource());
        state.put("database-core-api-marker", Boolean.toString(configs.main().getBoolean("setup.database.core-api.enabled", false)));
        state.put("database-core-api-available", Boolean.toString(coreApiAddonStateAvailable()));
        state.put("database-core-api-requires", "cloudislands-api,addon-state");
        state.put("database-core-api-mode", databaseCoreApiMode());
        state.put("database-core-api-endpoint", "table/key-value/bulk-save,table/key-value/bulk");
        state.put("database-core-api-local-cache", databaseCoreApiLocalCachePolicy());
        state.put("database-core-api-fallback-target", databaseCoreApiFallbackTarget());
        state.put("database-core-api-fallback-policy", databaseCoreApiFallbackPolicy());
        state.put("database-core-api-flattened-fallback-enabled", Boolean.toString(coreApiFlattenedFallbackEnabled()));
        state.put("database-core-api-write-fallback", databaseCoreApiWriteFallbackPolicy());
        state.put("database-config-env", "CLOUDISLANDS_SATIS_DATABASE_TYPE,CLOUDISLANDS_SATIS_DB");
        state.put("database-jdbc-source", databaseJdbcSource());
        state.put("database-jdbc-env", "CLOUDISLANDS_SATIS_JDBC_URL");
        state.put("database-credentials-source", databaseCredentialsSource());
        state.put("database-credentials-env", "CLOUDISLANDS_SATIS_DB_USERNAME,CLOUDISLANDS_SATIS_DB_PASSWORD");
        state.put("database-pool-source", databasePoolSource());
        state.put("database-pool-env", "CLOUDISLANDS_SATIS_DB_MAX_POOL_SIZE,CLOUDISLANDS_SATIS_DB_CONNECTION_TIMEOUT_MS");
        state.put("database-fallback-source", databaseFallbackSource());
        state.put("database-fallback-env", "CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED,CLOUDISLANDS_SATIS_DB_FALLBACK_ORDER");
        state.put("database-path", resolveDatabaseFileName());
        state.put("database-open", Boolean.toString(database != null));
        putRuntimeActivityState(state);
        putAddonReconnectPolicy(state);
        state.put("satis-state-schema", "3");
        state.put("island-position-remap", "center-delta");
        state.put("recovery-suspend-mode", "drop-local-dirty-state");
        state.put("recovery-resume-source", "core-api-confirmed-state");
        state.put("recovery-state-authority", "last-core-confirmed-state-only");
        state.put("recovery-stale-write-policy", "discard-local-dirty-state");
        putAddonStateSyncState(state);
        state.put("addon-state-bulk-save-api", "true");
        state.put("addon-state-bulk-save-global-endpoint", "/v1/addons/state/table-key-value/bulk-save");
        state.put("addon-state-bulk-save-island-endpoint", "/v1/addons/islands/state/table-key-value/bulk-save");
        state.put("addon-state-table-key-value-bulk-save-global-endpoint", "/v1/addons/state/table/key-value/bulk-save");
        state.put("addon-state-table-key-value-bulk-save-island-endpoint", "/v1/addons/islands/state/table/key-value/bulk-save");
        state.put("addon-state-table-key-value-bulk-global-endpoint", "/v1/addons/state/table/key-value/bulk");
        state.put("addon-state-table-key-value-bulk-island-endpoint", "/v1/addons/islands/state/table/key-value/bulk");
        state.put("addon-state-bulk-save-methods", "bulkSaveState,tableKeyValueBulkSaveState,bulkSaveTableKeyValueState,tableKeyValueBulkState,bulkTableKeyValueState,bulkSaveIslandState,tableKeyValueBulkSaveIslandState,bulkSaveIslandTableKeyValueState,tableKeyValueBulkIslandState,bulkIslandTableKeyValueState");
        state.put("core-api-table-save-mode", "bulk-save-with-table-prefix-and-configurable-flattened-fallback");
        state.put("configured-features", featureState(snapshot.configuredFeatures()));
        state.put("effective-features", featureState(snapshot.features()));
        state.put("operational-features", operationalFeatureState(snapshot.features()));
        state.put("feature-alias-disabled", disabledFeatureAliases());
        state.put("feature-gate-policy", "host-addon-config-and-satis-config-must-all-allow");
        state.put("feature-gate-sources", "addons.cloudislands-satis.enabled,satis.enabled,addons.cloudislands-satis.features,satis.features,features(legacy)");
        state.put("feature-gate-disabled-by", featureGateDisabledBy());
        state.put("feature-gate-runtime-policy", "disabled-features-skip-commands-gui-listeners-tasks-and-writes-preserve-data");
        state.put("dependency-disabled-features", dependencyDisabledFeatures(snapshot));
        state.put("feature-warnings", featureWarnings(snapshot));
        state.put("last-sync-reason", reason == null || reason.isBlank() ? "unknown" : reason);
        state.put("last-sync-at", Instant.now().toString());
        cloudIslandsApi.addons().putState(snapshot.id(), state).exceptionally(error -> {
            getLogger().warning("Failed to publish CloudIslands Satis addon state: " + error.getMessage());
            return Map.of();
        });
    }

    private void publishUnregisteredState(boolean allowed) {
        if (cloudIslandsApi == null || !allowed) {
            return;
        }
        Map<String, String> state = new LinkedHashMap<>();
        state.put("runtime-enabled", "false");
        state.put("database-shared", Boolean.toString(databaseShared()));
        state.put("database-scope", databaseScope());
        state.put("database-supported-backends", "SQLITE,POSTGRESQL,MYSQL,MARIADB,CORE_API");
        state.put("database-configured-backend", configuredDatabaseBackendName());
        state.put("database-setup-sections", databaseSetupSectionsMetadata());
        state.put("database-setup-auto-selected", Boolean.toString(databaseSetupAutoSelected()));
        state.put("database-setup-selected-backend", databaseSetupSelectedBackendMetadata());
        state.put("database-setup-warning", databaseSetupWarningMetadata());
        state.put("database-active-backend", database == null ? "NOT_OPEN" : database.activeBackend().name());
        state.put("database-attempted-backends", databaseAttemptedBackendsMetadata());
        state.put("database-attempt-order", databaseBackendAttemptOrderMetadata());
        state.put("database-jdbc-target", databaseJdbcTargetMetadata());
        state.put("database-fallback-jdbc-target", databaseFallbackJdbcTargetMetadata());
        state.put("database-fallback-jdbc-targets", databaseFallbackJdbcTargetsMetadata());
        state.put("database-fallback-reason", databaseFallbackReason);
        state.put("database-fallback-active", Boolean.toString(databaseFallbackActive()));
        state.put("database-fallback-status", databaseFallbackStatus());
        state.put("database-fallback-enabled", Boolean.toString(databaseSettings().fallbackEnabled()));
        state.put("database-fallback-order", databaseFallbackOrderMetadata());
        state.put("database-fallback-shared-backends", databaseFallbackSharedBackendsMetadata());
        state.put("database-fallback-shared-safe", Boolean.toString(databaseFallbackSharedSafe()));
        state.put("database-fallback-risk", databaseFallbackRisk());
        state.put("database-fallback-production-safe", Boolean.toString(databaseFallbackProductionSafe()));
        state.put("database-fallback-warning", databaseFallbackWarningMetadata());
        state.put("database-config-source", databaseConfigSource());
        state.put("database-core-api-marker", Boolean.toString(configs.main().getBoolean("setup.database.core-api.enabled", false)));
        state.put("database-core-api-available", Boolean.toString(coreApiAddonStateAvailable()));
        state.put("database-core-api-requires", "cloudislands-api,addon-state");
        state.put("database-core-api-mode", databaseCoreApiMode());
        state.put("database-core-api-endpoint", "table/key-value/bulk-save,table/key-value/bulk");
        state.put("database-core-api-local-cache", databaseCoreApiLocalCachePolicy());
        state.put("database-core-api-fallback-target", databaseCoreApiFallbackTarget());
        state.put("database-core-api-fallback-policy", databaseCoreApiFallbackPolicy());
        putAddonStateSyncState(state);
        state.put("database-config-env", "CLOUDISLANDS_SATIS_DATABASE_TYPE,CLOUDISLANDS_SATIS_DB");
        state.put("database-jdbc-source", databaseJdbcSource());
        state.put("database-jdbc-env", "CLOUDISLANDS_SATIS_JDBC_URL");
        state.put("database-credentials-source", databaseCredentialsSource());
        state.put("database-credentials-env", "CLOUDISLANDS_SATIS_DB_USERNAME,CLOUDISLANDS_SATIS_DB_PASSWORD");
        state.put("database-pool-source", databasePoolSource());
        state.put("database-pool-env", "CLOUDISLANDS_SATIS_DB_MAX_POOL_SIZE,CLOUDISLANDS_SATIS_DB_CONNECTION_TIMEOUT_MS");
        state.put("database-fallback-source", databaseFallbackSource());
        state.put("database-fallback-env", "CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED,CLOUDISLANDS_SATIS_DB_FALLBACK_ORDER");
        state.put("database-path", resolveDatabaseFileName());
        state.put("database-open", Boolean.toString(database != null));
        putRuntimeActivityState(state);
        state.put("satis-state-schema", "3");
        putAddonReconnectPolicy(state);
        state.put("last-sync-reason", "unregistered");
        state.put("last-sync-at", Instant.now().toString());
        state.put("last-lifecycle-status", "unregistered");
        state.put("last-lifecycle-operation", "unregistered");
        state.put("last-lifecycle-at", Instant.now().toString());
        cloudIslandsApi.addons().putState(ADDON_ID, state).exceptionally(error -> {
            getLogger().warning("Failed to publish CloudIslands Satis unregister state: " + error.getMessage());
            return Map.of();
        });
    }

    private void putAddonReconnectPolicy(Map<String, String> state) {
        state.put("unregister-delete-addon-data", "false");
        state.put("unregister-delete-island-state", "false");
        state.put("unregister-preserve-core-state", "true");
        state.put("unregister-preserve-local-cache", "true");
        state.put("reinstall-reconnect-policy", "reuse-existing-addon-state-by-island-uuid");
        state.put("reinstall-reconnect-requires", "matching-cloudislands-island-uuid-and-compatible-satis-state-schema");
    }

    private void publishLifecycleState(UUID islandId, String operation) {
        publishLifecycleState(islandId, operation, null);
    }

    private void publishLifecycleState(UUID islandId, String operation, FactoryIsland island) {
        publishLifecycleState(islandId, operation, island, "0,0,0", false, false);
    }

    private void publishLifecycleState(UUID islandId, String operation, FactoryIsland island, String remapDelta, boolean machinesRemapped, boolean resourceNodesRemapped) {
        if (cloudIslandsApi == null || islandId == null || !featureEnabled("addon-state")) {
            return;
        }
        String safeOperation = operation == null || operation.isBlank() ? "unknown" : operation;
        String eventNode = lifecycleEventNode(safeOperation);
        String activeNode = lifecycleActiveNode(safeOperation);
        String eventWorld = lifecycleEventWorld(safeOperation);
        String eventCell = lifecycleEventCell(safeOperation);
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-lifecycle-island", islandId.toString());
        state.put("last-lifecycle-operation", safeOperation);
        state.put("last-lifecycle-database-open", Boolean.toString(database != null));
        state.put("last-lifecycle-shared-database", Boolean.toString(databaseShared()));
        state.put("last-lifecycle-schema", "3");
        state.put("last-lifecycle-at", Instant.now().toString());
        state.put("last-lifecycle-status", "success");
        state.put("last-lifecycle-error", "");
        if (!eventNode.isBlank()) {
            state.put("last-lifecycle-node", eventNode);
        }
        if (!activeNode.isBlank()) {
            state.put("last-lifecycle-active-node", activeNode);
        }
        if (!eventWorld.isBlank()) {
            state.put("last-lifecycle-active-world", eventWorld);
        }
        if (!eventCell.isBlank()) {
            state.put("last-lifecycle-active-cell", eventCell);
        }
        if (island != null && island.hasActiveCenter()) {
            state.put("last-lifecycle-active-world", island.activeWorld());
            state.put("last-lifecycle-active-center", island.activeCenterX() + "," + island.activeCenterY() + "," + island.activeCenterZ());
        }
        state.put("last-lifecycle-remap-delta", remapDelta == null || remapDelta.isBlank() ? "0,0,0" : remapDelta);
        state.put("last-lifecycle-machines-remapped", Boolean.toString(machinesRemapped));
        state.put("last-lifecycle-resource-nodes-remapped", Boolean.toString(resourceNodesRemapped));
        state.put("last-lifecycle-remap-source", "active-world-center");
        cloudIslandsApi.addons().putState(ADDON_ID, state).exceptionally(error -> {
            getLogger().warning("Failed to publish CloudIslands Satis lifecycle state: " + error.getMessage());
            return Map.of();
        });
        if (!"purge".equalsIgnoreCase(operation)) {
            publishIslandLifecycleState(islandId, operation, island, "success", "", remapDelta, machinesRemapped, resourceNodesRemapped);
        }
    }

    private void publishLifecycleFailure(UUID islandId, String operation, RuntimeException exception) {
        if (cloudIslandsApi == null || islandId == null || !featureEnabled("addon-state")) {
            return;
        }
        String safeOperation = operation == null || operation.isBlank() ? "unknown" : operation;
        String eventNode = lifecycleEventNode(safeOperation);
        String activeNode = lifecycleActiveNode(safeOperation);
        String eventWorld = lifecycleEventWorld(safeOperation);
        String eventCell = lifecycleEventCell(safeOperation);
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-lifecycle-island", islandId.toString());
        state.put("last-lifecycle-operation", safeOperation);
        state.put("last-lifecycle-database-open", Boolean.toString(database != null));
        state.put("last-lifecycle-shared-database", Boolean.toString(databaseShared()));
        state.put("last-lifecycle-schema", "3");
        state.put("last-lifecycle-at", Instant.now().toString());
        state.put("last-lifecycle-status", "failed");
        state.put("last-lifecycle-error", shortError(exception));
        if (!eventNode.isBlank()) {
            state.put("last-lifecycle-node", eventNode);
        }
        if (!activeNode.isBlank()) {
            state.put("last-lifecycle-active-node", activeNode);
        }
        if (!eventWorld.isBlank()) {
            state.put("last-lifecycle-active-world", eventWorld);
        }
        if (!eventCell.isBlank()) {
            state.put("last-lifecycle-active-cell", eventCell);
        }
        cloudIslandsApi.addons().putState(ADDON_ID, state).exceptionally(error -> {
            getLogger().warning("Failed to publish CloudIslands Satis lifecycle failure: " + error.getMessage());
            return Map.of();
        });
        publishIslandLifecycleState(islandId, operation, null, "failed", shortError(exception));
    }

    private void publishIslandLifecycleState(UUID islandId, String operation, FactoryIsland island, String status, String error) {
        publishIslandLifecycleState(islandId, operation, island, status, error, "0,0,0", false, false);
    }

    private void publishIslandLifecycleState(UUID islandId, String operation, FactoryIsland island, String status, String error, String remapDelta, boolean machinesRemapped, boolean resourceNodesRemapped) {
        if (cloudIslandsApi == null || islandId == null || !featureEnabled("addon-state")) {
            return;
        }
        String safeOperation = operation == null || operation.isBlank() ? "unknown" : operation;
        String eventNode = lifecycleEventNode(safeOperation);
        String activeNode = lifecycleActiveNode(safeOperation);
        String eventWorld = lifecycleEventWorld(safeOperation);
        String eventCell = lifecycleEventCell(safeOperation);
        Map<String, String> state = new LinkedHashMap<>();
        state.put("island", islandId.toString());
        state.put("operation", safeOperation);
        state.put("status", status == null || status.isBlank() ? "unknown" : status);
        state.put("error", error == null ? "" : error);
        state.put("database-open", Boolean.toString(database != null));
        state.put("shared-database", Boolean.toString(databaseShared()));
        state.put("schema", "3");
        state.put("node-bound", "false");
        state.put("runtime-source", "CloudIslands IslandRuntime");
        state.put("remap-policy", "island-uuid-stable-active-world-and-center-volatile");
        state.put("remap-key", "islandUuid+activeWorld+activeCenter");
        state.put("operational-features", operationalFeatureState(effectiveFeatures));
        state.put("updated-at", Instant.now().toString());
        if (!eventNode.isBlank()) {
            state.put("node", eventNode);
        }
        if (!activeNode.isBlank()) {
            state.put("active-node", activeNode);
        }
        if (!eventWorld.isBlank()) {
            state.put("active-world", eventWorld);
        }
        if (!eventCell.isBlank()) {
            state.put("active-cell", eventCell);
        }
        if (island != null && island.hasActiveCenter()) {
            state.put("active-world", island.activeWorld());
            state.put("active-center", island.activeCenterX() + "," + island.activeCenterY() + "," + island.activeCenterZ());
        }
        state.put("remap-delta", remapDelta == null || remapDelta.isBlank() ? "0,0,0" : remapDelta);
        state.put("machines-remapped", Boolean.toString(machinesRemapped));
        state.put("resource-nodes-remapped", Boolean.toString(resourceNodesRemapped));
        state.put("remap-source", "active-world-center");
        cloudIslandsApi.addons().putIslandState(ADDON_ID, islandId, state).exceptionally(publishError -> {
            getLogger().warning("Failed to publish CloudIslands Satis island state: " + publishError.getMessage());
            return Map.of();
        });
    }

    private void clearIslandLifecycleState(UUID islandId) {
        if (cloudIslandsApi == null || islandId == null || !featureEnabled("addon-state")) {
            return;
        }
        cloudIslandsApi.addons().clearIslandState(ADDON_ID, islandId).exceptionally(error -> {
            getLogger().warning("Failed to clear CloudIslands Satis island state: " + error.getMessage());
            return null;
        });
    }

    private String shortError(RuntimeException exception) {
        String message = exception == null || exception.getMessage() == null ? "unknown" : exception.getMessage();
        return message.length() <= 256 ? message : message.substring(0, 256);
    }

    private String featureState(Map<String, Boolean> features) {
        if (features == null || features.isEmpty()) {
            return "none";
        }
        return features.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String operationalFeatureState(Map<String, Boolean> features) {
        return featureState(operationalFeatureSnapshot(features));
    }

    private Map<String, Boolean> operationalFeatureSnapshot(Map<String, Boolean> features) {
        Map<String, Boolean> operational = new LinkedHashMap<>(features == null ? Map.of() : features);
        boolean machinesEnabled = operational.getOrDefault("machines", true);
        boolean storageEnabled = operational.getOrDefault("storage", true);
        boolean factoriesEnabled = operational.getOrDefault("factories", machinesEnabled);
        operational.computeIfPresent("resource-nodes", (_key, enabled) -> enabled && machinesEnabled);
        operational.computeIfPresent("market", (_key, enabled) -> enabled && storageEnabled);
        operational.computeIfPresent("contracts", (_key, enabled) -> enabled && storageEnabled);
        operational.computeIfPresent("generators", (_key, enabled) -> enabled && factoriesEnabled);
        operational.computeIfPresent("missions", (_key, enabled) -> enabled && storageEnabled);
        FEATURE_ALIASES.forEach((alias, canonical) -> {
            if (operational.containsKey(alias) || operational.containsKey(canonical)) {
                boolean enabled = operational.getOrDefault(alias, true) && operational.getOrDefault(canonical, true);
                operational.put(alias, enabled);
            }
        });
        return operational;
    }

    @Override
    public void onAddonUnregistered() {
        boolean publishUnregistered = addonStateReportingWasEnabled;
        addonStateReportingWasEnabled = false;
        addonRuntimeEnabled = false;
        effectiveFeatures = Map.of();
        stopRuntimeActivity();
        publishUnregisteredState(publishUnregistered);
    }

    @Override
    public void onIslandCreated(IslandCreatedEvent event) {
        runSatisLifecycle(event.islandId(), "created", () -> {
            islands.getOrCreate(new kr.seungmin.satisskyfactory.hook.IslandRef(null, event.islandId(), event.ownerUuid()));
            if (storageDataEnabled()) {
                storage.islandStorage(event.islandId());
            }
            synchronizeSatisIsland(event.islandId(), "created");
        });
    }

    @Override
    public void onIslandActivated(IslandActivatedEvent event) {
        String operation = "activated:" + lifecycleNode(event.nodeId()) + lifecycleWorldToken(event.worldName()) + lifecycleCellToken(event.cellX(), event.cellZ());
        runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandMigrationRequested(IslandMigrationEvent event) {
        String operation = "migration-requested:" + lifecycleNode(event.sourceNode()) + "->" + lifecycleNode(event.targetNode()) + lifecycleWorldToken(event.worldName()) + lifecycleCellToken(event.cellX(), event.cellZ());
        runSatisLifecycle(event.islandId(), operation, () -> publishIslandLifecycleState(event.islandId(), operation, islands == null ? null : islands.find(event.islandId()).orElse(null), "requested", ""));
    }

    @Override
    public void onIslandDeactivationRequested(IslandDeactivationRequestEvent event) {
        runSatisLifecycle(event.islandId(), "deactivation-requested", () -> flushSatisIsland(event.islandId(), "deactivation-requested"));
    }

    @Override
    public void onIslandDeactivated(IslandDeactivatedEvent event) {
        String phase = event.phase() == null || event.phase().isBlank() ? "" : ":" + event.phase();
        String target = event.targetNode() == null || event.targetNode().isBlank() ? "" : "->" + lifecycleNode(event.targetNode());
        String operation = "deactivated:" + lifecycleNode(event.nodeId()) + target + phase + ":snapshot-" + event.snapshotNo();
        runSatisLifecycle(event.islandId(), operation, () -> flushSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandMigrated(IslandMigratedEvent event) {
        String operation = "migrated:" + lifecycleNode(event.fromNode()) + "->" + lifecycleNode(event.toNode()) + lifecycleWorldToken(event.worldName()) + lifecycleCellToken(event.cellX(), event.cellZ());
        runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandDeleted(IslandDeletedEvent event) {
        runSatisLifecycle(event.islandId(), "purge", () -> purgeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandDeleteRequested(kr.lunaf.cloudislands.api.event.IslandDeleteRequestEvent event) {
        runSatisLifecycle(event.islandId(), "delete-requested", () -> flushSatisIsland(event.islandId(), "delete-requested"));
    }

    @Override
    public void onIslandRestoreRequested(kr.lunaf.cloudislands.api.event.IslandRestoreRequestEvent event) {
        String operation = "restore-requested:" + lifecycleNode(event.targetNode()) + ":snapshot-" + event.snapshotNo();
        runSatisLifecycle(event.islandId(), operation, () -> flushSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandRestored(kr.lunaf.cloudislands.api.event.IslandRestoredEvent event) {
        runSatisLifecycle(event.islandId(), "restored", () -> synchronizeSatisIsland(event.islandId(), "restored"));
    }

    @Override
    public void onIslandReset(kr.lunaf.cloudislands.api.event.IslandResetEvent event) {
        runSatisLifecycle(event.islandId(), event.requested() ? "flush" : "purge", () -> {
            if (event.requested()) {
                flushSatisIsland(event.islandId());
            } else {
                purgeSatisIsland(event.islandId());
            }
        });
    }

    @Override
    public void onIslandRecoveryRequired(kr.lunaf.cloudislands.api.event.IslandRecoveryRequiredEvent event) {
        runSatisLifecycle(event.islandId(), "recovery-required:" + lifecycleNode(event.nodeId()), () -> suspendRecoveredIsland(event.islandId(), "recovery-required:" + lifecycleNode(event.nodeId())));
    }

    @Override
    public void onIslandRepaired(kr.lunaf.cloudislands.api.event.IslandRepairedEvent event) {
        runSatisLifecycle(event.islandId(), "repaired", () -> synchronizeSatisIsland(event.islandId(), "repaired"));
    }

    @Override
    public void onIslandRuntimeChanged(IslandRuntimeChangeEvent event) {
        String state = event.state() == null ? "" : event.state();
        if (state.equalsIgnoreCase("RECOVERY_REQUIRED") || state.equalsIgnoreCase("QUARANTINED")) {
            String operation = runtimeOperation(state, event.targetNode());
            runSatisLifecycle(event.islandId(), operation, () -> suspendRecoveredIsland(event.islandId(), operation));
            return;
        }
        if (state.equalsIgnoreCase("SAVING") || state.equalsIgnoreCase("DEACTIVATING")) {
            String operation = runtimeOperation(state, event.targetNode());
            runSatisLifecycle(event.islandId(), operation, () -> flushSatisIsland(event.islandId(), operation));
            return;
        }
        if (state.equalsIgnoreCase("ACTIVE")) {
            String operation = runtimeOperation(state, event.targetNode());
            runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
        }
    }

    @Override
    public void onIslandVisited(IslandVisitEvent event) {
        String operation = "visited:" + lifecycleNode(event.nodeId());
        runSatisLifecycle(event.islandId(), operation, () -> publishIslandLifecycleState(event.islandId(), operation, islands.find(event.islandId()).orElse(null), "ok", ""));
    }

    @Override
    public void onIslandMemberChanged(IslandMemberChangedEvent event) {
        runSatisLifecycle(event.islandId(), "member-change", () -> synchronizeSatisIsland(event.islandId(), "member-change"));
    }

    @Override
    public void onIslandRoleCatalogChanged(IslandRoleCatalogChangeEvent event) {
        String operation = "role-change:" + (event.operation() == null || event.operation().isBlank() ? "unknown" : event.operation());
        runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandOwnershipChanged(IslandOwnershipChangeEvent event) {
        runSatisLifecycle(event.islandId(), "ownership-change", () -> synchronizeSatisIsland(event.islandId(), "ownership-change"));
    }

    @Override
    public void onIslandFlagChanged(IslandFlagChangeEvent event) {
        runSatisLifecycle(event.islandId(), "flag-change", () -> synchronizeSatisIsland(event.islandId(), "flag-change"));
    }

    @Override
    public void onIslandPermissionChanged(IslandPermissionChangeEvent event) {
        runSatisLifecycle(event.islandId(), "permission-change", () -> synchronizeSatisIsland(event.islandId(), "permission-change"));
    }

    @Override
    public void onIslandBankChanged(IslandBankChangeEvent event) {
        String operation = "bank-change:" + (event.operation() == null || event.operation().isBlank() ? "unknown" : event.operation());
        runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandBiomeChanged(IslandBiomeChangeEvent event) {
        String operation = "biome-change:" + (event.biomeKey() == null || event.biomeKey().isBlank() ? "unknown" : event.biomeKey());
        runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandHomeChanged(IslandHomeChangeEvent event) {
        String operation = "home-change:" + (event.name() == null || event.name().isBlank() ? "default" : event.name());
        runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandWarpChanged(IslandWarpChangeEvent event) {
        String operation = "warp-change:" + (event.operation() == null || event.operation().isBlank() ? "unknown" : event.operation());
        runSatisLifecycle(event.islandId(), operation, () -> synchronizeSatisIsland(event.islandId(), operation));
    }

    @Override
    public void onIslandLevelUpdated(IslandLevelRecalculateEvent event) {
        runSatisLifecycle(event.islandId(), "level-update", () -> synchronizeSatisIsland(event.islandId(), "level-update"));
    }

    @Override
    public void onIslandWorthChanged(IslandWorthChangeEvent event) {
        runSatisLifecycle(event.islandId(), "worth-change", () -> synchronizeSatisIsland(event.islandId(), "worth-change"));
    }

    @Override
    public void onIslandUpgradeChanged(IslandUpgradeEvent event) {
        runSatisLifecycle(event.islandId(), "upgrade-change", () -> synchronizeSatisIsland(event.islandId(), "upgrade-change"));
    }

    @Override
    public void onIslandLimitChanged(IslandLimitChangeEvent event) {
        runSatisLifecycle(event.islandId(), "limit-change", () -> synchronizeSatisIsland(event.islandId(), "limit-change"));
    }

    @Override
    public void onIslandSnapshotRequested(IslandSnapshotRequestEvent event) {
        runSatisLifecycle(event.islandId(), "snapshot-requested", () -> flushSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandSnapshotCreated(IslandSnapshotCreateEvent event) {
        runSatisLifecycle(event.islandId(), "flush", () -> flushSatisIsland(event.islandId()));
    }

    private void runSatisLifecycle(UUID islandId, String operation, Runnable action) {
        if (islandId == null || database == null || !featureEnabled("lifecycle") || !lifecycleStateEnabled()) {
            return;
        }
        getServer().getScheduler().runTask(this, () -> {
            if (!isEnabled() || database == null || !featureEnabled("lifecycle") || !lifecycleStateEnabled()) {
                return;
            }
            try {
                action.run();
            } catch (RuntimeException exception) {
                publishLifecycleFailure(islandId, operation, exception);
                getLogger().warning("CloudIslands lifecycle sync failed for " + islandId + ": " + exception.getMessage());
            }
        });
    }

    private String lifecycleNode(String nodeId) {
        return nodeId == null || nodeId.isBlank() ? "unknown" : nodeId;
    }

    private String lifecycleWorld(String worldName) {
        return worldName == null || worldName.isBlank() ? "" : worldName;
    }

    private String lifecycleWorldToken(String worldName) {
        String safeWorld = lifecycleWorld(worldName);
        return safeWorld.isBlank() ? "" : "@" + safeWorld;
    }

    private String lifecycleCellToken(int cellX, int cellZ) {
        return "#" + cellX + "," + cellZ;
    }

    private String lifecycleNodePart(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "";
        }
        int worldSeparator = nodeId.indexOf('@');
        int cellSeparator = nodeId.indexOf('#');
        int end = worldSeparator < 0 ? cellSeparator : (cellSeparator < 0 ? worldSeparator : Math.min(worldSeparator, cellSeparator));
        return lifecycleNode(end < 0 ? nodeId : nodeId.substring(0, end));
    }

    private String lifecycleActiveNode(String operation) {
        if (operation == null) {
            return "";
        }
        if (operation.startsWith("activated:")) {
            return lifecycleNodePart(operation.substring("activated:".length()));
        }
        if (operation.startsWith("migrated:")) {
            int arrow = operation.indexOf("->");
            if (arrow < 0 || arrow + 2 >= operation.length()) {
                return "";
            }
            return lifecycleNodePart(operation.substring(arrow + 2));
        }
        if (operation.startsWith("migration-requested:")) {
            int arrow = operation.indexOf("->");
            if (arrow < 0 || arrow + 2 >= operation.length()) {
                return "";
            }
            return lifecycleNodePart(operation.substring(arrow + 2));
        }
        return "";
    }

    private String lifecycleEventNode(String operation) {
        if (operation != null && operation.startsWith("migration-requested:")) {
            int arrow = operation.indexOf("->");
            if (arrow > "migration-requested:".length()) {
                return lifecycleNodePart(operation.substring("migration-requested:".length(), arrow));
            }
        }
        String activeNode = lifecycleActiveNode(operation);
        if (!activeNode.isBlank()) {
            return activeNode;
        }
        if (operation != null && operation.startsWith("deactivated:")) {
            return lifecycleNodePart(operation.substring("deactivated:".length()));
        }
        if (operation != null && operation.startsWith("runtime:")) {
            int nodeSeparator = operation.lastIndexOf(':');
            if (nodeSeparator > "runtime:".length() && nodeSeparator + 1 < operation.length()) {
                return lifecycleNodePart(operation.substring(nodeSeparator + 1));
            }
        }
        return "";
    }

    private String runtimeOperation(String state, String targetNode) {
        String safeState = state == null || state.isBlank() ? "UNKNOWN" : state;
        return "runtime:" + safeState + ":" + lifecycleNode(targetNode);
    }

    private String lifecycleEventWorld(String operation) {
        if (operation == null) {
            return "";
        }
        int worldSeparator = operation.indexOf('@');
        if (worldSeparator < 0 || worldSeparator + 1 >= operation.length()) {
            return "";
        }
        String world = operation.substring(worldSeparator + 1);
        int cellSeparator = world.indexOf('#');
        return lifecycleWorld(cellSeparator < 0 ? world : world.substring(0, cellSeparator));
    }

    private String lifecycleEventCell(String operation) {
        if (operation == null) {
            return "";
        }
        int cellSeparator = operation.indexOf('#');
        if (cellSeparator < 0 || cellSeparator + 1 >= operation.length()) {
            return "";
        }
        String cell = operation.substring(cellSeparator + 1);
        int separator = cell.indexOf(' ');
        return separator < 0 ? cell : cell.substring(0, separator);
    }

    private void synchronizeSatisIsland(UUID islandId) {
        synchronizeSatisIsland(islandId, "synchronize");
    }

    private void synchronizeSatisIsland(UUID islandId, String operation) {
        if (islands == null) {
            return;
        }
        hydrateSatisIslandFromCore(islandId);
        islands.find(islandId).ifPresent(island -> {
            String remapDelta = "0,0,0";
            boolean machinesRemapped = false;
            boolean resourceNodesRemapped = false;
            org.bukkit.Location activeCenter = activeIslandCenter(islandId);
            if (activeCenter == null || activeCenter.getWorld() == null) {
                activeCenter = lifecycleFallbackCenter(island, operation);
            }
            if (activeCenter != null && activeCenter.getWorld() != null) {
                String activeWorld = activeCenter.getWorld().getName();
                int deltaX = island.hasActiveCenter() ? activeCenter.getBlockX() - island.activeCenterX() : 0;
                int deltaY = island.hasActiveCenter() ? activeCenter.getBlockY() - island.activeCenterY() : 0;
                int deltaZ = island.hasActiveCenter() ? activeCenter.getBlockZ() - island.activeCenterZ() : 0;
                remapDelta = deltaX + "," + deltaY + "," + deltaZ;
                if (machines != null && featureEnabled("machines")) {
                    machinesRemapped = machines.remapIslandRegion(islandId, activeWorld, deltaX, deltaY, deltaZ);
                }
                if (nodes != null && operationalFeatureEnabled("resource-nodes")) {
                    resourceNodesRemapped = nodes.remapIslandRegion(islandId, activeWorld, deltaX, deltaY, deltaZ);
                }
                island.activeWorld(activeWorld);
                island.activeCenterX(activeCenter.getBlockX());
                island.activeCenterY(activeCenter.getBlockY());
                island.activeCenterZ(activeCenter.getBlockZ());
                if (!lifecycleEventWorld(operation).isBlank() && !lifecycleEventWorld(operation).equals(activeWorld)) {
                    getLogger().warning("CloudIslands Satis lifecycle event world " + lifecycleEventWorld(operation)
                            + " differed from resolved active world " + activeWorld + " for " + islandId);
                }
            }
            if (maintenance != null && featureEnabled("maintenance")) {
                maintenance.updateStatus(island);
            }
            if (machines != null && itemNetworks != null && power != null && featureEnabled("machines")) {
                itemNetworks.rebuildIsland(islandId);
                power.rebuildIsland(islandId);
            }
            islands.save(island);
            publishLifecycleState(islandId, operation, island, remapDelta, machinesRemapped, resourceNodesRemapped);
        });
    }

    private void hydrateSatisIslandFromCore(UUID islandId) {
        if (islandId == null || database == null || coreApiState == null) {
            return;
        }
        if (database.activeBackend() != DatabaseService.StorageBackend.CORE_API) {
            return;
        }
        if (!coreHydratedIslands.add(islandId)) {
            return;
        }
        if (coreApiState.hydrateIsland(islandId, database)) {
            refreshIslandCache();
            refreshMachineCache();
            if (nodes != null) {
                nodes.forgetIsland(islandId);
            }
            if (storage != null) {
                storage.forgetIsland(islandId);
            }
        }
    }

    private org.bukkit.Location activeIslandCenter(UUID islandId) {
        if (skyblock == null) {
            return null;
        }
        return skyblock.getIslandByUuid(islandId)
                .flatMap(skyblock::getIslandCenter)
                .orElse(null);
    }

    private org.bukkit.Location lifecycleFallbackCenter(kr.seungmin.satisskyfactory.model.FactoryIsland island, String operation) {
        String eventWorld = lifecycleEventWorld(operation);
        if (eventWorld.isBlank()) {
            return null;
        }
        org.bukkit.World world = getServer().getWorld(eventWorld);
        if (world == null) {
            return null;
        }
        int x = island != null && island.hasActiveCenter() ? island.activeCenterX() : 0;
        int y = island != null && island.hasActiveCenter() ? island.activeCenterY() : 100;
        int z = island != null && island.hasActiveCenter() ? island.activeCenterZ() : 0;
        return new org.bukkit.Location(world, x + 0.5D, y, z + 0.5D);
    }

    private void flushSatisIsland(UUID islandId) {
        flushSatisIsland(islandId, "flush");
    }

    private void flushSatisIsland(UUID islandId, String operation) {
        String safeOperation = operation == null || operation.isBlank() ? "flush" : operation;
        synchronizeSatisIsland(islandId, safeOperation);
        if (dirtySaves != null) {
            dirtySaves.flushIslandSafely(islandId);
        }
        publishLifecycleState(islandId, safeOperation);
    }

    private void suspendRecoveredIsland(UUID islandId, String operation) {
        if (dirtySaves != null) {
            dirtySaves.forgetIsland(islandId);
        }
        if (machines != null) {
            machines.forgetIsland(islandId);
        }
        if (storage != null) {
            storage.forgetIsland(islandId);
        }
        if (nodes != null) {
            nodes.forgetIsland(islandId);
        }
        if (islands != null) {
            islands.forget(islandId);
        }
        coreHydratedIslands.remove(islandId);
        publishSuspendedLifecycleState(islandId, operation);
        publishIslandLifecycleState(islandId, operation, null, "suspended", "recovery-required-local-cache-evicted");
    }

    private void publishSuspendedLifecycleState(UUID islandId, String operation) {
        if (cloudIslandsApi == null || islandId == null || !featureEnabled("addon-state")) {
            return;
        }
        String safeOperation = operation == null || operation.isBlank() ? "recovery-required" : operation;
        String eventNode = lifecycleEventNode(safeOperation);
        String activeNode = lifecycleActiveNode(safeOperation);
        String eventWorld = lifecycleEventWorld(safeOperation);
        String eventCell = lifecycleEventCell(safeOperation);
        Map<String, String> state = new LinkedHashMap<>();
        state.put("last-lifecycle-island", islandId.toString());
        state.put("last-lifecycle-operation", safeOperation);
        state.put("last-lifecycle-database-open", Boolean.toString(database != null));
        state.put("last-lifecycle-shared-database", Boolean.toString(databaseShared()));
        state.put("last-lifecycle-schema", "3");
        state.put("last-lifecycle-at", Instant.now().toString());
        state.put("last-lifecycle-status", "suspended");
        state.put("last-lifecycle-error", "recovery-required-local-cache-evicted");
        state.put("last-lifecycle-suspend-mode", "drop-local-dirty-state");
        state.put("last-lifecycle-resume-source", "core-api-confirmed-state");
        state.put("last-lifecycle-state-authority", "last-core-confirmed-state-only");
        state.put("last-lifecycle-stale-write-policy", "discard-local-dirty-state");
        if (!eventNode.isBlank()) {
            state.put("last-lifecycle-node", eventNode);
        }
        if (!activeNode.isBlank()) {
            state.put("last-lifecycle-active-node", activeNode);
        }
        if (!eventWorld.isBlank()) {
            state.put("last-lifecycle-active-world", eventWorld);
        }
        if (!eventCell.isBlank()) {
            state.put("last-lifecycle-active-cell", eventCell);
        }
        cloudIslandsApi.addons().putState(ADDON_ID, state).exceptionally(error -> {
            getLogger().warning("Failed to publish CloudIslands Satis suspended lifecycle state: " + error.getMessage());
            return Map.of();
        });
    }

    private void purgeSatisIsland(UUID islandId) {
        if (islands != null) {
            islands.forget(islandId);
        }
        if (machines != null && featureEnabled("machines")) {
            machines.forgetIsland(islandId);
        }
        if (storage != null && storageDataEnabled()) {
            storage.forgetIsland(islandId);
        }
        if (nodes != null && operationalFeatureEnabled("resource-nodes")) {
            nodes.forgetIsland(islandId);
        }
        if (dirtySaves != null) {
            dirtySaves.forgetIsland(islandId);
        }
        coreHydratedIslands.remove(islandId);
        database.purgeIsland(islandId);
        publishLifecycleState(islandId, "purge");
        clearIslandLifecycleState(islandId);
    }

    private void stopRuntimeActivity() {
        installDisabledCommandHandler("addon");
        commandsRegistered = false;
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
            maintenanceTicker = null;
        }
        if (dirtySaves != null) {
            dirtySaves.discard();
            dirtySaves.coreStatePublisher(null);
            dirtySaves.coreStateDeletePublisher(null);
        }
        if (database != null) {
            database.coreStateWriter(null);
            database.coreTableWriter(null);
            database.coreGlobalStateWriter(null);
            database.coreGlobalTableWriter(null);
        }
        coreApiState = null;
        coreHydratedIslands.clear();
        if (placeholderHook != null) {
            placeholderHook.unregister();
            placeholderHook = null;
        }
        closeOpenFactoryGuis();
        clearRuntimeCaches();
        machineListenerRegistered = unregisterListener(machineListener, machineListenerRegistered);
        machineListener = null;
        guiListenerRegistered = unregisterListener(guiListener, guiListenerRegistered);
        guiListener = null;
        lifecycleListenerRegistered = unregisterListener(lifecycleListener, lifecycleListenerRegistered);
        lifecycleListener = null;
    }

    private void clearRuntimeCaches() {
        if (itemRegistry != null) {
            itemRegistry.clear();
        }
        if (machineDefinitions != null) {
            machineDefinitions.clear();
        }
        if (recipes != null) {
            recipes.clear();
        }
        if (islands != null) {
            islands.clear();
        }
        if (storage != null) {
            storage.clear();
        }
        if (machines != null) {
            machines.clear();
        }
        if (itemNetworks != null) {
            itemNetworks.clear();
        }
        if (power != null) {
            power.clear();
        }
        if (nodes != null) {
            nodes.clear();
        }
        if (market != null) {
            market.clear();
        }
        if (contracts != null) {
            contracts.clear();
        }
        if (maintenance != null) {
            maintenance.clear();
        }
        if (research != null) {
            research.clear();
        }
    }

    private void applyAddonRuntimeState() {
        if (database == null || configs == null) {
            return;
        }
        loadDefinitions();
        refreshIslandCache();
        refreshMachineCache();
        configureCoreApiStateWriters();
        if (featureEnabled("machines")) {
            rebuildNetworks();
        }
        restartRuntimeTasks();
        registerCommands();
        registerListeners();
        refreshPlaceholders();
    }

    private Map<String, Boolean> featureSnapshot() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("commands", configuredFeatureEnabled("commands"));
        features.put("machines", configuredFeatureEnabled("machines"));
        features.put("storage", configuredFeatureEnabled("storage"));
        features.put("gui", configuredFeatureEnabled("gui"));
        features.put("lifecycle", configuredFeatureEnabled("lifecycle"));
        features.put("resource-nodes", configuredFeatureEnabled("resource-nodes"));
        features.put("market", configuredFeatureEnabled("market"));
        features.put("contracts", configuredFeatureEnabled("contracts"));
        features.put("research", configuredFeatureEnabled("research"));
        features.put("maintenance", configuredFeatureEnabled("maintenance"));
        features.put("placeholders", configuredFeatureEnabled("placeholders"));
        features.put("migration", configuredFeatureEnabled("migration"));
        features.put("addon-state", configuredFeatureEnabled("addon-state"));
        FEATURE_ALIASES.forEach((alias, canonical) -> features.put(alias, features.getOrDefault(canonical, configuredFeatureEnabled(canonical)) && configuredFeatureEnabled(alias)));
        return features;
    }

    private String featureWarnings() {
        return featureWarnings(featureSnapshot());
    }

    private boolean addonStateReportingEnabled(CloudIslandsAddonSnapshot snapshot) {
        return snapshot.enabled() && snapshot.features().getOrDefault("addon-state", configuredFeatureEnabled("addon-state"));
    }

    private void putAddonStateSyncState(Map<String, String> state) {
        state.put("addon-state-sync", Boolean.toString(coreApiAddonStateAvailable()));
        state.put("addon-state-sync-configured", Boolean.toString(configuredFeatureEnabled("addon-state")));
        state.put("addon-state-sync-effective", Boolean.toString(featureEnabled("addon-state")));
        state.put("addon-state-sync-available", Boolean.toString(coreApiAddonStateAvailable()));
        state.put("addon-state-sync-policy", "configured-and-effective-feature-plus-cloudislands-api");
        state.put("addon-state-sync-runtime-source", "CloudIslands IslandRuntime");
        state.put("addon-state-sync-remap-policy", "island-uuid-stable-active-world-and-center-volatile");
        state.put("addon-state-sync-node-bound", "false");
        state.put("addon-state-sync-endpoint", "table/key-value/bulk-save,table/key-value/bulk");
        state.put("addon-state-sync-core-api-mode", databaseCoreApiMode());
        state.put("addon-state-sync-core-api-fallback-target", databaseCoreApiFallbackTarget());
        state.put("addon-state-sync-flattened-fallback-enabled", Boolean.toString(coreApiFlattenedFallbackEnabled()));
        state.put("addon-state-sync-write-fallback", databaseCoreApiWriteFallbackPolicy());
    }

    private boolean coreApiAddonStateAvailable() {
        return cloudIslandsApi != null && featureEnabled("addon-state");
    }

    private String featureWarnings(CloudIslandsAddonSnapshot snapshot) {
        List<String> warnings = new ArrayList<>();
        String effectiveWarnings = featureWarnings(snapshot.features());
        if (!effectiveWarnings.equals("none")) {
            warnings.add(effectiveWarnings);
        }
        dependencyDisabledFeatureMap(snapshot).forEach((feature, required) ->
                warnings.add("dependency-disabled:" + feature + "->" + required));
        return warnings.isEmpty() ? "none" : String.join(",", warnings);
    }

    private String dependencyDisabledFeatures(CloudIslandsAddonSnapshot snapshot) {
        Map<String, String> disabled = dependencyDisabledFeatureMap(snapshot);
        if (disabled.isEmpty()) {
            return "none";
        }
        List<String> features = new ArrayList<>();
        disabled.forEach((feature, required) -> features.add(feature + "->" + required));
        return String.join(",", features);
    }

    private Map<String, String> dependencyDisabledFeatureMap(CloudIslandsAddonSnapshot snapshot) {
        Map<String, String> disabled = new LinkedHashMap<>();
        snapshot.featureDependencies().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String feature = entry.getKey();
                    boolean configured = snapshot.configuredFeatures().getOrDefault(feature, true);
                    if (configured && !snapshot.featureEnabled(feature, true)) {
                        disabled.put(feature, entry.getValue());
                    }
                });
        return disabled;
    }

    private String featureWarnings(Map<String, Boolean> features) {
        List<String> warnings = new ArrayList<>();
        if (Boolean.TRUE.equals(features.get("gui"))
                && !Boolean.TRUE.equals(features.get("machines"))
                && !Boolean.TRUE.equals(features.get("storage"))
                && !Boolean.TRUE.equals(features.get("market"))
                && !Boolean.TRUE.equals(features.get("contracts"))
                && !Boolean.TRUE.equals(features.get("research"))) {
            warnings.add("gui-without-panels");
        }
        if (Boolean.TRUE.equals(features.get("market")) && !Boolean.TRUE.equals(features.get("storage"))) {
            warnings.add("market-without-storage-commands");
        }
        if (Boolean.TRUE.equals(features.get("contracts")) && !Boolean.TRUE.equals(features.get("storage"))) {
            warnings.add("contracts-without-storage");
        }
        if (Boolean.TRUE.equals(features.get("maintenance")) && !Boolean.TRUE.equals(features.get("storage"))) {
            warnings.add("maintenance-repair-without-storage");
        }
        if (Boolean.TRUE.equals(features.get("resource-nodes")) && !Boolean.TRUE.equals(features.get("machines"))) {
            warnings.add("resource-nodes-without-machines");
        }
        if (Boolean.TRUE.equals(features.get("generators")) && !Boolean.TRUE.equals(features.get("factories"))) {
            warnings.add("generators-without-factories");
        }
        if (Boolean.TRUE.equals(features.get("missions")) && !Boolean.TRUE.equals(features.get("storage"))) {
            warnings.add("missions-without-storage");
        }
        if (Boolean.TRUE.equals(features.get("contracts")) && !Boolean.TRUE.equals(features.get("maintenance"))) {
            warnings.add("contracts-without-maintenance-status");
        }
        if (Boolean.TRUE.equals(features.get("placeholders")) && !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            warnings.add("placeholderapi-not-installed");
        }
        if (Boolean.TRUE.equals(features.get("placeholders"))
                && !Boolean.TRUE.equals(features.get("machines"))
                && !Boolean.TRUE.equals(features.get("storage"))
                && !Boolean.TRUE.equals(features.get("contracts"))
                && !Boolean.TRUE.equals(features.get("research"))
                && !Boolean.TRUE.equals(features.get("maintenance"))) {
            warnings.add("placeholders-without-data-features");
        }
        if (Boolean.TRUE.equals(features.get("lifecycle"))
                && !Boolean.TRUE.equals(features.get("machines"))
                && !Boolean.TRUE.equals(features.get("storage"))
                && !Boolean.TRUE.equals(features.get("resource-nodes"))
                && !Boolean.TRUE.equals(features.get("market"))
                && !Boolean.TRUE.equals(features.get("contracts"))
                && !Boolean.TRUE.equals(features.get("research"))
                && !Boolean.TRUE.equals(features.get("maintenance"))) {
            warnings.add("lifecycle-without-satis-state");
        }
        if (!Boolean.TRUE.equals(features.get("lifecycle"))
                && (Boolean.TRUE.equals(features.get("machines"))
                || Boolean.TRUE.equals(features.get("storage"))
                || Boolean.TRUE.equals(features.get("resource-nodes"))
                || Boolean.TRUE.equals(features.get("market"))
                || Boolean.TRUE.equals(features.get("contracts"))
                || Boolean.TRUE.equals(features.get("research"))
                || Boolean.TRUE.equals(features.get("maintenance")))) {
            warnings.add("satis-state-without-lifecycle-sync");
        }
        if (!databaseShared()
                && (Boolean.TRUE.equals(features.get("machines"))
                || Boolean.TRUE.equals(features.get("storage"))
                || Boolean.TRUE.equals(features.get("resource-nodes"))
                || Boolean.TRUE.equals(features.get("market"))
                || Boolean.TRUE.equals(features.get("contracts"))
                || Boolean.TRUE.equals(features.get("research"))
                || Boolean.TRUE.equals(features.get("maintenance")))) {
            warnings.add("database-unshared-for-satis-state");
        }
        FEATURE_ALIASES.forEach((alias, canonical) -> {
            if (configFeature(canonical) && configFeatureDefined(alias) && !configFeature(alias)) {
                warnings.add("alias-disabled:" + alias + "->" + canonical);
            }
        });
        return warnings.isEmpty() ? "none" : String.join(",", warnings);
    }

    private void logFeatureWarnings() {
        String warnings = featureWarnings();
        if (!warnings.equals("none")) {
            getLogger().warning("CloudIslands Satis feature warnings: " + warnings);
        }
    }

    private boolean configuredFeatureEnabled(String key) {
        String requested = key == null ? "" : key;
        String canonical = canonicalFeature(key);
        boolean enabled = configFeature(canonical);
        if (!requested.equals(canonical) && configFeatureDefined(requested)) {
            enabled = enabled && configFeature(requested);
        }
        return enabled;
    }

    private boolean configFeature(String key) {
        String addonPath = "addons." + ADDON_ID + ".features." + key;
        String satisPath = "satis.features." + key;
        String legacyPath = "features." + key;
        boolean enabled = true;
        if (configs.main().contains(addonPath)) {
            enabled = configs.main().getBoolean(addonPath, true);
        }
        if (configs.main().contains(satisPath)) {
            enabled = enabled && configs.main().getBoolean(satisPath, true);
        }
        if (configs.main().contains(legacyPath)) {
            enabled = enabled && configs.main().getBoolean(legacyPath, true);
        }
        return enabled;
    }

    private boolean configFeatureDefined(String key) {
        return configs.main().contains("addons." + ADDON_ID + ".features." + key)
                || configs.main().contains("satis.features." + key)
                || configs.main().contains("features." + key);
    }

    private boolean featureEnabled(String key) {
        if (!addonRuntimeEnabled) {
            return false;
        }
        String requested = key == null ? "" : key;
        String canonical = canonicalFeature(key);
        boolean enabled = effectiveFeatures.getOrDefault(canonical, configuredFeatureEnabled(canonical));
        if (!requested.equals(canonical)) {
            Boolean effective = effectiveFeatures.get(requested);
            if (effective != null) {
                enabled = enabled && effective;
            }
        }
        return enabled;
    }

    private boolean operationalFeatureEnabled(String key) {
        String raw = key == null ? "" : key;
        if (raw.equals("generators") && !featureEnabled("factories")) {
            return false;
        }
        if (raw.equals("missions") && !featureEnabled("storage")) {
            return false;
        }
        String canonical = canonicalFeature(key);
        if (!featureEnabled(canonical)) {
            return false;
        }
        return switch (canonical) {
            case "resource-nodes" -> featureEnabled("machines");
            case "market", "contracts" -> featureEnabled("storage");
            default -> true;
        };
    }

    private String canonicalFeature(String key) {
        return FEATURE_ALIASES.getOrDefault(key, key);
    }

    private String resolveDatabaseFileName() {
        String envPath = System.getenv("CLOUDISLANDS_SATIS_DB");
        if (envPath != null && !envPath.isBlank()) {
            return envPath.trim();
        }
        String configuredPath = firstNonBlank(configs.main().getString("setup.database.path", ""), configs.main().getString("database.path", ""));
        if (configuredPath != null && !configuredPath.isBlank()) {
            return configuredPath.trim();
        }
        String sqliteFile = configuredDatabaseFileName();
        String sharedDirectory = firstNonBlank(configs.main().getString("setup.database.shared-directory", ""), configs.main().getString("database.shared-directory", ""));
        if (sharedDirectory != null && !sharedDirectory.isBlank()) {
            return new File(sharedDirectory.trim(), sqliteFile).getPath();
        }
        return sqliteFile;
    }

    private DatabaseService.Settings databaseSettings() {
        String configuredType = configuredDatabaseType();
        DatabaseService.StorageBackend backend = DatabaseService.StorageBackend.parse(configuredType, null);
        pendingDatabaseConfigFallbackReason = "none";
        if (backend == null) {
            backend = DatabaseService.StorageBackend.SQLITE;
            appendPendingDatabaseConfigFallbackReason("invalid-database-backend:" + safeReasonToken(configuredType) + "->SQLITE");
        }
        return new DatabaseService.Settings(
                backend,
                resolveDatabaseFileName(),
                firstNonBlank(System.getenv("CLOUDISLANDS_SATIS_JDBC_URL"),
                        firstNonBlank(configs.main().getString("setup.database.jdbc.url", ""), configs.main().getString("database.jdbc.url", ""))),
                jdbcUrl("postgresql", "jdbc:postgresql", 5432),
                jdbcUrl("mysql", "jdbc:mysql", 3306),
                jdbcUrl("mariadb", "jdbc:mariadb", 3306),
                databaseUsername(),
                databasePassword(),
                Math.max(1, databaseMaxPoolSize(8)),
                Math.max(1000L, databaseConnectionTimeoutMillis(5000L)),
                databaseBackendSettings("postgresql"),
                databaseBackendSettings("mysql"),
                databaseBackendSettings("mariadb"),
                envBoolean("CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED", setupBoolean("database.fallback.enabled", true)),
                databaseFallbackOrder(true)
        );
    }

    private String databaseSettingsFingerprint(DatabaseService.Settings settings) {
        if (settings == null) {
            return "";
        }
        String fallbackOrder = settings.fallbackOrder() == null
                ? ""
                : settings.fallbackOrder().stream()
                .map(backend -> backend == null ? "" : backend.name())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return String.join("|",
                settings.backend() == null ? "" : settings.backend().name(),
                safe(settings.sqliteFileName()),
                safe(settings.jdbcUrl()),
                safe(settings.postgresqlJdbcUrl()),
                safe(settings.mysqlJdbcUrl()),
                safe(settings.mariadbJdbcUrl()),
                safe(settings.username()),
                Integer.toHexString(safe(settings.password()).hashCode()),
                Integer.toString(settings.maxPoolSize()),
                Long.toString(settings.connectionTimeoutMillis()),
                backendSettingsFingerprint(settings.postgresqlSettings()),
                backendSettingsFingerprint(settings.mysqlSettings()),
                backendSettingsFingerprint(settings.mariadbSettings()),
                Boolean.toString(settings.fallbackEnabled()),
                fallbackOrder
        );
    }

    private String backendSettingsFingerprint(DatabaseService.BackendSettings settings) {
        if (settings == null) {
            return "";
        }
        return String.join(":",
                safe(settings.username()),
                Integer.toHexString(safe(settings.password()).hashCode()),
                Integer.toString(settings.maxPoolSize()),
                Long.toString(settings.connectionTimeoutMillis())
        );
    }

    private List<DatabaseService.StorageBackend> databaseFallbackOrder() {
        return databaseFallbackOrder(true);
    }

    private List<DatabaseService.StorageBackend> databaseFallbackOrder(boolean recordReason) {
        String envOrder = System.getenv("CLOUDISLANDS_SATIS_DB_FALLBACK_ORDER");
        List<String> configured = envOrder == null || envOrder.isBlank()
                ? configs.main().getStringList("setup.database.fallback.order")
                : java.util.Arrays.stream(envOrder.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
        if (configured == null || configured.isEmpty()) {
            configured = configs.main().getStringList("database.fallback.order");
        }
        if (configured == null || configured.isEmpty()) {
            configured = List.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API", "SQLITE");
        }
        List<DatabaseService.StorageBackend> backends = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String entry : configured) {
            DatabaseService.StorageBackend backend = DatabaseService.StorageBackend.parse(entry, null);
            if (backend != null && !backends.contains(backend)) {
                backends.add(backend);
            } else if (backend == null && entry != null && !entry.isBlank()) {
                invalid.add(safeReasonToken(entry));
            }
        }
        if (recordReason && !invalid.isEmpty()) {
            appendPendingDatabaseConfigFallbackReason("invalid-database-fallback-order:" + String.join("+", invalid));
        }
        if (backends.isEmpty()) {
            if (recordReason) {
                appendPendingDatabaseConfigFallbackReason("empty-database-fallback-order->POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE");
            }
            return List.of(
                    DatabaseService.StorageBackend.POSTGRESQL,
                    DatabaseService.StorageBackend.MYSQL,
                    DatabaseService.StorageBackend.MARIADB,
                    DatabaseService.StorageBackend.CORE_API,
                    DatabaseService.StorageBackend.SQLITE
            );
        }
        return backends;
    }

    private void applyCoreApiDatabaseFallback(DatabaseService.Settings settings) {
        if (database == null || settings == null || database.activeBackend() != DatabaseService.StorageBackend.CORE_API) {
            return;
        }
        if (coreApiAddonStateAvailable()) {
            return;
        }
        if (!settings.fallbackEnabled()) {
            databaseFallbackReason = cloudIslandsApi == null ? "core-api-cloudislands-api-missing" : "core-api-addon-state-disabled";
            getLogger().warning("Satis CORE_API database backend is active, but CloudIslands addon-state is unavailable and database fallback is disabled.");
            return;
        }
        DatabaseService.StorageBackend fallbackBackend = null;
        List<DatabaseService.StorageBackend> fallbackOrder = settings.fallbackOrder() == null ? List.of() : settings.fallbackOrder();
        for (DatabaseService.StorageBackend backend : fallbackOrder) {
            if (backend != null && backend != DatabaseService.StorageBackend.CORE_API && backend != settings.backend()) {
                fallbackBackend = backend;
                break;
            }
        }
        if (fallbackBackend == null) {
            databaseFallbackReason = cloudIslandsApi == null ? "core-api-cloudislands-api-missing" : "core-api-addon-state-disabled";
            getLogger().warning("Satis CORE_API database backend is active, but no non-CORE_API fallback backend is configured.");
            return;
        }
        databaseFallbackReason = (cloudIslandsApi == null ? "core-api-cloudislands-api-missing" : "core-api-addon-state-disabled") + "->" + fallbackBackend.name();
        getLogger().warning("Satis CORE_API database backend is unavailable; falling back to " + fallbackBackend + ".");
        database.close();
        List<DatabaseService.StorageBackend> safeFallbackOrder = fallbackOrder.stream()
                .filter(backend -> backend != DatabaseService.StorageBackend.CORE_API)
                .toList();
        database = new DatabaseService(this, new DatabaseService.Settings(
                fallbackBackend,
                settings.sqliteFileName(),
                settings.jdbcUrl(),
                settings.postgresqlJdbcUrl(),
                settings.mysqlJdbcUrl(),
                settings.mariadbJdbcUrl(),
                settings.username(),
                settings.password(),
                settings.maxPoolSize(),
                settings.connectionTimeoutMillis(),
                settings.postgresqlSettings(),
                settings.mysqlSettings(),
                settings.mariadbSettings(),
                true,
                safeFallbackOrder
        ));
        database.open();
        String backendFallback = database.fallbackReason();
        if (backendFallback != null && !backendFallback.isBlank() && !"none".equalsIgnoreCase(backendFallback)) {
            databaseFallbackReason = databaseFallbackReason + ";" + backendFallback;
        }
    }

    private void syncDatabaseFallbackReason() {
        if (database == null) {
            return;
        }
        String reason = database.fallbackReason();
        if (reason == null || reason.isBlank() || "none".equalsIgnoreCase(reason)) {
            return;
        }
        appendDatabaseFallbackReason(reason);
    }

    private void appendDatabaseFallbackReason(String reason) {
        if (reason == null || reason.isBlank() || "none".equalsIgnoreCase(reason)) {
            return;
        }
        if (databaseFallbackReason == null || databaseFallbackReason.isBlank() || "none".equalsIgnoreCase(databaseFallbackReason)) {
            databaseFallbackReason = reason;
            return;
        }
        if (!databaseFallbackReason.contains(reason)) {
            databaseFallbackReason = databaseFallbackReason + ";" + reason;
        }
    }

    private void appendPendingDatabaseConfigFallbackReason(String reason) {
        if (reason == null || reason.isBlank() || "none".equalsIgnoreCase(reason)) {
            return;
        }
        if (pendingDatabaseConfigFallbackReason == null
                || pendingDatabaseConfigFallbackReason.isBlank()
                || "none".equalsIgnoreCase(pendingDatabaseConfigFallbackReason)) {
            pendingDatabaseConfigFallbackReason = reason;
            return;
        }
        if (!pendingDatabaseConfigFallbackReason.contains(reason)) {
            pendingDatabaseConfigFallbackReason = pendingDatabaseConfigFallbackReason + ";" + reason;
        }
    }

    private String safeReasonToken(String value) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) {
            return "blank";
        }
        safe = safe.replaceAll("[^A-Za-z0-9_.:-]", "_");
        return safe.length() > 48 ? safe.substring(0, 48) : safe;
    }

    private String jdbcUrl(String section, String prefix, int defaultPort) {
        String configured = firstNonBlank(configs.main().getString("setup.database." + section + ".jdbc-url", ""),
                firstNonBlank(configs.main().getString("setup.database." + section + ".url", ""),
                        firstNonBlank(configs.main().getString("database." + section + ".jdbc-url", ""),
                                configs.main().getString("database." + section + ".url", ""))));
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        String host = firstNonBlank(configs.main().getString("setup.database." + section + ".host", ""), configs.main().getString("database." + section + ".host", "127.0.0.1"));
        String databaseName = firstNonBlank(configs.main().getString("setup.database." + section + ".name", ""),
                firstNonBlank(configs.main().getString("setup.database." + section + ".database", ""),
                        firstNonBlank(configs.main().getString("database." + section + ".name", ""),
                                configs.main().getString("database." + section + ".database", ""))));
        if (host == null || host.isBlank() || databaseName == null || databaseName.isBlank()) {
            return "";
        }
        int setupPort = configs.main().getInt("setup.database." + section + ".port", 0);
        int port = Math.max(1, setupPort > 0 ? setupPort : configs.main().getInt("database." + section + ".port", defaultPort));
        String options = firstNonBlank(configs.main().getString("setup.database." + section + ".options", ""), configs.main().getString("database." + section + ".options", ""));
        String url = prefix + "://" + host.trim() + ":" + port + "/" + databaseName.trim();
        if (options != null && !options.isBlank()) {
            url += "?" + options.trim();
        }
        return url;
    }

    private String databaseUsername() {
        String env = System.getenv("CLOUDISLANDS_SATIS_DB_USERNAME");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return firstNonBlank(typedDatabaseSetting("username"),
                firstNonBlank(configs.main().getString("setup.database.jdbc.username", ""), configs.main().getString("database.jdbc.username", "")));
    }

    private String databasePassword() {
        String env = System.getenv("CLOUDISLANDS_SATIS_DB_PASSWORD");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return firstNonBlank(typedDatabaseSetting("password"),
                firstNonBlank(configs.main().getString("setup.database.jdbc.password", ""), configs.main().getString("database.jdbc.password", "")));
    }

    private int databaseMaxPoolSize(int fallback) {
        int env = envInt("CLOUDISLANDS_SATIS_DB_MAX_POOL_SIZE", -1);
        if (env > 0) {
            return env;
        }
        int typed = typedDatabaseInt("max-pool-size", 0);
        return typed > 0 ? typed : setupInt("database.jdbc.max-pool-size", fallback);
    }

    private long databaseConnectionTimeoutMillis(long fallback) {
        long env = envLong("CLOUDISLANDS_SATIS_DB_CONNECTION_TIMEOUT_MS", -1L);
        if (env > 0L) {
            return env;
        }
        long typed = typedDatabaseLong("connection-timeout-ms", 0L);
        return typed > 0L ? typed : setupLong("database.jdbc.connection-timeout-ms", fallback);
    }

    private DatabaseService.BackendSettings databaseBackendSettings(String section) {
        return new DatabaseService.BackendSettings(
                firstNonBlank(configs.main().getString("setup.database." + section + ".username", ""), configs.main().getString("database." + section + ".username", "")),
                firstNonBlank(configs.main().getString("setup.database." + section + ".password", ""), configs.main().getString("database." + section + ".password", "")),
                positiveInt("setup.database." + section + ".max-pool-size", positiveInt("database." + section + ".max-pool-size", 0)),
                positiveLong("setup.database." + section + ".connection-timeout-ms", positiveLong("database." + section + ".connection-timeout-ms", 0L))
        );
    }

    private String typedDatabaseSetting(String key) {
        String section = databaseSetupSection();
        return section.isBlank() ? "" : configs.main().getString("setup.database." + section + "." + key, "");
    }

    private int typedDatabaseInt(String key, int fallback) {
        String section = databaseSetupSection();
        return section.isBlank() ? fallback : configs.main().getInt("setup.database." + section + "." + key, fallback);
    }

    private long typedDatabaseLong(String key, long fallback) {
        String section = databaseSetupSection();
        return section.isBlank() ? fallback : configs.main().getLong("setup.database." + section + "." + key, fallback);
    }

    private String databaseSetupSection() {
        return switch (configuredDatabaseBackendName()) {
            case "POSTGRESQL" -> "postgresql";
            case "MYSQL" -> "mysql";
            case "MARIADB" -> "mariadb";
            default -> "";
        };
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String configuredDatabaseFileName() {
        String sqliteFile = firstNonBlank(configs.main().getString("setup.database.sqlite-file", ""), configs.main().getString("database.sqlite-file", "data.db"));
        return sqliteFile == null || sqliteFile.isBlank() ? "data.db" : sqliteFile.trim();
    }

    private String configuredDatabaseType() {
        String envType = System.getenv("CLOUDISLANDS_SATIS_DATABASE_TYPE");
        if (envType != null && !envType.isBlank()) {
            return envType;
        }
        String setupType = configs.main().getString("setup.database.type", "");
        if (setupType != null && !setupType.isBlank()) {
            return setupType;
        }
        if (configs.main().getBoolean("setup.database.core-api.enabled", false)) {
            return "CORE_API";
        }
        String jdbcType = inferredJdbcDatabaseType();
        if (!jdbcType.isBlank()) {
            return jdbcType;
        }
        String setupSectionType = inferredSetupDatabaseType();
        if (!setupSectionType.isBlank()) {
            return setupSectionType;
        }
        return configs.main().getString("database.type", "SQLITE");
    }

    private String inferredJdbcDatabaseType() {
        String jdbcUrl = firstNonBlank(System.getenv("CLOUDISLANDS_SATIS_JDBC_URL"),
                firstNonBlank(configs.main().getString("setup.database.jdbc.url", ""), configs.main().getString("database.jdbc.url", "")));
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        String normalized = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:postgresql:")) {
            return "POSTGRESQL";
        }
        if (normalized.startsWith("jdbc:mysql:")) {
            return "MYSQL";
        }
        if (normalized.startsWith("jdbc:mariadb:")) {
            return "MARIADB";
        }
        return "";
    }

    private String inferredSetupDatabaseType() {
        List<String> configured = configuredSetupDatabaseSections();
        return configured.size() == 1 ? configured.get(0) : "";
    }

    private List<String> configuredSetupDatabaseSections() {
        List<String> configured = new ArrayList<>();
        if (setupDatabaseSectionConfigured("postgresql")) {
            configured.add("POSTGRESQL");
        }
        if (setupDatabaseSectionConfigured("mysql")) {
            configured.add("MYSQL");
        }
        if (setupDatabaseSectionConfigured("mariadb")) {
            configured.add("MARIADB");
        }
        return List.copyOf(configured);
    }

    private String databaseSetupSectionsMetadata() {
        List<String> configured = configuredSetupDatabaseSections();
        return configured.isEmpty() ? "none" : String.join(",", configured);
    }

    private boolean databaseSetupAutoSelected() {
        String envType = System.getenv("CLOUDISLANDS_SATIS_DATABASE_TYPE");
        if (envType != null && !envType.isBlank()) {
            return false;
        }
        String setupType = configs.main().getString("setup.database.type", "");
        if (setupType != null && !setupType.isBlank()) {
            return false;
        }
        if (configs.main().getBoolean("setup.database.core-api.enabled", false)) {
            return false;
        }
        return !inferredJdbcDatabaseType().isBlank() || !inferredSetupDatabaseType().isBlank();
    }

    private String databaseSetupSelectedBackendMetadata() {
        if (!databaseSetupAutoSelected()) {
            return "none";
        }
        String jdbcType = inferredJdbcDatabaseType();
        return jdbcType.isBlank() ? inferredSetupDatabaseType() : jdbcType;
    }

    private String databaseSetupWarningMetadata() {
        String envType = System.getenv("CLOUDISLANDS_SATIS_DATABASE_TYPE");
        if (envType != null && !envType.isBlank()) {
            return "none";
        }
        String setupType = configs.main().getString("setup.database.type", "");
        if (setupType != null && !setupType.isBlank()) {
            return "none";
        }
        if (configs.main().getBoolean("setup.database.core-api.enabled", false)) {
            return "none";
        }
        String databaseType = configs.main().getString("database.type", "");
        if (databaseType != null && !databaseType.isBlank() && !"SQLITE".equalsIgnoreCase(databaseType.trim())) {
            return "none";
        }
        List<String> configured = configuredSetupDatabaseSections();
        if (configured.size() <= 1) {
            return "none";
        }
        return "ambiguous:" + String.join(",", configured) + ":set-setup.database.type";
    }

    private boolean setupDatabaseSectionConfigured(String section) {
        String base = "setup.database." + section + ".";
        return nonBlankConfig(base + "jdbc-url")
                || nonBlankConfig(base + "url")
                || nonBlankConfig(base + "host")
                || nonBlankConfig(base + "name")
                || nonBlankConfig(base + "database")
                || nonBlankConfig(base + "username")
                || nonBlankConfig(base + "password")
                || configs.main().getInt(base + "port", 0) > 0
                || configs.main().getInt(base + "max-pool-size", 0) > 0
                || configs.main().getLong(base + "connection-timeout-ms", 0L) > 0L;
    }

    private String configuredDatabaseBackendName() {
        return DatabaseService.StorageBackend.parse(configuredDatabaseType(), DatabaseService.StorageBackend.SQLITE).name();
    }

    private String databaseFallbackOrderMetadata() {
        return databaseFallbackOrder(false).stream()
                .map(DatabaseService.StorageBackend::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }

    private String databaseFallbackSharedBackendsMetadata() {
        return databaseFallbackOrder(false).stream()
                .filter(this::isSharedStorageBackend)
                .map(DatabaseService.StorageBackend::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }

    private String databaseBackendAttemptOrderMetadata() {
        return databaseBackendAttemptOrder(databaseSettings()).stream()
                .map(DatabaseService.StorageBackend::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }

    private String databaseJdbcTargetMetadata() {
        DatabaseService.Settings settings = databaseSettings();
        DatabaseService.StorageBackend backend = settings.backend() == null ? DatabaseService.StorageBackend.SQLITE : settings.backend();
        return databaseJdbcTargetMetadata(settings, backend);
    }

    private String databaseFallbackJdbcTargetMetadata() {
        DatabaseService.Settings settings = databaseSettings();
        if (!settings.fallbackEnabled()) {
            return "disabled";
        }
        DatabaseService.StorageBackend configured = settings.backend() == null ? DatabaseService.StorageBackend.SQLITE : settings.backend();
        for (DatabaseService.StorageBackend backend : databaseBackendAttemptOrder(settings)) {
            if (backend != configured) {
                return databaseJdbcTargetMetadata(settings, backend);
            }
        }
        return "none";
    }

    private String databaseFallbackJdbcTargetsMetadata() {
        DatabaseService.Settings settings = databaseSettings();
        if (!settings.fallbackEnabled()) {
            return "disabled";
        }
        DatabaseService.StorageBackend configured = settings.backend() == null ? DatabaseService.StorageBackend.SQLITE : settings.backend();
        List<String> targets = new ArrayList<>();
        for (DatabaseService.StorageBackend backend : databaseBackendAttemptOrder(settings)) {
            if (backend != configured) {
                targets.add(backend.name() + "=" + databaseJdbcTargetMetadata(settings, backend));
            }
        }
        return targets.isEmpty() ? "none" : String.join(",", targets);
    }

    private String databaseJdbcTargetMetadata(DatabaseService.Settings settings, DatabaseService.StorageBackend backend) {
        if (settings == null || backend == null) {
            return "not-configured";
        }
        String url = switch (backend) {
            case POSTGRESQL -> firstNonBlank(settings.jdbcUrl(), settings.postgresqlJdbcUrl());
            case MYSQL -> firstNonBlank(settings.jdbcUrl(), settings.mysqlJdbcUrl());
            case MARIADB -> firstNonBlank(settings.jdbcUrl(), settings.mariadbJdbcUrl());
            default -> "";
        };
        if (url == null || url.isBlank()) {
            return backend == DatabaseService.StorageBackend.SQLITE || backend == DatabaseService.StorageBackend.CORE_API
                    ? "not-used"
                    : "not-configured";
        }
        return safeJdbcTarget(url);
    }

    private String safeJdbcTarget(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        String value = jdbcUrl.trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        return value.replaceAll("(?i)(password=)[^;&]+", "$1***");
    }

    private boolean databaseFallbackSharedSafe() {
        return databaseFallbackProductionSafe();
    }

    private String databaseFallbackRisk() {
        DatabaseService.Settings settings = databaseSettings();
        if (!settings.fallbackEnabled()) {
            return "fallback-disabled";
        }
        List<DatabaseService.StorageBackend> order = databaseBackendAttemptOrder(settings);
        int sqliteIndex = order.indexOf(DatabaseService.StorageBackend.SQLITE);
        int firstSharedIndex = firstSharedFallbackIndex(order);
        if (firstSharedIndex >= 0 && (sqliteIndex < 0 || firstSharedIndex < sqliteIndex)) {
            return sqliteIndex >= 0 ? "shared-before-local-sqlite" : "shared-only";
        }
        if (firstSharedIndex >= 0) {
            return "local-sqlite-before-shared";
        }
        if (sqliteIndex >= 0) {
            return "local-sqlite-only";
        }
        return "no-shared-fallback";
    }

    private boolean databaseFallbackProductionSafe() {
        String risk = databaseFallbackRisk();
        return risk.equals("shared-before-local-sqlite") || risk.equals("shared-only");
    }

    private String databaseFallbackWarningMetadata() {
        if (databaseFallbackProductionSafe()) {
            return "none";
        }
        return "unsafe:" + databaseFallbackRisk();
    }

    private List<DatabaseService.StorageBackend> databaseBackendAttemptOrder(DatabaseService.Settings settings) {
        if (settings == null) {
            return List.of(DatabaseService.StorageBackend.SQLITE);
        }
        List<DatabaseService.StorageBackend> order = new ArrayList<>();
        order.add(settings.backend() == null ? DatabaseService.StorageBackend.SQLITE : settings.backend());
        if (settings.fallbackEnabled()) {
            List<DatabaseService.StorageBackend> fallbackOrder = settings.fallbackOrder() == null ? List.of() : settings.fallbackOrder();
            for (DatabaseService.StorageBackend backend : fallbackOrder) {
                if (backend != null && !order.contains(backend)) {
                    order.add(backend);
                }
            }
        }
        return List.copyOf(order);
    }

    private int firstSharedFallbackIndex(List<DatabaseService.StorageBackend> order) {
        for (int index = 0; index < order.size(); index++) {
            if (isSharedStorageBackend(order.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private boolean isSharedStorageBackend(DatabaseService.StorageBackend backend) {
        return backend == DatabaseService.StorageBackend.POSTGRESQL
                || backend == DatabaseService.StorageBackend.MYSQL
                || backend == DatabaseService.StorageBackend.MARIADB
                || backend == DatabaseService.StorageBackend.CORE_API;
    }

    private String databaseAttemptedBackendsMetadata() {
        if (database == null || database.attemptedBackends().isEmpty()) {
            return "none";
        }
        return database.attemptedBackends().stream()
                .map(DatabaseService.StorageBackend::name)
                .reduce((left, right) -> left + "," + right)
                .orElse("none");
    }

    private boolean databaseFallbackActive() {
        return databaseFallbackReason != null
                && !databaseFallbackReason.isBlank()
                && !"none".equalsIgnoreCase(databaseFallbackReason);
    }

    private String databaseFallbackStatus() {
        if (!databaseFallbackActive()) {
            return databaseSettings().fallbackEnabled() ? "ready" : "disabled";
        }
        String active = database == null ? "NOT_OPEN" : database.activeBackend().name();
        return "active:" + active;
    }

    private String databaseCoreApiMode() {
        DatabaseService.StorageBackend configured = DatabaseService.StorageBackend.parse(configuredDatabaseType(), DatabaseService.StorageBackend.SQLITE);
        DatabaseService.StorageBackend active = database == null ? configured : database.activeBackend();
        if (active == DatabaseService.StorageBackend.CORE_API) {
            if (!coreApiAddonStateAvailable()) {
                return "core-api-local-sqlite-cache-only-addon-state-unavailable";
            }
            return "core-api-bulk-table-key-value-with-local-sqlite-cache";
        }
        if (configured == DatabaseService.StorageBackend.CORE_API) {
            return "configured-core-api-fell-back-to-" + active.name();
        }
        if (configs.main().getBoolean("setup.database.core-api.enabled", false)) {
            return "setup-core-api-marker-only";
        }
        return "not-selected";
    }

    private String databaseCoreApiLocalCachePolicy() {
        DatabaseService.StorageBackend configured = DatabaseService.StorageBackend.parse(configuredDatabaseType(), DatabaseService.StorageBackend.SQLITE);
        DatabaseService.StorageBackend active = database == null ? configured : database.activeBackend();
        if (active == DatabaseService.StorageBackend.CORE_API) {
            if (!coreApiAddonStateAvailable()) {
                return "local-cache-only-addon-state-unavailable";
            }
            return "required-for-runtime-reads-core-api-is-authoritative-publish-target";
        }
        if (configured == DatabaseService.StorageBackend.CORE_API) {
            return "inactive-after-fallback";
        }
        return "not-used";
    }

    private String databaseCoreApiFallbackTarget() {
        if (!databaseSettings().fallbackEnabled()) {
            return "disabled";
        }
        return databaseFallbackOrder(false).stream()
                .filter(backend -> backend != DatabaseService.StorageBackend.CORE_API)
                .map(DatabaseService.StorageBackend::name)
                .findFirst()
                .orElse("none");
    }

    private String databaseCoreApiFallbackPolicy() {
        if (!databaseSettings().fallbackEnabled()) {
            return "disabled-use-local-cache-only-and-warn";
        }
        return "if-cloudislands-api-or-addon-state-unavailable-use-first-non-core-api-backend";
    }

    private boolean coreApiFlattenedFallbackEnabled() {
        return setupBoolean("database.core-api.flattened-fallback.enabled", true);
    }

    private String databaseCoreApiWriteFallbackPolicy() {
        if (!coreApiFlattenedFallbackEnabled()) {
            return "disabled";
        }
        return "retry-table-key-value-bulk-save-as-flattened-addon-state";
    }

    private int setupInt(String path, int fallback) {
        String setupPath = "setup." + path;
        if (configs.main().contains(setupPath) && configs.main().getInt(setupPath, 0) > 0) {
            return configs.main().getInt(setupPath, fallback);
        }
        return configs.main().getInt(path, fallback);
    }

    private int positiveInt(String path, int fallback) {
        int value = configs.main().getInt(path, 0);
        return value > 0 ? value : fallback;
    }

    private long setupLong(String path, long fallback) {
        String setupPath = "setup." + path;
        if (configs.main().contains(setupPath) && configs.main().getLong(setupPath, 0L) > 0L) {
            return configs.main().getLong(setupPath, fallback);
        }
        return configs.main().getLong(path, fallback);
    }

    private long positiveLong(String path, long fallback) {
        long value = configs.main().getLong(path, 0L);
        return value > 0L ? value : fallback;
    }

    private boolean setupBoolean(String path, boolean fallback) {
        String setupPath = "setup." + path;
        if (configs.main().contains(setupPath)) {
            return configs.main().getBoolean(setupPath, fallback);
        }
        return configs.main().getBoolean(path, fallback);
    }

    private int envInt(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long envLong(String key, long fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean envBoolean(String key, boolean fallback) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on", "1", "enable", "enabled" -> true;
            case "false", "no", "off", "0", "disable", "disabled" -> false;
            default -> fallback;
        };
    }

    private String databaseScope() {
        DatabaseService.StorageBackend backend = database == null
                ? DatabaseService.StorageBackend.parse(configuredDatabaseType(), DatabaseService.StorageBackend.SQLITE)
                : database.activeBackend();
        if (backend == DatabaseService.StorageBackend.CORE_API) {
            return "CORE_API";
        }
        if (backend != DatabaseService.StorageBackend.SQLITE) {
            return backend.name();
        }
        String envPath = System.getenv("CLOUDISLANDS_SATIS_DB");
        if (envPath != null && !envPath.isBlank()) {
            return "ENV_SHARED";
        }
        String configuredPath = firstNonBlank(configs.main().getString("setup.database.path", ""), configs.main().getString("database.path", ""));
        if (configuredPath != null && !configuredPath.isBlank()) {
            return new File(configuredPath).isAbsolute() ? "ABSOLUTE_PATH" : "PLUGIN_RELATIVE_PATH";
        }
        String sharedDirectory = firstNonBlank(configs.main().getString("setup.database.shared-directory", ""), configs.main().getString("database.shared-directory", ""));
        if (sharedDirectory != null && !sharedDirectory.isBlank()) {
            return "SHARED_DIRECTORY";
        }
        return "PLUGIN_LOCAL";
    }

    private boolean databaseShared() {
        String scope = databaseScope();
        if (scope.equals("POSTGRESQL") || scope.equals("MYSQL") || scope.equals("MARIADB") || scope.equals("CORE_API")) {
            return true;
        }
        return !scope.equals("PLUGIN_LOCAL") && !scope.equals("PLUGIN_RELATIVE_PATH");
    }

    private String databaseConfigSource() {
        DatabaseService.StorageBackend backend = DatabaseService.StorageBackend.parse(
                configuredDatabaseType(), DatabaseService.StorageBackend.SQLITE);
        String envBackend = System.getenv("CLOUDISLANDS_SATIS_DATABASE_TYPE");
        if (envBackend != null && !envBackend.isBlank()) {
            return "CLOUDISLANDS_SATIS_DATABASE_TYPE";
        }
        String setupType = configs.main().getString("setup.database.type", "");
        if (setupType != null && !setupType.isBlank()) {
            return "setup.database.type";
        }
        if (configs.main().getBoolean("setup.database.core-api.enabled", false)) {
            return "setup.database.core-api.enabled";
        }
        String setupSectionType = inferredSetupDatabaseType();
        if (!setupSectionType.isBlank()) {
            return "setup.database." + setupSectionType.toLowerCase(Locale.ROOT);
        }
        if (backend != DatabaseService.StorageBackend.SQLITE) {
            return "database.type";
        }
        String envPath = System.getenv("CLOUDISLANDS_SATIS_DB");
        if (envPath != null && !envPath.isBlank()) {
            return "CLOUDISLANDS_SATIS_DB";
        }
        String setupPath = configs.main().getString("setup.database.path", "");
        if (setupPath != null && !setupPath.isBlank()) {
            return "setup.database.path";
        }
        String configuredPath = configs.main().getString("database.path", "");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return "database.path";
        }
        String setupSharedDirectory = configs.main().getString("setup.database.shared-directory", "");
        if (setupSharedDirectory != null && !setupSharedDirectory.isBlank()) {
            return "setup.database.shared-directory";
        }
        String sharedDirectory = configs.main().getString("database.shared-directory", "");
        if (sharedDirectory != null && !sharedDirectory.isBlank()) {
            return "database.shared-directory";
        }
        return "database.sqlite-file";
    }

    private String databaseCredentialsSource() {
        String envUsername = System.getenv("CLOUDISLANDS_SATIS_DB_USERNAME");
        String envPassword = System.getenv("CLOUDISLANDS_SATIS_DB_PASSWORD");
        if ((envUsername != null && !envUsername.isBlank()) || (envPassword != null && !envPassword.isBlank())) {
            return "CLOUDISLANDS_SATIS_DB_USERNAME/PASSWORD";
        }
        String typedSection = databaseSetupSection();
        if (!typedSection.isBlank()
                && (!configs.main().getString("setup.database." + typedSection + ".username", "").isBlank()
                || !configs.main().getString("setup.database." + typedSection + ".password", "").isBlank())) {
            return "setup.database." + typedSection + ".username/password";
        }

        String setupUsername = configs.main().getString("setup.database.jdbc.username", "");
        String setupPassword = configs.main().getString("setup.database.jdbc.password", "");
        if ((setupUsername != null && !setupUsername.isBlank()) || (setupPassword != null && !setupPassword.isBlank())) {
            return "setup.database.jdbc.username/password";
        }
        return "database.jdbc.username/password";
    }

    private String databaseJdbcSource() {
        if (envPresent("CLOUDISLANDS_SATIS_JDBC_URL")) {
            return "CLOUDISLANDS_SATIS_JDBC_URL";
        }
        if (nonBlankConfig("setup.database.jdbc.url")) {
            return "setup.database.jdbc.url";
        }
        if (nonBlankConfig("database.jdbc.url")) {
            return "database.jdbc.url";
        }
        String backend = configuredDatabaseBackendName().toLowerCase(java.util.Locale.ROOT);
        if (nonBlankConfig("setup.database." + backend + ".jdbc-url")
                || nonBlankConfig("setup.database." + backend + ".url")
                || nonBlankConfig("setup.database." + backend + ".host")
                || nonBlankConfig("setup.database." + backend + ".name")
                || nonBlankConfig("setup.database." + backend + ".database")) {
            return "setup.database." + backend;
        }
        return "database." + backend;
    }

    private String databasePoolSource() {
        if (envPresent("CLOUDISLANDS_SATIS_DB_MAX_POOL_SIZE") || envPresent("CLOUDISLANDS_SATIS_DB_CONNECTION_TIMEOUT_MS")) {
            return "CLOUDISLANDS_SATIS_DB_MAX_POOL_SIZE/CONNECTION_TIMEOUT_MS";
        }
        String typedSection = databaseSetupSection();
        if (!typedSection.isBlank()
                && (configs.main().getInt("setup.database." + typedSection + ".max-pool-size", 0) > 0
                || configs.main().getLong("setup.database." + typedSection + ".connection-timeout-ms", 0L) > 0L)) {
            return "setup.database." + typedSection + ".pool";
        }

        if (configs.main().getInt("setup.database.jdbc.max-pool-size", 0) > 0
                || configs.main().getLong("setup.database.jdbc.connection-timeout-ms", 0L) > 0L) {
            return "setup.database.jdbc.pool";
        }
        return "database.jdbc.pool";
    }

    private String databaseFallbackSource() {
        if (envPresent("CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED") || envPresent("CLOUDISLANDS_SATIS_DB_FALLBACK_ORDER")) {
            return "CLOUDISLANDS_SATIS_DB_FALLBACK_ENABLED/ORDER";
        }
        if (configs.main().contains("setup.database.fallback.enabled")
                || !configs.main().getStringList("setup.database.fallback.order").isEmpty()) {
            return "setup.database.fallback";
        }
        return "database.fallback";
    }

    private boolean envPresent(String key) {
        String value = System.getenv(key);
        return value != null && !value.isBlank();
    }

    private boolean nonBlankConfig(String path) {
        String value = configs.main().getString(path, "");
        return value != null && !value.isBlank();
    }

    private void warnIfUnsharedDatabaseInCloudIslandsMode() {
        if (!requiresCloudIslandsApi() || databaseShared()) {
            return;
        }
        String scope = databaseScope();
        getLogger().warning("CloudIslands Satis is using an unshared SQLite database from " + databaseConfigSource()
                + " (scope=" + scope + ")"
                + ". Set database.shared-directory, database.path, or CLOUDISLANDS_SATIS_DB so A/B island nodes share factory state.");
    }

    private void warnIfAmbiguousSetupDatabase() {
        String warning = databaseSetupWarningMetadata();
        if (warning.equals("none")) {
            return;
        }
        getLogger().warning("CloudIslands Satis setup database selection is ambiguous"
                + " (sections=" + databaseSetupSectionsMetadata()
                + ", warning=" + warning
                + "). Set setup.database.type to POSTGRESQL, MYSQL, MARIADB, or CORE_API.");
    }

    private void warnIfUnsafeDatabaseFallbackChain() {
        if (databaseFallbackProductionSafe()) {
            return;
        }
        getLogger().warning("CloudIslands Satis database fallback chain is not production-safe"
                + " (risk=" + databaseFallbackRisk()
                + ", attempts=" + databaseBackendAttemptOrderMetadata()
                + ", fallbackTargets=" + databaseFallbackJdbcTargetsMetadata()
                + "). Put POSTGRESQL, MYSQL, MARIADB, or CORE_API before SQLITE for A/B island node pools.");
    }
}
