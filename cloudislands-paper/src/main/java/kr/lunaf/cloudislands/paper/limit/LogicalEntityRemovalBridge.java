package kr.lunaf.cloudislands.paper.limit;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Reconciles logical entity removals that one physical Bukkit death cannot represent. */
public final class LogicalEntityRemovalBridge implements Listener {
    private static final String ROSE_MULTIPLE_DEATH = "dev.rosewood.rosestacker.event.EntityStackMultipleDeathEvent";
    private static final String ROSE_STACK_CLEAR = "dev.rosewood.rosestacker.event.EntityStackClearEvent";
    private static final String WILD_ENTITY_UNSTACK = "com.bgsoftware.wildstacker.api.events.EntityUnstackEvent";
    private static final long DEATH_CONTEXT_TTL_NANOS = java.time.Duration.ofSeconds(10).toNanos();

    private final Plugin plugin;
    private final ProtectionController protection;
    private final IslandLevelScanService levelScanService;
    private final IslandLimitCache limits;
    private final Map<Entity, DeathContext> deathContexts = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Random random = new Random();
    private final AtomicBoolean compatibilityWarningLogged = new AtomicBoolean();
    private volatile boolean roseMultipleDeathAvailable;

    private LogicalEntityRemovalBridge(
        Plugin plugin,
        ProtectionController protection,
        IslandLevelScanService levelScanService,
        IslandLimitCache limits
    ) {
        this.plugin = plugin;
        this.protection = protection;
        this.levelScanService = levelScanService;
        this.limits = limits;
    }

    public static LogicalEntityRemovalBridge register(
        Plugin plugin,
        ProtectionController protection,
        IslandLevelScanService levelScanService,
        IslandLimitCache limits
    ) {
        LogicalEntityRemovalBridge bridge = new LogicalEntityRemovalBridge(plugin, protection, levelScanService, limits);
        PaperEvents.register(plugin, bridge);
        bridge.roseMultipleDeathAvailable = bridge.registerRoseStacker();
        bridge.registerWildStacker();
        if (bridge.roseMultipleDeathAvailable) {
            PaperSchedulers.runTimer(plugin, bridge::expireDeathContexts, 20L, 20L);
        }
        return bridge;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void captureSynchronousDeathContext(EntityDeathEvent event) {
        if (event.isAsynchronous() || limits == null || !roseMultipleDeathAvailable
            || !IslandEntityLimitKeys.counts(event.getEntity())) {
            return;
        }
        protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> {
            long percent = MobDropRateScaler.normalizePercent(limits.limit(islandId, "RATE:MOB_DROPS", 100L));
            if (percent != 100L) {
                deathContexts.put(event.getEntity(), new DeathContext(percent, System.nanoTime() + DEATH_CONTEXT_TTL_NANOS));
            }
        });
    }

    /** RoseStacker's synthetic per-entity death events may be asynchronous. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsynchronousEntityDeath(EntityDeathEvent event) {
        if (!event.isAsynchronous()) {
            return;
        }
        PaperSchedulers.run(plugin, () -> recordEntityRemoval(event.getEntity(), 1L));
    }

    private boolean registerRoseStacker() {
        Plugin vendor = enabledPlugin("RoseStacker");
        if (vendor == null) {
            return false;
        }
        try {
            ClassLoader loader = vendor.getClass().getClassLoader();
            Class<? extends Event> multipleDeathClass = eventClass(loader, ROSE_MULTIPLE_DEATH);
            Method killCount = multipleDeathClass.getMethod("getEntityKillCount");
            Method mainEntity = multipleDeathClass.getMethod("getMainEntity");
            Method entityDrops = multipleDeathClass.getMethod("getEntityDrops");
            register(multipleDeathClass, false, event -> {
                applyRoseMultipleDeathDropRate(event, mainEntity, entityDrops);
                runOnMain(event, () -> {
                long supplemental = supplementalRemoval(number(killCount.invoke(event)));
                if (supplemental > 0L) {
                    recordEntityRemoval((Entity) mainEntity.invoke(event), supplemental);
                }
                });
            });

            Class<? extends Event> clearClass = eventClass(loader, ROSE_STACK_CLEAR);
            Method stacks = clearClass.getMethod("getStacks");
            register(clearClass, true, event -> runOnMain(event, () -> recordRoseClear(stacks.invoke(event))));
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("Unable to install RoseStacker logical entity removal bridge", exception);
            return false;
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

    private void applyRoseMultipleDeathDropRate(Event event, Method mainEntityGetter, Method entityDropsGetter) {
        try {
            Object mainEntity = mainEntityGetter.invoke(event);
            if (!(mainEntity instanceof Entity bukkitMainEntity)) {
                return;
            }
            DeathContext context = deathContexts.remove(bukkitMainEntity);
            if (context == null) {
                return;
            }
            Object multimap = entityDropsGetter.invoke(event);
            Method entriesGetter = multimap.getClass().getMethod("entries");
            Object entriesValue = entriesGetter.invoke(multimap);
            if (!(entriesValue instanceof Collection<?> entries)) {
                return;
            }
            for (Object entryValue : entries) {
                if (!(entryValue instanceof Map.Entry<?, ?> entry) || entry.getKey() == mainEntity || entry.getValue() == null) {
                    continue;
                }
                Method dropsGetter = entry.getValue().getClass().getMethod("getDrops");
                Object dropsValue = dropsGetter.invoke(entry.getValue());
                if (dropsValue instanceof java.util.List<?> rawDrops) {
                    @SuppressWarnings("unchecked")
                    java.util.List<ItemStack> drops = (java.util.List<ItemStack>) rawDrops;
                    MobDropRateScaler.scale(drops, context.percent(), random);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            warn("Unable to apply the island mob-drop rate to RoseStacker multiple-death drops", exception);
        }
    }

    private void expireDeathContexts() {
        long now = System.nanoTime();
        synchronized (deathContexts) {
            deathContexts.values().removeIf(context -> context.expiresAtNanos() <= now);
        }
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

    private record DeathContext(long percent, long expiresAtNanos) {
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
