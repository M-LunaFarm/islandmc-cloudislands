package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.junit.jupiter.api.Test;

class IslandEntityLimitKeysTest {
    @Test
    void countsOnlyLivingHangingAndVehicleRuntimeEntities() {
        assertTrue(IslandEntityLimitKeys.counts(proxy(ArmorStand.class)));
        assertTrue(IslandEntityLimitKeys.counts(proxy(Vehicle.class)));
        assertFalse(IslandEntityLimitKeys.counts(proxy(Player.class)));
        assertFalse(IslandEntityLimitKeys.counts(proxy(Item.class)));
        assertFalse(IslandEntityLimitKeys.counts(null));
    }

    private static <T extends Entity> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] {type},
            (_proxy, method, _args) -> defaultValue(method.getReturnType())
        ));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }
}
