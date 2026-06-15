package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        return "{\"accepted\":" + accepted + "}";
    }

    public static String error(String code, String message) {
        return "{\"error\":{\"code\":\"" + escape(code) + "\",\"message\":\"" + escape(message) + "\",\"timestamp\":\"" + Instant.now() + "\"}}";
    }

    public static String error(String code, String message, Map<String, String> details) {
        StringBuilder builder = new StringBuilder("{\"error\":{\"code\":\"")
            .append(escape(code))
            .append("\",\"message\":\"")
            .append(escape(message))
            .append("\",\"timestamp\":\"")
            .append(Instant.now())
            .append("\"");
        if (details != null && !details.isEmpty()) {
            builder.append(",\"details\":{");
            boolean first = true;
            for (Map.Entry<String, String> entry : details.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"')
                    .append(escape(entry.getKey()))
                    .append("\":\"")
                    .append(escape(entry.getValue()))
                    .append('"');
            }
            builder.append('}');
        }
        return builder.append("}}").toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
