package kr.lunaf.cloudislands.paper.integration.customitem;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.event.IslandPermissionCheckEvent;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import kr.lunaf.cloudislands.paper.platform.event.PaperEvents;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Protects and accounts for CraftEngine furniture without a hard plugin dependency. */
public final class CraftEngineFurnitureBridge implements Listener {
    private static final String PLACE_EVENT = "net.momirealms.craftengine.bukkit.api.event.FurniturePlaceEvent";
    private static final String BREAK_EVENT = "net.momirealms.craftengine.bukkit.api.event.FurnitureBreakEvent";

    private final Plugin plugin;
    private final ProtectionController protection;
    private final BlockDeltaReporter blockDeltas;
    private final AtomicBoolean compatibilityWarningLogged = new AtomicBoolean();
    private Adapter adapter;

    private CraftEngineFurnitureBridge(Plugin plugin, ProtectionController protection, BlockDeltaReporter blockDeltas) {
        this.plugin = plugin;
        this.protection = protection;
        this.blockDeltas = blockDeltas;
    }

    public static CraftEngineFurnitureBridge register(
        Plugin plugin,
        ProtectionController protection,
        BlockDeltaReporter blockDeltas
    ) {
        CraftEngineFurnitureBridge bridge = new CraftEngineFurnitureBridge(plugin, protection, blockDeltas);
        bridge.install();
        return bridge;
    }

    private void install() {
        Plugin craftEngine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        if (craftEngine == null || !craftEngine.isEnabled()) {
            return;
        }
        try {
            ClassLoader loader = craftEngine.getClass().getClassLoader();
            Class<? extends Event> placeClass = eventClass(loader, PLACE_EVENT);
            Class<? extends Event> breakClass = eventClass(loader, BREAK_EVENT);
            this.adapter = Adapter.create(placeClass, breakClass);
            register(placeClass, EventPriority.HIGHEST, event -> protect(event, IslandPermission.BUILD));
            register(breakClass, EventPriority.HIGHEST, event -> protect(event, IslandPermission.BREAK));
            register(placeClass, EventPriority.MONITOR, event -> account(event, true));
            register(breakClass, EventPriority.MONITOR, event -> account(event, false));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("Unable to install CraftEngine furniture protection and accounting bridge", exception);
        }
    }

    private void protect(Event event, IslandPermission permission) {
        if (!(event instanceof Cancellable cancellable) || adapter == null) {
            return;
        }
        try {
            Context context = adapter.context(event);
            Location location = context.location();
            Player player = context.player();
            if (location == null || location.getWorld() == null || player == null) {
                cancellable.setCancelled(true);
                return;
            }
            PermissionResult result = protection.checkBlock(
                player.getUniqueId(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                permission,
                player.hasPermission("cloudislands.admin.bypass")
            );
            protection.islandAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ()).ifPresent(islandId ->
                PaperEvents.call(new IslandPermissionCheckEvent(
                    islandId, player.getUniqueId(), player, location.getBlock(), permission, result
                ))
            );
            cancellable.setCancelled(!result.allowed());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            cancellable.setCancelled(true);
            warn("Cancelled a CraftEngine furniture operation after an incompatible event payload", exception);
        }
    }

    private void account(Event event, boolean placed) {
        if (adapter == null) {
            return;
        }
        try {
            Context context = adapter.context(event);
            Location location = context.location();
            if (location == null || location.getWorld() == null || context.customKey().isBlank()) {
                return;
            }
            protection.islandAt(location.getWorld().getName(), location.getBlockX(), location.getBlockZ()).ifPresent(islandId -> {
                if (placed) {
                    blockDeltas.customEntityPlaced(islandId, context.customKey());
                } else {
                    blockDeltas.customEntityRemoved(islandId, context.customKey());
                }
            });
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("CraftEngine furniture value accounting requires a reconciliation scan", exception);
        }
    }

    private void register(Class<? extends Event> eventClass, EventPriority priority, EventConsumer consumer) {
        plugin.getServer().getPluginManager().registerEvent(
            eventClass,
            this,
            priority,
            (ignored, event) -> consumer.accept(event),
            plugin,
            true
        );
    }

    private void warn(String message, Throwable exception) {
        if (compatibilityWarningLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning(message + ": " + exception.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventClass(ClassLoader loader, String className) throws ClassNotFoundException {
        Class<?> type = loader.loadClass(className);
        if (!Event.class.isAssignableFrom(type)) {
            throw new ClassNotFoundException(className + " is not a Bukkit event");
        }
        return (Class<? extends Event>) type;
    }

    record Context(Player player, Location location, String customKey) {
    }

    record Adapter(
        Method placePlayer,
        Method placeLocation,
        Method placeFurniture,
        Method breakPlayer,
        Method breakLocation,
        Method breakFurniture,
        Method furnitureConfig,
        Method configId,
        Method keyAsString
    ) {
        static Adapter create(Class<? extends Event> placeClass, Class<? extends Event> breakClass) throws ReflectiveOperationException {
            Method placeFurniture = placeClass.getMethod("furniture");
            Method breakFurniture = breakClass.getMethod("furniture");
            Class<?> furnitureClass = placeFurniture.getReturnType();
            if (!furnitureClass.equals(breakFurniture.getReturnType())) {
                throw new NoSuchMethodException("CraftEngine furniture event payload types differ");
            }
            Method config = furnitureClass.getMethod("config");
            Method id = config.getReturnType().getMethod("id");
            Method asString = id.getReturnType().getMethod("asString");
            return new Adapter(
                placeClass.getMethod("player"),
                placeClass.getMethod("location"),
                placeFurniture,
                breakClass.getMethod("player"),
                breakClass.getMethod("location"),
                breakFurniture,
                config,
                id,
                asString
            );
        }

        Context context(Event event) throws ReflectiveOperationException {
            boolean place = event.getClass().getName().equals(PLACE_EVENT);
            Method playerMethod = place ? placePlayer : breakPlayer;
            Method locationMethod = place ? placeLocation : breakLocation;
            Method furnitureMethod = place ? placeFurniture : breakFurniture;
            Player player = (Player) playerMethod.invoke(event);
            Location location = (Location) locationMethod.invoke(event);
            Object furniture = furnitureMethod.invoke(event);
            Object config = furniture == null ? null : furnitureConfig.invoke(furniture);
            Object id = config == null ? null : configId.invoke(config);
            Object key = id == null ? null : keyAsString.invoke(id);
            String rawKey = key == null ? "" : key.toString().trim();
            return new Context(player, location, rawKey.isBlank() ? "" : CustomBlockKeyService.customKey("CraftEngine", rawKey));
        }
    }

    @FunctionalInterface
    private interface EventConsumer {
        void accept(Event event);
    }
}
