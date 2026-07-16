package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerBorderApplyRequestTest {
    @Test
    void acceptsOnlyTheLatestRequestForTheOriginalConnectionAndIsland() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000001101");
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000001102");
        Player expected = player(playerUuid, true);
        PlayerBorderApplyRequest request = new PlayerBorderApplyRequest(
            PlayerConnectionSession.capture(expected), islandId, 7L);

        assertTrue(request.isCurrent(expected, islandId, 7L));
        assertFalse(request.isCurrent(player(playerUuid, true), islandId, 7L),
            "a reconnect with the same UUID must not inherit an older border response");
        assertFalse(request.isCurrent(player(playerUuid, false), islandId, 7L));
        assertFalse(request.isCurrent(expected,
            UUID.fromString("00000000-0000-0000-0000-000000001103"), 7L),
            "moving to another island must invalidate the delayed border response");
        assertFalse(request.isCurrent(expected, islandId, 8L),
            "a newer request on the same connection must supersede the older response");
        assertFalse(request.isCurrent(null, islandId, 7L));
        assertThrows(IllegalArgumentException.class, () -> new PlayerBorderApplyRequest(
            PlayerConnectionSession.capture(expected), islandId, 0L));
    }

    private static Player player(UUID playerUuid, boolean online) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] {Player.class},
            (_proxy, method, _args) -> switch (method.getName()) {
                case "getUniqueId" -> playerUuid;
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
