package kr.lunaf.cloudislands.coreservice.http;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.JsonCodec;
import kr.lunaf.cloudislands.common.json.JsonCodecException;

public final class JsonFields {
    private JsonFields() {
    }

    public static String text(String json, String field, String fallback) {
        Object value = field(json, field);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text) {
            return text;
        }
        throw invalidRequest("Field '" + field + "' must be a string");
    }

    public static Map<String, String> object(String json, String field) {
        Object value = field(json, field);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw invalidRequest("Field '" + field + "' must be an object");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object item = entry.getValue();
            if (item instanceof Map<?, ?> || item instanceof List<?>) {
                throw invalidRequest("Field '" + field + "' must contain scalar values");
            }
            values.put(String.valueOf(entry.getKey()), item == null ? "" : String.valueOf(item));
        }
        return Map.copyOf(values);
    }

    public static Map<String, Map<String, String>> objectMap(String json, String field) {
        Object value = field(json, field);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw invalidRequest("Field '" + field + "' must be an object");
        }
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> nested)) {
                throw invalidRequest("Field '" + field + "' must contain nested objects");
            }
            Map<String, String> nestedValues = new LinkedHashMap<>();
            for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                Object item = nestedEntry.getValue();
                if (item instanceof Map<?, ?> || item instanceof List<?>) {
                    throw invalidRequest("Field '" + field + "' nested objects must contain scalar values");
                }
                nestedValues.put(String.valueOf(nestedEntry.getKey()), item == null ? "" : String.valueOf(item));
            }
            values.put(String.valueOf(entry.getKey()), Map.copyOf(nestedValues));
        }
        return Map.copyOf(values);
    }

    public static List<String> objects(String json, String field) {
        Object value = field(json, field);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw invalidRequest("Field '" + field + "' must be an array");
        }
        return list.stream()
            .map(item -> {
                if (!(item instanceof Map<?, ?>)) {
                    throw invalidRequest("Field '" + field + "' must contain objects");
                }
                return JsonCodec.write(item);
            })
            .toList();
    }

    public static int integer(String json, String field, int fallback) {
        Object value = field(json, field);
        if (value == null) {
            return fallback;
        }
        Number number = number(value, field);
        try {
            return Math.toIntExact(number.longValue());
        } catch (ArithmeticException exception) {
            throw invalidRequest("Field '" + field + "' is outside the integer range");
        }
    }

    public static long longValue(String json, String field, long fallback) {
        Object value = field(json, field);
        if (value == null) {
            return fallback;
        }
        Number number = number(value, field);
        if (number instanceof BigInteger bigInteger) {
            try {
                return bigInteger.longValueExact();
            } catch (ArithmeticException exception) {
                throw invalidRequest("Field '" + field + "' is outside the long range");
            }
        }
        if (number instanceof BigDecimal bigDecimal) {
            try {
                return bigDecimal.toBigIntegerExact().longValueExact();
            } catch (ArithmeticException exception) {
                throw invalidRequest("Field '" + field + "' must be an integer");
            }
        }
        return number.longValue();
    }

    public static double decimal(String json, String field, double fallback) {
        Object value = field(json, field);
        if (value == null) {
            return fallback;
        }
        Number number = number(value, field);
        double decimal = number.doubleValue();
        if (!Double.isFinite(decimal)) {
            throw invalidRequest("Field '" + field + "' must be a finite number");
        }
        return decimal;
    }

    public static boolean bool(String json, String field, boolean fallback) {
        Object value = field(json, field);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw invalidRequest("Field '" + field + "' must be a boolean");
    }

    public static UUID uuid(String json, String field, UUID fallback) {
        Object value = field(json, field);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String text)) {
            throw invalidRequest("Field '" + field + "' must be a UUID string");
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("Field '" + field + "' must be a valid UUID");
        }
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String json, String field, E fallback) {
        Object value = field(json, field);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof String text)) {
            throw invalidRequest("Field '" + field + "' must be a string enum value");
        }
        try {
            return Enum.valueOf(type, text);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest("Field '" + field + "' is not a valid " + type.getSimpleName());
        }
    }

    private static Object field(String json, String field) {
        try {
            return JsonCodec.readObject(json).get(field);
        } catch (JsonCodecException exception) {
            if (exception.kind() == JsonCodecException.Kind.INVALID_JSON) {
                throw new CoreHttpException(400, "INVALID_JSON", exception.getMessage());
            }
            throw new CoreHttpException(400, "INVALID_REQUEST", exception.getMessage());
        }
    }

    private static Number number(Object value, String field) {
        if (value instanceof Number number) {
            return number;
        }
        throw invalidRequest("Field '" + field + "' must be a number");
    }

    private static CoreHttpException invalidRequest(String message) {
        return new CoreHttpException(400, "INVALID_REQUEST", message);
    }
}
