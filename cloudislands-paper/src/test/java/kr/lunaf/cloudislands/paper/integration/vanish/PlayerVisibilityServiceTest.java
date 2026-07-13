package kr.lunaf.cloudislands.paper.integration.vanish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PlayerVisibilityServiceTest {
    @Test
    void installedAdapterAndBukkitVisibilityBothHideVanishedTargets() {
        UUID hiddenId = UUID.randomUUID();
        Player hidden = player(hiddenId, "Hidden", true);
        Player visible = player(UUID.randomUUID(), "Visible", true);
        Player viewer = player(UUID.randomUUID(), "Viewer", false);
        Predicate<Player> vanished = player -> player.getUniqueId().equals(hiddenId);
        PlayerVisibilityService service = new PlayerVisibilityService(Map.of("SuperVanish", vanished));

        assertTrue(service.supports("SuperVanish"));
        assertFalse(service.visibleTo(viewer, hidden));
        assertFalse(service.visibleTo(viewer, visible));
        assertTrue(service.visibleTo(hidden, hidden));
    }

    @Test
    void ordinaryVisiblePlayerRemainsSuggestibleAndUnknownPluginIsNotCertified() {
        Player target = player(UUID.randomUUID(), "Visible", true);
        Player viewer = player(UUID.randomUUID(), "Viewer", true);
        PlayerVisibilityService service = new PlayerVisibilityService(Map.of());

        assertTrue(service.visibleTo(viewer, target));
        assertFalse(service.supports("SuperVanish"));
        assertTrue(service.runtimeDetails("SuperVanish").get("metadataFallback").equals("vanished"));
    }

    private static Player player(UUID uuid, String name, boolean canSee) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> uuid;
                case "getName" -> name;
                case "canSee" -> canSee;
                case "getMetadata" -> List.of();
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> name;
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
        if (type == double.class) {
            return 0.0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
