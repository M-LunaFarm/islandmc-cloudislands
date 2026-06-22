package kr.lunaf.cloudislands.coreclient;

import java.util.Map;

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
        Map<String, String> details = Map.of();
        String requestId = "";
        if (!value.isBlank()) {
            try {
                Map<?, ?> root = CoreJson.object(value);
                Map<?, ?> error = CoreJson.objectValue(root, "error");
                code = firstText(root, error, "code", code);
                message = firstText(root, error, "message", message);
                details = CoreJson.stringMap(CoreJson.objectValue(error, "details"));
                requestId = firstText(root, error, "requestId", "");
            } catch (RuntimeException ignored) {
                message = value;
            }
        }
        return new CoreApiException(code, message, status, details, requestId);
    }

    private static String firstText(Map<?, ?> root, Map<?, ?> error, String key, String fallback) {
        String value = CoreJson.text(root, key);
        if (!value.isBlank()) {
            return value;
        }
        value = CoreJson.text(error, key);
        return value.isBlank() ? fallback : value;
    }
}
