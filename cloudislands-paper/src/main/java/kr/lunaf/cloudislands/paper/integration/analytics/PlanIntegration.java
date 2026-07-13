package kr.lunaf.cloudislands.paper.integration.analytics;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class PlanIntegration extends PolicyBackedCloudIntegration {
    public PlanIntegration() {
        this(IntegrationExternalRuntime.noop());
    }

    public PlanIntegration(IntegrationExternalRuntime externalRuntime) {
        super("Plan", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.RUNTIME_SERVICE
        ), externalRuntime);
    }
}
