package kr.lunaf.cloudislands.common.feature;

import java.util.List;

public final class GameplayParityPolicy {
    public static final String CONTRACT = "ss2-gameplay-parity-requires-stacked-block-effects-rates-and-block-value-surfaces";
    public static final String STACKED_BLOCK_POLICY = "prefer-rose-wild-stacker-adapters-or-cloudislands-stacked-block-subsystem-with-core-worth-limit-and-restore-state";
    public static final String EFFECT_RATE_POLICY = "island-effects-crop-growth-mob-drops-and-spawner-rates-are-core-visible-admin-controlled-runtime-modifiers";
    public static final String BLOCK_VALUE_POLICY = "block-values-drive-level-worth-gui-search-admin-set-and-ranking-recalculation";

    private static final List<String> REQUIRED_PLAYER_SURFACES = List.of(
        "island-toggle-blocks",
        "island-effects",
        "island-generator",
        "island-upgrades",
        "island-value",
        "island-values"
    );

    private static final List<String> REQUIRED_ADMIN_SURFACES = List.of(
        "ciadmin-setblockamount",
        "ciadmin-seteffect",
        "ciadmin-setcropgrowth",
        "ciadmin-setmobdrops",
        "ciadmin-setspawnerrates",
        "ciadmin-block-values-list",
        "ciadmin-block-values-set",
        "ciadmin-block-values-reload"
    );

    private GameplayParityPolicy() {
    }

    public static List<String> requiredPlayerSurfaces() {
        return REQUIRED_PLAYER_SURFACES;
    }

    public static List<String> requiredAdminSurfaces() {
        return REQUIRED_ADMIN_SURFACES;
    }

    public static String requiredPlayerSurfaceSummary() {
        return String.join(",", REQUIRED_PLAYER_SURFACES);
    }

    public static String requiredAdminSurfaceSummary() {
        return String.join(",", REQUIRED_ADMIN_SURFACES);
    }

    public static boolean requiredPlayerSurface(String surface) {
        return surface != null && REQUIRED_PLAYER_SURFACES.contains(surface);
    }

    public static boolean requiredAdminSurface(String surface) {
        return surface != null && REQUIRED_ADMIN_SURFACES.contains(surface);
    }
}
