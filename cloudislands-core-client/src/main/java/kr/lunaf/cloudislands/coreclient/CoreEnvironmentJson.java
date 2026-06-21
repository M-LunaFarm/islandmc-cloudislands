package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreEnvironmentJson {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private CoreEnvironmentJson() {
    }

    static IslandBiomeSnapshot biome(UUID islandId, String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new IslandBiomeSnapshot(
            islandId == null ? uuid(CoreJson.text(root, "islandId")) : islandId,
            textOrDefault(CoreJson.text(root, "biomeKey"), "minecraft:plains"),
            uuid(CoreJson.text(root, "updatedBy")),
            instant(CoreJson.text(root, "updatedAt"))
        );
    }

    static IslandFlagsSnapshot flags(UUID islandId, String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> source = SimpleJson.object(root.get("flags"));
        if (source.isEmpty()) {
            source = SimpleJson.object(root.get("values"));
        }
        Map<IslandFlag, String> values = new EnumMap<>(IslandFlag.class);
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            try {
                values.put(IslandFlag.valueOf(SimpleJson.text(entry.getKey())), SimpleJson.text(entry.getValue()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new IslandFlagsSnapshot(islandId == null ? uuid(CoreJson.text(root, "islandId")) : islandId, Map.copyOf(values));
    }

    static List<IslandLimitSnapshot> limits(UUID islandId, String body) {
        return CoreJson.entries(body).stream()
            .map(values -> limit(islandId, values))
            .filter(limit -> !limit.limitKey().isBlank())
            .toList();
    }

    static CoreGuiViews.BiomeView biomeView(IslandBiomeSnapshot snapshot) {
        if (snapshot == null) {
            return new CoreGuiViews.BiomeView("minecraft:plains", "", "");
        }
        return new CoreGuiViews.BiomeView(
            textOrDefault(snapshot.biomeKey(), "minecraft:plains"),
            snapshot.updatedBy() == null || snapshot.updatedBy().equals(EMPTY_UUID) ? "" : snapshot.updatedBy().toString(),
            snapshot.updatedAt() == null || snapshot.updatedAt().equals(Instant.EPOCH) ? "" : snapshot.updatedAt().toString()
        );
    }

    static CoreGuiViews.LimitView limitView(IslandLimitSnapshot snapshot) {
        if (snapshot == null) {
            return new CoreGuiViews.LimitView("", 0L, "");
        }
        return new CoreGuiViews.LimitView(
            snapshot.limitKey(),
            snapshot.value(),
            snapshot.updatedAt() == null || snapshot.updatedAt().equals(Instant.EPOCH) ? "" : snapshot.updatedAt().toString()
        );
    }

    private static IslandLimitSnapshot limit(UUID islandId, Map<?, ?> values) {
        String key = CoreJson.text(values, "key");
        if (key.isBlank()) {
            key = CoreJson.text(values, "limitKey");
        }
        return new IslandLimitSnapshot(
            islandId == null ? uuid(CoreJson.text(values, "islandId")) : islandId,
            key,
            CoreJson.number(values, "value"),
            uuid(CoreJson.text(values, "updatedBy")),
            instant(CoreJson.text(values, "updatedAt"))
        );
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? EMPTY_UUID : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return EMPTY_UUID;
        }
    }

    private static Instant instant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }
}
