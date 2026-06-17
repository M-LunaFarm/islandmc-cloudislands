package kr.lunaf.cloudislands.coreservice.addon;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryAddonStateRepositoryTest {
    @Test
    void tableKeyValueBulkSaveKeepsSlashKeys() {
        InMemoryAddonStateRepository repository = new InMemoryAddonStateRepository();

        Map<String, String> state = repository.tableKeyValueBulkSave(
                "cloudislands-satis",
                Map.of("runtime-status", "ok"),
                Map.of("machines", Map.of("island/0001/machine/0002", "active")));

        assertEquals("ok", state.get("runtime-status"));
        assertEquals("active", state.get(AddonStateRepository.tableStateKey("machines", "island/0001/machine/0002")));
        assertEquals(state, repository.list("cloudislands-satis"));
    }

    @Test
    void tableKeyValueBulkSaveIslandKeepsSlashKeys() {
        InMemoryAddonStateRepository repository = new InMemoryAddonStateRepository();
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000702");

        Map<String, String> state = repository.tableKeyValueBulkSaveIsland(
                "cloudislands-satis",
                islandId,
                Map.of("active-node", "island-2"),
                Map.of("resource_nodes", Map.of("node/ore/0/0", "12000")));

        assertEquals("island-2", state.get("active-node"));
        assertEquals("12000", state.get(AddonStateRepository.tableStateKey("resource_nodes", "node/ore/0/0")));
        assertEquals(state, repository.listIsland("cloudislands-satis", islandId));
    }

    @Test
    void explicitGlobalClearDoesNotRemoveIslandState() {
        InMemoryAddonStateRepository repository = new InMemoryAddonStateRepository();
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000703");

        repository.put("cloudislands-satis", Map.of("factory", "saved"));
        repository.putIsland("cloudislands-satis", islandId, Map.of("machine", "running"));

        repository.clear("cloudislands-satis");

        assertEquals(Map.of(), repository.list("cloudislands-satis"));
        assertEquals(Map.of("machine", "running"), repository.listIsland("cloudislands-satis", islandId));
    }
}
