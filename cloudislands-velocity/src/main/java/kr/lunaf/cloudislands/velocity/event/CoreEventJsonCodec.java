package kr.lunaf.cloudislands.velocity.event;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.JsonCodec;

public final class CoreEventJsonCodec implements CoreEventCodec {
    @Override
    public CoreEventBatch decodeBatch(String json) {
        Map<String, Object> object = JsonCodec.readObject(json);
        long oldestSequence = sequence(object, "oldestSeq");
        long latestSequence = sequence(object, "latestSeq");
        List<CoreEventEnvelope> events = new ArrayList<>();
        List<?> rawEvents = array(object, "events");
        for (int index = 0; index < rawEvents.size(); index++) {
            Object item = rawEvents.get(index);
            if (!(item instanceof Map<?, ?> event)) {
                throw invalid("events[" + index + "] must be an object");
            }
            events.add(event(event, index));
        }
        return new CoreEventBatch(oldestSequence, latestSequence, List.copyOf(events));
    }

    private static CoreEventEnvelope event(Map<?, ?> event, int index) {
        return new CoreEventEnvelope(
            sequence(event, "events[" + index + "].seq", "seq"),
            requiredText(event, "events[" + index + "].type", "type"),
            stringMap(event, "events[" + index + "].fields", "fields"),
            requiredText(event, "events[" + index + "].occurredAt", "occurredAt")
        );
    }

    private static List<?> array(Map<String, Object> object, String field) {
        Object value = object.get(field);
        if (value instanceof List<?> list) {
            return list;
        }
        throw invalid(field + " must be an array");
    }

    private static Map<String, String> stringMap(Map<?, ?> object, String path, String field) {
        Object value = object.get(field);
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(path + " must be an object");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw invalid(path + " keys must be non-blank strings");
            }
            Object rawValue = entry.getValue();
            if (rawValue == null || rawValue instanceof Map<?, ?> || rawValue instanceof List<?>) {
                throw invalid(path + "." + key + " must be a scalar value");
            }
            result.put(key, rawValue.toString());
        }
        return Map.copyOf(result);
    }

    private static String requiredText(Map<?, ?> object, String path, String field) {
        Object value = object.get(field);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw invalid(path + " must be a non-blank string");
    }

    private static long sequence(Map<?, ?> object, String field) {
        return sequence(object, field, field);
    }

    private static long sequence(Map<?, ?> object, String path, String field) {
        Object value = object.get(field);
        long sequence;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            sequence = ((Number) value).longValue();
        } else if (value instanceof BigInteger integer) {
            try {
                sequence = integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw invalid(path + " must fit in a signed 64-bit integer");
            }
        } else if (value instanceof BigDecimal decimal) {
            try {
                sequence = decimal.toBigIntegerExact().longValueExact();
            } catch (ArithmeticException exception) {
                throw invalid(path + " must be an integer");
            }
        } else if (value instanceof Number number) {
            double decimal = number.doubleValue();
            sequence = number.longValue();
            if (!Double.isFinite(decimal) || decimal != sequence) {
                throw invalid(path + " must be an integer");
            }
        } else if (value instanceof String text && !text.isBlank()) {
            try {
                sequence = Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                throw invalid(path + " must be an integer");
            }
        } else {
            throw invalid(path + " must be an integer");
        }
        if (sequence < 0L) {
            throw invalid(path + " must not be negative");
        }
        return sequence;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("invalid Core event JSON: " + message);
    }
}
