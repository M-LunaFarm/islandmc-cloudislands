package kr.lunaf.cloudislands.paper.integration.economy;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class VaultIntegration extends PolicyBackedCloudIntegration {
    public VaultIntegration() {
        this(IntegrationExternalRuntime.noop());
    }

    public VaultIntegration(IntegrationExternalRuntime externalRuntime) {
        super("Vault", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.ISLAND_ACTIVATE,
            IntegrationCapability.RUNTIME_AUTHORITY
        ), externalRuntime);
    }

    @Override
    public IntegrationResult onIslandActivate(IntegrationContext context) {
        return guardedObservationHook("economy-transaction-smoke", context, "providerName", "currency", "testAccount", "economyTransactionId");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "economy-transaction-smoke" -> "Vault Economy#withdrawPlayer+depositPlayer+getBalance";
            default -> "";
        };
    }

    @Override
    protected String externalStateArtifacts(String operation) {
        return switch (operation == null ? "" : operation) {
            case "economy-transaction-smoke" -> "withdraw-receipt,deposit-receipt,balance-round-trip";
            default -> "";
        };
    }

    @Override
    protected String externalSafetyBarriers(String operation) {
        return switch (operation == null ? "" : operation) {
            case "economy-transaction-smoke" -> "runtime-authority,fencing-token,idempotency-key,refund-on-core-failure";
            default -> "";
        };
    }
}
