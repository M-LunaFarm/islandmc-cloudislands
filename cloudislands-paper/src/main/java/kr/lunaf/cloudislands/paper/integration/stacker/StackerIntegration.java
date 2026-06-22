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
            IntegrationCapability.ISLAND_ACTIVATE,
            IntegrationCapability.ISLAND_DEACTIVATE,
            IntegrationCapability.STATE_EXPORT,
            IntegrationCapability.STATE_RESTORE,
            IntegrationCapability.RUNTIME_AUTHORITY
        ));
    }

    @Override
    public IntegrationResult onIslandActivate(IntegrationContext context) {
        return guardedStateHook("effective-stack-activate", context, "world", "cell", "entityCountKey", "spawnerCountKey");
    }

    @Override
    public IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return guardedStateHook("effective-stack-deactivate", context, "world", "cell", "entityCountKey", "spawnerCountKey", "bundleKey");
    }

    @Override
    public IntegrationResult exportState(IntegrationContext context) {
        return guardedStateHook("effective-stack-export", context, "world", "cell", "entityCountKey", "spawnerCountKey", "bundleKey");
    }

    @Override
    public IntegrationResult restoreState(IntegrationContext context) {
        return guardedStateHook("effective-stack-restore", context, "world", "cell", "entityCountKey", "spawnerCountKey", "bundleKey");
    }

    @Override
    protected String externalApiCall(String operation) {
        return switch (operation == null ? "" : operation) {
            case "effective-stack-activate" -> pluginName() + " API#readEffectiveStackLimits";
            case "effective-stack-deactivate", "effective-stack-export" -> pluginName() + " API#serializeStackedEntitiesAndSpawners";
            case "effective-stack-restore" -> pluginName() + " API#restoreStackedEntitiesAndSpawners";
            default -> "";
        };
    }

    @Override
    protected String externalStateArtifacts(String operation) {
        return switch (operation == null ? "" : operation) {
            case "effective-stack-activate" -> "effective-entity-limit,effective-spawner-limit";
            case "effective-stack-deactivate", "effective-stack-export" ->
                "stacked-entity-state,stacked-spawner-state,effective-limit-keys";
            case "effective-stack-restore" -> "stacked-entity-state,stacked-spawner-state,restore-count-plan";
            default -> "";
        };
    }

    @Override
    protected String externalSafetyBarriers(String operation) {
        return switch (operation == null ? "" : operation) {
            case "effective-stack-activate", "effective-stack-deactivate", "effective-stack-export", "effective-stack-restore" ->
                "runtime-authority,fencing-token,idempotency-key,effective-count-key,bundle-key";
            default -> "";
        };
    }
}
