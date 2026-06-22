package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreJsonPayload {
    private CoreJsonPayload() {
    }

    static Map<String, String> stringMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    static Map<String, Long> positiveLongMap(Map<String, Long> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank() && entry.getValue() != null && entry.getValue() > 0L) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    static Map<String, Map<String, String>> tableMap(Map<String, Map<String, String>> tables) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        if (tables != null) {
            for (Map.Entry<String, Map<String, String>> entry : tables.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                    continue;
                }
                result.put(entry.getKey(), stringMap(entry.getValue()));
            }
        }
        return result;
    }

    static String object(Object... fields) {
        if (fields == null || fields.length == 0) {
            return "{}";
        }
        if (fields.length % 2 != 0) {
            throw new IllegalArgumentException("JSON object fields must be key-value pairs");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) {
            values.put(String.valueOf(fields[i]), normalizeValue(fields[i + 1]));
        }
        return SimpleJson.stringify(values);
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

    private static Object normalizeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
                }
            }
            return normalized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(CoreJsonPayload::normalizeValue).toList();
        }
        return value;
    }
}
