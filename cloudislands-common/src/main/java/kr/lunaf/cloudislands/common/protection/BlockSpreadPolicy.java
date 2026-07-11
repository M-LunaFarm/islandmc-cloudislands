package kr.lunaf.cloudislands.common.protection;

import java.util.Locale;

public final class BlockSpreadPolicy {
    private BlockSpreadPolicy() {
    }

    public static boolean fireSpread(String sourceMaterial, String resultMaterial) {
        return fire(sourceMaterial) || fire(resultMaterial);
    }

    private static boolean fire(String material) {
        if (material == null) {
            return false;
        }
        String normalized = material.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("FIRE") || normalized.equals("SOUL_FIRE");
    }
}
