package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.api.model.IslandLocation;

final class CoreJsonPayload {
    private CoreJsonPayload() {
    }

    static String stringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        return values.entrySet().stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null)
            .map(entry -> "\"" + escape(entry.getKey()) + "\":\"" + escape(entry.getValue()) + "\"")
            .collect(Collectors.joining(",", "{", "}"));
    }

    static String tableMap(Map<String, Map<String, String>> tables) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        if (tables != null) {
            for (Map.Entry<String, Map<String, String>> entry : tables.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append("\"").append(escape(entry.getKey())).append("\":").append(stringMap(entry.getValue()));
            }
        }
        return builder.append("}").toString();
    }

    static String object(Object... fields) {
        if (fields == null || fields.length == 0) {
            return "{}";
        }
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("JSON object fields must be key-value pairs");
        }
        StringBuilder builder = new StringBuilder("{");
        for (int i = 0; i < fields.length; i += 2) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"').append(escape(String.valueOf(fields[i]))).append("\":");
            appendValue(builder, fields[i + 1]);
        }
        return builder.append('}').toString();
    }

    static Object raw(String value) {
        return new RawJson(value == null || value.isBlank() ? "{}" : value);
    }

    static String warp(UUID islandId, UUID actorUuid, String name, String category, IslandLocation location, boolean publicAccess) {
        if (category == null || category.isBlank()) {
            return object(
                "islandId", islandId,
                "actorUuid", actorUuid,
                "name", name,
                "worldName", location.worldName(),
                "localX", location.localX(),
                "localY", location.localY(),
                "localZ", location.localZ(),
                "yaw", location.yaw(),
                "pitch", location.pitch(),
                "publicAccess", publicAccess
            );
        }
        return object(
            "islandId", islandId,
            "actorUuid", actorUuid,
            "name", name,
            "category", category,
            "worldName", location.worldName(),
            "localX", location.localX(),
            "localY", location.localY(),
            "localZ", location.localZ(),
            "yaw", location.yaw(),
            "pitch", location.pitch(),
            "publicAccess", publicAccess
        );
    }

    static String location(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        return object(
            "islandId", islandId,
            "actorUuid", actorUuid,
            "name", name,
            "worldName", location.worldName(),
            "localX", location.localX(),
            "localY", location.localY(),
            "localZ", location.localZ(),
            "yaw", location.yaw(),
            "pitch", location.pitch()
        );
    }

    private static void appendValue(StringBuilder builder, Object value) {
        if (value instanceof RawJson rawJson) {
            builder.append(rawJson.value());
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        builder.append('"').append(escape(value == null ? "" : String.valueOf(value))).append('"');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private record RawJson(String value) {
    }
}
