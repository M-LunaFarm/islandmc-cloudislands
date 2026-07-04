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
}
