package kr.lunaf.cloudislands.paper.integration.customitem;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class CustomItemIntegration extends PolicyBackedCloudIntegration {
    public CustomItemIntegration(String pluginName) {
        this(pluginName, IntegrationExternalRuntime.noop());
    }

    public CustomItemIntegration(String pluginName, IntegrationExternalRuntime externalRuntime) {
        super(pluginName, capabilities(pluginName), externalRuntime);
    }

    private static Set<IntegrationCapability> capabilities(String pluginName) {
        return CustomBlockKeyService.supportedPlugins().contains(pluginName)
            ? Set.of(
                IntegrationCapability.DETECT,
                IntegrationCapability.VALIDATE_VERSION,
                IntegrationCapability.RUNTIME_SERVICE
            )
            : Set.of(
                IntegrationCapability.DETECT,
                IntegrationCapability.VALIDATE_VERSION
            );
    }
}
