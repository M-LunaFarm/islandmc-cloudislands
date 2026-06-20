package kr.lunaf.cloudislands.api.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface CloudIntegrationPort {
    String pluginName();

    IntegrationCategory category();

    CompletableFuture<CloudIntegrationResult> apply(CloudIntegrationContext context, CloudIntegrationRequest request);

    default CompletableFuture<CloudIntegrationResult> applyValidated(CloudIntegrationContext context, CloudIntegrationRequest request) {
        List<String> violations = validateIntegrationContext(context, request);
        if (!violations.isEmpty()) {
            return CompletableFuture.completedFuture(new CloudIntegrationResult(
                false,
                "DISTRIBUTED_CONTEXT_REJECTED",
                "integration hook rejected before core authority mutation",
                Map.of(
                    "plugin", pluginName(),
                    "category", category().name(),
                    "violations", String.join(",", violations)
                )
            ));
        }
        return apply(context, request);
    }

    default List<String> validateIntegrationContext(CloudIntegrationContext context, CloudIntegrationRequest request) {
        List<String> violations = new ArrayList<>();
        if (context == null) {
            violations.add("context-missing");
            return List.copyOf(violations);
        }
        if (request == null) {
            violations.add("request-missing");
            return List.copyOf(violations);
        }
        if (blank(context.pluginName())) {
            violations.add("plugin-name-missing");
        } else if (!context.pluginName().equals(pluginName())) {
            violations.add("plugin-name-mismatch");
        }
        if (requiresRuntimeAuthority(request)) {
            if (context.islandId() == null) {
                violations.add("island-uuid-missing");
            }
            if (blank(context.nodeId())) {
                violations.add("node-id-missing");
            }
            if (context.fencingToken() <= 0L) {
                violations.add("runtime-fencing-token-missing");
            }
            if (!context.nodeOwnsIsland()) {
                violations.add("node-ownership-missing");
            }
            if (blank(context.idempotencyKey())) {
                violations.add("core-idempotency-key-missing");
            }
        }
        return List.copyOf(violations);
    }

    default boolean requiresRuntimeAuthority(CloudIntegrationRequest request) {
        if (request != null && request.coreStateMutation()) {
            return true;
        }
        return switch (category()) {
            case AUDIT_ROLLBACK, WORLD_EDIT, CUSTOM_ITEMS, STACKER, SPAWNER, ECONOMY -> true;
            case PERMISSION, ANALYTICS, PRESENCE, PLACEHOLDER, PROTOCOL, IDENTITY, WORLD_STORAGE, SERVER_TOOLS -> false;
        };
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
