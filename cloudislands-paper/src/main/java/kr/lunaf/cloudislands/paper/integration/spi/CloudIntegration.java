package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.Set;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;

public interface CloudIntegration {
    String pluginName();

    String category();

    Set<IntegrationCapability> capabilities();

    default boolean detect(boolean pluginEnabled) {
        return pluginEnabled;
    }

    default IntegrationResult validateVersion(IntegrationContext context) {
        return IntegrationResult.skipped(pluginName() + " version validation hook is not implemented");
    }

    default CloudIntegrationPolicy.HookDecision validateRuntimeAuthority(IntegrationContext context, boolean coreStateMutation) {
        return CloudIntegrationPolicy.validateHookContext(new CloudIntegrationPolicy.HookContext(
            pluginName(),
            context == null ? null : context.islandId(),
            context == null ? "" : context.nodeId(),
            context == null ? 0L : context.fencingToken(),
            context != null && context.nodeOwnsIsland(),
            context == null ? "" : context.idempotencyKey(),
            coreStateMutation
        ));
    }

    default IntegrationResult onIslandActivate(IntegrationContext context) {
        return IntegrationResult.skipped(pluginName() + " activate hook is not implemented");
    }

    default IntegrationResult onIslandDeactivate(IntegrationContext context) {
        return IntegrationResult.skipped(pluginName() + " deactivate hook is not implemented");
    }

    default IntegrationResult exportState(IntegrationContext context) {
        return IntegrationResult.skipped(pluginName() + " export hook is not implemented");
    }

    default IntegrationResult restoreState(IntegrationContext context) {
        return IntegrationResult.skipped(pluginName() + " restore hook is not implemented");
    }
}
