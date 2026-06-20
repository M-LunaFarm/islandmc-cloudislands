package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.Map;
import java.util.UUID;

public record IntegrationContext(
    UUID islandId,
    String nodeId,
    long fencingToken,
    boolean nodeOwnsIsland,
    String idempotencyKey,
    Map<String, String> metadata
) {
    public IntegrationContext {
        nodeId = nodeId == null ? "" : nodeId.trim();
        idempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
