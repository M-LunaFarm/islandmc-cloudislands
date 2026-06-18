package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisAddonRemovalPolicyTest {
    @Test
    void unregisterStopsSatisRuntimeWithoutDeletingAddonOrCoreState() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private void stopRuntimeActivity()"));
        assertTrue(source.contains("private void ensureDirtySaveService()"));
        assertTrue(source.contains("ensureDirtySaveService();"));
        assertTrue(source.contains("if (dataWritesEnabled() && dirtySaves != null)"));
        assertTrue(source.contains(".thenAccept(snapshot -> applySatisRuntimeFallback(reason))"));
        assertTrue(source.contains("if (database == null) {\n            startRuntime();\n            return;\n        }"));
        assertTrue(source.contains("unregisterAddonCommands();"));
        assertTrue(source.contains("dirtySaves.stop();"));
        assertTrue(source.contains("dirtySaves.coreStatePublisher(null);"));
        assertTrue(source.contains("dirtySaves.coreStateDeletePublisher(null);"));
        assertTrue(source.contains("if (storage != null)"));
        assertTrue(source.contains("storage.dirtySaves(null);"));
        assertTrue(source.contains("if (islands != null)"));
        assertTrue(source.contains("islands.dirtySaves(null);"));
        assertTrue(source.contains("if (machines != null)"));
        assertTrue(source.contains("machines.dirtySaves(null);"));
        assertTrue(source.contains("if (nodes != null)"));
        assertTrue(source.contains("nodes.dirtySaves(null);"));
        assertTrue(source.contains("dirtySaves = null;"));
        assertTrue(source.contains("if (database != null) {\n            database.purgeIsland(islandId);\n        }"));
        assertTrue(source.contains("database.coreStateWriter(null);"));
        assertTrue(source.contains("database.coreTableWriter(null);"));
        assertTrue(source.contains("database.coreBulkWriter(null);"));
        assertTrue(source.contains("database.coreGlobalStateWriter(null);"));
        assertTrue(source.contains("database.coreGlobalTableWriter(null);"));
        assertTrue(source.contains("database.coreGlobalBulkWriter(null);"));
        assertTrue(source.contains("coreHydratedIslandActivations.clear();"));
        assertTrue(source.contains("placeholderHook.unregister();"));
        assertTrue(source.contains("machineListenerRegistered = unregisterListener(machineListener, machineListenerRegistered);"));
        assertTrue(source.contains("guiListenerRegistered = unregisterListener(guiListener, guiListenerRegistered);"));
        assertTrue(source.contains("lifecycleListenerRegistered = unregisterListener(lifecycleListener, lifecycleListenerRegistered);"));
    }

    @Test
    void unregisterStateDocumentsDataPreservation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private void putAddonReconnectPolicy(Map<String, String> state)"));
        assertTrue(source.contains("state.put(\"unregister-delete-addon-data\", \"false\");"));
        assertTrue(source.contains("state.put(\"unregister-delete-island-state\", \"false\");"));
        assertTrue(source.contains("state.put(\"unregister-preserve-core-state\", \"true\");"));
        assertTrue(source.contains("state.put(\"unregister-preserve-local-cache\", \"true\");"));
        assertTrue(source.contains("state.put(\"addon-removal-dirty-save-detach-policy\", SatisAddonIntegrationPolicy.DIRTY_SAVE_DETACH_POLICY);"));
        assertTrue(source.contains("state.put(\"addon-removal-dirty-save-reattach-policy\", SatisAddonIntegrationPolicy.DIRTY_SAVE_REATTACH_POLICY);"));
        assertTrue(source.contains("state.put(\"addon-reload-runtime-restart-policy\", SatisAddonIntegrationPolicy.RELOAD_RUNTIME_RESTART_POLICY);"));
        assertTrue(source.contains("state.put(\"addon-core-refresh-reapply-policy\", SatisAddonIntegrationPolicy.CORE_REFRESH_REAPPLY_POLICY);"));
        assertTrue(source.contains("state.put(\"reinstall-reconnect-policy\", \"reuse-existing-addon-state-by-island-uuid\");"));
    }
}
