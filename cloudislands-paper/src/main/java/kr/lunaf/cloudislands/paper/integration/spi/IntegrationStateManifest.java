package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record IntegrationStateManifest(
    String pluginName,
    String category,
    String operation,
    UUID islandId,
    String nodeId,
    long fencingToken,
    String idempotencyKey,
    String world,
    String cell,
    String bundleKey,
    Map<String, String> metadata
) {
    public IntegrationStateManifest {
        pluginName = requireText(pluginName, "pluginName");
        category = category == null ? "" : category.trim();
        operation = requireText(operation, "operation");
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        nodeId = requireText(nodeId, "nodeId");
        if (fencingToken <= 0L) {
            throw new IllegalArgumentException("fencingToken must be positive");
        }
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        world = requireText(world, "world");
        cell = requireText(cell, "cell");
        bundleKey = bundleKey == null ? "" : bundleKey.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static IntegrationStateManifest from(String pluginName, String category, String operation, IntegrationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        return new IntegrationStateManifest(
            pluginName,
            category,
            operation,
            context.islandId(),
            context.nodeId(),
            context.fencingToken(),
            context.idempotencyKey(),
            context.metadata().get("world"),
            context.metadata().get("cell"),
            context.metadata().get("bundleKey"),
            context.metadata()
        );
    }

    public Map<String, String> details() {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("manifest.plugin", pluginName);
        details.put("manifest.category", category);
        details.put("manifest.operation", operation);
        details.put("manifest.islandId", islandId.toString());
        details.put("manifest.nodeId", nodeId);
        details.put("manifest.fencingToken", Long.toString(fencingToken));
        details.put("manifest.idempotencyKey", idempotencyKey);
        details.put("manifest.world", world);
        details.put("manifest.cell", cell);
        details.put("manifest.bundleKey", bundleKey);
        details.put("manifest.runtimeScope", world + ":" + cell);
        details.put("manifest.stateKey", stateKey());
        details.put("manifest.bundleRelativePath", bundleRelativePath());
        metadata.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> details.put("manifest.metadata." + entry.getKey(), entry.getValue()));
        return Map.copyOf(details);
    }

    private String stateKey() {
        String safeBundleKey = bundleKey.isBlank() ? "live" : bundleKey;
        return category + "/" + pluginName + "/" + islandId + "/" + safeBundleKey + "/" + operation;
    }

    private String bundleRelativePath() {
        return "integrations/" + category + "/" + pluginName + "/" + operation + ".json";
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }
}
