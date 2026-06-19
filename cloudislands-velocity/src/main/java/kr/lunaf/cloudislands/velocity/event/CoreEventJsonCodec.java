package kr.lunaf.cloudislands.velocity.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreEventJsonCodec implements CoreEventCodec {
    @Override
    public CoreEventBatch decodeBatch(String json) {
        if (json == null || json.isBlank()) {
            return new CoreEventBatch(0L, 0L, List.of());
        }
        JsonCursor cursor = new JsonCursor(json);
        Object root = cursor.readValue();
        if (!(root instanceof Map<?, ?> object)) {
            return new CoreEventBatch(0L, 0L, List.of());
        }
        long oldestSequence = number(object.get("oldestSeq"));
        long latestSequence = number(object.get("latestSeq"));
        List<CoreEventEnvelope> events = new ArrayList<>();
        Object rawEvents = object.get("events");
        if (rawEvents instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> event) {
                    events.add(event(event));
                }
            }
        }
        return new CoreEventBatch(oldestSequence, latestSequence, List.copyOf(events));
    }

    private static CoreEventEnvelope event(Map<?, ?> event) {
        return new CoreEventEnvelope(
            number(event.get("seq")),
            text(event.get("type")),
            stringMap(event.get("fields")),
            text(event.get("occurredAt"))
        );
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(text(entry.getKey()), text(entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long number(Object value) {
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

    private static final class JsonCursor {
        private final String json;
        private int index;

        private JsonCursor(String json) {
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
                String key = readString();
                skipWhitespace();
                if (index < json.length() && json.charAt(index) == ':') {
                    index++;
                }
                Object value = readValue();
                result.put(key, value);
                skipWhitespace();
                if (index < json.length() && json.charAt(index) == ',') {
                    index++;
                    skipWhitespace();
                }
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
                result.add(readValue());
                skipWhitespace();
                if (index < json.length() && json.charAt(index) == ',') {
                    index++;
                    skipWhitespace();
                }
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
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }
    }
}
