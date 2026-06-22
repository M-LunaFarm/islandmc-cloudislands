package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreJson {
    private CoreJson() {
    }

    static Map<?, ?> object(String body) {
        return SimpleJson.object(value(body));
    }

    static Map<?, ?> actionObject(String body, String successCode) {
        String normalized = body == null ? "" : body.trim();
        if (!normalized.isBlank()) {
            char first = normalized.charAt(0);
            if (first != '{' && first != '[') {
                return Map.of("accepted", true, "code", successCode);
            }
        }
        return object(body);
    }

    static Object value(String body) {
        String normalized = body == null ? "" : body.trim();
        if (normalized.isBlank()) {
            return SimpleJson.parse("{}");
        }
        char first = normalized.charAt(0);
        if (first != '{' && first != '[') {
            throw new CoreApiException("INVALID_CORE_JSON", "Core API response is not a JSON object or array");
        }
        try {
            return SimpleJson.parse(normalized);
        } catch (RuntimeException exception) {
            throw new CoreApiException("INVALID_CORE_JSON", "Core API response could not be parsed as JSON");
        }
    }

    static List<Map<?, ?>> entries(String body) {
        Object parsed = value(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        for (Object value : root.values()) {
            if (value instanceof List<?>) {
                return SimpleJson.list(value).stream()
                    .map(SimpleJson::object)
                    .filter(map -> !map.isEmpty())
                    .toList();
            }
        }
        return root.isEmpty() ? List.of() : List.of(root);
    }

    static List<Map<?, ?>> objects(Map<?, ?> root, String key) {
        return SimpleJson.list(root == null ? null : root.get(key)).stream()
            .map(SimpleJson::object)
            .filter(map -> !map.isEmpty())
            .toList();
    }

    static Map<?, ?> objectValue(Map<?, ?> root, String key) {
        return SimpleJson.object(root == null ? null : root.get(key));
    }

    static Map<String, String> stringMap(Map<?, ?> root) {
        if (root == null || root.isEmpty()) {
            return Map.of();
        }
        return root.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            entry -> SimpleJson.text(entry.getKey()),
            entry -> SimpleJson.text(entry.getValue())
        ));
    }

    static boolean accepted(Map<?, ?> root) {
        return root != null
            && !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("ok"))
            && !Boolean.FALSE.equals(root.get("applied"));
    }

    static boolean acceptedWithCode(Map<?, ?> root, String successCode) {
        String code = text(root, "code");
        return accepted(root) && (code.isBlank() || code.equals(successCode));
    }

    static String code(Map<?, ?> root, String successCode) {
        return code(root, successCode, accepted(root));
    }

    static String code(Map<?, ?> root, String successCode, boolean accepted) {
        String code = text(root, "code");
        if (!code.isBlank()) {
            return code;
        }
        return accepted ? successCode : "FAILED";
    }

    static String text(Map<?, ?> root, String key) {
        return root == null ? "" : SimpleJson.text(root.get(key));
    }

    static String firstText(Map<?, ?> root, String... keys) {
        for (String key : keys) {
            String value = text(root, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static long number(Map<?, ?> root, String key) {
        return root == null ? 0L : SimpleJson.number(root.get(key));
    }

    static double decimal(Map<?, ?> root, String key) {
        if (root == null) {
            return 0.0D;
        }
        Object value = root.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(SimpleJson.text(value));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    static boolean bool(Map<?, ?> root, String key) {
        if (root == null) {
            return false;
        }
        Object value = root.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }

    static boolean bool(Map<?, ?> root, String key, boolean fallback) {
        if (root == null) {
            return fallback;
        }
        Object value = root.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }

    static List<String> strings(Map<?, ?> root, String key) {
        return SimpleJson.list(root == null ? null : root.get(key)).stream()
            .map(SimpleJson::text)
            .filter(text -> !text.isBlank())
            .toList();
    }
}
