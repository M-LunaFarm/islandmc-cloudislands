package kr.lunaf.cloudislands.paper.integration.placeholder;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class PlaceholderApiIntegration extends PolicyBackedCloudIntegration {
    public PlaceholderApiIntegration() {
        this(IntegrationExternalRuntime.noop());
    }

    public PlaceholderApiIntegration(IntegrationExternalRuntime externalRuntime) {
        super("PlaceholderAPI", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.RUNTIME_SERVICE
        ), externalRuntime);
    }
}
