package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.Set;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;

public class PolicyBackedCloudIntegration implements CloudIntegration {
    private final String pluginName;
    private final Set<IntegrationCapability> capabilities;

    public PolicyBackedCloudIntegration(String pluginName, Set<IntegrationCapability> capabilities) {
        this.pluginName = pluginName == null ? "" : pluginName;
        this.capabilities = capabilities == null || capabilities.isEmpty()
            ? Set.of(IntegrationCapability.DETECT)
            : Set.copyOf(capabilities);
    }

    @Override
    public String pluginName() {
        return pluginName;
    }

    @Override
    public String category() {
        return CloudIntegrationPolicy.category(pluginName);
    }

    @Override
    public Set<IntegrationCapability> capabilities() {
        return capabilities;
    }
}
