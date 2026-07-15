package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class AddonCommandDeliveryTicketTest {
    @Test
    void acceptsOnlyTheOriginalOnlinePlayerWithinTheSamePluginLifecycle() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000721");
        Plugin plugin = plugin("current");
        Player expected = player(playerUuid, true);
        AddonCommandDeliveryTicket ticket = new AddonCommandDeliveryTicket(plugin, 7L, expected, playerUuid);

        assertTrue(ticket.isCurrent(plugin, 7L, expected));
        assertFalse(ticket.isCurrent(plugin("replacement"), 7L, expected));
        assertFalse(ticket.isCurrent(plugin, 8L, expected), "disable or reconfigure must invalidate queued results");
        assertFalse(ticket.isCurrent(plugin, 7L, player(playerUuid, true)),
            "a reconnect with the same UUID must not inherit an addon result");
        assertFalse(ticket.isCurrent(plugin, 7L, player(playerUuid, false)));
        assertFalse(ticket.isCurrent(plugin, 7L, null));
    }

    private static Plugin plugin(String name) {
        return (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[] {Plugin.class},
            (_proxy, method, _args) -> switch (method.getName()) {
                case "getName" -> name;
                case "hashCode" -> System.identityHashCode(_proxy);
                case "equals" -> _proxy == _args[0];
                case "toString" -> "Plugin[" + name + "]";
                default -> defaultValue(method.getReturnType());
            }
        );
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
