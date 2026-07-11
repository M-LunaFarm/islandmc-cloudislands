package kr.lunaf.cloudislands.paper.integration.coreprotect;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class CoreProtectIntegration extends PolicyBackedCloudIntegration {
    public CoreProtectIntegration() {
        this(IntegrationExternalRuntime.noop());
    }

    public CoreProtectIntegration(IntegrationExternalRuntime externalRuntime) {
        super("CoreProtect", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION
        ), externalRuntime);
    }
}
