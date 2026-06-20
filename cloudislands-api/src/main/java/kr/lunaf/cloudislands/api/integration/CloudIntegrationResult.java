package kr.lunaf.cloudislands.api.integration;

import java.util.Map;

public record CloudIntegrationResult(
    boolean accepted,
    String code,
    String message,
    Map<String, String> metadata
) {
    public CloudIntegrationResult {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
