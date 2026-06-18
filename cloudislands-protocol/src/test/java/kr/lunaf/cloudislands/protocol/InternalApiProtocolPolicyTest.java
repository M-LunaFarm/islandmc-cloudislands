package kr.lunaf.cloudislands.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InternalApiProtocolPolicyTest {
    @Test
    void pinsInternalHttpAndGrpcTransportContract() {
        assertEquals("http-and-grpc-share-versioned-request-response-dtos", InternalApiProtocolPolicy.TRANSPORT_POLICY);
        assertEquals("grpc-methods-must-match-internal-http-semantics-and-auth-controls", InternalApiProtocolPolicy.GRPC_PARITY_POLICY);
        assertEquals(List.of("http-json", "grpc"), InternalApiProtocolPolicy.transports());
        assertTrue(InternalApiProtocolPolicy.transport("grpc"));
        assertTrue(InternalApiProtocolPolicy.transport(" HTTP-JSON "));
        assertFalse(InternalApiProtocolPolicy.transport("plugin-message"));
    }

    @Test
    void recordsProtocolDtoFamiliesFromThePackagePlan() {
        assertEquals(
            List.of("request-response-dto", "event-dto", "job-dto", "route-ticket-dto", "version-negotiation"),
            InternalApiProtocolPolicy.dtoFamilies()
        );
        assertTrue(InternalApiProtocolPolicy.dtoFamily("route-ticket-dto"));
        assertTrue(InternalApiProtocolPolicy.dtoFamily("VERSION-NEGOTIATION"));
        assertFalse(InternalApiProtocolPolicy.dtoFamily("server-local-object"));
    }

    @Test
    void exposesRequiredInternalApiEndpoints() {
        assertEquals(16, InternalApiProtocolPolicy.requiredEndpoints().size());
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("GET /v1/islands/{islandId}"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("GET /v1/islands/by-owner/{playerUuid}"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("GET /v1/players/{playerUuid}/island"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("POST /v1/routes/home"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("POST /v1/routes/visit"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("POST /v1/routes/warp"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("POST /v1/admin/islands/{islandId}/activate"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint("POST /v1/admin/nodes/{nodeId}/drain"));
        assertTrue(InternalApiProtocolPolicy.requiredEndpoint(" POST   /v1/admin/nodes/{nodeId}/undrain "));
        assertFalse(InternalApiProtocolPolicy.requiredEndpoint("POST /v1/admin/raw-sql"));
    }

    @Test
    void requiresSecurityControlsForInternalApi() {
        assertEquals("mtls-or-api-token-plus-ip-allowlist-admin-permission-audit-log-and-rate-limit", InternalApiProtocolPolicy.AUTH_POLICY);
        assertEquals(
            List.of("mtls-or-api-token", "ip-allowlist", "admin-endpoint-permission", "audit-log", "rate-limit"),
            InternalApiProtocolPolicy.securityControls()
        );
        assertTrue(InternalApiProtocolPolicy.securityControl("api-token"));
        assertTrue(InternalApiProtocolPolicy.securityControl("admin-endpoint-permission"));
        assertFalse(InternalApiProtocolPolicy.securityControl("trusted-internal-network"));
    }

    @Test
    void keepsWritesAndSyncEventsBehindSafeBoundaries() {
        assertEquals("all-write-operations-route-through-core-api-transaction-boundary", InternalApiProtocolPolicy.WRITE_AUTHORITY_POLICY);
        assertEquals("paper-sync-events-use-local-cache-never-core-api-database-redis-or-grpc", InternalApiProtocolPolicy.SYNC_EVENT_READ_POLICY);
        assertEquals(
            "operations-web-panel-discord-bot-and-admin-tools",
            InternalApiProtocolPolicy.apiLayers().get("internal-http-grpc-api")
        );
    }
}
