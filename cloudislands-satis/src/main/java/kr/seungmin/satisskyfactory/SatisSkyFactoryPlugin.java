package kr.seungmin.satisskyfactory;

import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddonBootstrap;
import kr.lunaf.cloudislands.api.event.IslandActivatedEvent;
import kr.lunaf.cloudislands.api.event.IslandCreatedEvent;
import kr.lunaf.cloudislands.api.event.IslandDeactivateEvent;
import kr.lunaf.cloudislands.api.event.IslandDeletedEvent;
import kr.lunaf.cloudislands.api.event.IslandLevelRecalculateEvent;
import kr.lunaf.cloudislands.api.event.IslandLimitChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandMemberChangedEvent;
import kr.lunaf.cloudislands.api.event.IslandMigratedEvent;
import kr.lunaf.cloudislands.api.event.IslandPermissionChangeEvent;
import kr.lunaf.cloudislands.api.event.IslandSnapshotCreateEvent;
import kr.lunaf.cloudislands.api.event.IslandUpgradeEvent;
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
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private MachineListener machineListener;
    private FactoryGuiListener guiListener;
    private FactoryLifecycleListener lifecycleListener;
    private Map<String, Boolean> effectiveFeatures = Map.of();

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

        database = new DatabaseService(this, resolveDatabaseFileName());
        database.open();
        warnIfUnsharedDatabaseInCloudIslandsMode();
        getLogger().info("Satis database path: " + database.databasePath().getAbsolutePath());

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
        market = new MarketService(storage, economy, database, itemRegistry, () -> featureEnabled("maintenance"));
        contracts = new ContractService(storage, economy, database, boosts, () -> featureEnabled("maintenance"));
        maintenance = new MaintenanceService(machines, economy, database);
        research = new ResearchService(database, economy, () -> featureEnabled("maintenance"));
        gui = new FactoryGuiService(storage, itemRegistry, machineDefinitions, recipes, islands, research, economy, messages, this::featureEnabled);

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
                    activeParticleLimit(configs.main(), configInt("settings.max-machines-per-tick", "settings.max-machines-per-cycle", 300)),
                    () -> featureEnabled("maintenance"),
                    () -> featureEnabled("resource-nodes")
            );
            ticker.start(configLong("settings.tick-period-ticks", "settings.tick-interval", 20));
        }
        maintenanceTicker = new MaintenanceTickService(this, islands, skyblock, maintenance, () -> featureEnabled("maintenance"));
        if (featureEnabled("maintenance")) {
            maintenanceTicker.start(configLong("settings.maintenance-check-period-ticks", "settings.maintenance-check-interval", 1200));
        }
        if (dataWritesEnabled()) {
            dirtySaves.start(dirtySavePeriodTicks(configs.main()));
        }
    }

    private boolean dataWritesEnabled() {
        return featureEnabled("machines")
                || featureEnabled("resource-nodes")
                || featureEnabled("market")
                || featureEnabled("contracts")
                || featureEnabled("research")
                || featureEnabled("maintenance")
                || (featureEnabled("lifecycle") && lifecycleStateEnabled());
    }

    private boolean lifecycleStateEnabled() {
        return featureEnabled("machines")
                || featureEnabled("resource-nodes")
                || featureEnabled("market")
                || featureEnabled("contracts")
                || featureEnabled("research")
                || featureEnabled("maintenance");
    }

    private boolean storageDataEnabled() {
        return featureEnabled("machines")
                || featureEnabled("market")
                || featureEnabled("contracts");
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
        String provider = configs.main().getString("integration.skyblock-provider", "CLOUDISLANDS");
        if (!"CLOUDISLANDS".equalsIgnoreCase(provider) && !"CLOUD_ISLANDS".equalsIgnoreCase(provider)) {
            getLogger().warning("Ignoring legacy skyblock provider '" + provider + "'. CloudIslands Satis uses the CloudIslands API provider.");
        }
        return new CloudIslandsSkyblockProvider(this);
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
        if (featureEnabled("resource-nodes")) {
            nodes.load(configs.file("resource-nodes.yml"));
        } else {
            nodes.clear();
            if (dirtySaves != null) {
                dirtySaves.forgetNodes();
            }
        }
        if (featureEnabled("market")) {
            market.load(configs.file("market.yml"), configs.file("maintenance.yml"));
        } else {
            market.clear();
        }
        if (featureEnabled("contracts")) {
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
                || featureEnabled("market")
                || featureEnabled("contracts")
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
                this::featureEnabled,
                this::addonMetadata,
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
                    () -> featureEnabled("resource-nodes"),
                    () -> featureEnabled("maintenance"),
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
            );
            getServer().getPluginManager().registerEvents(guiListener, this);
            guiListenerRegistered = true;
        }
        if (!featureEnabled("lifecycle") || !lifecycleStateEnabled()) {
            lifecycleListenerRegistered = unregisterListener(lifecycleListener, lifecycleListenerRegistered);
            lifecycleListener = null;
        } else if (!lifecycleListenerRegistered) {
            lifecycleListener = new FactoryLifecycleListener(
                    () -> featureEnabled("lifecycle") && lifecycleStateEnabled(),
                    () -> featureEnabled("resource-nodes"),
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
        placeholderHook = new PlaceholderHook(this, islands, machines, storage, power, boosts, research, contracts, this::featureEnabled);
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
            getLogger().info("Registered CloudIslands addon: " + addon.id() + " enabled=" + addon.enabled());
            if (!addon.enabled()) {
                getLogger().info("CloudIslands disabled this addon through the parent config.");
                return false;
            }
        } catch (RuntimeException exception) {
            getLogger().warning("Failed to register CloudIslands Satis addon: " + exception.getMessage());
            addonRuntimeEnabled = false;
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
        return configs.main().contains("satis.enabled")
                ? configs.main().getBoolean("satis.enabled", true)
                : configs.main().getBoolean("integration.enabled", true);
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
        metadata.put("skyblock-provider", "CLOUDISLANDS");
        metadata.put("cloudislands-adapter", Boolean.toString(configs.main().getBoolean("integration.cloudislands-adapter", true)));
        metadata.put("requires-cloudislands-api", Boolean.toString(requiresCloudIslandsApi()));
        metadata.put("database-scope", scope);
        metadata.put("database-config-source", databaseConfigSource());
        metadata.put("database-file", configuredDatabaseFileName());
        metadata.put("database-path", resolveDatabaseFileName());
        metadata.put("database-shared", Boolean.toString(!scope.equals("PLUGIN_LOCAL") && !scope.equals("PLUGIN_RELATIVE_PATH")));
        metadata.put("feature-aliases", featureAliasesMetadata());
        metadata.put("feature-warnings", featureWarnings());
        return metadata;
    }

    private String featureAliasesMetadata() {
        return FEATURE_ALIASES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
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
        stopRuntimeActivity();
    }

    @Override
    public void onIslandCreated(IslandCreatedEvent event) {
        runSatisLifecycle(event.islandId(), () -> {
            islands.getOrCreate(new kr.seungmin.satisskyfactory.hook.IslandRef(null, event.islandId(), event.ownerUuid()));
            if (storageDataEnabled()) {
                storage.islandStorage(event.islandId());
            }
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
        runSatisLifecycle(event.islandId(), () -> purgeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandDeleteRequested(kr.lunaf.cloudislands.api.event.IslandDeleteRequestEvent event) {
        runSatisLifecycle(event.islandId(), () -> flushSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandRestoreRequested(kr.lunaf.cloudislands.api.event.IslandRestoreRequestEvent event) {
        runSatisLifecycle(event.islandId(), () -> flushSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandRestored(kr.lunaf.cloudislands.api.event.IslandRestoredEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandReset(kr.lunaf.cloudislands.api.event.IslandResetEvent event) {
        runSatisLifecycle(event.islandId(), () -> {
            if (event.requested()) {
                flushSatisIsland(event.islandId());
            } else {
                purgeSatisIsland(event.islandId());
            }
        });
    }

    @Override
    public void onIslandRecoveryRequired(kr.lunaf.cloudislands.api.event.IslandRecoveryRequiredEvent event) {
        runSatisLifecycle(event.islandId(), () -> flushSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandRepaired(kr.lunaf.cloudislands.api.event.IslandRepairedEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
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
    public void onIslandUpgradeChanged(IslandUpgradeEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandLimitChanged(IslandLimitChangeEvent event) {
        runSatisLifecycle(event.islandId(), () -> synchronizeSatisIsland(event.islandId()));
    }

    @Override
    public void onIslandSnapshotCreated(IslandSnapshotCreateEvent event) {
        runSatisLifecycle(event.islandId(), () -> flushSatisIsland(event.islandId()));
    }

    private void runSatisLifecycle(UUID islandId, Runnable action) {
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
                getLogger().warning("CloudIslands lifecycle sync failed for " + islandId + ": " + exception.getMessage());
            }
        });
    }

    private void synchronizeSatisIsland(UUID islandId) {
        if (islands == null) {
            return;
        }
        islands.find(islandId).ifPresent(island -> {
            String activeWorld = activeIslandWorld(islandId);
            if (machines != null && activeWorld != null && featureEnabled("machines")) {
                machines.remapIslandWorld(islandId, activeWorld);
            }
            if (nodes != null && activeWorld != null && featureEnabled("resource-nodes")) {
                nodes.remapIslandWorld(islandId, activeWorld);
            }
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

    private String activeIslandWorld(UUID islandId) {
        if (skyblock == null) {
            return null;
        }
        return skyblock.getIslandByUuid(islandId)
                .flatMap(skyblock::getIslandCenter)
                .map(location -> location.getWorld() == null ? null : location.getWorld().getName())
                .orElse(null);
    }

    private void flushSatisIsland(UUID islandId) {
        synchronizeSatisIsland(islandId);
        if (dirtySaves != null) {
            dirtySaves.flushIslandSafely(islandId);
        }
    }

    private void purgeSatisIsland(UUID islandId) {
        if (islands != null) {
            islands.forget(islandId);
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
        if (dirtySaves != null) {
            dirtySaves.forgetIsland(islandId);
        }
        database.purgeIsland(islandId);
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
        }
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
        features.put("gui", configuredFeatureEnabled("gui"));
        features.put("lifecycle", configuredFeatureEnabled("lifecycle"));
        features.put("resource-nodes", configuredFeatureEnabled("resource-nodes"));
        features.put("market", configuredFeatureEnabled("market"));
        features.put("contracts", configuredFeatureEnabled("contracts"));
        features.put("research", configuredFeatureEnabled("research"));
        features.put("maintenance", configuredFeatureEnabled("maintenance"));
        features.put("placeholders", configuredFeatureEnabled("placeholders"));
        FEATURE_ALIASES.forEach((alias, canonical) -> features.put(alias, features.getOrDefault(canonical, configuredFeatureEnabled(canonical))));
        return features;
    }

    private String featureWarnings() {
        Map<String, Boolean> features = featureSnapshot();
        List<String> warnings = new ArrayList<>();
        if (Boolean.TRUE.equals(features.get("gui"))
                && !Boolean.TRUE.equals(features.get("machines"))
                && !Boolean.TRUE.equals(features.get("market"))
                && !Boolean.TRUE.equals(features.get("contracts"))
                && !Boolean.TRUE.equals(features.get("research"))) {
            warnings.add("gui-without-panels");
        }
        if (Boolean.TRUE.equals(features.get("market")) && !Boolean.TRUE.equals(features.get("machines"))) {
            warnings.add("market-without-storage-commands");
        }
        if (Boolean.TRUE.equals(features.get("resource-nodes")) && !Boolean.TRUE.equals(features.get("machines"))) {
            warnings.add("resource-nodes-without-machines");
        }
        if (Boolean.TRUE.equals(features.get("contracts")) && !Boolean.TRUE.equals(features.get("maintenance"))) {
            warnings.add("contracts-without-maintenance-status");
        }
        if (Boolean.TRUE.equals(features.get("placeholders")) && !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            warnings.add("placeholderapi-not-installed");
        }
        if (Boolean.TRUE.equals(features.get("placeholders"))
                && !Boolean.TRUE.equals(features.get("machines"))
                && !Boolean.TRUE.equals(features.get("contracts"))
                && !Boolean.TRUE.equals(features.get("research"))
                && !Boolean.TRUE.equals(features.get("maintenance"))) {
            warnings.add("placeholders-without-data-features");
        }
        if (Boolean.TRUE.equals(features.get("lifecycle"))
                && !Boolean.TRUE.equals(features.get("machines"))
                && !Boolean.TRUE.equals(features.get("resource-nodes"))
                && !Boolean.TRUE.equals(features.get("market"))
                && !Boolean.TRUE.equals(features.get("contracts"))
                && !Boolean.TRUE.equals(features.get("research"))
                && !Boolean.TRUE.equals(features.get("maintenance"))) {
            warnings.add("lifecycle-without-satis-state");
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
        String canonical = canonicalFeature(key);
        boolean enabled = configFeature(canonical);
        for (Map.Entry<String, String> alias : FEATURE_ALIASES.entrySet()) {
            if (alias.getValue().equals(canonical) && configFeatureDefined(alias.getKey())) {
                enabled = enabled && configFeature(alias.getKey());
            }
        }
        return enabled;
    }

    private boolean configFeature(String key) {
        String satisPath = "satis.features." + key;
        String legacyPath = "features." + key;
        boolean enabled = true;
        if (configs.main().contains(satisPath)) {
            enabled = configs.main().getBoolean(satisPath, true);
        }
        if (configs.main().contains(legacyPath)) {
            enabled = enabled && configs.main().getBoolean(legacyPath, true);
        }
        return enabled;
    }

    private boolean configFeatureDefined(String key) {
        return configs.main().contains("satis.features." + key) || configs.main().contains("features." + key);
    }

    private boolean featureEnabled(String key) {
        if (!addonRuntimeEnabled) {
            return false;
        }
        String canonical = canonicalFeature(key);
        boolean enabled = effectiveFeatures.getOrDefault(canonical, configuredFeatureEnabled(canonical));
        for (Map.Entry<String, String> alias : FEATURE_ALIASES.entrySet()) {
            if (alias.getValue().equals(canonical)) {
                Boolean effective = effectiveFeatures.get(alias.getKey());
                if (effective != null) {
                    enabled = enabled && effective;
                }
            }
        }
        return enabled;
    }

    private String canonicalFeature(String key) {
        return FEATURE_ALIASES.getOrDefault(key, key);
    }

    private String resolveDatabaseFileName() {
        String envPath = System.getenv("CLOUDISLANDS_SATIS_DB");
        if (envPath != null && !envPath.isBlank()) {
            return envPath.trim();
        }
        String configuredPath = configs.main().getString("database.path", "");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return configuredPath.trim();
        }
        String sqliteFile = configuredDatabaseFileName();
        String sharedDirectory = configs.main().getString("database.shared-directory", "");
        if (sharedDirectory != null && !sharedDirectory.isBlank()) {
            return new File(sharedDirectory.trim(), sqliteFile).getPath();
        }
        return sqliteFile;
    }

    private String configuredDatabaseFileName() {
        String sqliteFile = configs.main().getString("database.sqlite-file", "data.db");
        return sqliteFile == null || sqliteFile.isBlank() ? "data.db" : sqliteFile.trim();
    }

    private String databaseScope() {
        String envPath = System.getenv("CLOUDISLANDS_SATIS_DB");
        if (envPath != null && !envPath.isBlank()) {
            return "ENV_SHARED";
        }
        String configuredPath = configs.main().getString("database.path", "");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return new File(configuredPath).isAbsolute() ? "ABSOLUTE_PATH" : "PLUGIN_RELATIVE_PATH";
        }
        String sharedDirectory = configs.main().getString("database.shared-directory", "");
        if (sharedDirectory != null && !sharedDirectory.isBlank()) {
            return "SHARED_DIRECTORY";
        }
        return "PLUGIN_LOCAL";
    }

    private String databaseConfigSource() {
        String envPath = System.getenv("CLOUDISLANDS_SATIS_DB");
        if (envPath != null && !envPath.isBlank()) {
            return "CLOUDISLANDS_SATIS_DB";
        }
        String configuredPath = configs.main().getString("database.path", "");
        if (configuredPath != null && !configuredPath.isBlank()) {
            return "database.path";
        }
        String sharedDirectory = configs.main().getString("database.shared-directory", "");
        if (sharedDirectory != null && !sharedDirectory.isBlank()) {
            return "database.shared-directory";
        }
        return "database.sqlite-file";
    }

    private void warnIfUnsharedDatabaseInCloudIslandsMode() {
        String scope = databaseScope();
        boolean shared = !scope.equals("PLUGIN_LOCAL") && !scope.equals("PLUGIN_RELATIVE_PATH");
        if (!requiresCloudIslandsApi() || shared) {
            return;
        }
        getLogger().warning("CloudIslands Satis is using an unshared SQLite database from " + databaseConfigSource()
                + " (scope=" + scope + ")"
                + ". Set database.shared-directory, database.path, or CLOUDISLANDS_SATIS_DB so A/B island nodes share factory state.");
    }
}
