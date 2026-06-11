package kr.lunaf.cloudislands.coreservice.http;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class JsonFields {
    private JsonFields() {}

    public static String text(String json, String field, String fallback) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? fallback : json.substring(valueStart, end);
    }

    public static Map<String, String> object(String json, String field) {
        int fieldStart = json.indexOf("\"" + field + "\":");
        if (fieldStart < 0) {
            return Map.of();
        }
        int objectStart = json.indexOf('{', fieldStart);
        if (objectStart < 0) {
            return Map.of();
        }
        int depth = 0;
        int objectEnd = -1;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) {
                objectEnd = i;
                break;
            }
        }
        if (objectEnd < 0) {
            return Map.of();
        }
        String body = json.substring(objectStart + 1, objectEnd).trim();
        if (body.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                values.put(unquote(pair.substring(0, colon)), unquote(pair.substring(colon + 1)));
            }
        }
        return Map.copyOf(values);
    }

    public static int integer(String json, String field, int fallback) {
        String value = scalar(json, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static long longValue(String json, String field, long fallback) {
        String value = scalar(json, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static double decimal(String json, String field, double fallback) {
        String value = scalar(json, field);
        if (value == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static boolean bool(String json, String field, boolean fallback) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
        String tail = json.substring(valueStart).trim();
        if (tail.startsWith("true")) {
            return true;
        }
        if (tail.startsWith("false")) {
            return false;
        }
        return Boolean.parseBoolean(text(json, field, Boolean.toString(fallback)));
    }

    public static UUID uuid(String json, String field, UUID fallback) {
        try {
            return UUID.fromString(text(json, field, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String json, String field, E fallback) {
        try {
            return Enum.valueOf(type, text(json, field, fallback.name()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String scalar(String json, String field) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length() && "0123456789.-".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        return end == valueStart ? null : json.substring(valueStart, end);
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
