package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import kr.lunaf.cloudislands.coreservice.security.CoreApiIdentity;

public final class NodeScopedRequestGuard {
    private NodeScopedRequestGuard() {
    }

    public static boolean allowNode(HttpExchange exchange, String requestedNodeId) throws IOException {
        String requested = normalize(requestedNodeId);
        String authenticated = normalize(CoreApiIdentity.authenticatedNodeId(exchange));
        if (authenticated.isBlank()) {
            if (CoreApiIdentity.nodeCredentialBindingConfigured(exchange)) {
                CoreHttpResponses.write(exchange, 403, ApiResponses.error("NODE_CREDENTIAL_REQUIRED", "Node credential binding is required for node-scoped operations"));
                return false;
            }
            return true;
        }
        if (!authenticated.equals(requested)) {
            CoreHttpResponses.write(exchange, 403, ApiResponses.error("NODE_ID_MISMATCH", "Authenticated node identity does not match the requested node"));
            return false;
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
