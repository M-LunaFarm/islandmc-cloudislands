package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ApiTokenGuard {
    private final String expectedToken;
    private final NodeCredentialBindings nodeCredentials;

    public ApiTokenGuard(String expectedToken) {
        this(expectedToken, new NodeCredentialBindings(java.util.Map.of()));
    }

    public ApiTokenGuard(String expectedToken, NodeCredentialBindings nodeCredentials) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
        this.nodeCredentials = nodeCredentials == null ? new NodeCredentialBindings(java.util.Map.of()) : nodeCredentials;
    }

    public boolean allowed(HttpExchange exchange) {
        return authenticate(exchange).allowed();
    }

    CoreApiAuthentication authenticate(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        String token = bearerToken(header);
        if (token.isBlank()) {
            return CoreApiAuthentication.denied(nodeCredentials.configured());
        }
        String nodeId = NodeCredentialBindings.normalizeNodeId(exchange.getRequestHeaders().getFirst(CoreApiIdentity.NODE_ID_HEADER));
        if (!nodeId.isBlank()) {
            if (nodeCredentials.configured()) {
                return nodeCredentials.tokenMatches(nodeId, token)
                    ? CoreApiAuthentication.allowed(nodeId, true)
                    : CoreApiAuthentication.denied(true);
            }
            return constantTimeEquals(token, expectedToken)
                ? CoreApiAuthentication.allowed(nodeId, false)
                : CoreApiAuthentication.denied(false);
        }
        return constantTimeEquals(token, expectedToken)
            ? CoreApiAuthentication.allowed("", nodeCredentials.configured())
            : CoreApiAuthentication.denied(nodeCredentials.configured());
    }

    private static String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return "";
        }
        return header.substring("Bearer ".length());
    }

    private boolean constantTimeEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
