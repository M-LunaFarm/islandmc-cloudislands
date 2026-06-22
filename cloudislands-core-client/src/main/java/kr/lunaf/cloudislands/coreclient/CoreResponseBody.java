package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

record CoreResponseBody(String value, int status, boolean accepted) {
    CoreResponseBody {
        value = value == null ? "" : value;
    }

    public String value() {
        if (!accepted) {
            throw error();
        }
        return value;
    }

    private CoreApiException error() {
        String code = "HTTP_" + status;
        String message = "Core API request failed with HTTP status " + status;
        if (!value.isBlank()) {
            try {
                Map<?, ?> root = SimpleJson.object(SimpleJson.parse(value));
                Map<?, ?> error = SimpleJson.object(root.get("error"));
                code = firstText(root, error, "code", code);
                message = firstText(root, error, "message", message);
            } catch (RuntimeException ignored) {
                message = value;
            }
        }
        return new CoreApiException(code, message, status);
    }

    private static String firstText(Map<?, ?> root, Map<?, ?> error, String key, String fallback) {
        String value = SimpleJson.text(root.get(key));
        if (!value.isBlank()) {
            return value;
        }
        value = SimpleJson.text(error.get(key));
        return value.isBlank() ? fallback : value;
    }
}
