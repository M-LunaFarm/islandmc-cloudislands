package kr.lunaf.cloudislands.paper.integration.coreprotect;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class CoreProtectIntegration extends PolicyBackedCloudIntegration {
    public CoreProtectIntegration() {
        super("CoreProtect", Set.of(
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
        return guardedStateHook("audit-activate", context, "world", "cell");
    }

    @Override
    public IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return guardedStateHook("audit-deactivate", context, "world", "cell", "bundleKey");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("audit-export", context, "world", "cell", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("rollback-restore", context, "world", "cell", "rollbackSeconds", "bundleKey");
    }
}
