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
}
