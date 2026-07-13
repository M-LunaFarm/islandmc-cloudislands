package kr.lunaf.cloudislands.paper.limit;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.OptionalLong;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/**
 * Enforces island spawner limits for logical stack increases that are larger than the
 * physical {@code BlockPlaceEvent} delta exposed by Bukkit.
 *
 * <p>The bridge intentionally uses reflection so CloudIslands remains loadable when no
 * supported stacker is installed. Existing-stack merge events are not observed: merging
 * two stacks already present in an island preserves the island's total logical count.</p>
 */
public final class StackedSpawnerLimitBridge implements Listener {
    private static final String LIMIT_KEY = "SPAWNER";
    private static final String MATERIAL_KEY = Material.SPAWNER.getKey().toString();
    private static final String ROSE_EVENT = "dev.rosewood.rosestacker.event.SpawnerStackEvent";
    private static final String WILD_PLACE_EVENT = "com.bgsoftware.wildstacker.api.events.SpawnerPlaceEvent";
    private static final String WILD_INVENTORY_EVENT = "com.bgsoftware.wildstacker.api.events.SpawnerPlaceInventoryEvent";

    private final Plugin plugin;
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final IslandLevelScanService levelScanService;
    private final MessageRenderer messages;
    private final Map<Event, AcceptedIncrease> accepted = Collections.synchronizedMap(new WeakHashMap<>());
    private final AtomicBoolean asyncWarningLogged = new AtomicBoolean();

    private StackedSpawnerLimitBridge(
        Plugin plugin,
        ProtectionController protection,
        IslandLimitCache limits,
        IslandLevelScanService levelScanService,
        MessageRenderer messages
    ) {
        this.plugin = plugin;
        this.protection = protection;
        this.limits = limits;
        this.levelScanService = levelScanService;
        this.messages = messages;
    }

    public static StackedSpawnerLimitBridge register(
        Plugin plugin,
        ProtectionController protection,
        IslandLimitCache limits,
        IslandLevelScanService levelScanService,
        MessageRenderer messages
    ) {
        StackedSpawnerLimitBridge bridge = new StackedSpawnerLimitBridge(plugin, protection, limits, levelScanService, messages);
        bridge.register("RoseStacker", ROSE_EVENT, EventKind.ROSE_STACK);
        bridge.register("WildStacker", WILD_PLACE_EVENT, EventKind.WILD_PLACE);
        bridge.register("WildStacker", WILD_INVENTORY_EVENT, EventKind.WILD_INVENTORY);
        return bridge;
    }

    private void register(String pluginName, String eventClassName, EventKind kind) {
        Plugin vendor = plugin.getServer().getPluginManager().getPlugin(pluginName);
        if (vendor == null || !vendor.isEnabled()) {
            return;
        }
        try {
            Class<?> rawEventClass = vendor.getClass().getClassLoader().loadClass(eventClassName);
            if (!Event.class.isAssignableFrom(rawEventClass)) {
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) rawEventClass;
            Adapter adapter = Adapter.create(eventClass, kind);
            plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                this,
                EventPriority.HIGHEST,
                (ignored, event) -> check(event, adapter),
                plugin,
                true
            );
            plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                this,
                EventPriority.MONITOR,
                (ignored, event) -> record(event),
                plugin,
                true
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Unable to install " + pluginName + " spawner-limit bridge for " + eventClassName
                + ": " + exception.getClass().getSimpleName());
        }
    }

    private void check(Event event, Adapter adapter) {
        if (!(event instanceof Cancellable cancellable)) {
            return;
        }
        if (event.isAsynchronous()) {
            cancellable.setCancelled(true);
            if (asyncWarningLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("Cancelled an asynchronous stacked-spawner increase because island limits require main-thread region state");
            }
            return;
        }
        try {
            Increase increase = adapter.read(event);
            if (increase.logicalAddition() <= 0L) {
                return;
            }
            IslandRegion region = protection.regionAt(increase.location().getBlock()).orElse(null);
            if (region == null) {
                return;
            }
            OptionalLong resolvedLimit = limits.limitIfReady(region.islandId(), LIMIT_KEY, Long.MAX_VALUE);
            OptionalLong resolvedCount = limits.blockCountIfReady(region.islandId(), LIMIT_KEY);
            if (resolvedLimit.isEmpty() || resolvedCount.isEmpty()) {
                cancellable.setCancelled(true);
                notify(increase.player(), "limit-data-loading", "섬 스포너 한도 데이터를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.");
                return;
            }
            long limit = resolvedLimit.getAsLong();
            long current = resolvedCount.getAsLong();
            if (limit != Long.MAX_VALUE && (current >= limit || increase.logicalAddition() > limit - current)) {
                cancellable.setCancelled(true);
                notify(increase.player(), "limit-reached", "섬 스포너 제한에 도달했습니다. 현재 " + current + "/" + limit,
                    "limit", "스포너", "current", Long.toString(current), "max", Long.toString(limit));
                return;
            }
            if (increase.supplementalDelta() > 0L) {
                accepted.put(event, new AcceptedIncrease(region, increase.supplementalDelta()));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            cancellable.setCancelled(true);
            plugin.getLogger().warning("Cancelled a stacked-spawner increase after an incompatible vendor event payload: "
                + exception.getClass().getSimpleName());
        }
    }

    private void record(Event event) {
        AcceptedIncrease increase = accepted.remove(event);
        if (increase == null || event instanceof Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }
        levelScanService.recordBlockDelta(increase.region().islandId(), MATERIAL_KEY, increase.delta());
        levelScanService.recordBlockDelta(
            increase.region().islandId(),
            IslandBlockLimitKeys.countKey(Material.SPAWNER),
            increase.delta()
        );
    }

    private void notify(Player player, String key, String fallback, String... variables) {
        if (player == null) {
            return;
        }
        String rendered = messages == null ? fallback : messages.plain(key, variables);
        player.sendMessage(rendered == null || rendered.isBlank() ? fallback : rendered);
    }

    static long roseSupplementalDelta(boolean isNew, long currentStackSize, long increaseAmount) {
        if (increaseAmount <= 0L) {
            return 0L;
        }
        return isNew && currentStackSize == 0L ? Math.max(0L, increaseAmount - 1L) : increaseAmount;
    }

    static long wildPlacementSupplementalDelta(long stackAmount) {
        return Math.max(0L, stackAmount - 1L);
    }

    private enum EventKind {
        ROSE_STACK,
        WILD_PLACE,
        WILD_INVENTORY
    }

    private record Increase(Location location, Player player, long logicalAddition, long supplementalDelta) {
    }

    private record AcceptedIncrease(IslandRegion region, long delta) {
    }

    private record Adapter(
        EventKind kind,
        Method stackGetter,
        Method playerGetter,
        Method amountGetter,
        Method newGetter,
        Method locationGetter,
        Method stackSizeGetter
    ) {
        static Adapter create(Class<? extends Event> eventClass, EventKind kind) throws ReflectiveOperationException {
            Method stackGetter = eventClass.getMethod(kind == EventKind.ROSE_STACK ? "getStack" : "getSpawner");
            Class<?> stackClass = stackGetter.getReturnType();
            Method playerGetter = eventClass.getMethod("getPlayer");
            Method amountGetter = switch (kind) {
                case ROSE_STACK, WILD_INVENTORY -> eventClass.getMethod("getIncreaseAmount");
                case WILD_PLACE -> stackClass.getMethod("getStackAmount");
            };
            Method newGetter = kind == EventKind.ROSE_STACK ? eventClass.getMethod("isNew") : null;
            Method locationGetter = stackClass.getMethod("getLocation");
            Method stackSizeGetter = kind == EventKind.ROSE_STACK ? stackClass.getMethod("getStackSize") : null;
            return new Adapter(kind, stackGetter, playerGetter, amountGetter, newGetter, locationGetter, stackSizeGetter);
        }

        Increase read(Event event) throws ReflectiveOperationException {
            Object stack = stackGetter.invoke(event);
            Location location = (Location) locationGetter.invoke(stack);
            Player player = (Player) playerGetter.invoke(event);
            long amount = ((Number) (kind == EventKind.WILD_PLACE ? amountGetter.invoke(stack) : amountGetter.invoke(event))).longValue();
            long supplemental = switch (kind) {
                case ROSE_STACK -> roseSupplementalDelta(
                    (Boolean) newGetter.invoke(event),
                    ((Number) stackSizeGetter.invoke(stack)).longValue(),
                    amount
                );
                case WILD_PLACE -> wildPlacementSupplementalDelta(amount);
                case WILD_INVENTORY -> Math.max(0L, amount);
            };
            return new Increase(location, player, Math.max(0L, amount), supplemental);
        }
    }
}
