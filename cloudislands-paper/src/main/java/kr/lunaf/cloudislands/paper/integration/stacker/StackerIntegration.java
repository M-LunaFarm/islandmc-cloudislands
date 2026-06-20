package kr.lunaf.cloudislands.paper.integration.stacker;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class StackerIntegration extends PolicyBackedCloudIntegration {
    public StackerIntegration(String pluginName) {
        super(pluginName, Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.STATE_EXPORT,
            IntegrationCapability.RUNTIME_AUTHORITY
        ));
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("effective-stack-export", context, "world", "cell", "entityCountKey", "spawnerCountKey", "bundleKey");
    }
}
