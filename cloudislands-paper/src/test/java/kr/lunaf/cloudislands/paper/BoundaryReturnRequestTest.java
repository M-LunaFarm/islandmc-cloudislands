package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BoundaryReturnRequestTest {
    @Test
    void acceptsOnlyTheOriginalOnlinePlayerAtTheAuthorizingBlock() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000711");
        World islandWorld = world(UUID.fromString("00000000-0000-0000-0000-000000000712"));
        World otherWorld = world(UUID.fromString("00000000-0000-0000-0000-000000000713"));
        Location origin = new Location(islandWorld, 10.5D, 90.0D, -4.5D);
        Player expected = player(playerUuid, true, origin);
        BoundaryReturnRequest request = BoundaryReturnRequest.capture(expected, origin);

        assertTrue(request.isCurrent(expected));
        assertFalse(request.isCurrent(player(playerUuid, true, origin)),
            "a reconnect with the same UUID must not inherit an old boundary return");
        assertFalse(request.isCurrent(player(playerUuid, false, origin)));
        assertFalse(request.isCurrent(player(playerUuid, true, new Location(islandWorld, 11.5D, 90.0D, -4.5D))),
            "a later teleport or movement must invalidate the delayed return");
        assertFalse(request.isCurrent(player(playerUuid, true, new Location(otherWorld, 10.5D, 90.0D, -4.5D))));
        assertFalse(request.isCurrent(null));
    }

    private static World world(UUID worldId) {
        return (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[] {World.class},
            (_proxy, method, _args) -> switch (method.getName()) {
                case "getUID" -> worldId;
                case "hashCode" -> System.identityHashCode(_proxy);
                case "equals" -> _proxy == _args[0];
                case "toString" -> "World[" + worldId + "]";
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private static Player player(UUID playerUuid, boolean online, Location location) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (_proxy, method, _args) -> switch (method.getName()) {
                case "getUniqueId" -> playerUuid;
                case "getLocation" -> location.clone();
                case "isOnline" -> online;
                case "hashCode" -> System.identityHashCode(_proxy);
                case "equals" -> _proxy == _args[0];
                case "toString" -> "Player[" + playerUuid + "]";
                default -> defaultValue(method.getReturnType());
            }
        );
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
        return 0;
    }
}
