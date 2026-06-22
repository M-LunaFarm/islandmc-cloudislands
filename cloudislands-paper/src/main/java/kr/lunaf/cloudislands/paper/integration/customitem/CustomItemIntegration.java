package kr.lunaf.cloudislands.paper.integration.customitem;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class CustomItemIntegration extends PolicyBackedCloudIntegration {
    public CustomItemIntegration(String pluginName) {
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
        return guardedStateHook("custom-item-index-activate", context, "world", "cell", "namespace");
    }

    @Override
    public IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return guardedStateHook("custom-item-index-deactivate", context, "world", "cell", "namespace", "bundleKey");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("custom-item-export", context, "world", "cell", "externalBlockIds", "coreBlockValueKeys", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("custom-item-restore", context, "world", "cell", "externalBlockIds", "coreBlockValueKeys", "bundleKey");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "custom-item-index-activate" -> pluginName() + " registry#resolveCustomBlockIds";
            case "custom-item-index-deactivate", "custom-item-export" -> pluginName() + " registry#serializeCustomBlockState";
            case "custom-item-restore" -> pluginName() + " registry#restoreCustomBlockState";
            default -> "";
        };
    }

    @Override
    protected String externalStateArtifacts(String operation) {
        return switch (operation == null ? "" : operation) {
            case "custom-item-index-activate" -> "custom-block-id-index";
            case "custom-item-index-deactivate", "custom-item-export" ->
                "custom-block-id-index,custom-block-state,core-block-value-mapping";
            case "custom-item-restore" -> "custom-block-state,core-block-value-mapping,restore-remap-plan";
            default -> "";
        };
    }

    @Override
    protected String externalSafetyBarriers(String operation) {
        return switch (operation == null ? "" : operation) {
            case "custom-item-index-activate", "custom-item-index-deactivate", "custom-item-export", "custom-item-restore" ->
                "runtime-authority,fencing-token,idempotency-key,custom-id-mapping,bundle-key";
            default -> "";
        };
    }
}
