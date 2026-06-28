package kr.lunaf.cloudislands.paper.integration.placeholder;

import java.util.Set;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;

public final class PlaceholderApiIntegration extends PolicyBackedCloudIntegration {
    public PlaceholderApiIntegration() {
        this(IntegrationExternalRuntime.noop());
    }

    public PlaceholderApiIntegration(IntegrationExternalRuntime externalRuntime) {
        super("PlaceholderAPI", Set.of(
            IntegrationCapability.DETECT,
            IntegrationCapability.VALIDATE_VERSION,
            IntegrationCapability.ISLAND_ACTIVATE
        ), externalRuntime);
    }

    @Override
    public IntegrationResult onIslandActivate(IntegrationContext context) {
        return guardedObservationHook("placeholder-render-smoke", context, "placeholderKeys", "renderTarget");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "placeholder-render-smoke" -> "PlaceholderAPI#setPlaceholders";
            default -> "";
        };
    }

    @Override
    protected String externalStateArtifacts(String operation) {
        return switch (operation == null ? "" : operation) {
            case "placeholder-render-smoke" -> "placeholder-render-output";
            default -> "";
        };
    }

    @Override
    protected String externalSafetyBarriers(String operation) {
        return switch (operation == null ? "" : operation) {
            case "placeholder-render-smoke" -> "typed-core-view,player-scope,empty-safe-render";
            default -> "";
        };
    }
}
