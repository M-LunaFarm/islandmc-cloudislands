package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.integration.vanish.PlayerVisibilityService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class IslandCommandVanishCompletionTest {
    @Test
    void playerTargetCompletionOmitsTargetsHiddenByBukkitVisibility() {
        Player visible = player("Visible", true);
        Player hidden = player("Hidden", true);
        Player viewer = viewer(hidden);
        Server server = server(List.of(visible, hidden));
        Plugin plugin = plugin(server);
        IslandCommandTabCompleter completer = new IslandCommandTabCompleter(
            plugin,
            null,
            PlayerVisibilityService.metadataOnly()
        );

        List<String> suggestions = completer.onTabComplete(viewer, null, "island", new String[]{"invite", ""});

        assertEquals(List.of("Visible"), suggestions);
    }

    @Test
    void targetAwareCommandsSuggestOnlyVisibleOnlinePlayersAlongsideLiterals() {
        Player visible = player("Visible", true);
        Player hidden = player("Hidden", true);
        Player viewer = viewer(hidden);
        IslandCommandTabCompleter completer = new IslandCommandTabCompleter(
            plugin(server(List.of(visible, hidden))),
            null,
            PlayerVisibilityService.metadataOnly()
        );

        for (String subcommand : List.of("info", "select", "balance", "team", "warp", "accept", "decline")) {
            assertEquals(List.of("Visible"), completer.onTabComplete(viewer, null, "island", new String[]{subcommand, ""}), subcommand);
        }

        assertVisibleTargetWithLiteral(completer, viewer, "visit", "random");
        assertVisibleTargetWithLiteral(completer, viewer, "rate", "current");
        assertVisibleTargetWithLiteral(completer, viewer, "delete-review", "current");
        assertVisibleTargetWithLiteral(completer, viewer, "values", "10");
        assertEquals(List.of("default", "shop", "farm", "event", "pvp"), completer.onTabComplete(viewer, null, "island", new String[]{"warp", "Visible", ""}));
        assertEquals(List.of("10", "25", "50", "100"), completer.onTabComplete(viewer, null, "island", new String[]{"values", "Visible", ""}));
        assertEquals(List.of("10", "25", "50", "100"), completer.onTabComplete(viewer, null, "island", new String[]{"rank", "worth", ""}));
        assertEquals(List.of("10", "25", "50", "100"), completer.onTabComplete(viewer, null, "island", new String[]{"reviewrank", ""}));
        assertTrue(completer.onTabComplete(viewer, null, "island", new String[]{"rank-list", ""}).containsAll(List.of("worth", "bank", "review", "10")));
    }

    private static void assertVisibleTargetWithLiteral(IslandCommandTabCompleter completer, Player viewer, String subcommand, String literal) {
        List<String> suggestions = completer.onTabComplete(viewer, null, "island", new String[]{subcommand, ""});
        assertTrue(suggestions.contains("Visible"), subcommand);
        assertTrue(suggestions.contains(literal), subcommand);
        assertFalse(suggestions.contains("Hidden"), subcommand);
    }

    private static Player viewer(Player hidden) {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getName" -> "Viewer";
            case "hasPermission" -> true;
            case "canSee" -> args[0] != hidden;
            case "getMetadata" -> List.of();
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Player player(String name, boolean visible) {
        UUID uuid = UUID.randomUUID();
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> uuid;
            case "getName" -> name;
            case "getMetadata" -> List.of();
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Server server(Collection<? extends Player> players) {
        return (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class}, (proxy, method, args) -> {
            if (method.getName().equals("getOnlinePlayers")) {
                return players;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Plugin plugin(Server server) {
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class}, (proxy, method, args) -> {
            if (method.getName().equals("getServer")) {
                return server;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
