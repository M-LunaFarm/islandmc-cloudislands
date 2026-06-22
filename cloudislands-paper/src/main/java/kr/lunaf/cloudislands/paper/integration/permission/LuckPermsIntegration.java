package kr.lunaf.cloudislands.paper.integration.permission;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class LuckPermsIntegration extends PolicyBackedCloudIntegration {
    public LuckPermsIntegration() {
        super("LuckPerms", Set.of(
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
        return guardedObservationHook("permission-bypass-scope-activate", context, "permissionNode", "bypassScope");
    }

    @Override
    public IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return guardedObservationHook("permission-bypass-scope-deactivate", context, "permissionNode", "bypassScope");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("permission-context-export", context, "world", "cell", "permissionNode", "bypassScope", "contextKey", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("permission-context-restore", context, "world", "cell", "permissionNode", "bypassScope", "contextKey", "bundleKey");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "permission-bypass-scope-activate", "permission-bypass-scope-deactivate" -> "LuckPerms#contextManager+cachedData";
            case "permission-context-export" -> "LuckPerms#userManager+trackManager#saveContextState";
            case "permission-context-restore" -> "LuckPerms#userManager+trackManager#restoreContextState";
            default -> "";
        };
    }

    @Override
    protected String externalStateArtifacts(String operation) {
        return switch (operation == null ? "" : operation) {
            case "permission-bypass-scope-activate", "permission-bypass-scope-deactivate" ->
                "context-calculator-scope,bypass-permission-node";
            case "permission-context-export" -> "user-context-nodes,track-context-state,bypass-scope";
            case "permission-context-restore" -> "user-context-nodes,track-context-state,context-restore-plan";
            default -> "";
        };
    }

    @Override
    protected String externalSafetyBarriers(String operation) {
        return switch (operation == null ? "" : operation) {
            case "permission-bypass-scope-activate", "permission-bypass-scope-deactivate" ->
                "permission-node-scope,bypass-scope";
            case "permission-context-export", "permission-context-restore" ->
                "runtime-authority,fencing-token,idempotency-key,permission-node-scope,context-key,bundle-key";
            default -> "";
        };
    }
}
