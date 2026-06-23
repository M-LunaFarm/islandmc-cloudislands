package kr.lunaf.cloudislands.coreclient;

import java.math.BigDecimal;
import java.math.BigInteger;
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

    static List<Map<?, ?>> entries(String body, String... keys) {
        Object parsed = value(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        if (keys != null && keys.length > 0) {
            for (String key : keys) {
                List<Map<?, ?>> entries = objects(root, key);
                if (!entries.isEmpty()) {
                    return entries;
                }
            }
            return List.of();
        }
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
            entry -> textValue(entry.getKey()),
            entry -> textValue(entry.getValue())
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
        return root == null ? "" : textValue(root.get(key));
    }

    static String textValue(Object value) {
        return SimpleJson.text(value);
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
        return root == null ? 0L : numberValue(root.get(key));
    }

    static long numberValue(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger integer) {
            try {
                return integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw invalidNumber(value);
            }
        }
        if (value instanceof BigDecimal decimal) {
            try {
                return decimal.toBigIntegerExact().longValueExact();
            } catch (ArithmeticException exception) {
                throw invalidNumber(value);
            }
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            long integer = ((Number) value).longValue();
            if (!Double.isFinite(number) || number != integer) {
                throw invalidNumber(value);
            }
            return integer;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                throw invalidNumber(value);
            }
        }
        throw invalidNumber(value);
    }

    static double decimal(Map<?, ?> root, String key) {
        if (root == null) {
            return 0.0D;
        }
        return decimalValue(root.get(key));
    }

    static double decimalValue(Object value) {
        if (value == null) {
            return 0.0D;
        }
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            if (!Double.isFinite(decimal)) {
                throw invalidDecimal(value);
            }
            return decimal;
        }
        String text = SimpleJson.text(value).trim();
        if (text.isBlank()) {
            return 0.0D;
        }
        try {
            double decimal = Double.parseDouble(text);
            if (!Double.isFinite(decimal)) {
                throw invalidDecimal(value);
            }
            return decimal;
        } catch (NumberFormatException ignored) {
            throw invalidDecimal(value);
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

    private static CoreApiException invalidNumber(Object value) {
        return new CoreApiException("INVALID_CORE_JSON", "Core API numeric value is not an integer: " + SimpleJson.text(value));
    }

    private static CoreApiException invalidDecimal(Object value) {
        return new CoreApiException("INVALID_CORE_JSON", "Core API numeric value is not decimal: " + SimpleJson.text(value));
    }
}
