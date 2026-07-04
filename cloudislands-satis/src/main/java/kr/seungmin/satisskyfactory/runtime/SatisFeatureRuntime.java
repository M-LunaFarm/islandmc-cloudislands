package kr.seungmin.satisskyfactory.runtime;

public final class SatisFeatureRuntime {
    public SatisRuntimeComponentPlan plan(ComponentSnapshot snapshot) {
        return new SatisRuntimeComponentPlan(
                snapshot.addonRuntimeEnabled(),
                snapshot.commandsRegistered(),
                snapshot.machineListenerRegistered(),
                snapshot.guiListenerRegistered(),
                snapshot.lifecycleListenerRegistered(),
                snapshot.machineTickerRunning(),
                snapshot.maintenanceTickerRunning(),
                snapshot.placeholderRegistered(),
                snapshot.coreApiStateWriterActive(),
                snapshot.dirtySaveRunning(),
                snapshot.commandsEnabled(),
                snapshot.machinesEnabled(),
                snapshot.storageEnabled(),
                snapshot.resourceNodesEnabled(),
                snapshot.marketEnabled(),
                snapshot.contractsEnabled(),
                snapshot.researchEnabled(),
                snapshot.guiEnabled(),
                snapshot.lifecycleListenerNeeded(),
                snapshot.maintenanceEnabled(),
                snapshot.placeholdersEnabled(),
                snapshot.placeholderApiInstalled(),
                snapshot.dataWritesEnabled(),
                snapshot.addonStateEnabled(),
                snapshot.coreApiAvailable()
        );
    }

    public record ComponentSnapshot(
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
    }
}
