package kr.seungmin.satisskyfactory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisRuntimeWriteGatePolicyTest {
    @Test
    void dirtySaveTaskOnlyStartsWhenRuntimeDataWritesAreAllowed() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("if (dataWritesEnabled()) {\n            dirtySaves.start(dirtySavePeriodTicks(configs.main()));\n        }"));
        assertTrue(source.contains("return SatisRuntimeTickAuthorityPolicy.writeReady(database.activeBackend(), storageWriteAuthorityReady())\n                && runtimeWriteFeatureEnabled();"));
        assertTrue(source.contains("dirtySaves.writeGates("));
        assertTrue(source.contains("this::dataWritesEnabled"));
        assertTrue(source.contains("dirtySaves.inventoryWriteGate(this::inventoryDataWritesEnabled);"));
    }

    @Test
    void coreApiStateWritersDetachBeforeCheckingAddonStateGate() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private void configureCoreApiStateWriters()"));
        assertTrue(source.contains("database.coreStateWriter(null);"));
        assertTrue(source.contains("database.coreTableWriter(null);"));
        assertTrue(source.contains("database.coreBulkWriter(null);"));
        assertTrue(source.contains("database.coreGlobalStateWriter(null);"));
        assertTrue(source.contains("database.coreGlobalTableWriter(null);"));
        assertTrue(source.contains("database.coreGlobalBulkWriter(null);"));
        assertTrue(source.contains("dirtySaves.coreStatePublisher(null);"));
        assertTrue(source.contains("dirtySaves.coreStateDeletePublisher(null);"));
        assertTrue(source.contains("coreApiState = null;"));
        assertTrue(source.contains("if (!operationalFeatureEnabled(\"addon-state\"))"));
        assertTrue(source.contains("database.activeBackend() != DatabaseService.StorageBackend.CORE_API || !coreApiAddonStateAvailable()"));
    }
}
