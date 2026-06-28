package kr.lunaf.cloudislands.paper.application;

import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.common.protection.IslandRegion;

public final class IslandBorderRuntimePolicy {
    private IslandBorderRuntimePolicy() {
    }

    public static BorderSettings settings(long borderSize, Map<IslandFlag, String> flags, IslandRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("region is required");
        }
        Map<IslandFlag, String> safeFlags = flags == null ? Map.of() : flags;
        String policy = normalizePolicy(flagValue(safeFlags, IslandFlag.BORDER_POLICY, visible(safeFlags) ? "visible" : "hidden"));
        boolean applies = visible(safeFlags) && !policy.equals("hidden");
        return new BorderSettings(
            applies,
            region.originX(),
            region.originZ(),
            Math.max(1.0D, borderSize),
            Math.max(0, (int) longValue(flagValue(safeFlags, IslandFlag.BORDER_WARNING_BLOCKS, "8"), 8L)),
            normalizeColor(flagValue(safeFlags, IslandFlag.BORDER_COLOR, "blue")),
            policy
        );
    }

    public static boolean visible(Map<IslandFlag, String> flags) {
        String value = flagValue(flags == null ? Map.of() : flags, IslandFlag.BORDER_VISIBLE, "true");
        return !value.equalsIgnoreCase("false")
            && !value.equalsIgnoreCase("off")
            && !value.equals("0")
            && !value.equalsIgnoreCase("hide")
            && !value.equalsIgnoreCase("hidden")
            && !value.equals("숨김");
    }

    public static String flagValue(Map<IslandFlag, String> flags, IslandFlag flag, String fallback) {
        String value = flags == null ? "" : flags.getOrDefault(flag, "");
        return value.isBlank() ? fallback : value;
    }

    public static String normalizeColor(String value) {
        return switch ((value == null ? "" : value).toLowerCase(Locale.ROOT)) {
            case "red", "빨강" -> "red";
            case "green", "초록" -> "green";
            case "aqua", "cyan", "하늘" -> "aqua";
            case "yellow", "노랑" -> "yellow";
            case "purple", "보라" -> "purple";
            default -> "blue";
        };
    }

    public static String normalizePolicy(String value) {
        String normalized = (value == null ? "" : value).toLowerCase(Locale.ROOT);
        if (normalized.equals("hidden") || normalized.equals("hide") || normalized.equals("숨김")) {
            return "hidden";
        }
        if (normalized.equals("warning") || normalized.equals("warn") || normalized.equals("경고")) {
            return "warning";
        }
        return "visible";
    }

    private static long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public record BorderSettings(
        boolean visible,
        double centerX,
        double centerZ,
        double size,
        int warningDistance,
        String color,
        String policy
    ) {}
}
