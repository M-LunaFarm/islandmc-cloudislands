package kr.lunaf.cloudislands.common.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.JsonCodecException.Kind;

public final class JsonCodec {
    public static final int MAX_NESTING_DEPTH = 64;
    public static final int MAX_STRING_LENGTH = 262_144;
    public static final int MAX_NUMBER_LENGTH = 256;
    public static final int MAX_COLLECTION_ITEMS = 10_000;

    private static final JsonFactory FACTORY = JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(MAX_NESTING_DEPTH)
            .maxStringLength(MAX_STRING_LENGTH)
            .maxNumberLength(MAX_NUMBER_LENGTH)
            .build())
        .build();

    private JsonCodec() {
    }

    public static Map<String, Object> readObject(String json) {
        Object value = read(json);
        if (value instanceof Map<?, ?> map) {
            return castObject(map);
        }
        throw new JsonCodecException(Kind.INVALID_REQUEST, "JSON request body must be an object");
    }

    public static Object read(String json) {
        if (json == null || json.isBlank()) {
            throw new JsonCodecException(Kind.INVALID_JSON, "JSON request body is empty");
        }
        try (JsonParser parser = FACTORY.createParser(json)) {
            JsonToken token = parser.nextToken();
            if (token == null) {
                throw new JsonCodecException(Kind.INVALID_JSON, "JSON request body is empty");
            }
            Object value = readValue(parser, 0);
            if (parser.nextToken() != null) {
                throw new JsonCodecException(Kind.INVALID_JSON, "Trailing JSON content is not allowed");
            }
            return value;
        } catch (JsonCodecException exception) {
            throw exception;
        } catch (StreamConstraintsException exception) {
            throw new JsonCodecException(Kind.INVALID_JSON, "JSON exceeds configured parser limits", exception);
        } catch (JsonParseException exception) {
            throw new JsonCodecException(Kind.INVALID_JSON, "Malformed JSON request body", exception);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (RuntimeException exception) {
            throw new JsonCodecException(Kind.INVALID_JSON, "Malformed JSON request body", exception);
        }
    }

    public static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        appendJson(builder, value);
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castObject(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private static Object readValue(JsonParser parser, int depth) throws IOException {
        if (depth > MAX_NESTING_DEPTH) {
            throw new JsonCodecException(Kind.INVALID_JSON, "JSON nesting depth exceeds " + MAX_NESTING_DEPTH);
        }
        JsonToken token = parser.currentToken();
        if (token == null) {
            throw new JsonCodecException(Kind.INVALID_JSON, "Unexpected end of JSON input");
        }
        return switch (token) {
            case START_OBJECT -> readObjectValue(parser, depth + 1);
            case START_ARRAY -> readArrayValue(parser, depth + 1);
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT -> integerValue(parser);
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw new JsonCodecException(Kind.INVALID_JSON, "Unexpected JSON token " + token);
        };
    }

    private static Map<String, Object> readObjectValue(JsonParser parser, int depth) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        int fields = 0;
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw new JsonCodecException(Kind.INVALID_JSON, "Expected JSON object field name");
            }
            if (++fields > MAX_COLLECTION_ITEMS) {
                throw new JsonCodecException(Kind.INVALID_JSON, "JSON object contains too many fields");
            }
            String fieldName = parser.currentName();
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                throw new JsonCodecException(Kind.INVALID_JSON, "Unexpected end of JSON object");
            }
            values.put(fieldName, readValue(parser, depth));
        }
        return Collections.unmodifiableMap(values);
    }

    private static List<Object> readArrayValue(JsonParser parser, int depth) throws IOException {
        List<Object> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() == null) {
                throw new JsonCodecException(Kind.INVALID_JSON, "Unexpected end of JSON array");
            }
            if (values.size() >= MAX_COLLECTION_ITEMS) {
                throw new JsonCodecException(Kind.INVALID_JSON, "JSON array contains too many items");
            }
            values.add(readValue(parser, depth));
        }
        return Collections.unmodifiableList(values);
    }

    private static Number integerValue(JsonParser parser) throws IOException {
        BigInteger value = parser.getBigIntegerValue();
        if (value.bitLength() <= 31) {
            return value.intValue();
        }
        if (value.bitLength() <= 63) {
            return value.longValue();
        }
        return new BigDecimal(value);
    }

    private static void appendJson(StringBuilder builder, Object value) {
        if (value instanceof Map<?, ?> map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                appendJson(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                appendJson(builder, entry.getValue());
            }
            builder.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            builder.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                appendJson(builder, item);
            }
            builder.append(']');
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        if (value == null) {
            builder.append("null");
            return;
        }
        builder.append('"').append(escape(String.valueOf(value))).append('"');
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (current < 0x20) {
                        builder.append(String.format("\\u%04x", (int) current));
                    } else {
                        builder.append(current);
                    }
                }
            }
        }
        return builder.toString();
    }
}
