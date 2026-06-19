package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CoreHttpResponses {
    private CoreHttpResponses() {
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    public static void write(HttpExchange exchange, int status, String body) throws IOException {
        write(exchange, status, body, "application/json; charset=utf-8");
    }

    public static void write(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of(contentType));
        exchange.getResponseHeaders().put("Cache-Control", List.of("no-store"));
        exchange.getResponseHeaders().put("Pragma", List.of("no-cache"));
        exchange.getResponseHeaders().put("X-Content-Type-Options", List.of("nosniff"));
        exchange.getResponseHeaders().put("X-Frame-Options", List.of("DENY"));
        exchange.getResponseHeaders().put("Referrer-Policy", List.of("no-referrer"));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }
}
