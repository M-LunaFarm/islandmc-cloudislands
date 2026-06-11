package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;

public final class ApiTokenGuard {
    private final String expectedToken;

    public ApiTokenGuard(String expectedToken) {
        this.expectedToken = expectedToken == null ? "" : expectedToken;
    }

    public boolean allowed(HttpExchange exchange) {
        if (expectedToken.isBlank()) {
            return false;
        }
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.equals("Bearer " + expectedToken);
    }
}
