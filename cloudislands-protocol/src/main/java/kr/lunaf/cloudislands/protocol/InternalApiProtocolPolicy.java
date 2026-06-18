package kr.lunaf.cloudislands.protocol;

import java.util.List;
import java.util.Map;

public final class InternalApiProtocolPolicy {
    public static final String TRANSPORT_POLICY = "http-and-grpc-share-versioned-request-response-dtos";
    public static final String GRPC_PARITY_POLICY = "grpc-methods-must-match-internal-http-semantics-and-auth-controls";
    public static final String AUTH_POLICY = "mtls-or-api-token-plus-ip-allowlist-admin-permission-audit-log-and-rate-limit";
    public static final String WRITE_AUTHORITY_POLICY = "all-write-operations-route-through-core-api-transaction-boundary";
    public static final String SYNC_EVENT_READ_POLICY = "paper-sync-events-use-local-cache-never-core-api-database-redis-or-grpc";

    private static final List<String> TRANSPORTS = List.of(
        "http-json",
        "grpc"
    );

    private static final List<String> DTO_FAMILIES = List.of(
        "request-response-dto",
        "event-dto",
        "job-dto",
        "route-ticket-dto",
        "version-negotiation"
    );

    private static final List<String> REQUIRED_ENDPOINTS = List.of(
        "GET /v1/islands/{islandId}",
        "GET /v1/islands/by-owner/{playerUuid}",
        "GET /v1/players/{playerUuid}/island",
        "GET /v1/islands/{islandId}/members",
        "GET /v1/islands/{islandId}/runtime",
        "GET /v1/islands/{islandId}/flags",
        "POST /v1/islands",
        "DELETE /v1/islands/{islandId}",
        "POST /v1/routes/home",
        "POST /v1/routes/visit",
        "POST /v1/routes/warp",
        "POST /v1/admin/islands/{islandId}/activate",
        "POST /v1/admin/islands/{islandId}/deactivate",
        "POST /v1/admin/islands/{islandId}/migrate",
        "POST /v1/admin/nodes/{nodeId}/drain",
        "POST /v1/admin/nodes/{nodeId}/undrain"
    );

    private static final List<String> SECURITY_CONTROLS = List.of(
        "mtls-or-api-token",
        "ip-allowlist",
        "admin-endpoint-permission",
        "audit-log",
        "rate-limit"
    );

    private static final Map<String, String> API_LAYERS = Map.of(
        "java-plugin-api", "paper-plugin-facing-async-service-contracts",
        "internal-http-grpc-api", "operations-web-panel-discord-bot-and-admin-tools",
        "event-api", "local-paper-events-and-global-append-only-events"
    );

    private InternalApiProtocolPolicy() {
    }

    public static List<String> transports() {
        return TRANSPORTS;
    }

    public static boolean transport(String value) {
        return value != null && TRANSPORTS.contains(value.trim().toLowerCase());
    }

    public static List<String> dtoFamilies() {
        return DTO_FAMILIES;
    }

    public static boolean dtoFamily(String value) {
        return value != null && DTO_FAMILIES.contains(value.trim().toLowerCase());
    }

    public static List<String> requiredEndpoints() {
        return REQUIRED_ENDPOINTS;
    }

    public static boolean requiredEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        String normalized = endpoint.trim().replaceAll("\\s+", " ");
        return REQUIRED_ENDPOINTS.stream().anyMatch(normalized::equals);
    }

    public static List<String> securityControls() {
        return SECURITY_CONTROLS;
    }

    public static boolean securityControl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return SECURITY_CONTROLS.contains(normalized)
            || (normalized.equals("mtls") && SECURITY_CONTROLS.contains("mtls-or-api-token"))
            || (normalized.equals("api-token") && SECURITY_CONTROLS.contains("mtls-or-api-token"));
    }

    public static Map<String, String> apiLayers() {
        return API_LAYERS;
    }

    public static String endpointSummary() {
        return String.join(",", REQUIRED_ENDPOINTS);
    }

    public static String securityControlSummary() {
        return String.join(",", SECURITY_CONTROLS);
    }
}
