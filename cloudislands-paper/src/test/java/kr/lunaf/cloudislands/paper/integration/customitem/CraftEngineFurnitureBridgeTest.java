package kr.lunaf.cloudislands.paper.integration.customitem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent;
import net.momirealms.craftengine.bukkit.api.event.FurniturePlaceEvent;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class CraftEngineFurnitureBridgeTest {
    @Test
    void officialPlaceAndBreakPayloadsResolveOneStableCustomKey() throws Exception {
        CraftEngineFurnitureBridge.Adapter adapter = CraftEngineFurnitureBridge.Adapter.create(
            FurniturePlaceEvent.class,
            FurnitureBreakEvent.class
        );
        Player player = player();
        Location location = new Location(world("ci_shard_001"), 12.0, 80.0, -4.0);
        BukkitFurniture furniture = new BukkitFurniture("custom:chair");

        CraftEngineFurnitureBridge.Context placed = adapter.context(new FurniturePlaceEvent(player, furniture, location));
        CraftEngineFurnitureBridge.Context broken = adapter.context(new FurnitureBreakEvent(player, furniture, location));

        assertEquals(player, placed.player());
        assertEquals(location, placed.location());
        assertEquals("craftengine:custom:chair", placed.customKey());
        assertEquals("craftengine:custom:chair", broken.customKey());
    }

    private static Player player() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getName" -> name;
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0D;
        if (type == float.class) return 0.0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
