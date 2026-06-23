package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CoreHttpResponses {
    public static final int MAX_REQUEST_BODY_BYTES = 1_048_576;

    private CoreHttpResponses() {
    }

    public static String readBody(HttpExchange exchange) throws IOException {
        return readBody(exchange, MAX_REQUEST_BODY_BYTES);
    }

    public static String readBody(HttpExchange exchange, int maxBytes) throws IOException {
        int limit = Math.max(1, maxBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        InputStream input = exchange.getRequestBody();
        while (true) {
            int read = input.read(buffer);
            if (read < 0) {
                break;
            }
            total += read;
            if (total > limit) {
                throw new CoreHttpException(413, "REQUEST_BODY_TOO_LARGE", "Request body exceeds " + limit + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
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
