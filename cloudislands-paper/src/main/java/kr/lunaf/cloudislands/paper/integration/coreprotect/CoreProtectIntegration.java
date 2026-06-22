package kr.lunaf.cloudislands.paper.integration.coreprotect;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class CoreProtectIntegration extends PolicyBackedCloudIntegration {
    public CoreProtectIntegration() {
        this(IntegrationExternalRuntime.noop());
    }

    public CoreProtectIntegration(IntegrationExternalRuntime externalRuntime) {
        super("CoreProtect", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.ISLAND_ACTIVATE,
            IntegrationCapability.ISLAND_DEACTIVATE,
            IntegrationCapability.STATE_EXPORT,
            IntegrationCapability.STATE_RESTORE,
            IntegrationCapability.RUNTIME_AUTHORITY
        ), externalRuntime);
    }

    @Override
    public IntegrationResult onIslandActivate(IntegrationContext context) {
        return guardedStateHook("audit-activate", context, "world", "cell", "region");
    }

    @Override
    public IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return guardedStateHook("audit-deactivate", context, "world", "cell", "region", "bundleKey");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("audit-export", context, "world", "cell", "region", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("rollback-restore", context, "world", "cell", "region", "rollbackSeconds", "bundleKey");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "audit-activate", "audit-deactivate", "audit-export" -> "CoreProtectAPI#performLookup";
            case "rollback-restore" -> "CoreProtectAPI#performRollback";
            default -> "";
        };
    }

    @Override
    protected String externalStateArtifacts(String operation) {
        return switch (operation == null ? "" : operation) {
            case "audit-activate" -> "region-audit-cursor";
            case "audit-deactivate", "audit-export" -> "region-audit-cursor,coreprotect-lookup-events";
            case "rollback-restore" -> "rollback-plan,affected-region-audit";
            default -> "";
        };
    }

    @Override
    protected String externalSafetyBarriers(String operation) {
        return switch (operation == null ? "" : operation) {
            case "audit-activate", "audit-deactivate", "audit-export", "rollback-restore" ->
                "runtime-authority,fencing-token,idempotency-key,region-boundary";
            default -> "";
        };
    }
}
