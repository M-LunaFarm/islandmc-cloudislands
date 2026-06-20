package kr.lunaf.cloudislands.api.integration;

import java.util.Map;
import java.util.UUID;

public record CloudIntegrationContext(
    String pluginName,
    UUID islandId,
    String nodeId,
    long fencingToken,
    boolean nodeOwnsIsland,
    String idempotencyKey,
    Map<String, String> attributes
) {
    public CloudIntegrationContext {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
