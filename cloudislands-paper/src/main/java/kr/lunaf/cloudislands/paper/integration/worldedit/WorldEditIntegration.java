package kr.lunaf.cloudislands.paper.integration.worldedit;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class WorldEditIntegration extends PolicyBackedCloudIntegration {
    public WorldEditIntegration(String pluginName) {
        super(pluginName, Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.ISLAND_ACTIVATE,
            IntegrationCapability.ISLAND_DEACTIVATE,
            IntegrationCapability.STATE_EXPORT,
            IntegrationCapability.STATE_RESTORE,
            IntegrationCapability.RUNTIME_AUTHORITY
        ));
    }

    @Override
    public IntegrationResult onIslandActivate(IntegrationContext context) {
        return guardedStateHook("clipboard-activate", context, "world", "cell", "region");
    }

    @Override
    public IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return guardedStateHook("edit-session-deactivate", context, "world", "cell", "region", "activeOperationsDrained", "editSessionFlushed");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("schematic-export", context, "world", "cell", "region", "activeOperationsDrained", "editSessionFlushed", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("schematic-restore", context, "world", "cell", "region", "bundleKey");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "clipboard-activate" -> "WorldEdit#newEditSession";
            case "edit-session-deactivate" -> "EditSession#flushQueue+Operations#complete";
            case "schematic-export" -> "ClipboardWriter#write";
            case "schematic-restore" -> "ClipboardReader#read+EditSession#paste";
            default -> "";
        };
    }

    @Override
    protected String externalStateArtifacts(String operation) {
        return switch (operation == null ? "" : operation) {
            case "clipboard-activate" -> "region-edit-session";
            case "edit-session-deactivate" -> "operation-drain-marker,edit-session-flush-marker";
            case "schematic-export" -> "clipboard-schematic,operation-drain-marker,edit-session-flush-marker";
            case "schematic-restore" -> "clipboard-schematic,paste-operation-plan";
            default -> "";
        };
    }

    @Override
    protected String externalSafetyBarriers(String operation) {
        return switch (operation == null ? "" : operation) {
            case "clipboard-activate", "schematic-restore" ->
                "runtime-authority,fencing-token,idempotency-key,region-boundary";
            case "edit-session-deactivate", "schematic-export" ->
                "runtime-authority,fencing-token,idempotency-key,active-operations-drained,edit-session-flushed,region-boundary";
            default -> "";
        };
    }
}
