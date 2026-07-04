package kr.seungmin.satisskyfactory.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisFeatureRuntimeTest {
    private final SatisFeatureRuntime runtime = new SatisFeatureRuntime();

    @Test
    void buildsComponentPlanForFeatureGateRuntime() {
        SatisRuntimeComponentPlan plan = runtime.plan(snapshot(
                true,
                true,
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true
        ));

        assertTrue(plan.activeComponentsMetadata().contains("commands"));
        assertTrue(plan.activeComponentsMetadata().contains("gui-listener"));
        assertTrue(plan.skippedComponentsMetadata().contains("machine-listener"));
        assertTrue(plan.skippedComponentsMetadata().contains("machine-ticker"));
        assertTrue(plan.skippedComponentsMetadata().contains("lifecycle-listener"));
        assertTrue(plan.blockedComponentsMetadata().contains("machine-listener:machines-feature-disabled"));
    }

    @Test
    void cloudIslandsApiMissingBlocksStandaloneRuntime() {
        SatisRuntimeComponentPlan plan = runtime.plan(snapshot(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                false
        ));

        assertEquals("blocked-cloudislands-api-unavailable-no-standalone-island-management", plan.islandRuntimeAuthorityMetadata());
        assertEquals("all:cloudislands-api-unavailable-no-standalone-island-management", plan.featureBlockReasonsMetadata());
    }

    private SatisFeatureRuntime.ComponentSnapshot snapshot(
            boolean addonRuntimeEnabled,
            boolean commandsRegistered,
            boolean machineListenerRegistered,
            boolean guiListenerRegistered,
            boolean lifecycleListenerRegistered,
            boolean machineTickerRunning,
            boolean maintenanceTickerRunning,
            boolean placeholderRegistered,
            boolean coreApiStateWriterActive,
            boolean dirtySaveRunning,
            boolean commandsEnabled,
            boolean machinesEnabled,
            boolean storageEnabled,
            boolean resourceNodesEnabled,
            boolean marketEnabled,
            boolean contractsEnabled,
            boolean researchEnabled,
            boolean guiEnabled,
            boolean lifecycleListenerNeeded,
            boolean maintenanceEnabled,
            boolean placeholdersEnabled,
            boolean placeholderApiInstalled,
            boolean dataWritesEnabled,
            boolean addonStateEnabled,
            boolean coreApiAvailable
    ) {
        return new SatisFeatureRuntime.ComponentSnapshot(
                addonRuntimeEnabled,
                commandsRegistered,
                machineListenerRegistered,
                guiListenerRegistered,
                lifecycleListenerRegistered,
                machineTickerRunning,
                maintenanceTickerRunning,
                placeholderRegistered,
                coreApiStateWriterActive,
                dirtySaveRunning,
                commandsEnabled,
                machinesEnabled,
                storageEnabled,
                resourceNodesEnabled,
                marketEnabled,
                contractsEnabled,
                researchEnabled,
                guiEnabled,
                lifecycleListenerNeeded,
                maintenanceEnabled,
                placeholdersEnabled,
                placeholderApiInstalled,
                dataWritesEnabled,
                addonStateEnabled,
                coreApiAvailable
        );
    }
}
