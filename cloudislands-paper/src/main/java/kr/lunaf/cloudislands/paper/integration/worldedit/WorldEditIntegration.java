package kr.lunaf.cloudislands.paper.integration.worldedit;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class WorldEditIntegration extends PolicyBackedCloudIntegration {
    public WorldEditIntegration(String pluginName) {
        super(pluginName, Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.ISLAND_ACTIVATE,
            IntegrationCapability.STATE_EXPORT,
            IntegrationCapability.STATE_RESTORE,
            IntegrationCapability.RUNTIME_AUTHORITY
        ));
    }
}
