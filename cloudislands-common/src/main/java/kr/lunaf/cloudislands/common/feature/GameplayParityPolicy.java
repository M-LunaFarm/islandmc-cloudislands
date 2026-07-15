package kr.lunaf.cloudislands.common.feature;

import java.util.List;

public final class GameplayParityPolicy {
    public static final String CONTRACT = "ss2-gameplay-parity-requires-stacked-block-effects-rates-and-block-value-surfaces";
    public static final String STACKED_BLOCK_POLICY = "prefer-rose-wild-stacker-adapters-or-cloudislands-stacked-block-subsystem-with-core-worth-limit-and-restore-state";
    public static final String EFFECT_RATE_POLICY = "island-effects-crop-growth-mob-drops-and-spawner-rates-are-core-visible-admin-controlled-runtime-modifiers";
    public static final String BLOCK_VALUE_POLICY = "block-values-drive-level-worth-gui-search-admin-set-and-ranking-recalculation";
    public static final String BLOCK_AMOUNT_LIMIT_PREFIX = "BLOCK_AMOUNT:";
    public static final String ENTITY_TYPE_LIMIT_PREFIX = "ENTITY_TYPE:";
    public static final String ROLE_LIMIT_PREFIX = "ROLE_LIMIT:";
    public static final String STACKED_BLOCKS_VISIBLE_LIMIT_KEY = "STACKED_BLOCKS_VISIBLE";
    public static final String WAREHOUSE_ROWS_LIMIT_KEY = "WAREHOUSE_ROWS";

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

    public static String blockAmountLimitKey(String materialKey) {
        return BLOCK_AMOUNT_LIMIT_PREFIX + normalizeNamespacedGameplayKey(materialKey, "UNKNOWN");
    }

    public static boolean blockAmountLimit(String limitKey) {
        return normalizeIslandLimitKey(limitKey).startsWith(BLOCK_AMOUNT_LIMIT_PREFIX);
    }

    public static String blockAmountMaterialKey(String limitKey) {
        String normalized = normalizeIslandLimitKey(limitKey);
        if (!normalized.startsWith(BLOCK_AMOUNT_LIMIT_PREFIX)) {
            return "";
        }
        return normalized.substring(BLOCK_AMOUNT_LIMIT_PREFIX.length());
    }

    public static String roleLimitKey(String roleKey) {
        return ROLE_LIMIT_PREFIX + normalizeGameplayKey(roleKey, "MEMBER");
    }

    public static String entityTypeLimitKey(String entityTypeKey) {
        return ENTITY_TYPE_LIMIT_PREFIX + normalizeNamespacedGameplayKey(entityTypeKey, "UNKNOWN");
    }

    public static boolean entityTypeLimit(String limitKey) {
        return normalizeIslandLimitKey(limitKey).startsWith(ENTITY_TYPE_LIMIT_PREFIX);
    }

    public static String entityTypeLimitEntityKey(String limitKey) {
        String normalized = normalizeIslandLimitKey(limitKey);
        if (!normalized.startsWith(ENTITY_TYPE_LIMIT_PREFIX)) {
            return "";
        }
        return normalized.substring(ENTITY_TYPE_LIMIT_PREFIX.length());
    }

    public static boolean roleLimit(String limitKey) {
        return normalizeIslandLimitKey(limitKey).startsWith(ROLE_LIMIT_PREFIX);
    }

    public static String roleLimitRoleKey(String limitKey) {
        String normalized = normalizeIslandLimitKey(limitKey);
        if (!normalized.startsWith(ROLE_LIMIT_PREFIX)) {
            return "";
        }
        return normalized.substring(ROLE_LIMIT_PREFIX.length());
    }

    public static String normalizeIslandLimitKey(String limitKey) {
        String normalized = limitKey == null ? "" : limitKey.trim().toUpperCase().replaceAll("[^A-Z0-9_.:-]+", "_");
        return normalized.isBlank() ? "HOPPER" : normalized;
    }

    public static String normalizeGameplayKey(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase().replaceAll("[^A-Z0-9_.:-]+", "_");
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String normalizeNamespacedGameplayKey(String value, String fallback) {
        String normalized = normalizeGameplayKey(value, fallback).replace('-', '_');
        return normalized.contains(":") || normalized.equals(fallback) ? normalized : "MINECRAFT:" + normalized;
    }
}
