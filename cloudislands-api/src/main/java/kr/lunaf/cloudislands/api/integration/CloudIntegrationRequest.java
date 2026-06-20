package kr.lunaf.cloudislands.api.integration;

import java.util.Map;

public record CloudIntegrationRequest(
    String action,
    boolean coreStateMutation,
    Map<String, String> payload
) {
    public CloudIntegrationRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
