package kr.lunaf.cloudislands.paper.limit;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
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
 * Keeps incremental worth and restricted-block counts aligned with logical block stacks.
 *
 * <p>The bridge intentionally uses reflection so CloudIslands remains loadable without a
 * stacker. Count-preserving merges of two objects already present in the world are omitted;
 * only placement, inventory deposit, and unstack operations change an island's total.</p>
 */
public final class LogicalStackDeltaBridge implements Listener {
    private static final long NOTICE_COOLDOWN_MILLIS = 3_000L;
    private static final String ROSE_SPAWNER_STACK = "dev.rosewood.rosestacker.event.SpawnerStackEvent";
    private static final String ROSE_BLOCK_STACK = "dev.rosewood.rosestacker.event.BlockStackEvent";
    private static final String ROSE_SPAWNER_UNSTACK = "dev.rosewood.rosestacker.event.SpawnerUnstackEvent";
    private static final String ROSE_BLOCK_UNSTACK = "dev.rosewood.rosestacker.event.BlockUnstackEvent";
    private static final String WILD_SPAWNER_PLACE = "com.bgsoftware.wildstacker.api.events.SpawnerPlaceEvent";
    private static final String WILD_SPAWNER_INVENTORY = "com.bgsoftware.wildstacker.api.events.SpawnerPlaceInventoryEvent";
    private static final String WILD_SPAWNER_UNSTACK = "com.bgsoftware.wildstacker.api.events.SpawnerUnstackEvent";
    private static final String WILD_BARREL_PLACE = "com.bgsoftware.wildstacker.api.events.BarrelPlaceEvent";
    private static final String WILD_BARREL_INVENTORY = "com.bgsoftware.wildstacker.api.events.BarrelPlaceInventoryEvent";
    private static final String WILD_BARREL_UNSTACK = "com.bgsoftware.wildstacker.api.events.BarrelUnstackEvent";

    private final Plugin plugin;
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final IslandLevelScanService levelScanService;
    private final MessageRenderer messages;
    private final Map<Event, PendingChange> pending = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<String, Long> lastNotice = new ConcurrentHashMap<>();
    private final Set<String> compatibilityWarnings = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean asyncWarningLogged = new AtomicBoolean();

    private LogicalStackDeltaBridge(
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

    public static LogicalStackDeltaBridge register(
        Plugin plugin,
        ProtectionController protection,
        IslandLimitCache limits,
        IslandLevelScanService levelScanService,
        MessageRenderer messages
    ) {
        LogicalStackDeltaBridge bridge = new LogicalStackDeltaBridge(plugin, protection, limits, levelScanService, messages);
        bridge.register("RoseStacker", ROSE_SPAWNER_STACK, EventKind.ROSE_SPAWNER_INCREASE);
        bridge.register("RoseStacker", ROSE_BLOCK_STACK, EventKind.ROSE_BLOCK_INCREASE);
        bridge.register("RoseStacker", ROSE_SPAWNER_UNSTACK, EventKind.ROSE_SPAWNER_DECREASE);
        bridge.register("RoseStacker", ROSE_BLOCK_UNSTACK, EventKind.ROSE_BLOCK_DECREASE);
        bridge.register("WildStacker", WILD_SPAWNER_PLACE, EventKind.WILD_SPAWNER_PLACE);
        bridge.register("WildStacker", WILD_SPAWNER_INVENTORY, EventKind.WILD_SPAWNER_INVENTORY);
        bridge.register("WildStacker", WILD_SPAWNER_UNSTACK, EventKind.WILD_SPAWNER_DECREASE);
        bridge.register("WildStacker", WILD_BARREL_PLACE, EventKind.WILD_BARREL_PLACE);
        bridge.register("WildStacker", WILD_BARREL_INVENTORY, EventKind.WILD_BARREL_INVENTORY);
        bridge.register("WildStacker", WILD_BARREL_UNSTACK, EventKind.WILD_BARREL_DECREASE);
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
            warnOnce(eventClassName, "Unable to install " + pluginName + " logical-stack bridge: "
                + exception.getClass().getSimpleName());
        }
    }

    private void check(Event event, Adapter adapter) {
        if (!(event instanceof Cancellable cancellable)) {
            return;
        }
        if (event.isAsynchronous()) {
            cancellable.setCancelled(true);
            if (asyncWarningLogged.compareAndSet(false, true)) {
                plugin.getLogger().warning("Cancelled an asynchronous logical-stack mutation because island state is main-thread owned");
            }
            return;
        }
        try {
            LogicalChange change = adapter.read(event);
            if (change.supplementalDelta() == 0L) {
                return;
            }
            IslandRegion region = protection.regionAt(change.location().getBlock()).orElse(null);
            if (region == null) {
                return;
            }
            String limitKey = IslandBlockLimitKeys.limitKey(change.material());
            if (change.logicalAddition() > 0L && limitKey != null
                && !allowed(cancellable, change.player(), region.islandId(), limitKey, change.logicalAddition())) {
                return;
            }
            pending.put(event, new PendingChange(region, adapter));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            cancellable.setCancelled(true);
            warnOnce(event.getClass().getName(), "Cancelled a logical-stack mutation after an incompatible vendor payload: "
                + exception.getClass().getSimpleName());
        }
    }

    private boolean allowed(
        Cancellable event,
        Player player,
        UUID islandId,
        String limitKey,
        long logicalAddition
    ) {
        OptionalLong resolvedLimit = limits.limitIfReady(islandId, limitKey, Long.MAX_VALUE);
        OptionalLong resolvedCount = limits.blockCountIfReady(islandId, limitKey);
        if (resolvedLimit.isEmpty() || resolvedCount.isEmpty()) {
            event.setCancelled(true);
            notify(player, islandId, limitKey + ":loading", "limit-data-loading",
                "섬 " + limitName(limitKey) + " 한도 데이터를 불러오는 중입니다. 잠시 후 다시 시도해 주세요.",
                "limit", limitName(limitKey));
            return false;
        }
        long limit = resolvedLimit.getAsLong();
        long current = resolvedCount.getAsLong();
        if (limit != Long.MAX_VALUE && (current >= limit || logicalAddition > limit - current)) {
            event.setCancelled(true);
            notify(player, islandId, limitKey, "limit-reached",
                "섬 " + limitName(limitKey) + " 제한에 도달했습니다. 현재 " + current + "/" + limit,
                "limit", limitName(limitKey), "current", Long.toString(current), "max", Long.toString(limit));
            return false;
        }
        return true;
    }

    private void record(Event event) {
        PendingChange accepted = pending.remove(event);
        if (accepted == null || event instanceof Cancellable cancellable && cancellable.isCancelled()) {
            return;
        }
        try {
            LogicalChange change = accepted.adapter().read(event);
            long delta = change.supplementalDelta();
            if (delta == 0L) {
                return;
            }
            UUID islandId = accepted.region().islandId();
            levelScanService.recordBlockDelta(islandId, change.material().getKey().toString(), delta);
            String countKey = IslandBlockLimitKeys.countKey(change.material());
            if (countKey != null) {
                levelScanService.recordBlockDelta(islandId, countKey, delta);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warnOnce(event.getClass().getName() + ":monitor", "A logical-stack mutation needs reconciliation after a vendor payload changed: "
                + exception.getClass().getSimpleName());
            levelScanService.rescanIsland(accepted.region().islandId());
        }
    }

    private void notify(
        Player player,
        UUID islandId,
        String noticeKey,
        String messageKey,
        String fallback,
        String... variables
    ) {
        if (player == null) {
            return;
        }
        String key = islandId + ":" + noticeKey + ":" + player.getUniqueId();
        long now = System.currentTimeMillis();
        long previous = lastNotice.getOrDefault(key, 0L);
        if (now - previous < NOTICE_COOLDOWN_MILLIS) {
            return;
        }
        lastNotice.put(key, now);
        String rendered = messages == null ? fallback : messages.plain(messageKey, variables);
        player.sendMessage(rendered == null || rendered.isBlank() ? fallback : rendered);
    }

    private void warnOnce(String key, String warning) {
        if (compatibilityWarnings.add(key)) {
            plugin.getLogger().warning(warning);
        }
    }

    private static String limitName(String key) {
        return switch (key) {
            case "HOPPER" -> "호퍼";
            case "SPAWNER" -> "스포너";
            case "REDSTONE" -> "레드스톤";
            default -> key;
        };
    }

    static long roseIncreaseDelta(boolean isNew, long currentStackSize, long increaseAmount) {
        if (increaseAmount <= 0L) {
            return 0L;
        }
        return isNew && currentStackSize == 0L ? Math.max(0L, increaseAmount - 1L) : increaseAmount;
    }

    static long wildSpawnerPlacementDelta(long stackAmount) {
        return Math.max(0L, stackAmount - 1L);
    }

    static long wildSpawnerDecreaseDelta(long currentStackSize, long decreaseAmount, boolean playerSource) {
        long amount = Math.min(Math.max(0L, decreaseAmount), Math.max(0L, currentStackSize));
        if (!playerSource && amount >= currentStackSize) {
            return Math.max(0L, amount - 1L);
        }
        return amount;
    }

    private enum EventKind {
        ROSE_SPAWNER_INCREASE("getStack", "getIncreaseAmount", true, true, false, false),
        ROSE_BLOCK_INCREASE("getStack", "getIncreaseAmount", true, false, false, false),
        ROSE_SPAWNER_DECREASE("getStack", "getDecreaseAmount", false, true, false, false),
        ROSE_BLOCK_DECREASE("getStack", "getDecreaseAmount", false, false, false, false),
        WILD_SPAWNER_PLACE("getSpawner", null, true, true, true, false),
        WILD_SPAWNER_INVENTORY("getSpawner", "getIncreaseAmount", true, true, false, false),
        WILD_SPAWNER_DECREASE("getSpawner", "getAmount", false, true, false, true),
        WILD_BARREL_PLACE("getBarrel", null, true, false, true, false),
        WILD_BARREL_INVENTORY("getBarrel", "getIncreaseAmount", true, false, false, false),
        WILD_BARREL_DECREASE("getBarrel", "getAmount", false, false, false, true);

        private final String objectMethod;
        private final String amountMethod;
        private final boolean increase;
        private final boolean spawner;
        private final boolean amountFromObject;
        private final boolean wildDecrease;

        EventKind(
            String objectMethod,
            String amountMethod,
            boolean increase,
            boolean spawner,
            boolean amountFromObject,
            boolean wildDecrease
        ) {
            this.objectMethod = objectMethod;
            this.amountMethod = amountMethod;
            this.increase = increase;
            this.spawner = spawner;
            this.amountFromObject = amountFromObject;
            this.wildDecrease = wildDecrease;
        }

        boolean roseIncrease() {
            return this == ROSE_SPAWNER_INCREASE || this == ROSE_BLOCK_INCREASE;
        }

        boolean wildSpawnerPlace() {
            return this == WILD_SPAWNER_PLACE;
        }

        boolean wildBarrelPlace() {
            return this == WILD_BARREL_PLACE;
        }
    }

    private record LogicalChange(
        Location location,
        Material material,
        Player player,
        long logicalAddition,
        long supplementalDelta
    ) {
    }

    private record PendingChange(IslandRegion region, Adapter adapter) {
    }

    private record Adapter(
        EventKind kind,
        Method objectGetter,
        Method playerGetter,
        Method amountGetter,
        Method newGetter,
        Method locationGetter,
        Method materialGetter,
        Method stackSizeGetter,
        Method sourceGetter
    ) {
        static Adapter create(Class<? extends Event> eventClass, EventKind kind) throws ReflectiveOperationException {
            Method objectGetter = eventClass.getMethod(kind.objectMethod);
            Class<?> objectClass = objectGetter.getReturnType();
            Method playerGetter = methodOrNull(eventClass, "getPlayer");
            Method amountGetter = kind.amountFromObject
                ? objectClass.getMethod("getStackAmount")
                : eventClass.getMethod(kind.amountMethod);
            Method newGetter = kind.roseIncrease() ? eventClass.getMethod("isNew") : null;
            Method locationGetter = objectClass.getMethod("getLocation");
            Method materialGetter = kind.spawner ? null : methodOrNull(objectClass, "getType");
            Method stackSizeGetter = kind.roseIncrease() || !kind.increase
                ? objectClass.getMethod(kind.wildDecrease ? "getStackAmount" : "getStackSize")
                : null;
            Method sourceGetter = kind.wildDecrease ? eventClass.getMethod("getUnstackSource") : null;
            return new Adapter(kind, objectGetter, playerGetter, amountGetter, newGetter, locationGetter, materialGetter, stackSizeGetter, sourceGetter);
        }

        LogicalChange read(Event event) throws ReflectiveOperationException {
            Object object = objectGetter.invoke(event);
            Location location = (Location) locationGetter.invoke(object);
            Material material = kind.spawner ? Material.SPAWNER : material(object, location);
            Player player = playerGetter == null ? null : (Player) playerGetter.invoke(event);
            long amount = ((Number) (kind.amountFromObject ? amountGetter.invoke(object) : amountGetter.invoke(event))).longValue();
            long current = stackSizeGetter == null ? 0L : ((Number) stackSizeGetter.invoke(object)).longValue();
            long boundedDecrease = kind.increase ? 0L : Math.min(Math.max(0L, amount), Math.max(0L, current));
            boolean playerSource = sourceGetter != null && sourceGetter.invoke(event) instanceof Player;
            long supplemental = switch (kind) {
                case ROSE_SPAWNER_INCREASE, ROSE_BLOCK_INCREASE -> roseIncreaseDelta(
                    (Boolean) newGetter.invoke(event), current, amount
                );
                case WILD_SPAWNER_PLACE -> wildSpawnerPlacementDelta(amount);
                case WILD_BARREL_PLACE, WILD_SPAWNER_INVENTORY, WILD_BARREL_INVENTORY -> Math.max(0L, amount);
                case WILD_SPAWNER_DECREASE -> -wildSpawnerDecreaseDelta(current, amount, playerSource);
                case ROSE_SPAWNER_DECREASE, ROSE_BLOCK_DECREASE, WILD_BARREL_DECREASE -> -boundedDecrease;
            };
            return new LogicalChange(location, material, player, kind.increase ? Math.max(0L, amount) : 0L, supplemental);
        }

        private Material material(Object object, Location location) throws ReflectiveOperationException {
            if (materialGetter != null) {
                Object value = materialGetter.invoke(object);
                if (value instanceof Material material) {
                    return material;
                }
            }
            return location.getBlock().getType();
        }

        private static Method methodOrNull(Class<?> type, String name) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }
}
