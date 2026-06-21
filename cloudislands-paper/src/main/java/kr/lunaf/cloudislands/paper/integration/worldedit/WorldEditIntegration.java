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
            IntegrationCapability.STATE_EXPORT,
            IntegrationCapability.STATE_RESTORE,
            IntegrationCapability.RUNTIME_AUTHORITY
        ));
    }

    @Override
    public IntegrationResult onIslandActivate(IntegrationContext context) {
        return guardedStateHook("clipboard-activate", context, "world", "cell");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("schematic-export", context, "world", "cell", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("schematic-restore", context, "world", "cell", "bundleKey");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "clipboard-activate" -> "WorldEdit#newEditSession";
            case "schematic-export" -> "ClipboardWriter#write";
            case "schematic-restore" -> "ClipboardReader#read+EditSession#paste";
            default -> "";
        };
    }
}
