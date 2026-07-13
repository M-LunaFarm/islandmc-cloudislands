package kr.lunaf.cloudislands.paper.integration.vanish;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class VanishIntegration extends PolicyBackedCloudIntegration {
    public VanishIntegration(String pluginName) {
        this(pluginName, IntegrationExternalRuntime.noop());
    }

    public VanishIntegration(String pluginName, IntegrationExternalRuntime externalRuntime) {
        super(pluginName, Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.RUNTIME_SERVICE
        ), externalRuntime);
    }
}
