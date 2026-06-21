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
            IntegrationCapability.ISLAND_DEACTIVATE
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
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "permission-bypass-scope-activate", "permission-bypass-scope-deactivate" -> "LuckPerms#contextManager+cachedData";
            default -> "";
        };
    }
}
