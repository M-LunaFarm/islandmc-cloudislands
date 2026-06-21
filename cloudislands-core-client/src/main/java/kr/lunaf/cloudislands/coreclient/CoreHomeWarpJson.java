package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreHomeWarpJson {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private CoreHomeWarpJson() {
    }

    static List<IslandHomeSnapshot> homes(UUID islandId, String body) {
        return CoreJson.entries(body).stream()
            .map(values -> home(islandId, values))
            .filter(home -> !home.name().isBlank())
            .toList();
    }

    static List<IslandWarpSnapshot> warps(UUID islandId, String body) {
        return CoreJson.entries(body).stream()
            .map(values -> warp(islandId, values))
            .filter(warp -> !warp.name().isBlank())
            .toList();
    }

    static CoreGuiViews.HomeView homeView(IslandHomeSnapshot home) {
        IslandLocation location = home == null ? null : home.location();
        return new CoreGuiViews.HomeView(
            home == null || home.islandId() == null || home.islandId().equals(EMPTY_UUID) ? "" : home.islandId().toString(),
            home == null ? "" : home.name(),
            location == null ? 0.0d : location.localX(),
            location == null ? 0.0d : location.localY(),
            location == null ? 0.0d : location.localZ(),
            home == null || home.createdBy() == null || home.createdBy().equals(EMPTY_UUID) ? "" : home.createdBy().toString(),
            home == null || home.createdAt() == null || home.createdAt().equals(Instant.EPOCH) ? "" : home.createdAt().toString()
        );
    }

    static CoreGuiViews.WarpView warpView(IslandWarpSnapshot warp) {
        IslandLocation location = warp == null ? null : warp.location();
        return new CoreGuiViews.WarpView(
            warp == null || warp.islandId() == null || warp.islandId().equals(EMPTY_UUID) ? "" : warp.islandId().toString(),
            warp == null ? "" : warp.name(),
            location == null ? 0.0d : location.localX(),
            location == null ? 0.0d : location.localY(),
            location == null ? 0.0d : location.localZ(),
            warp != null && warp.publicAccess(),
            warp == null || warp.createdBy() == null || warp.createdBy().equals(EMPTY_UUID) ? "" : warp.createdBy().toString(),
            warp == null || warp.createdAt() == null || warp.createdAt().equals(Instant.EPOCH) ? "" : warp.createdAt().toString(),
            warp == null ? "" : warp.category()
        );
    }

    private static IslandHomeSnapshot home(UUID islandId, Map<?, ?> values) {
        return new IslandHomeSnapshot(
            islandId == null ? uuid(CoreJson.text(values, "islandId")) : islandId,
            CoreJson.text(values, "name"),
            location(values),
            uuid(CoreJson.text(values, "createdBy")),
            instant(CoreJson.text(values, "createdAt"))
        );
    }

    private static IslandWarpSnapshot warp(UUID islandId, Map<?, ?> values) {
        String name = CoreJson.text(values, "name");
        if (name.isBlank()) {
            name = CoreJson.text(values, "warpName");
        }
        return new IslandWarpSnapshot(
            islandId == null ? uuid(CoreJson.text(values, "islandId")) : islandId,
            name,
            location(values),
            Boolean.TRUE.equals(values.get("publicAccess")),
            uuid(CoreJson.text(values, "createdBy")),
            instant(CoreJson.text(values, "createdAt")),
            CoreJson.text(values, "category")
        );
    }

    private static IslandLocation location(Map<?, ?> values) {
        Map<?, ?> location = SimpleJson.object(values.get("location"));
        return new IslandLocation(
            firstText(location, values, "worldName"),
            firstNumber(location, values, "localX", "x"),
            firstNumber(location, values, "localY", "y"),
            firstNumber(location, values, "localZ", "z"),
            (float) firstNumber(location, values, "yaw"),
            (float) firstNumber(location, values, "pitch")
        );
    }

    private static String firstText(Map<?, ?> primary, Map<?, ?> fallback, String key) {
        String value = CoreJson.text(primary, key);
        return value.isBlank() ? CoreJson.text(fallback, key) : value;
    }

    private static double firstNumber(Map<?, ?> primary, Map<?, ?> fallback, String... keys) {
        for (String key : keys) {
            double value = number(primary, key);
            if (value != 0.0d || primary.containsKey(key)) {
                return value;
            }
        }
        for (String key : keys) {
            double value = number(fallback, key);
            if (value != 0.0d || fallback.containsKey(key)) {
                return value;
            }
        }
        return 0.0d;
    }

    private static double number(Map<?, ?> values, String key) {
        Object value = values == null ? null : values.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0d : Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            return 0.0d;
        }
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
