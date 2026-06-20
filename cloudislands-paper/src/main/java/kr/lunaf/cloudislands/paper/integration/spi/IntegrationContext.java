package kr.lunaf.cloudislands.paper.integration.spi;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    public Set<String> missingMetadata(String... keys) {
        if (keys == null || keys.length == 0) {
            return Set.of();
        }
        return java.util.Arrays.stream(keys)
            .filter(key -> key == null || key.isBlank() || !metadata.containsKey(key) || metadata.get(key).isBlank())
            .collect(Collectors.toUnmodifiableSet());
    }
}
