package kr.lunaf.cloudislands.paper.integration.stacker;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class StackerIntegration extends PolicyBackedCloudIntegration {
    public StackerIntegration(String pluginName) {
        this(pluginName, IntegrationExternalRuntime.noop());
    }

    public StackerIntegration(String pluginName, IntegrationExternalRuntime externalRuntime) {
        super(pluginName, Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION
        ), externalRuntime);
    }
}
