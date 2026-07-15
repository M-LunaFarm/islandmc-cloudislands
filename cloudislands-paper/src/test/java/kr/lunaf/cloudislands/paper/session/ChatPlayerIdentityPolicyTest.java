package kr.lunaf.cloudislands.paper.session;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ChatPlayerIdentityPolicyTest {
    @Test
    void acceptsOnlyTheSameOnlinePlayerInstance() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000701");
        Player expected = player(playerUuid, true);

        assertTrue(ChatPlayerIdentityPolicy.isCurrent(expected, expected));
        assertFalse(ChatPlayerIdentityPolicy.isCurrent(expected, player(playerUuid, true)),
            "a reconnect with the same UUID must not inherit queued chat work");
        assertFalse(ChatPlayerIdentityPolicy.isCurrent(expected, player(playerUuid, false)));
        assertFalse(ChatPlayerIdentityPolicy.isCurrent(expected, null));
        assertFalse(ChatPlayerIdentityPolicy.isCurrent(null, expected));
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
