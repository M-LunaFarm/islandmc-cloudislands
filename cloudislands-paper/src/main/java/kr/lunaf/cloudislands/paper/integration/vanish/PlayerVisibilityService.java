package kr.lunaf.cloudislands.paper.integration.vanish;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;

public final class PlayerVisibilityService {
    private static final List<String> SUPPORTED_PLUGINS = List.of("SuperVanish", "PremiumVanish", "CMI");

    private final Map<String, Predicate<Player>> adapters;

    PlayerVisibilityService(Map<String, Predicate<Player>> adapters) {
        this.adapters = Map.copyOf(adapters == null ? Map.of() : adapters);
    }

    public static PlayerVisibilityService discover(Server server) {
        LinkedHashMap<String, Predicate<Player>> adapters = new LinkedHashMap<>();
        if (enabled(server, "SuperVanish") || enabled(server, "PremiumVanish")) {
            Predicate<Player> superVanish = staticPlayerPredicate(
                "de.myzelyam.api.vanish.VanishAPI",
                "isInvisible"
            );
            if (superVanish != null) {
                adapters.put("SuperVanish", superVanish);
                adapters.put("PremiumVanish", superVanish);
            }
        }
        if (enabled(server, "CMI")) {
            Predicate<Player> cmi = cmiPredicate();
            if (cmi != null) {
                adapters.put("CMI", cmi);
            }
        }
        return new PlayerVisibilityService(adapters);
    }

    public static PlayerVisibilityService metadataOnly() {
        return new PlayerVisibilityService(Map.of());
    }

    public boolean visibleTo(Player viewer, Player target) {
        if (target == null || viewer == target || Objects.equals(viewer == null ? null : viewer.getUniqueId(), target.getUniqueId())) {
            return target != null;
        }
        if (viewer != null && !canSee(viewer, target)) {
            return false;
        }
        return !isVanished(target);
    }

    public boolean isVanished(Player player) {
        if (player == null) {
            return false;
        }
        if (metadataVanished(player)) {
            return true;
        }
        for (Predicate<Player> adapter : adapters.values()) {
            try {
                if (adapter.test(player)) {
                    return true;
                }
            } catch (LinkageError | RuntimeException ignored) {
                // A failing optional hook must not break command completion.
            }
        }
        return false;
    }

    public boolean supports(String pluginName) {
        return pluginName != null && adapters.containsKey(pluginName);
    }

    public Map<String, String> runtimeDetails(String pluginName) {
        return Map.of(
            "adapter", supports(pluginName) ? adapterName(pluginName) : "metadata-fallback",
            "filters", "island-player-command-suggestions",
            "metadataFallback", "vanished"
        );
    }

    public static List<String> supportedPlugins() {
        return SUPPORTED_PLUGINS;
    }

    private String adapterName(String pluginName) {
        return pluginName.equals("CMI") ? "cmi-vanish-manager" : "supervanish-api";
    }

    private static boolean enabled(Server server, String pluginName) {
        try {
            return server != null && server.getPluginManager().isPluginEnabled(pluginName);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean canSee(Player viewer, Player target) {
        try {
            return viewer.canSee(target);
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean metadataVanished(Player player) {
        try {
            for (MetadataValue value : player.getMetadata("vanished")) {
                if (value != null && value.asBoolean()) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // Continue with installed plugin APIs when metadata access is unavailable.
        }
        return false;
    }

    private static Predicate<Player> staticPlayerPredicate(String className, String methodName) {
        try {
            Class<?> apiClass = Class.forName(className);
            Method method = apiClass.getMethod(methodName, Player.class);
            return player -> invokeBoolean(method, null, player);
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
            return null;
        }
    }

    private static Predicate<Player> cmiPredicate() {
        try {
            Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
            Method getInstance = cmiClass.getMethod("getInstance");
            Object cmi = getInstance.invoke(null);
            if (cmi == null) {
                return null;
            }
            Method getVanishManager = cmi.getClass().getMethod("getVanishManager");
            Object vanishManager = getVanishManager.invoke(cmi);
            if (vanishManager == null) {
                return null;
            }
            Method getAllVanished = vanishManager.getClass().getMethod("getAllVanished");
            return player -> {
                try {
                    Object vanished = getAllVanished.invoke(vanishManager);
                    return vanished instanceof Collection<?> collection && collection.contains(player.getUniqueId());
                } catch (ReflectiveOperationException | RuntimeException error) {
                    return false;
                }
            };
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean invokeBoolean(Method method, Object target, Player player) {
        try {
            return Boolean.TRUE.equals(method.invoke(target, player));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }
}
