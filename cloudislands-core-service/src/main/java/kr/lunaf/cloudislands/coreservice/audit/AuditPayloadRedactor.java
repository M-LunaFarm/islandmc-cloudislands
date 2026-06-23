package kr.lunaf.cloudislands.coreservice.audit;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.common.config.ConfigV2Validator;

final class AuditPayloadRedactor {
    static final String REDACTED = "<redacted>";

    private AuditPayloadRedactor() {
    }

    static Map<String, String> redact(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, String> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            redacted.put(key, sensitiveKey(key) ? REDACTED : safeValue(entry.getValue()));
        }
        return Map.copyOf(redacted);
    }

    static boolean sensitiveKey(String key) {
        if (ConfigV2Validator.secretKey(key)) {
            return true;
        }
        String compact = key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return compact.contains("password")
            || compact.contains("secret")
            || compact.contains("credential")
            || compact.contains("authorization")
            || compact.contains("accesskey")
            || compact.contains("token")
            || compact.contains("ticket")
            || compact.contains("nonce");
    }

    static String payloadJson(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : redact(payload).entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue())).append('"');
        }
        return builder.append('}').toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
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

    private static String safeValue(String value) {
        return value == null ? "" : value;
    }
}
