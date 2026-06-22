package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record IntegrationOperationPlan(
    String pluginName,
    String category,
    String operation,
    String externalApi,
    boolean stateChanging,
    List<String> requiredMetadata
) {
    public IntegrationOperationPlan {
        pluginName = pluginName == null ? "" : pluginName.trim();
        category = category == null ? "" : category.trim();
        operation = operation == null ? "" : operation.trim();
        externalApi = externalApi == null ? "" : externalApi.trim();
        requiredMetadata = requiredMetadata == null
            ? List.of()
            : requiredMetadata.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    public static IntegrationOperationPlan of(String pluginName, String category, String operation, String externalApi, boolean stateChanging, String... requiredMetadata) {
        return new IntegrationOperationPlan(
            pluginName,
            category,
            operation,
            externalApi,
            stateChanging,
            requiredMetadata == null ? List.of() : Arrays.asList(requiredMetadata)
        );
    }

    public Map<String, String> details() {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("plan.plugin", pluginName);
        details.put("plan.category", category);
        details.put("plan.operation", operation);
        details.put("plan.stateChanging", Boolean.toString(stateChanging));
        details.put("plan.requiredMetadata", String.join(",", requiredMetadata));
        if (!externalApi.isBlank()) {
            details.put("plan.externalApi", externalApi);
        }
        return Map.copyOf(details);
    }
}
