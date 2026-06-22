package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.Map;

@FunctionalInterface
public interface IntegrationExternalRuntime {
    IntegrationResult invoke(
        String pluginName,
        String category,
        String operation,
        IntegrationContext context,
        IntegrationOperationPlan plan
    );

    static IntegrationExternalRuntime noop() {
        return (pluginName, category, operation, context, plan) -> IntegrationResult.success(
            "External runtime not configured for " + pluginName,
            Map.of("runtime", "none")
        );
    }
}
