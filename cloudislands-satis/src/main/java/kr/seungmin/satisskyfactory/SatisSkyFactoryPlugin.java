package kr.seungmin.satisskyfactory;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddonBootstrap;
import kr.lunaf.cloudislands.api.event.IslandActivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandCreatedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivateEvent;
import kr.lunaf.cloudislands.api.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.api.event.IslandLevelRecalculateEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberChangedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigratedEvent;
import kr.lunaf.cloudislands.api.event.IslandPermissionChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotCreateEvent;
import kr.lunaf.cloudislands.api.event.IslandWorthChangeEvent;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import kr.seungmin.satisskyfactory.command.FactoryCommand;
import kr.seungmin.satisskyfactory.config.ConfigService;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyModeFactory;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.CloudIslandsSkyblockProvider;
import kr.seungmin.satisskyfactory.hook.PlaceholderHook;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.hook.SuperiorSkyblockHook;
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
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.recipe.RecipeService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.task.DirtySaveService;
import kr.seungmin.satisskyfactory.task.MachineTickService;
import kr.seungmin.satisskyfactory.task.MaintenanceTickService;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SatisSkyFactoryPlugin extends JavaPlugin implements CloudIslandsAddon {
    private static final String ADDON_ID = "cloudislands-satis";
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
    private MachineTickService ticker;
    private MaintenanceTickService maintenanceTicker;
    private PlaceholderHook placeholderHook;
    private CloudIslandsApi cloudIslandsApi;
    private boolean addonRuntimeEnabled;
    private Map<String, Boolean> effectiveFeatures = Map.of();

    @Override
    public void onEnable() {
        configs = new ConfigService(this);
        configs.load();
        addonRuntimeEnabled = false;
        effectiveFeatures = Map.of();
        if (!registerCloudIslandsAddon()) {
            return;
        }
        startRuntime();
    }

    private void startRuntime() {
        if (database != null) {
            applyAddonRuntimeState();
            return;
        }
        messages = new MessageService(configs);

        skyblock = createSkyblockProvider();
        if (!skyblock.enable()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        configureSkyblockHook();

        database = new DatabaseService(this, configs.main().getString("database.sqlite-file", "data.db"));
        database.open();

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
        storage.dirtySaves(dirtySaves);
        islands.dirtySaves(dirtySaves);
        machines.dirtySaves(dirtySaves);
        nodes.dirtySaves(dirtySaves);
        itemNetworks = new ItemNetworkService(database, machines, machineDefinitions);
        power = new PowerNetworkService(database, machines, machineDefinitions, recipes, storage);
        market = new MarketService(storage, economy, database, itemRegistry);
        contracts = new ContractService(storage, economy, database, boosts);
        maintenance = new MaintenanceService(machines, economy, database);
        research = new ResearchService(database, economy);
        gui = new FactoryGuiService(storage, itemRegistry, machineDefinitions, recipes, islands, research, economy, messages);

        loadDefinitions();
        islands.load();
        machines.load();
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
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
        }
        if (dirtySaves != null) {
            dirtySaves.stop();
        }
        if (placeholderHook != null) {
            placeholderHook.unregister();
        }
        unregisterCloudIslandsAddon();
        if (database != null) {
            database.close();
        }
    }

    public void reloadPluginConfig() {
        configs.load();
        effectiveFeatures = Map.of();
        if (!registerCloudIslandsAddon()) {
            stopRuntimeActivity();
            return;
        }
        configureSkyblockHook();
        boosts.configure(configs.main());
        loadDefinitions();
        if (featureEnabled("machines")) {
            rebuildNetworks();
        }
        restartRuntimeTasks();
    }

    private void restartRuntimeTasks() {
        if (ticker != null) {
            ticker.stop();
        }
        if (maintenanceTicker != null) {
            maintenanceTicker.stop();
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
                    activeParticleLimit(configs.main(), configInt("settings.max-machines-per-tick", "settings.max-machines-per-cycle", 300))
            );
            ticker.start(configLong("settings.tick-period-ticks", "settings.tick-interval", 20));
        }
        maintenanceTicker = new MaintenanceTickService(this, islands, skyblock, maintenance);
        if (featureEnabled("maintenance")) {
            maintenanceTicker.start(configLong("settings.maintenance-check-period-ticks", "settings.maintenance-check-interval", 1200));
        }
        dirtySaves.start(dirtySavePeriodTicks(configs.main()));
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
        boolean allowSpawnIsland = configBoolean("superior-skyblock.allow-spawn-island", "settings.allow-spawn-island", false);
        skyblock.configure(
                configBoolean("superior-skyblock.allow-coop-build", "settings.allow-coop-build", false),
                !allowSpawnIsland && configBoolean("superior-skyblock.protect-spawn-island", "settings.protect-spawn-island", true),
                configBoolean("superior-skyblock.require-island-member", "settings.require-island-member", true)
        );
    }

    private SkyblockProvider createSkyblockProvider() {
        String provider = configs.main().getString("integration.skyblock-provider", "SUPERIOR_SKYBLOCK2");
        if ("CLOUDISLANDS".equalsIgnoreCase(provider) || "CLOUD_ISLANDS".equalsIgnoreCase(provider)) {
            return new CloudIslandsSkyblockProvider(this);
        }
        return new SuperiorSkyblockHook(this);
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
        itemRegistry.load(configs.file("items.yml"));
        machineDefinitions.load(configs.file("machines.yml"));
        recipes.load(configs.file("recipes.yml"));
        if (featureEnabled("resource-nodes")) {
            nodes.load(configs.file("resource-nodes.yml"));
        }
        if (featureEnabled("market")) {
            market.load(configs.file("market.yml"), configs.file("maintenance.yml"));
        }
        if (featureEnabled("contracts")) {
            contracts.load(configs.file("contracts.yml"));
        }
        if (featureEnabled("maintenance")) {
            maintenance.load(configs.file("maintenance.yml"));
        }
        if (featureEnabled("research")) {
            research.load(configs.file("research.yml"), configs.file("maintenance.yml"));
        }
    }

    private void registerCommands() {
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
                this::featureEnabled,
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
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new MachineListener(
                () -> featureEnabled("machines"),
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
        ), this);
        getServer().getPluginManager().registerEvents(new FactoryGuiListener(
                this::featureEnabled,
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
        ), this);
        getServer().getPluginManager().registerEvents(new FactoryLifecycleListener(
                () -> featureEnabled("lifecycle"),
                islands,
                skyblock,
                nodes,
                machines,
                itemNetworks,
                power,
                maintenance
        ), this);
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
        placeholderHook = new PlaceholderHook(this, islands, machines, storage, power, boosts, research, contracts);
        placeholderHook.register();
        getLogger().info("Registered PlaceholderAPI expansion: satisskyfactory");
    }

    private void refreshPlaceholders() {
        if (!featureEnabled("placeholders")) {
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

    private boolean registerCloudIslandsAddon() {
        cloudIslandsApi = resolveCloudIslandsApi();
        if (cloudIslandsApi == null) {
            if (requiresCloudIslandsApi()) {
                getLogger().severe("CloudIslands API is required for the configured Satis integration mode.");
                addonRuntimeEnabled = false;
                return false;
            }
            addonRuntimeEnabled = true;
            return true;
        }
        CloudIslandsAddonSnapshot addon = register(cloudIslandsApi).join();
        getLogger().info("Registered CloudIslands addon: " + addon.id() + " enabled=" + addon.enabled());
        if (!addon.enabled()) {
            getLogger().info("CloudIslands disabled this addon through the parent config.");
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
        String provider = configs.main().getString("integration.skyblock-provider", "CLOUDISLANDS");
        return "CLOUDISLANDS".equalsIgnoreCase(provider)
                || "CLOUD_ISLANDS".equalsIgnoreCase(provider)
                || configs.main().getBoolean("integration.cloudislands-adapter", true);
    }

    private void unregisterCloudIslandsAddon() {
        if (cloudIslandsApi == null) {
            return;
        }
        unregister(cloudIslandsApi);
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
        return configs.main().getBoolean("integration.enabled", false);
    }

    @Override
    public Map<String, Boolean> addonFeatures() {
        return featureSnapshot();
    }

    @Override
    public Map<String, String> addonMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("mode", configs.main().getString("integration.mode", "ADDON"));
        metadata.put("skyblock-provider", configs.main().getString("integration.skyblock-provider", "CLOUDISLANDS"));
        metadata.put("cloudislands-adapter", Boolean.toString(configs.main().getBoolean("integration.cloudislands-adapter", true)));
        return metadata;
    }

    @Override
    public void onAddonRegistered(CloudIslandsAddonSnapshot snapshot) {
        addonRuntimeEnabled = snapshot.enabled();
        effectiveFeatures = snapshot.features();
    }

    @Override
    public void onAddonReloaded(CloudIslandsAddonSnapshot snapshot) {
        addonRuntimeEnabled = snapshot.enabled();
        effectiveFeatures = snapshot.features();
        getLogger().info("Reloaded CloudIslands addon config: " + snapshot.id() + " enabled=" + snapshot.enabled());
        if (!snapshot.enabled()) {
            getLogger().info("CloudIslands disabled this addon during config reload.");
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
    public void onAddonUnregistered() {
        addonRuntimeEnabled = false;
        effectiveFeatures = Map.of();
    }

    @Override
    public void onIslandCreated(IslandCreatedEvent event) {
        runSatisLifecycle(event.islandId(), () -> {
            islands.getOrCreate(new kr.seungmin.satisskyfactory.hook.IslandRef(null, event.islandId(), event.ownerUuid()));
            storage.islandStorage(event.islandId());
            synchronizeSatisIsland(event.islandId());
        });
    }

    @Override
    public void onIslandActivated(IslandActivatedEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandDeactivated(IslandDeactivateEvent event) {
        runSatisLifecycle(event.islandId(), () -> flushSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandMigrated(IslandMigratedEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandDeleted(IslandDeletedEvent event) {
        runSatisLifecycle(event.islandId(), () -> flushSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandMemberChanged(IslandMemberChangedEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandPermissionChanged(IslandPermissionChangeEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandLevelUpdated(IslandLevelRecalculateEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandWorthChanged(IslandWorthChangeEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandSnapshotCreated(IslandSnapshotCreateEvent event) {
        runSatisLifecycle(event.islandId(), () -> flushSatisIsland(event.islandId()));
    }

    private void runSatisLifecycle(UUID islandId, Runnable action) {
        if (islandId == null || database == null || !featureEnabled("lifecycle")) {
            return;
        }
        getServer().getScheduler().runTask(this, () -> {
            if (!isEnabled() || database == null || !featureEnabled("lifecycle")) {
                return;
            }
            try {
                action.run();
            } catch (RuntimeException exception) {
                getLogger().warning("CloudIslands lifecycle sync failed for " + islandId + ": " + exception.getMessage());
            }
        });
    }

    private void synchronizeSatisIsland(UUID islandId) {
        if (islands == null) {
            return;
        }
        islands.find(islandId).ifPresent(island -> {
            if (maintenance != null && featureEnabled("maintenance")) {
                maintenance.updateStatus(island);
            }
            if (machines != null && itemNetworks != null && power != null && featureEnabled("machines")) {
                itemNetworks.rebuildIsland(islandId);
                power.rebuildIsland(islandId);
            }
            islands.save(island);
        });
    }

    private void flushSatisIsland(UUID islandId) {
        if (dirtySaves != null) {
            dirtySaves.flushSafely();
        }
        synchronizeSatisIsland(islandId);
    }

    private void stopRuntimeActivity() {
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
    }

    private void applyAddonRuntimeState() {
        if (database == null || configs == null) {
            return;
        }
        loadDefinitions();
        if (featureEnabled("machines")) {
            rebuildNetworks();
        }
        restartRuntimeTasks();
        refreshPlaceholders();
    }

    private Map<String, Boolean> featureSnapshot() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("commands", configuredFeatureEnabled("commands"));
        features.put("machines", configuredFeatureEnabled("machines"));
        features.put("gui", configuredFeatureEnabled("gui"));
        features.put("lifecycle", configuredFeatureEnabled("lifecycle"));
        features.put("resource-nodes", configuredFeatureEnabled("resource-nodes"));
        features.put("market", configuredFeatureEnabled("market"));
        features.put("contracts", configuredFeatureEnabled("contracts"));
        features.put("research", configuredFeatureEnabled("research"));
        features.put("maintenance", configuredFeatureEnabled("maintenance"));
        features.put("placeholders", configuredFeatureEnabled("placeholders"));
        return features;
    }

    private boolean configuredFeatureEnabled(String key) {
        return configs.main().getBoolean("features." + key, true);
    }

    private boolean featureEnabled(String key) {
        if (!addonRuntimeEnabled) {
            return false;
        }
        Boolean effective = effectiveFeatures.get(key);
        if (effective != null) {
            return effective;
        }
        return configuredFeatureEnabled(key);
    }
}
