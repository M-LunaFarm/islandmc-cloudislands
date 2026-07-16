package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerConnectionSessionTest {
    @Test
    void acceptsOnlyTheSameOnlineConnection() {
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000001001");
        Player expected = player(playerUuid, true);
        PlayerConnectionSession session = PlayerConnectionSession.capture(expected);

        assertTrue(session.isCurrent(expected));
        assertFalse(session.isCurrent(player(playerUuid, true)),
            "a reconnect with the same UUID must not inherit older player work");
        assertFalse(session.isCurrent(player(playerUuid, false)));
        assertFalse(session.isCurrent(null));
        assertThrows(NullPointerException.class, () -> PlayerConnectionSession.capture(null));
        assertThrows(IllegalArgumentException.class,
            () -> new PlayerConnectionSession(UUID.fromString("00000000-0000-0000-0000-000000001002"), expected));
    }

    @Test
    void routePipelinesCarryTheSessionThroughEveryDelayedBoundary() throws Exception {
        String consumer = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/RouteTicketConsumer.java"));
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/session/PaperRouteSessionListener.java"));

        assertTrue(consumer.contains("consumeAndTeleport(playerSession, ticketId, nonce, attempt + 1)"));
        assertTrue(consumer.contains("teleport(playerSession, ticket, attempt + 1)"));
        assertTrue(consumer.contains("completeTeleport(playerSession, ticket"));
        assertTrue(consumer.contains("Player player = currentPlayer(playerSession)"));
        assertTrue(consumer.contains("return playerSession.isCurrent(player) ? player : null;"));
        assertTrue(listener.contains("PlayerConnectionSession.capture(event.getPlayer())"));
        assertTrue(listener.contains("consumeSession(playerSession, attempt + 1, expectedSession)"));
        assertTrue(listener.contains("ticketConsumer.consumeAndTeleport(playerSession"));
        assertTrue(listener.contains("if (playerSession.isCurrent(stillHere))"),
            "the delayed missing-session kick must not target a replacement connection");
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
