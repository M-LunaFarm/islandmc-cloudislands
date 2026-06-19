package kr.lunaf.cloudislands.velocity.message;

public final class VelocityJsonFields {
    private VelocityJsonFields() {
    }

    public static String jsonValue(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json == null ? -1 : json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    public static String arrayValue(String body, String field) {
        String needle = "\"" + field + "\":[";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length() - 1;
        int depth = 0;
        for (int i = start; i < body.length(); i++) {
            char current = body.charAt(i);
            if (current == '[') {
                depth++;
            } else if (current == ']') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    public static String objectValue(String body, String field) {
        String needle = "\"" + field + "\":{";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length() - 1;
        int depth = 0;
        for (int i = start; i < body.length(); i++) {
            char current = body.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    public static boolean boolValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return false;
        }
        start += needle.length();
        while (start < body.length() && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        return body.startsWith("true", start);
    }

    public static long longValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && (body.charAt(end) == '-' || Character.isDigit(body.charAt(end)))) {
            end++;
        }
        if (end == start) {
            return 0L;
        }
        return parseLong(body.substring(start, end));
    }

    public static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    public static double doubleValue(String body, String field) {
        String needle = "\"" + field + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && (body.charAt(end) == '-' || body.charAt(end) == '+' || body.charAt(end) == '.' || Character.isDigit(body.charAt(end)))) {
            end++;
        }
        try {
            return Double.parseDouble(body.substring(start, end));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    public static int countObjects(String array) {
        if (array == null || array.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while (index < array.length()) {
            int objectStart = array.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(array, objectStart);
            if (objectEnd < 0) {
                break;
            }
            count++;
            index = objectEnd + 1;
        }
        return count;
    }

    public static int matchingObjectEnd(String value, int objectStart) {
        if (value == null || objectStart < 0 || objectStart >= value.length()) {
            return -1;
        }
        int depth = 0;
        for (int i = objectStart; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
