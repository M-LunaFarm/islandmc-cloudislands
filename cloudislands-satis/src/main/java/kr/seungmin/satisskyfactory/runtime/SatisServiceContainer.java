package kr.seungmin.satisskyfactory.runtime;

import kr.seungmin.satisskyfactory.config.ConfigService;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.economy.EconomyModeFactory;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
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
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.function.Predicate;

public record SatisServiceContainer(
        EconomyService economy,
        ItemRegistry itemRegistry,
        CustomItemFactory itemFactory,
        MachineDefinitionService machineDefinitions,
        RecipeService recipes,
        StorageService storage,
        FactoryIslandService islands,
        MachineService machines,
        IslandBoostService boosts,
        ResourceNodeService nodes,
        DirtySaveService dirtySaves,
        ItemNetworkService itemNetworks,
        PowerNetworkService power,
        MarketService market,
        ContractService contracts,
        MaintenanceService maintenance,
        ResearchService research,
        FactoryGuiService gui
) {
    public static SatisServiceContainer create(
            JavaPlugin plugin,
            ConfigService configs,
            MessageService messages,
            DatabaseService database,
            SkyblockProvider skyblock,
            int storageCapacity,
            Predicate<String> featureEnabled
    ) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(configs, "configs");
        EconomyService economy = EconomyModeFactory.create(plugin, configs.main());
        ItemRegistry itemRegistry = new ItemRegistry();
        MachineDefinitionService machineDefinitions = new MachineDefinitionService();
        CustomItemFactory itemFactory = new CustomItemFactory(plugin, machineDefinitions);
        RecipeService recipes = new RecipeService();
        IslandBoostService boosts = new IslandBoostService(skyblock);
        boosts.configure(configs.main());
        return assemble(
                messages,
                database,
                skyblock,
                storageCapacity,
                featureEnabled,
                economy,
                itemRegistry,
                itemFactory,
                machineDefinitions,
                recipes,
                boosts,
                new DirtySaveService(plugin, database)
        );
    }

    public SatisServiceContainer rebindDatabase(
            JavaPlugin plugin,
            ConfigService configs,
            MessageService messages,
            DatabaseService database,
            SkyblockProvider skyblock,
            int storageCapacity,
            Predicate<String> featureEnabled
    ) {
        boosts.configure(configs.main());
        return assemble(
                messages,
                database,
                skyblock,
                storageCapacity,
                featureEnabled,
                economy,
                itemRegistry,
                itemFactory,
                machineDefinitions,
                recipes,
                boosts,
                new DirtySaveService(plugin, database)
        );
    }

    private static SatisServiceContainer assemble(
            MessageService messages,
            DatabaseService database,
            SkyblockProvider skyblock,
            int storageCapacity,
            Predicate<String> featureEnabled,
            EconomyService economy,
            ItemRegistry itemRegistry,
            CustomItemFactory itemFactory,
            MachineDefinitionService machineDefinitions,
            RecipeService recipes,
            IslandBoostService boosts,
            DirtySaveService dirtySaves
    ) {
        Predicate<String> enabled = featureEnabled == null ? feature -> false : featureEnabled;
        StorageService storage = new StorageService(database, storageCapacity);
        FactoryIslandService islands = new FactoryIslandService(skyblock, database);
        MachineService machines = new MachineService(database, machineDefinitions, storage);
        ResourceNodeService nodes = new ResourceNodeService(database);
        storage.dirtySaves(dirtySaves);
        islands.dirtySaves(dirtySaves);
        machines.dirtySaves(dirtySaves);
        nodes.dirtySaves(dirtySaves);
        ItemNetworkService itemNetworks = new ItemNetworkService(database, machines, machineDefinitions);
        PowerNetworkService power = new PowerNetworkService(database, machines, machineDefinitions, recipes, storage);
        MarketService market = new MarketService(storage, economy, database, itemRegistry, () -> enabled.test("maintenance"), islands::save);
        ContractService contracts = new ContractService(storage, economy, database, boosts, () -> enabled.test("maintenance"), islands::save);
        MaintenanceService maintenance = new MaintenanceService(machines, economy, database);
        ResearchService research = new ResearchService(database, economy, () -> enabled.test("maintenance"), islands::save);
        FactoryGuiService gui = new FactoryGuiService(storage, itemRegistry, machineDefinitions, recipes, islands, research, economy, messages, enabled::test);
        return new SatisServiceContainer(
                economy,
                itemRegistry,
                itemFactory,
                machineDefinitions,
                recipes,
                storage,
                islands,
                machines,
                boosts,
                nodes,
                dirtySaves,
                itemNetworks,
                power,
                market,
                contracts,
                maintenance,
                research,
                gui
        );
    }
}
