package kr.lunaf.cloudislands.paper.limit;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.platform.event.PaperEvents;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.Plugin;

/** Reconciles logical entity removals that one physical Bukkit death cannot represent. */
public final class LogicalEntityRemovalBridge implements Listener {
    private static final String ROSE_MULTIPLE_DEATH = "dev.rosewood.rosestacker.event.EntityStackMultipleDeathEvent";
    private static final String ROSE_STACK_CLEAR = "dev.rosewood.rosestacker.event.EntityStackClearEvent";
    private static final String WILD_ENTITY_UNSTACK = "com.bgsoftware.wildstacker.api.events.EntityUnstackEvent";

    private final Plugin plugin;
    private final ProtectionController protection;
    private final IslandLevelScanService levelScanService;
    private final AtomicBoolean compatibilityWarningLogged = new AtomicBoolean();

    private LogicalEntityRemovalBridge(
        Plugin plugin,
        ProtectionController protection,
        IslandLevelScanService levelScanService
    ) {
        this.plugin = plugin;
        this.protection = protection;
        this.levelScanService = levelScanService;
    }

    public static LogicalEntityRemovalBridge register(
        Plugin plugin,
        ProtectionController protection,
        IslandLevelScanService levelScanService
    ) {
        LogicalEntityRemovalBridge bridge = new LogicalEntityRemovalBridge(plugin, protection, levelScanService);
        PaperEvents.register(plugin, bridge);
        bridge.registerRoseStacker();
        bridge.registerWildStacker();
        return bridge;
    }

    /** RoseStacker's synthetic per-entity death events may be asynchronous. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsynchronousEntityDeath(EntityDeathEvent event) {
        if (!event.isAsynchronous()) {
            return;
        }
        PaperSchedulers.run(plugin, () -> recordEntityRemoval(event.getEntity(), 1L));
    }

    private void registerRoseStacker() {
        Plugin vendor = enabledPlugin("RoseStacker");
        if (vendor == null) {
            return;
        }
        try {
            ClassLoader loader = vendor.getClass().getClassLoader();
            Class<? extends Event> multipleDeathClass = eventClass(loader, ROSE_MULTIPLE_DEATH);
            Method killCount = multipleDeathClass.getMethod("getEntityKillCount");
            Method mainEntity = multipleDeathClass.getMethod("getMainEntity");
            register(multipleDeathClass, false, event -> runOnMain(event, () -> {
                long supplemental = supplementalRemoval(number(killCount.invoke(event)));
                if (supplemental > 0L) {
                    recordEntityRemoval((Entity) mainEntity.invoke(event), supplemental);
                }
            }));

            Class<? extends Event> clearClass = eventClass(loader, ROSE_STACK_CLEAR);
            Method stacks = clearClass.getMethod("getStacks");
            register(clearClass, true, event -> runOnMain(event, () -> recordRoseClear(stacks.invoke(event))));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("Unable to install RoseStacker logical entity removal bridge", exception);
        }
    }

    private void registerWildStacker() {
        Plugin vendor = enabledPlugin("WildStacker");
        if (vendor == null) {
            return;
        }
        try {
            Class<? extends Event> eventClass = eventClass(vendor.getClass().getClassLoader(), WILD_ENTITY_UNSTACK);
            Method amount = eventClass.getMethod("getAmount");
            Method stackedEntity = eventClass.getMethod("getEntity");
            register(eventClass, true, event -> runOnMain(event, () -> {
                long supplemental = supplementalRemoval(number(amount.invoke(event)));
                if (supplemental <= 0L) {
                    return;
                }
                Object stack = stackedEntity.invoke(event);
                Entity entity = (Entity) stack.getClass().getMethod("getLivingEntity").invoke(stack);
                recordEntityRemoval(entity, supplemental);
            }));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("Unable to install WildStacker logical entity removal bridge", exception);
        }
    }

    private void recordRoseClear(Object value) throws ReflectiveOperationException {
        if (!(value instanceof Collection<?> stacks) || stacks.isEmpty()) {
            return;
        }
        Map<RemovalKey, Long> removals = new HashMap<>();
        for (Object stack : stacks) {
            if (stack == null) {
                continue;
            }
            Method entityGetter = stack.getClass().getMethod("getEntity");
            Method sizeGetter = stack.getClass().getMethod("getStackSize");
            Object entity = entityGetter.invoke(stack);
            if (!(entity instanceof Entity bukkitEntity)) {
                continue;
            }
            long amount = numberUnchecked(sizeGetter, stack);
            if (amount > 0L) {
                protection.islandAt(bukkitEntity.getLocation().getBlock()).ifPresent(islandId ->
                    removals.merge(new RemovalKey(islandId, bukkitEntity.getType()), amount, Long::sum));
            }
        }
        removals.forEach((key, amount) -> recordRemoval(key.islandId(), key.entityType(), amount));
    }

    private void recordEntityRemoval(Entity entity, long amount) {
        if (entity == null || amount <= 0L) {
            return;
        }
        protection.islandAt(entity.getLocation().getBlock()).ifPresent(islandId ->
            recordRemoval(islandId, entity.getType(), amount));
    }

    private void recordRemoval(UUID islandId, EntityType entityType, long amount) {
        if (islandId == null || entityType == null || amount <= 0L) {
            return;
        }
        long delta = -Math.max(1L, amount);
        levelScanService.recordBlockDelta(islandId, IslandEntityLimitKeys.COUNT_KEY, delta);
        levelScanService.recordBlockDelta(islandId, "entity:" + entityType.getKey(), delta);
    }

    private void runOnMain(Event event, CheckedRunnable task) {
        if (event.isAsynchronous()) {
            PaperSchedulers.run(plugin, () -> invoke(task));
        } else {
            invoke(task);
        }
    }

    private void invoke(CheckedRunnable task) {
        try {
            task.run();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("A logical entity removal needs reconciliation", exception);
        }
    }

    private void register(Class<? extends Event> eventClass, boolean ignoreCancelled, EventConsumer consumer) {
        plugin.getServer().getPluginManager().registerEvent(
            eventClass,
            this,
            EventPriority.MONITOR,
            (ignored, event) -> consumer.accept(event),
            plugin,
            ignoreCancelled
        );
    }

    private Plugin enabledPlugin(String name) {
        Plugin vendor = plugin.getServer().getPluginManager().getPlugin(name);
        return vendor != null && vendor.isEnabled() ? vendor : null;
    }

    private void warn(String message, Throwable exception) {
        if (compatibilityWarningLogged.compareAndSet(false, true)) {
            plugin.getLogger().warning(message + ": " + exception.getClass().getSimpleName());
        }
    }

    static long supplementalRemoval(long totalRemoval) {
        return Math.max(0L, totalRemoval - 1L);
    }

    private static long number(Object value) {
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    private static long numberUnchecked(Method method, Object target) {
        try {
            return number(method.invoke(target));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Event> eventClass(ClassLoader loader, String name) throws ClassNotFoundException {
        Class<?> type = loader.loadClass(name);
        if (!Event.class.isAssignableFrom(type)) {
            throw new ClassNotFoundException(name + " is not a Bukkit event");
        }
        return (Class<? extends Event>) type;
    }

    private record RemovalKey(UUID islandId, EntityType entityType) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws ReflectiveOperationException;
    }

    @FunctionalInterface
    private interface EventConsumer {
        void accept(Event event);
    }
}
