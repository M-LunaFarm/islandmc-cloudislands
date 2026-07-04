package kr.seungmin.satisskyfactory.database;

import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.economy.EconomyService;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.node.NodeGenerationService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisGameplayAcceptanceTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultBalanceSimulationKeepsOneHourFactoryLoopViable() {
        YamlConfiguration machines = load("machines.yml");
        YamlConfiguration recipes = load("recipes.yml");
        YamlConfiguration market = load("market.yml");
        YamlConfiguration contracts = load("contracts.yml");
        YamlConfiguration maintenance = load("maintenance.yml");

        long grinderCyclesPerHour = cyclesPerHour(recipes, "flour_from_wheat");
        long packagerCyclesPerHour = cyclesPerHour(recipes, "bread_box");
        long flourPerHour = grinderCyclesPerHour * recipes.getLong("recipes.flour_from_wheat.outputs.flour");
        long breadBoxesPerHour = Math.min(
                packagerCyclesPerHour * recipes.getLong("recipes.bread_box.outputs.bread_box"),
                flourPerHour / recipes.getLong("recipes.bread_box.inputs.flour")
        );
        double processingPower = machines.getDouble("machines.grinder_t1.power-consumption")
                + machines.getDouble("machines.packager_t1.power-consumption");
        double generatorPower = machines.getDouble("machines.bio_generator_t1.power-generation");
        long breadMarketValue = Math.round(
                breadBoxesPerHour
                        * market.getLong("market.items.bread_box.base-price")
                        * market.getDouble("market.factor-max")
        );
        long breadContractRequirement = contracts.getLong("contracts.templates.bread_supply.required.bread_box");
        long breadContractReward = contracts.getLong("contracts.templates.bread_supply.rewards.money");
        long maintenanceScore = machines.getLong("machines.grinder_t1.maintenance-score")
                + machines.getLong("machines.packager_t1.maintenance-score")
                + machines.getLong("machines.bio_generator_t1.maintenance-score");
        long dailyMaintenanceFee = Math.round(
                maintenance.getLong("maintenance.base-fee")
                        * Math.pow(maintenanceScore, maintenance.getDouble("maintenance.exponent"))
        );

        assertTrue(breadBoxesPerHour >= breadContractRequirement, "one-hour processing should cover bread_supply input");
        assertTrue(generatorPower > processingPower, "starter bio power should cover grinder and packager");
        assertTrue(breadMarketValue > dailyMaintenanceFee, "one-hour bread sale should exceed one maintenance charge");
        assertTrue(breadContractReward > dailyMaintenanceFee, "bread contract should cover one maintenance charge");
        assertTrue(contracts.getLong("contracts.templates.bread_supply.rewards.research-points") > 0);
    }

    @Test
    void serviceLevelHappyPathCoversIslandNodeMachineProductionSaleResearchAndMaintenance() {
        try (DatabaseHandle handle = openDatabase("satis-happy-path")) {
            DatabaseService database = handle.database();
            TrackingEconomy economy = new TrackingEconomy(false);
            StorageService storage = new StorageService(database, 2048);
            ItemRegistry items = new ItemRegistry();
            items.load(load("items.yml"));
            MachineDefinitionService definitions = new MachineDefinitionService();
            definitions.load(load("machines.yml"));
            MachineService machines = new MachineService(database, definitions, storage);
            MarketService market = new MarketService(storage, economy, database, items);
            market.load(load("market.yml"), load("maintenance.yml"));
            ContractService contracts = new ContractService(storage, economy, database, new IslandBoostService(null));
            contracts.load(load("contracts.yml"));
            ResearchService research = new ResearchService(database, economy);
            YamlConfiguration researchConfig = load("research.yml");
            researchConfig.set("research.unlocks.tier_2.cost-money", 0);
            researchConfig.set("research.unlocks.tier_2.cost-research-points", 3);
            researchConfig.set("research.unlocks.tier_2.required-reputation", 1);
            research.load(researchConfig, load("maintenance.yml"));
            MaintenanceService maintenance = new MaintenanceService(machines, economy, database);
            YamlConfiguration maintenanceConfig = load("maintenance.yml");
            maintenanceConfig.set("maintenance.new-island-free-days", 0);
            maintenance.load(maintenanceConfig);

            UUID islandUuid = UUID.fromString("00000000-0000-0000-0000-000000007001");
            UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000007002");
            FactoryIsland island = new FactoryIsland(islandUuid, ownerUuid);
            database.saveIsland(island);

            List<ResourceNode> generatedNodes = new NodeGenerationService(load("resource-nodes.yml")).generate(
                    islandUuid,
                    new BlockKey("world", 0, 64, 0),
                    location -> true,
                    1234L
            );
            generatedNodes.forEach(database::saveNode);
            assertTrue(database.loadNodes(islandUuid).stream().anyMatch(node -> node.resourceId().equals("iron_ore")));

            MachineDefinition grinder = definitions.get("grinder_t1").orElseThrow();
            MachineInstance machine = new MachineInstance(
                    UUID.fromString("00000000-0000-0000-0000-000000007003"),
                    islandUuid,
                    ownerUuid,
                    "grinder_t1",
                    grinder.tier(),
                    new BlockKey("world", 4, 64, 4)
            );
            VirtualInventory input = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_INPUT", grinder.inputCapacity());
            VirtualInventory output = storage.createMachineInventory(islandUuid, machine.machineId(), "MACHINE_OUTPUT", grinder.outputCapacity());
            machine.inputInventoryId(input.inventoryId());
            machine.outputInventoryId(output.inventoryId());
            assertTrue(machines.save(machine));

            assertTrue(input.add("wheat", 4));
            storage.save(input);
            assertTrue(input.remove("wheat", 4));
            assertTrue(output.add("flour", 1));
            storage.save(input);
            storage.save(output);
            assertTrue(output.remove("flour", 1));
            VirtualInventory islandStorage = storage.islandStorage(islandUuid);
            assertTrue(islandStorage.add("flour", 1));
            assertTrue(islandStorage.add("wheat", 128));
            storage.save(islandStorage);

            MarketService.SellResult sale = market.sell(island, null, "flour", 1).orElseThrow();
            assertTrue(sale.gross() > 0);
            assertEquals(1, database.marketPersonalSold(islandUuid, "flour", java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()));

            ContractService.ActiveContract wheatContract = contracts.activeContracts(island).stream()
                    .filter(contract -> contract.template().id().equals("beginner_wheat"))
                    .findFirst()
                    .orElseThrow();
            assertTrue(contracts.completeContract(island, null, wheatContract.contractId()).isPresent());
            assertEquals(3, island.researchPoints());
            assertEquals(1, island.reputation());

            assertEquals(ResearchService.UnlockResult.UNLOCKED, research.unlock(island, "tier_2"));
            assertEquals(2, island.tier());
            assertTrue(database.loadUnlocks(islandUuid).contains("harvester_t2"));

            long due = maintenance.chargeNow(island, null, null);
            assertTrue(due > 0);
            assertTrue(island.maintenanceDebt() > 0);
            assertFalse(island.maintenanceStatus() == MaintenanceStatus.NORMAL);
        }
    }

    private long cyclesPerHour(YamlConfiguration config, String recipeId) {
        return 60L * 60L * 1000L / config.getLong("recipes." + recipeId + ".cycle-ms");
    }

    private DatabaseHandle openDatabase(String name) {
        DatabaseService database = new DatabaseService(tempDir.resolve(name).toFile());
        database.open();
        return new DatabaseHandle(database);
    }

    private YamlConfiguration load(String name) {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources", name));
    }

    private record DatabaseHandle(DatabaseService database) implements AutoCloseable {
        @Override
        public void close() {
            database.close();
        }
    }

    private static final class TrackingEconomy implements EconomyService {
        private final boolean maintenanceCanPay;
        private double deposited;

        private TrackingEconomy(boolean maintenanceCanPay) {
            this.maintenanceCanPay = maintenanceCanPay;
        }

        @Override
        public boolean deposit(OfflinePlayer player, double amount) {
            deposited += amount;
            return true;
        }

        @Override
        public boolean withdraw(OfflinePlayer player, double amount) {
            return maintenanceCanPay;
        }

        @Override
        public double balance(OfflinePlayer player) {
            return deposited;
        }

        @Override
        public String name() {
            return "Acceptance";
        }
    }
}
