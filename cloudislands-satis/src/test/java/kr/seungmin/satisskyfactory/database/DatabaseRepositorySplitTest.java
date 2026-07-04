package kr.seungmin.satisskyfactory.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseRepositorySplitTest {
    @Test
    void factoryIslandSqlLivesInFactoryIslandRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/FactoryIslandRepository.java"));

        assertTrue(databaseService.contains("private final FactoryIslandRepository factoryIslandRepository"));
        assertTrue(databaseService.contains("return factoryIslandRepository.find(islandUuid);"));
        assertTrue(databaseService.contains("factoryIslandRepository.save(island);"));
        assertFalse(databaseService.contains("INSERT INTO factory_islands"));
        assertFalse(databaseService.contains("SELECT * FROM factory_islands WHERE island_uuid = ?"));

        assertTrue(repository.contains("INSERT INTO factory_islands"));
        assertTrue(repository.contains("SELECT * FROM factory_islands WHERE island_uuid = ?"));
        assertTrue(repository.contains("SELECT * FROM factory_islands"));
    }

    @Test
    void virtualInventorySqlLivesInVirtualInventoryRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/VirtualInventoryRepository.java"));

        assertTrue(databaseService.contains("private final VirtualInventoryRepository virtualInventoryRepository"));
        assertTrue(databaseService.contains("virtualInventoryRepository.save(inventory);"));
        assertTrue(databaseService.contains("return virtualInventoryRepository.load(inventoryId);"));
        assertTrue(databaseService.contains("return virtualInventoryRepository.findByHolder(islandUuid, holderType, holderId);"));
        assertTrue(databaseService.contains("virtualInventoryRepository.delete(inventoryId);"));
        assertFalse(databaseService.contains("INSERT INTO virtual_inventories"));
        assertFalse(databaseService.contains("SELECT * FROM virtual_inventories WHERE inventory_id = ?"));
        assertFalse(databaseService.contains("INSERT INTO virtual_inventory_items"));
        assertFalse(databaseService.contains("SELECT item_id, amount FROM virtual_inventory_items WHERE inventory_id = ?"));

        assertTrue(repository.contains("INSERT INTO virtual_inventories"));
        assertTrue(repository.contains("SELECT * FROM virtual_inventories WHERE inventory_id = ?"));
        assertTrue(repository.contains("INSERT INTO virtual_inventory_items"));
        assertTrue(repository.contains("SELECT item_id, amount FROM virtual_inventory_items WHERE inventory_id = ?"));
    }

    @Test
    void resourceNodeSqlLivesInResourceNodeRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/ResourceNodeRepository.java"));

        assertTrue(databaseService.contains("private final ResourceNodeRepository resourceNodeRepository"));
        assertTrue(databaseService.contains("return resourceNodeRepository.load(islandUuid);"));
        assertTrue(databaseService.contains("resourceNodeRepository.save(node);"));
        assertFalse(databaseService.contains("INSERT INTO resource_nodes"));
        assertFalse(databaseService.contains("SELECT * FROM resource_nodes WHERE island_uuid = ?"));

        assertTrue(repository.contains("INSERT INTO resource_nodes"));
        assertTrue(repository.contains("SELECT * FROM resource_nodes WHERE island_uuid = ?"));
    }

    @Test
    void researchUnlockSqlLivesInResearchRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/ResearchRepository.java"));

        assertTrue(databaseService.contains("private final ResearchRepository researchRepository"));
        assertTrue(databaseService.contains("return researchRepository.loadUnlocks(islandUuid);"));
        assertTrue(databaseService.contains("researchRepository.saveUnlock(islandUuid, unlockId);"));
        assertTrue(databaseService.contains("researchRepository.unlockEntries(islandUuid)"));
        assertFalse(databaseService.contains("INSERT INTO island_unlocks"));
        assertFalse(databaseService.contains("INSERT IGNORE INTO island_unlocks"));
        assertFalse(databaseService.contains("INSERT OR IGNORE INTO island_unlocks"));
        assertFalse(databaseService.contains("SELECT unlock_id FROM island_unlocks WHERE island_uuid = ?"));
        assertFalse(databaseService.contains("SELECT unlock_id, unlocked_at FROM island_unlocks WHERE island_uuid = ?"));

        assertTrue(repository.contains("INSERT INTO island_unlocks"));
        assertTrue(repository.contains("INSERT IGNORE INTO island_unlocks"));
        assertTrue(repository.contains("INSERT OR IGNORE INTO island_unlocks"));
        assertTrue(repository.contains("SELECT unlock_id FROM island_unlocks WHERE island_uuid = ?"));
        assertTrue(repository.contains("SELECT unlock_id, unlocked_at FROM island_unlocks WHERE island_uuid = ?"));
    }

    @Test
    void contractSqlLivesInContractRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/ContractRepository.java"));

        assertTrue(databaseService.contains("private final ContractRepository contractRepository"));
        assertTrue(databaseService.contains("contractRepository.load(islandUuid)"));
        assertTrue(databaseService.contains("return contractRepository.load(islandUuid, status);"));
        assertTrue(databaseService.contains("return contractRepository.existsForTemplate(islandUuid, templateId, status);"));
        assertTrue(databaseService.contains("return contractRepository.count(islandUuid, contractType, status, updatedSince);"));
        assertTrue(databaseService.contains("contractRepository.save(contract);"));
        assertTrue(databaseService.contains("contractRepository.updateStatus(contractId, status, progressJson)"));
        assertFalse(databaseService.contains("INSERT INTO contracts"));
        assertFalse(databaseService.contains("SELECT * FROM contracts WHERE island_uuid = ?"));
        assertFalse(databaseService.contains("SELECT * FROM contracts WHERE island_uuid = ? AND status = ? ORDER BY created_at ASC"));
        assertFalse(databaseService.contains("SELECT 1 FROM contracts WHERE island_uuid = ? AND template_id = ? AND status = ? LIMIT 1"));
        assertFalse(databaseService.contains("SELECT COUNT(*) AS count FROM contracts"));
        assertFalse(databaseService.contains("UPDATE contracts SET status = ?, progress_json = ?, updated_at = ? WHERE contract_id = ?"));
        assertFalse(databaseService.contains("SELECT * FROM contracts WHERE contract_id = ?"));

        assertTrue(repository.contains("INSERT INTO contracts"));
        assertTrue(repository.contains("SELECT * FROM contracts WHERE island_uuid = ?"));
        assertTrue(repository.contains("SELECT * FROM contracts WHERE island_uuid = ? AND status = ? ORDER BY created_at ASC"));
        assertTrue(repository.contains("SELECT 1 FROM contracts WHERE island_uuid = ? AND template_id = ? AND status = ? LIMIT 1"));
        assertTrue(repository.contains("SELECT COUNT(*) AS count FROM contracts"));
        assertTrue(repository.contains("UPDATE contracts SET status = ?, progress_json = ?, updated_at = ? WHERE contract_id = ?"));
        assertTrue(repository.contains("SELECT * FROM contracts WHERE contract_id = ?"));
    }

    @Test
    void ledgerSqlLivesInLedgerRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/LedgerRepository.java"));

        assertTrue(databaseService.contains("private final LedgerRepository ledgerRepository"));
        assertTrue(databaseService.contains("ledgerRepository.load(islandUuid)"));
        assertTrue(databaseService.contains("ledgerRepository.add(islandUuid, type, amount, reason);"));
        assertTrue(databaseService.contains("return ledgerRepository.beginEconomyLedger(islandUuid, playerUuid, operation, amount, reason, idempotencyKey);"));
        assertTrue(databaseService.contains("return ledgerRepository.economyLedgerClaim(idempotencyKey);"));
        assertTrue(databaseService.contains("ledgerRepository.completeEconomyLedger(idempotencyKey);"));
        assertTrue(databaseService.contains("ledgerRepository.failEconomyLedger(idempotencyKey);"));
        assertTrue(databaseService.contains("ledgerRepository.compensateEconomyLedger(idempotencyKey);"));
        assertTrue(databaseService.contains("ledgerRepository.saveSnapshot(ledgerId, islandUuid, type, amount, reason, createdAt);"));
        assertFalse(databaseService.contains("INSERT INTO ledger"));
        assertFalse(databaseService.contains("SELECT ledger_id, type, amount, reason, created_at FROM ledger WHERE island_uuid = ?"));
        assertFalse(databaseService.contains("INSERT IGNORE INTO satis_economy_ledger"));
        assertFalse(databaseService.contains("INSERT INTO satis_economy_ledger"));
        assertFalse(databaseService.contains("SELECT status FROM satis_economy_ledger WHERE idempotency_key = ?"));
        assertFalse(databaseService.contains("UPDATE satis_economy_ledger"));

        assertTrue(repository.contains("INSERT INTO ledger"));
        assertTrue(repository.contains("SELECT ledger_id, type, amount, reason, created_at FROM ledger WHERE island_uuid = ?"));
        assertTrue(repository.contains("INSERT IGNORE INTO satis_economy_ledger"));
        assertTrue(repository.contains("INSERT INTO satis_economy_ledger"));
        assertTrue(repository.contains("SELECT status FROM satis_economy_ledger WHERE idempotency_key = ?"));
        assertTrue(repository.contains("UPDATE satis_economy_ledger"));
    }

    @Test
    void marketSqlLivesInMarketRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/MarketRepository.java"));

        assertTrue(databaseService.contains("private final MarketRepository marketRepository"));
        assertTrue(databaseService.contains("marketRepository.personalRows(islandUuid)"));
        assertTrue(databaseService.contains("marketRepository.dailyRows()"));
        assertTrue(databaseService.contains("return marketRepository.dailySold(itemId, dateKey);"));
        assertTrue(databaseService.contains("return marketRepository.personalSold(islandUuid, itemId, dateKey);"));
        assertTrue(databaseService.contains("marketRepository.recordSale(islandUuid, itemId, dateKey, amount, demandFactor);"));
        assertTrue(databaseService.contains("marketRepository.saveDailySnapshot(itemId, dateKey, soldAmount, demandFactor);"));
        assertTrue(databaseService.contains("marketRepository.savePersonalSnapshot(islandUuid, itemId, dateKey, soldAmount);"));
        assertFalse(databaseService.contains("SELECT item_id, date_key, sold_amount FROM market_personal_daily WHERE island_uuid = ?"));
        assertFalse(databaseService.contains("SELECT item_id, date_key, sold_amount, demand_factor FROM market_daily"));
        assertFalse(databaseService.contains("SELECT sold_amount FROM market_daily WHERE item_id = ? AND date_key = ?"));
        assertFalse(databaseService.contains("SELECT sold_amount FROM market_personal_daily"));
        assertFalse(databaseService.contains("INSERT INTO market_daily"));
        assertFalse(databaseService.contains("INSERT INTO market_personal_daily"));
        assertFalse(databaseService.contains("recordMarketDailySql"));
        assertFalse(databaseService.contains("recordMarketPersonalSql"));
        assertFalse(databaseService.contains("saveMarketDailySnapshotSql"));
        assertFalse(databaseService.contains("saveMarketPersonalSnapshotSql"));

        assertTrue(repository.contains("SELECT item_id, date_key, sold_amount FROM market_personal_daily WHERE island_uuid = ?"));
        assertTrue(repository.contains("SELECT item_id, date_key, sold_amount, demand_factor FROM market_daily"));
        assertTrue(repository.contains("SELECT sold_amount FROM market_daily WHERE item_id = ? AND date_key = ?"));
        assertTrue(repository.contains("SELECT sold_amount FROM market_personal_daily"));
        assertTrue(repository.contains("INSERT INTO market_daily"));
        assertTrue(repository.contains("INSERT INTO market_personal_daily"));
        assertTrue(repository.contains("recordMarketDailySql"));
        assertTrue(repository.contains("recordMarketPersonalSql"));
        assertTrue(repository.contains("saveMarketDailySnapshotSql"));
        assertTrue(repository.contains("saveMarketPersonalSnapshotSql"));
    }

    @Test
    void networkSqlLivesInNetworkRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/NetworkRepository.java"));

        assertTrue(databaseService.contains("private final NetworkRepository networkRepository"));
        assertTrue(databaseService.contains("networkRepository.replaceItemNetworks(islandUuid, networks);"));
        assertTrue(databaseService.contains("return networkRepository.loadItemNetworks(islandUuid);"));
        assertTrue(databaseService.contains("networkRepository.replacePowerNetworks(islandUuid, networks);"));
        assertTrue(databaseService.contains("return networkRepository.loadPowerNetworks(islandUuid);"));
        assertFalse(databaseService.contains("SELECT * FROM item_networks WHERE island_uuid = ? ORDER BY network_id"));
        assertFalse(databaseService.contains("SELECT * FROM power_networks WHERE island_uuid = ? ORDER BY network_id"));
        assertFalse(databaseService.contains("INSERT INTO item_networks(network_id, island_uuid, throughput_per_minute, buffer_inventory_id, dirty, updated_at)"));
        assertFalse(databaseService.contains("INSERT INTO power_networks(network_id, island_uuid, generation_per_second, consumption_per_second"));
        assertFalse(databaseService.contains("SELECT machine_id FROM machine_network_links WHERE network_id = ? AND network_type = ?"));
        assertFalse(databaseService.contains("SELECT buffer_inventory_id FROM item_networks WHERE network_id = ?"));
        assertFalse(databaseService.contains("private List<ItemNetwork.Route> itemRoutes"));
        assertFalse(databaseService.contains("private Set<UUID> loadNetworkMachineIds"));

        assertTrue(repository.contains("SELECT * FROM item_networks WHERE island_uuid = ? ORDER BY network_id"));
        assertTrue(repository.contains("SELECT * FROM power_networks WHERE island_uuid = ? ORDER BY network_id"));
        assertTrue(repository.contains("INSERT INTO item_networks(network_id, island_uuid, throughput_per_minute, buffer_inventory_id, dirty, updated_at)"));
        assertTrue(repository.contains("INSERT INTO power_networks(network_id, island_uuid, generation_per_second, consumption_per_second"));
        assertTrue(repository.contains("SELECT machine_id FROM machine_network_links WHERE network_id = ? AND network_type = ?"));
        assertTrue(repository.contains("SELECT buffer_inventory_id FROM item_networks WHERE network_id = ?"));
        assertTrue(repository.contains("private List<ItemNetwork.Route> itemRoutes"));
        assertTrue(repository.contains("private Set<UUID> loadNetworkMachineIds"));
    }

    @Test
    void machineSqlLivesInMachineRepository() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String repository = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/MachineRepository.java"));

        assertTrue(databaseService.contains("private final MachineRepository machineRepository"));
        assertTrue(databaseService.contains("return machineRepository.loadAll();"));
        assertTrue(databaseService.contains("machineRepository.save(machine);"));
        assertTrue(databaseService.contains("machineRepository.delete(machineId);"));
        assertFalse(databaseService.contains("SELECT * FROM machines"));
        assertFalse(databaseService.contains("INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction, status"));
        assertFalse(databaseService.contains("DELETE FROM machines WHERE machine_id = ?"));
        assertFalse(databaseService.contains("DELETE FROM machine_network_links WHERE machine_id = ?"));
        assertFalse(databaseService.contains("private String saveMachineSql"));
        assertFalse(databaseService.contains("private String selectedRecipeId("));

        assertTrue(repository.contains("SELECT * FROM machines"));
        assertTrue(repository.contains("INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction, status"));
        assertTrue(repository.contains("DELETE FROM machines WHERE machine_id = ?"));
        assertTrue(repository.contains("DELETE FROM machine_network_links WHERE machine_id = ?"));
        assertTrue(repository.contains("private String saveMachineSql"));
        assertTrue(repository.contains("private String selectedRecipeId("));
    }

    @Test
    void coreAddonStatePublishingLivesInCoreAddonStatePublisher() throws Exception {
        String databaseService = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));
        String publisher = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/CoreAddonStatePublisher.java"));

        assertTrue(databaseService.contains("private final CoreAddonStatePublisher coreStatePublisher"));
        assertTrue(databaseService.contains("coreStatePublisher.coreStateWriter(coreStateWriter);"));
        assertTrue(databaseService.contains("coreStatePublisher.publishRow("));
        assertTrue(databaseService.contains("coreStatePublisher.publishTable("));
        assertTrue(databaseService.contains("coreStatePublisher.publishGlobalRow("));
        assertTrue(databaseService.contains("coreStatePublisher.publishGlobalTable("));
        assertFalse(databaseService.contains("private Consumer<CoreRowWrite> coreStateWriter"));
        assertFalse(databaseService.contains("private Consumer<CoreTableWrite> coreTableWriter"));
        assertFalse(databaseService.contains("private Consumer<CoreBulkWrite> coreBulkWriter"));
        assertFalse(databaseService.contains("private Consumer<CoreGlobalRowWrite> coreGlobalStateWriter"));
        assertFalse(databaseService.contains("private boolean coreStatePublishingSuspended"));
        assertFalse(databaseService.contains("private void publishCoreRow"));
        assertFalse(databaseService.contains("private void publishCoreTable"));
        assertFalse(databaseService.contains("private void publishCoreGlobalRow"));
        assertFalse(databaseService.contains("private void publishCoreGlobalTable"));

        assertTrue(publisher.contains("private Consumer<DatabaseService.CoreRowWrite> coreStateWriter"));
        assertTrue(publisher.contains("void publishRow(UUID islandUuid, String key, String value)"));
        assertTrue(publisher.contains("void publishTable(UUID islandUuid, String table, Map<String, String> values)"));
        assertTrue(publisher.contains("void publishGlobalRow(String key, String value)"));
        assertTrue(publisher.contains("void publishGlobalTable(String table, Map<String, String> values)"));
        assertTrue(publisher.contains("void withPublishingSuspended(Runnable action)"));
    }
}
