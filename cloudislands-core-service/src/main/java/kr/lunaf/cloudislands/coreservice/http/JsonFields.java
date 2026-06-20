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
        String body = objectBody(json, field);
        if (body.isBlank()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : splitTopLevelPairs(body)) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                values.put(unquote(pair.substring(0, colon)), unquote(pair.substring(colon + 1)));
            }
        }
        return Map.copyOf(values);
    }

    public static Map<String, Map<String, String>> objectMap(String json, String field) {
        String body = objectBody(json, field);
        if (body.isBlank()) {
            return Map.of();
        }
        Map<String, Map<String, String>> values = new LinkedHashMap<>();
        for (String pair : splitTopLevelPairs(body)) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon));
            Map<String, String> nested = object("{\"value\":" + pair.substring(colon + 1) + "}", "value");
            if (!key.isBlank() && !nested.isEmpty()) {
                values.put(key, nested);
            }
        }
        return Map.copyOf(values);
    }

    public static java.util.List<String> objects(String json, String field) {
        String body = arrayBody(json, field);
        if (body.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> objects = new java.util.ArrayList<>();
        for (String part : splitTopLevelPairs(body)) {
            String trimmed = part.trim();
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                objects.add(trimmed);
            }
        }
        return java.util.List.copyOf(objects);
    }

    private static String arrayBody(String json, String field) {
        int fieldStart = json.indexOf("\"" + field + "\":");
        if (fieldStart < 0) {
            return "";
        }
        int arrayStart = json.indexOf('[', fieldStart);
        if (arrayStart < 0) {
            return "";
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        int arrayEnd = -1;
        for (int i = arrayStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && c == '[') depth++;
            if (!quoted && c == ']') depth--;
            if (!quoted && depth == 0) {
                arrayEnd = i;
                break;
            }
        }
        if (arrayEnd < 0) {
            return "";
        }
        return json.substring(arrayStart + 1, arrayEnd).trim();
    }

    private static String objectBody(String json, String field) {
        int fieldStart = json.indexOf("\"" + field + "\":");
        if (fieldStart < 0) {
            return "";
        }
        int objectStart = json.indexOf('{', fieldStart);
        if (objectStart < 0) {
            return "";
        }
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        int objectEnd = -1;
        for (int i = objectStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (!quoted && c == '{') depth++;
            if (!quoted && c == '}') depth--;
            if (!quoted && depth == 0) {
                objectEnd = i;
                break;
            }
        }
        if (objectEnd < 0) {
            return "";
        }
        return json.substring(objectStart + 1, objectEnd).trim();
    }

    private static java.util.List<String> splitTopLevelPairs(String body) {
        java.util.List<String> pairs = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = 0; index < body.length(); index++) {
            char value = body.charAt(index);
            if (escaped) {
                current.append(value);
                escaped = false;
                continue;
            }
            if (value == '\\') {
                current.append(value);
                escaped = true;
                continue;
            }
            if (value == '"') {
                quoted = !quoted;
            }
            if (!quoted && (value == '{' || value == '[')) {
                depth++;
            }
            if (!quoted && (value == '}' || value == ']')) {
                depth--;
            }
            if (value == ',' && !quoted && depth == 0) {
                pairs.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(value);
        }
        if (!current.isEmpty()) {
            pairs.add(current.toString());
        }
        return pairs;
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
