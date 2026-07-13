package kr.lunaf.cloudislands.paper.limit;

import java.lang.reflect.Method;
import java.util.Deque;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.Location;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

/** Accounts for RoseStacker spawns that increase nearby logical stacks without a Bukkit entity spawn. */
public final class LogicalEntitySpawnBridge implements Listener {
    private static final String PRE_EVENT = "dev.rosewood.rosestacker.event.PreStackedSpawnerSpawnEvent";
    private static final String POST_EVENT = "dev.rosewood.rosestacker.event.PostStackedSpawnerSpawnEvent";
    private static final long RESERVATION_EXPIRY_TICKS = 20L;

    private final Plugin plugin;
    private final ProtectionController protection;
    private final IslandLimitCache limits;
    private final IslandLevelScanService levelScanService;
    private final Map<UUID, AtomicLong> reservedByIsland = new ConcurrentHashMap<>();
    private final Map<SpawnerKey, Deque<Reservation>> reservationsBySpawner = new ConcurrentHashMap<>();
    private final AtomicBoolean compatibilityWarningLogged = new AtomicBoolean();
    private Adapter adapter;

    private LogicalEntitySpawnBridge(
        Plugin plugin,
        ProtectionController protection,
        IslandLimitCache limits,
        IslandLevelScanService levelScanService
    ) {
        this.plugin = plugin;
        this.protection = protection;
        this.limits = limits;
        this.levelScanService = levelScanService;
    }

    public static LogicalEntitySpawnBridge register(
        Plugin plugin,
        ProtectionController protection,
        IslandLimitCache limits,
        IslandLevelScanService levelScanService
    ) {
        LogicalEntitySpawnBridge bridge = new LogicalEntitySpawnBridge(plugin, protection, limits, levelScanService);
        bridge.install();
        return bridge;
    }

    private void install() {
        Plugin roseStacker = plugin.getServer().getPluginManager().getPlugin("RoseStacker");
        if (roseStacker == null || !roseStacker.isEnabled()) {
            return;
        }
        try {
            ClassLoader loader = roseStacker.getClass().getClassLoader();
            Class<? extends Event> preClass = eventClass(loader, PRE_EVENT);
            Class<? extends Event> postClass = eventClass(loader, POST_EVENT);
            this.adapter = Adapter.create(preClass, postClass);
            plugin.getServer().getPluginManager().registerEvent(
                preClass, this, EventPriority.HIGHEST, (ignored, event) -> preSpawn(event), plugin, true
            );
            plugin.getServer().getPluginManager().registerEvent(
                postClass, this, EventPriority.MONITOR, (ignored, event) -> postSpawn(event), plugin, false
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("Unable to install RoseStacker logical entity spawn bridge", exception);
        }
    }

    private void preSpawn(Event event) {
        if (!(event instanceof Cancellable cancellable) || adapter == null) {
            return;
        }
        try {
            SpawnContext context = adapter.context(event);
            IslandRegion region = protection.regionAt(context.key().worldName(), context.key().blockX(), context.key().blockZ()).orElse(null);
            if (region == null) {
                return;
            }
            int requested = Math.max(0, adapter.spawnAmount(event));
            OptionalLong resolvedLimit = limits.limitIfReady(region.islandId(), "ENTITY", Long.MAX_VALUE);
            if (resolvedLimit.isEmpty()) {
                cancellable.setCancelled(true);
                return;
            }
            long limit = resolvedLimit.getAsLong();
            long allowed = requested;
            boolean finite = limit != Long.MAX_VALUE;
            if (finite) {
                OptionalLong resolvedCount = limits.blockCountIfReady(region.islandId(), "ENTITY");
                if (resolvedCount.isEmpty()) {
                    cancellable.setCancelled(true);
                    return;
                }
                allowed = reserve(region.islandId(), requested, resolvedCount.getAsLong(), limit);
                if (allowed <= 0L) {
                    cancellable.setCancelled(true);
                    return;
                }
                adapter.setSpawnAmount(event, Math.toIntExact(allowed));
            }
            Reservation reservation = new Reservation(region.islandId(), context.key(), allowed, finite);
            reservationsBySpawner.computeIfAbsent(context.key(), ignored -> new ConcurrentLinkedDeque<>()).addLast(reservation);
            PaperSchedulers.runLater(plugin, () -> expire(reservation), RESERVATION_EXPIRY_TICKS);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            cancellable.setCancelled(true);
            warn("Cancelled a RoseStacker spawn after an incompatible pre-spawn payload", exception);
        }
    }

    private void postSpawn(Event event) {
        if (adapter == null) {
            return;
        }
        try {
            SpawnContext context = adapter.context(event);
            Reservation reservation = poll(context.key());
            if (reservation == null) {
                return;
            }
            PaperSchedulers.runLater(plugin, () -> recordPost(event, reservation), 1L);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("A RoseStacker post-spawn payload could not be matched", exception);
        }
    }

    private void recordPost(Event event, Reservation reservation) {
        try {
            long directDelta = directLogicalDelta(adapter.spawnAmount(event), adapter.spawnedStackCount(event));
            if (directDelta > 0L) {
                EntityType entityType = adapter.entityType(event);
                if (entityType == null) {
                    levelScanService.rescanIsland(reservation.islandId());
                } else {
                    levelScanService.recordBlockDelta(reservation.islandId(), IslandEntityLimitKeys.COUNT_KEY, directDelta);
                    levelScanService.recordBlockDelta(reservation.islandId(), "entity:" + entityType.getKey(), directDelta);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("A RoseStacker logical entity spawn needs reconciliation", exception);
            levelScanService.rescanIsland(reservation.islandId());
        } finally {
            release(reservation);
        }
    }

    private long reserve(UUID islandId, long requested, long current, long limit) {
        AtomicLong reserved = reservedByIsland.computeIfAbsent(islandId, ignored -> new AtomicLong());
        while (true) {
            long existing = reserved.get();
            long available = Math.max(0L, limit - current - existing);
            long allowed = Math.min(Math.max(0L, requested), available);
            if (allowed == 0L || reserved.compareAndSet(existing, existing + allowed)) {
                return allowed;
            }
        }
    }

    private Reservation poll(SpawnerKey key) {
        Deque<Reservation> queue = reservationsBySpawner.get(key);
        if (queue == null) {
            return null;
        }
        Reservation reservation;
        while ((reservation = queue.pollFirst()) != null && reservation.released().get()) {
            // Skip reservations already expired by the main-thread timeout.
        }
        if (queue.isEmpty()) {
            reservationsBySpawner.remove(key, queue);
        }
        return reservation;
    }

    private void expire(Reservation reservation) {
        Deque<Reservation> queue = reservationsBySpawner.get(reservation.key());
        if (queue != null) {
            queue.remove(reservation);
            if (queue.isEmpty()) {
                reservationsBySpawner.remove(reservation.key(), queue);
            }
        }
        release(reservation);
    }

    private void release(Reservation reservation) {
        if (!reservation.released().compareAndSet(false, true) || !reservation.finite()) {
            return;
        }
        AtomicLong reserved = reservedByIsland.get(reservation.islandId());
        if (reserved != null && reserved.addAndGet(-reservation.amount()) <= 0L) {
            reservedByIsland.remove(reservation.islandId(), reserved);
        }
    }

    private void warn(String message, Throwable exception) {
        if (compatibilityWarningLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning(message + ": " + exception.getClass().getSimpleName());
        }
    }

    static long directLogicalDelta(long spawnedAmount, long physicalStacks) {
        return Math.max(0L, spawnedAmount - Math.max(0L, physicalStacks));
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventClass(ClassLoader loader, String className) throws ClassNotFoundException {
        Class<?> type = loader.loadClass(className);
        if (!Event.class.isAssignableFrom(type)) {
            throw new ClassNotFoundException(className + " is not a Bukkit event");
        }
        return (Class<? extends Event>) type;
    }

    private record SpawnerKey(String worldName, int blockX, int blockY, int blockZ) {
        static SpawnerKey of(Location location) {
            String worldName = location == null || location.getWorld() == null ? "" : location.getWorld().getName();
            return new SpawnerKey(worldName, location == null ? 0 : location.getBlockX(),
                location == null ? 0 : location.getBlockY(), location == null ? 0 : location.getBlockZ());
        }
    }

    private record SpawnContext(SpawnerKey key) {
    }

    private record Reservation(UUID islandId, SpawnerKey key, long amount, boolean finite, AtomicBoolean released) {
        Reservation(UUID islandId, SpawnerKey key, long amount, boolean finite) {
            this(islandId, key, amount, finite, new AtomicBoolean());
        }
    }

    private record Adapter(
        Method stackGetter,
        Method locationGetter,
        Method preAmountGetter,
        Method preAmountSetter,
        Method postAmountGetter,
        Method spawnedStacksGetter
    ) {
        static Adapter create(Class<? extends Event> preClass, Class<? extends Event> postClass) throws ReflectiveOperationException {
            Method stackGetter = preClass.getMethod("getStack");
            Class<?> stackClass = stackGetter.getReturnType();
            return new Adapter(
                stackGetter,
                stackClass.getMethod("getLocation"),
                preClass.getMethod("getSpawnAmount"),
                preClass.getMethod("setSpawnAmount", int.class),
                postClass.getMethod("getSpawnAmount"),
                postClass.getMethod("getSpawnedStacks")
            );
        }

        SpawnContext context(Event event) throws ReflectiveOperationException {
            Object stack = stackGetter.invoke(event);
            return new SpawnContext(SpawnerKey.of((Location) locationGetter.invoke(stack)));
        }

        int spawnAmount(Event event) throws ReflectiveOperationException {
            Method getter = event.getClass().getName().equals(PRE_EVENT) ? preAmountGetter : postAmountGetter;
            return ((Number) getter.invoke(event)).intValue();
        }

        void setSpawnAmount(Event event, int amount) throws ReflectiveOperationException {
            preAmountSetter.invoke(event, amount);
        }

        int spawnedStackCount(Event event) throws ReflectiveOperationException {
            Object value = spawnedStacksGetter.invoke(event);
            return value instanceof java.util.Collection<?> collection ? collection.size() : 0;
        }

        EntityType entityType(Event event) throws ReflectiveOperationException {
            Object stack = stackGetter.invoke(event);
            Object spawner = stack.getClass().getMethod("getSpawner").invoke(stack);
            return spawner instanceof CreatureSpawner creatureSpawner ? creatureSpawner.getSpawnedType() : null;
        }
    }
}
