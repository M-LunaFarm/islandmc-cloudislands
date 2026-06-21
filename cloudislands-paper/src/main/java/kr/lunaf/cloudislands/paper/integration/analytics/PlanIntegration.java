package kr.lunaf.cloudislands.paper.integration.analytics;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class PlanIntegration extends PolicyBackedCloudIntegration {
    public PlanIntegration() {
        super("Plan", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.ISLAND_ACTIVATE,
            IntegrationCapability.ISLAND_DEACTIVATE,
            IntegrationCapability.STATE_EXPORT
        ));
    }

    @Override
    public IntegrationResult onIslandActivate(IntegrationContext context) {
        return guardedObservationHook("presence-activate", context, "analyticsScope", "presenceKey");
    }

    @Override
    public IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return guardedObservationHook("presence-deactivate", context, "analyticsScope", "presenceKey");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedObservationHook("analytics-export", context, "analyticsScope", "bundleKey");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "presence-activate", "presence-deactivate" -> "PlanAPI#playerContainer";
            case "analytics-export" -> "PlanAPI#queryService";
            default -> "";
        };
    }
}
