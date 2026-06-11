package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;

public final class AdminEndpointGuard {
    private final String adminToken;

    public AdminEndpointGuard(String adminToken) {
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    public boolean allowed(String path, HttpExchange exchange) {
        if (!path.startsWith("/v1/admin") && !path.equals("/v1/audit")) {
            return true;
        }
        if (adminToken.isBlank()) {
            return true;
        }
        String header = exchange.getRequestHeaders().getFirst("X-CloudIslands-Admin-Token");
        return adminToken.equals(header);
    }
}
