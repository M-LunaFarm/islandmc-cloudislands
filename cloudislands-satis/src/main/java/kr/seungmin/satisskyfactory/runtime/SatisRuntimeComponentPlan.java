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
        boolean guiEnabled,
        boolean lifecycleListenerNeeded,
        boolean maintenanceEnabled,
        boolean placeholdersEnabled,
        boolean placeholderApiInstalled,
        boolean dataWritesEnabled,
        boolean addonStateEnabled,
        boolean coreApiAvailable
) {
    public String commandBlockReason() {
        if (!addonRuntimeEnabled) {
            return "addon-disabled";
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
        if (!addonRuntimeEnabled) {
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
        List<String> skipped = new ArrayList<>();
        if (!commandsEnabled) {
            skipped.add("commands");
        }
        if (!machinesEnabled) {
            skipped.add("machine-listener");
            skipped.add("machine-ticker");
        }
        if (!guiEnabled) {
            skipped.add("gui-listener");
        }
        if (!lifecycleListenerNeeded) {
            skipped.add("lifecycle-listener");
        }
        if (!maintenanceEnabled) {
            skipped.add("maintenance-ticker");
        }
        if (!placeholdersEnabled) {
            skipped.add("placeholder-expansion");
        } else if (!placeholderApiInstalled) {
            skipped.add("placeholder-expansion:placeholderapi-not-installed");
        }
        if (!dataWritesEnabled) {
            skipped.add("dirty-save");
        }
        if (!addonStateEnabled || !coreApiAvailable) {
            skipped.add("core-api-state-writer");
        }
        return skipped.isEmpty() ? "none" : String.join(",", skipped);
    }

    public String blockedComponentsMetadata() {
        if (!addonRuntimeEnabled) {
            return "commands:addon-disabled,machine-listener:addon-disabled,gui-listener:addon-disabled,lifecycle-listener:addon-disabled,placeholders:addon-disabled,machine-ticker:addon-disabled,maintenance-ticker:addon-disabled,dirty-save:addon-disabled,core-api-state-writer:addon-disabled";
        }
        List<String> blocked = new ArrayList<>();
        if (!commandsRegistered) {
            blocked.add("commands:" + commandBlockReason());
        }
        if (!machineListenerRegistered) {
            blocked.add("machine-listener:" + (!machinesEnabled ? "machines-feature-disabled" : "not-registered"));
        }
        if (!guiListenerRegistered) {
            blocked.add("gui-listener:" + (!guiEnabled ? "gui-feature-disabled" : "not-registered"));
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

    private String placeholderBlockReason() {
        if (placeholderRegistered) {
            return "none";
        }
        if (!placeholdersEnabled) {
            return "placeholders-feature-disabled";
        }
        if (!placeholderApiInstalled) {
            return "placeholderapi-not-installed";
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
