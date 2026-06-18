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
        assertTrue(source.contains("unregisterAddonCommands();"));
        assertTrue(source.contains("dirtySaves.stop();"));
        assertTrue(source.contains("dirtySaves.coreStatePublisher(null);"));
        assertTrue(source.contains("dirtySaves.coreStateDeletePublisher(null);"));
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
        assertTrue(source.contains("state.put(\"reinstall-reconnect-policy\", \"reuse-existing-addon-state-by-island-uuid\");"));
    }
}
