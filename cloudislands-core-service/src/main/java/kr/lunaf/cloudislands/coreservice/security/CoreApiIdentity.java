package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;

public final class CoreApiIdentity {
    public static final String NODE_ID_HEADER = "X-CloudIslands-Node-Id";

    private static final String AUTHENTICATED_NODE_ID = "cloudislands.authenticatedNodeId";
    private static final String NODE_CREDENTIAL_BINDING_CONFIGURED = "cloudislands.nodeCredentialBindingConfigured";

    private CoreApiIdentity() {
    }

    static void apply(HttpExchange exchange, CoreApiAuthentication authentication) {
        if (exchange == null || authentication == null) {
            return;
        }
        exchange.setAttribute(AUTHENTICATED_NODE_ID, authentication.nodeId());
        exchange.setAttribute(NODE_CREDENTIAL_BINDING_CONFIGURED, authentication.nodeCredentialBindingConfigured());
    }

    public static String authenticatedNodeId(HttpExchange exchange) {
        Object value = exchange == null ? null : exchange.getAttribute(AUTHENTICATED_NODE_ID);
        return value instanceof String text ? text : "";
    }

    public static boolean nodeCredentialBindingConfigured(HttpExchange exchange) {
        Object value = exchange == null ? null : exchange.getAttribute(NODE_CREDENTIAL_BINDING_CONFIGURED);
        return Boolean.TRUE.equals(value);
    }
}
