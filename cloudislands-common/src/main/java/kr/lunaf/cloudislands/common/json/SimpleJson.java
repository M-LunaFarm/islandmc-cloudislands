package kr.lunaf.cloudislands.common.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJson {
    private SimpleJson() {
    }

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return new Cursor(json).readValue();
    }

    public static Map<?, ?> object(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    public static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    public static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    public static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public static String stringify(Object value) {
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append('"').append(escape(text(entry.getKey()))).append("\":").append(stringify(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(stringify(item));
            }
            return builder.append(']').toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value == null) {
            return "null";
        }
        return "\"" + escape(text(value)) + "\"";
    }

    private static String escape(String value) {
        return value == null ? "" : value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static final class Cursor {
        private final String json;
        private int index;

        private Cursor(String json) {
            this.json = json;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= json.length()) {
                return null;
            }
            char current = json.charAt(index);
            if (current == '{') {
                return readObject();
            }
            if (current == '[') {
                return readArray();
            }
            if (current == '"') {
                return readString();
            }
            if (current == 't' && consumeLiteral("true")) {
                return Boolean.TRUE;
            }
            if (current == 'f' && consumeLiteral("false")) {
                return Boolean.FALSE;
            }
            if (current == 'n' && consumeLiteral("null")) {
                return null;
            }
            return readNumber();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            while (index < json.length() && json.charAt(index) != '}') {
                int previous = index;
                String key = readString();
                skipWhitespace();
                if (index < json.length() && json.charAt(index) == ':') {
                    index++;
                }
                result.put(key, readValue());
                skipWhitespace();
                if (index < json.length() && json.charAt(index) == ',') {
                    index++;
                    skipWhitespace();
                }
                ensureProgress(previous);
            }
            if (index < json.length() && json.charAt(index) == '}') {
                index++;
            }
            return result;
        }

        private List<Object> readArray() {
            List<Object> result = new ArrayList<>();
            index++;
            skipWhitespace();
            while (index < json.length() && json.charAt(index) != ']') {
                int previous = index;
                result.add(readValue());
                skipWhitespace();
                if (index < json.length() && json.charAt(index) == ',') {
                    index++;
                    skipWhitespace();
                }
                ensureProgress(previous);
            }
            if (index < json.length() && json.charAt(index) == ']') {
                index++;
            }
            return result;
        }

        private String readString() {
            if (index >= json.length() || json.charAt(index) != '"') {
                return "";
            }
            index++;
            StringBuilder builder = new StringBuilder();
            while (index < json.length()) {
                char current = json.charAt(index++);
                if (current == '"') {
                    break;
                }
                if (current == '\\' && index < json.length()) {
                    appendEscaped(builder, json.charAt(index++));
                    continue;
                }
                builder.append(current);
            }
            return builder.toString();
        }

        private void appendEscaped(StringBuilder builder, char escaped) {
            switch (escaped) {
                case '"', '\\', '/' -> builder.append(escaped);
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'u' -> appendUnicode(builder);
                default -> builder.append(escaped);
            }
        }

        private void appendUnicode(StringBuilder builder) {
            if (index + 4 > json.length()) {
                return;
            }
            String hex = json.substring(index, index + 4);
            index += 4;
            try {
                builder.append((char) Integer.parseInt(hex, 16));
            } catch (NumberFormatException ignored) {
                builder.append(hex);
            }
        }

        private Number readNumber() {
            int start = index;
            while (index < json.length()) {
                char current = json.charAt(index);
                if ((current >= '0' && current <= '9') || current == '-' || current == '+' || current == '.' || current == 'e' || current == 'E') {
                    index++;
                    continue;
                }
                break;
            }
            if (start == index) {
                return 0L;
            }
            String token = json.substring(start, index);
            try {
                if (token.contains(".") || token.contains("e") || token.contains("E")) {
                    return Double.parseDouble(token);
                }
                return Long.parseLong(token);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }

        private boolean consumeLiteral(String literal) {
            if (json.startsWith(literal, index)) {
                index += literal.length();
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (index < json.length()) {
                char current = json.charAt(index);
                if (current == ' ' || current == '\n' || current == '\r' || current == '\t') {
                    index++;
                    continue;
                }
                break;
            }
        }

        private void ensureProgress(int previous) {
            if (index <= previous) {
                throw new IllegalArgumentException("JSON parser made no progress at offset " + index);
            }
        }
    }
}
