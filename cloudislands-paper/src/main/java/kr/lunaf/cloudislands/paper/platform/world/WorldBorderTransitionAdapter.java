package kr.lunaf.cloudislands.paper.platform.world;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.WorldBorder;

public final class WorldBorderTransitionAdapter {
    private static final Method TICK_METHOD = method("changeSize");
    private static final Method LEGACY_SECONDS_METHOD = method("setSize");

    private WorldBorderTransitionAdapter() {
    }

    public static boolean changeSize(WorldBorder border, double targetSize, long durationTicks) {
        if (border == null) {
            throw new IllegalArgumentException("border is required");
        }
        long ticks = Math.max(0L, Math.min(2_147_483_647L, durationTicks));
        if (invoke(TICK_METHOD, border, targetSize, ticks)) {
            return true;
        }
        if (invoke(LEGACY_SECONDS_METHOD, border, targetSize, legacySeconds(ticks))) {
            return true;
        }
        border.setSize(targetSize);
        return false;
    }

    static long legacySeconds(long durationTicks) {
        long ticks = Math.max(0L, durationTicks);
        return ticks == 0L ? 0L : Math.max(1L, ticks / 20L);
    }

    private static Method method(String name) {
        try {
            return WorldBorder.class.getMethod(name, double.class, long.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static boolean invoke(Method method, WorldBorder border, double targetSize, long duration) {
        if (method == null) {
            return false;
        }
        try {
            method.invoke(border, targetSize, duration);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return false;
        }
    }
}
