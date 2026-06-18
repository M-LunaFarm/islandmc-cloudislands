package kr.seungmin.satisskyfactory.runtime;

import java.util.ArrayList;
import java.util.List;

public record SatisRuntimeComponentPlan(
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
    public static final String STANDALONE_ISLAND_RUNTIME_POLICY = "cloudislands-api-required-no-standalone-island-runtime";

    public String commandBlockReason() {
        if (!addonRuntimeEnabled) {
            return "addon-disabled";
        }
        if (!coreApiAvailable) {
            return "cloudislands-api-unavailable-no-standalone-island-management";
        }
        if (commandsRegistered) {
            return "none";
        }
        if (!commandsEnabled) {
            return "commands-feature-disabled";
        }
        return "command-not-registered";
    }

    public String activeComponentsMetadata() {
        if (!addonRuntimeEnabled || !coreApiAvailable) {
            return "none";
        }
        List<String> active = new ArrayList<>();
        if (commandsRegistered) {
            active.add("commands");
        }
        if (machineListenerRegistered) {
            active.add("machine-listener");
        }
        if (guiListenerRegistered) {
            active.add("gui-listener");
        }
        if (lifecycleListenerRegistered) {
            active.add("lifecycle-listener");
        }
        if (machineTickerRunning) {
            active.add("machine-ticker");
        }
        if (maintenanceTickerRunning) {
            active.add("maintenance-ticker");
        }
        if (placeholderRegistered) {
            active.add("placeholder-expansion");
        }
        if (coreApiStateWriterActive) {
            active.add("core-api-state-writer");
        }
        if (dirtySaveRunning) {
            active.add("dirty-save");
        }
        return active.isEmpty() ? "none" : String.join(",", active);
    }

    public String skippedComponentsMetadata() {
        if (!addonRuntimeEnabled) {
            return "commands,machine-listener,machine-ticker,gui-listener,lifecycle-listener,maintenance-ticker,placeholder-expansion,dirty-save,core-api-state-writer";
        }
        if (!coreApiAvailable) {
            return "commands,machine-listener,machine-ticker,gui-listener,lifecycle-listener,maintenance-ticker,placeholder-expansion,dirty-save,core-api-state-writer";
        }
        List<String> skipped = new ArrayList<>();
        if (!commandsEnabled) {
            skipped.add("commands");
        } else if (!commandsRegistered) {
            skipped.add("commands:not-registered");
        }
        if (!machinesEnabled) {
            skipped.add("machine-listener");
            skipped.add("machine-ticker");
        } else {
            if (!machineListenerRegistered) {
                skipped.add("machine-listener:not-registered");
            }
            if (!machineTickerRunning) {
                skipped.add("machine-ticker:not-running");
            }
        }
        if (!guiEnabled) {
            skipped.add("gui-listener");
        } else if (!guiListenerRegistered) {
            skipped.add("gui-listener:not-registered");
        }
        if (!lifecycleListenerNeeded) {
            skipped.add("lifecycle-listener");
        } else if (!lifecycleListenerRegistered) {
            skipped.add("lifecycle-listener:not-registered");
        }
        if (!maintenanceEnabled) {
            skipped.add("maintenance-ticker");
        } else if (!maintenanceTickerRunning) {
            skipped.add("maintenance-ticker:not-running");
        }
        if (!placeholdersEnabled) {
            skipped.add("placeholder-expansion");
        } else if (!placeholderApiInstalled) {
            skipped.add("placeholder-expansion:placeholderapi-not-installed");
        } else if (!placeholderRegistered) {
            skipped.add("placeholder-expansion:not-registered");
        }
        if (!dataWritesEnabled) {
            skipped.add("dirty-save");
        } else if (!dirtySaveRunning) {
            skipped.add("dirty-save:not-running");
        }
        if (!addonStateEnabled || !coreApiAvailable) {
            skipped.add("core-api-state-writer");
        } else if (!coreApiStateWriterActive) {
            skipped.add("core-api-state-writer:not-active");
        }
        return skipped.isEmpty() ? "none" : String.join(",", skipped);
    }

    public String blockedComponentsMetadata() {
        if (!addonRuntimeEnabled) {
            return "commands:addon-disabled,machine-listener:addon-disabled,gui-listener:addon-disabled,lifecycle-listener:addon-disabled,placeholders:addon-disabled,machine-ticker:addon-disabled,maintenance-ticker:addon-disabled,dirty-save:addon-disabled,core-api-state-writer:addon-disabled";
        }
        if (!coreApiAvailable) {
            return "commands:cloudislands-api-unavailable,machine-listener:cloudislands-api-unavailable,gui-listener:cloudislands-api-unavailable,lifecycle-listener:cloudislands-api-unavailable,placeholders:cloudislands-api-unavailable,machine-ticker:cloudislands-api-unavailable,maintenance-ticker:cloudislands-api-unavailable,dirty-save:cloudislands-api-unavailable,core-api-state-writer:cloudislands-api-unavailable";
        }
        List<String> blocked = new ArrayList<>();
        if (!commandsRegistered) {
            blocked.add("commands:" + commandBlockReason());
        }
        if (!machineListenerRegistered) {
            blocked.add("machine-listener:" + (!machinesEnabled ? "machines-feature-disabled" : "not-registered"));
        }
        if (!guiListenerRegistered) {
            blocked.add("gui-listener:" + (!guiEnabled ? guiBlockReason() : "not-registered"));
        }
        if (!lifecycleListenerRegistered) {
            blocked.add("lifecycle-listener:" + (!lifecycleListenerNeeded ? "lifecycle-state-disabled" : "not-registered"));
        }
        if (!placeholderRegistered) {
            blocked.add("placeholders:" + placeholderBlockReason());
        }
        if (!machineTickerRunning) {
            blocked.add("machine-ticker:" + (!machinesEnabled ? "machines-feature-disabled" : "not-running"));
        }
        if (!maintenanceTickerRunning) {
            blocked.add("maintenance-ticker:" + (!maintenanceEnabled ? "maintenance-feature-disabled" : "not-running"));
        }
        if (!dirtySaveRunning) {
            blocked.add("dirty-save:" + (!dataWritesEnabled ? "data-writes-disabled" : "not-running"));
        }
        if (!coreApiStateWriterActive) {
            blocked.add("core-api-state-writer:" + coreApiStateWriterBlockReason());
        }
        return blocked.isEmpty() ? "none" : String.join(",", blocked);
    }

    public String featureBlockReasonsMetadata() {
        if (!addonRuntimeEnabled) {
            return "all:addon-disabled";
        }
        if (!coreApiAvailable) {
            return "all:cloudislands-api-unavailable-no-standalone-island-management";
        }
        List<String> reasons = new ArrayList<>();
        if (!commandsEnabled) {
            reasons.add("commands:commands-feature-disabled");
        }
        if (!machinesEnabled) {
            reasons.add("machines:machines-feature-disabled");
        }
        if (!storageEnabled) {
            reasons.add("storage:storage-feature-disabled");
        }
        if (!resourceNodesEnabled) {
            reasons.add("resource-nodes:resource-nodes-or-machines-feature-disabled");
        }
        if (!marketEnabled) {
            reasons.add("market:market-or-storage-feature-disabled");
        }
        if (!contractsEnabled) {
            reasons.add("contracts:contracts-or-storage-feature-disabled");
        }
        if (!researchEnabled) {
            reasons.add("research:research-feature-disabled");
        }
        if (!guiEnabled) {
            reasons.add("gui:" + guiBlockReason());
        }
        if (!lifecycleListenerNeeded) {
            reasons.add("lifecycle:lifecycle-feature-or-state-disabled");
        }
        if (!maintenanceEnabled) {
            reasons.add("maintenance:maintenance-feature-disabled");
        }
        if (!placeholdersEnabled) {
            reasons.add("placeholders:" + placeholderBlockReason());
        } else if (!placeholderApiInstalled) {
            reasons.add("placeholders:placeholderapi-not-installed");
        }
        if (!dataWritesEnabled) {
            reasons.add("writes:data-write-authority-or-write-feature-disabled");
        }
        if (!addonStateEnabled) {
            reasons.add("addon-state:addon-state-feature-disabled");
        } else if (!coreApiAvailable) {
            reasons.add("addon-state:cloudislands-api-unavailable");
        }
        return reasons.isEmpty() ? "none" : String.join(",", reasons);
    }

    public String islandRuntimeAuthorityMetadata() {
        if (!addonRuntimeEnabled) {
            return "disabled-no-standalone-island-management";
        }
        if (!coreApiAvailable) {
            return "blocked-cloudislands-api-unavailable-no-standalone-island-management";
        }
        return "cloudislands-api";
    }

    private String placeholderBlockReason() {
        if (placeholderRegistered) {
            return "none";
        }
        if (!machinesEnabled) {
            return "placeholders-machines-feature-disabled";
        }
        if (!placeholdersEnabled) {
            return "placeholders-feature-disabled";
        }
        if (!placeholderApiInstalled) {
            return "placeholderapi-not-installed";
        }
        return "not-registered";
    }

    private String guiBlockReason() {
        if (guiListenerRegistered) {
            return "none";
        }
        if (!machinesEnabled) {
            return "gui-machines-feature-disabled";
        }
        if (!guiEnabled) {
            return "gui-feature-disabled";
        }
        return "not-registered";
    }

    private String coreApiStateWriterBlockReason() {
        if (coreApiStateWriterActive) {
            return "none";
        }
        if (!addonStateEnabled) {
            return "addon-state-feature-disabled";
        }
        if (!coreApiAvailable) {
            return "cloudislands-api-unavailable";
        }
        return "not-active";
    }
}
