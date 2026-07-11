package kr.lunaf.cloudislands.paper.integration.worldedit;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class WorldEditIntegration extends PolicyBackedCloudIntegration {
    public WorldEditIntegration(String pluginName) {
        this(pluginName, IntegrationExternalRuntime.noop());
    }

    public WorldEditIntegration(String pluginName, IntegrationExternalRuntime externalRuntime) {
        super(pluginName, Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION
        ), externalRuntime);
    }
}
