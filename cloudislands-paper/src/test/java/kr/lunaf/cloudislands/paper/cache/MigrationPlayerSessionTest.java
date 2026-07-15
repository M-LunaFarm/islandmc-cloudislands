package kr.lunaf.cloudislands.paper.cache;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MigrationPlayerSessionTest {
    @Test
    void acceptsOnlyTheSameOnlinePlayerInstance() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000801");
        Player expected = player(playerUuid, true);
        MigrationPlayerSession session = MigrationPlayerSession.capture(expected);

        assertTrue(session.isCurrent(expected));
        assertFalse(session.isCurrent(player(playerUuid, true)),
            "a reconnect with the same UUID must not inherit an older migration ticket");
        assertFalse(session.isCurrent(player(playerUuid, false)));
        assertFalse(session.isCurrent(null));
        assertThrows(NullPointerException.class, () -> MigrationPlayerSession.capture(null));
        assertThrows(IllegalArgumentException.class,
            () -> new MigrationPlayerSession(UUID.fromString("00000000-0000-0000-0000-000000000802"), expected));
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
