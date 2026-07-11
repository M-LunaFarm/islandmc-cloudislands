package kr.lunaf.cloudislands.paper.integration.economy;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class VaultIntegration extends PolicyBackedCloudIntegration {
    public VaultIntegration() {
        this(IntegrationExternalRuntime.noop());
    }

    public VaultIntegration(IntegrationExternalRuntime externalRuntime) {
        super("Vault", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION
        ), externalRuntime);
    }
}
