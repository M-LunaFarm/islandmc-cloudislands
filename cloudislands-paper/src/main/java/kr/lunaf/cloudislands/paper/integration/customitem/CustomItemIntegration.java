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
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("custom-item-export", context, "world", "cell", "externalBlockIds", "coreBlockValueKeys", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("custom-item-restore", context, "world", "cell", "externalBlockIds", "coreBlockValueKeys", "bundleKey");
    }
}
