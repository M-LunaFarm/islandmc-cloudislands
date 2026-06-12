package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

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
        return constantTimeEquals(header, "Bearer " + expectedToken);
    }

    private boolean constantTimeEquals(String actual, String expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }
}
