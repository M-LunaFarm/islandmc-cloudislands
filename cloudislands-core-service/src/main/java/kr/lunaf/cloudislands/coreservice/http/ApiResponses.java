package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class ApiResponses {
    private ApiResponses() {}

    public static void json(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().put("Content-Type", List.of("application/json; charset=utf-8"));
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream response = exchange.getResponseBody()) {
            response.write(bytes);
        }
    }

    public static String ok(boolean accepted) {
        return SimpleJson.stringify(Map.of("accepted", accepted));
    }

    public static String error(String code, String message) {
        return error(code, message, Map.of());
    }

    public static String error(String code, String message, Map<String, String> details) {
        LinkedHashMap<String, Object> error = new LinkedHashMap<>();
        error.put("code", safe(code));
        error.put("message", safe(message));
        error.put("timestamp", Instant.now());
        if (details != null && !details.isEmpty()) {
            LinkedHashMap<String, String> safeDetails = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : details.entrySet()) {
                safeDetails.put(safe(entry.getKey()), safe(entry.getValue()));
            }
            error.put("details", safeDetails);
        }
        return SimpleJson.stringify(Map.of("error", error));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
