package kr.lunaf.cloudislands.paper.level;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RuntimeCommandClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.bootstrap.RuntimeComponent;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomBlockKeyService;
import kr.lunaf.cloudislands.paper.integration.stacker.StackAmountService;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import kr.lunaf.cloudislands.paper.limit.IslandBlockLimitKeys;
import kr.lunaf.cloudislands.paper.limit.IslandEntityLimitKeys;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class IslandLevelScanService implements RuntimeComponent {
    static final int MAX_BLOCKS_PER_TICK = 8_192;
    static final int MAX_ENTITIES_PER_TICK = 512;
    static final long MAX_TICK_NANOS = 2_000_000L;

    private final Supplier<ActiveIslandRegistry> activeIslands;
    private final RuntimeCommandClient runtimeCommands;
    private final PaperWorldGateway worlds;
    private final CustomBlockKeyService customBlockKeys;
    private final StackAmountService stackAmounts;
    private final TickScheduler scheduler;
    private IslandLimitCache limitCounts;
    private final Map<UUID, ScanJob> inFlight = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private final Map<UUID, Long> mutationVersions = new HashMap<>();
    private final Map<UUID, CompletableFuture<Void>> writeTails = new HashMap<>();
    private volatile boolean stopped;

    public IslandLevelScanService(Plugin plugin, Supplier<ActiveIslandRegistry> activeIslands, CoreApiClient client) {
        this(
            plugin,
            activeIslands,
            client,
            new BukkitWorldGateway(plugin),
            plugin instanceof kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin cloudIslands
                ? cloudIslands.customBlockKeys()
                : CustomBlockKeyService.discover(plugin.getServer()),
            plugin instanceof kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin cloudIslands
                ? cloudIslands.stackAmounts()
                : StackAmountService.discover(plugin.getServer())
        );
    }

    public IslandLevelScanService(
        Plugin plugin,
        Supplier<ActiveIslandRegistry> activeIslands,
        CoreApiClient client,
        IslandLimitCache limitCounts
    ) {
        this(plugin, activeIslands, client);
        this.limitCounts = limitCounts;
    }

    IslandLevelScanService(Plugin plugin, Supplier<ActiveIslandRegistry> activeIslands, CoreApiClient client, PaperWorldGateway worlds) {
        this(plugin, activeIslands, client, worlds, CustomBlockKeyService.vanillaOnly(), StackAmountService.physicalOnly());
    }

    IslandLevelScanService(Plugin plugin, Supplier<ActiveIslandRegistry> activeIslands, CoreApiClient client, PaperWorldGateway worlds, CustomBlockKeyService customBlockKeys) {
        this(plugin, activeIslands, client, worlds, customBlockKeys, StackAmountService.physicalOnly());
    }

    IslandLevelScanService(
        Plugin plugin,
        Supplier<ActiveIslandRegistry> activeIslands,
        CoreApiClient client,
        PaperWorldGateway worlds,
        CustomBlockKeyService customBlockKeys,
        StackAmountService stackAmounts
    ) {
        this(plugin, activeIslands, client, worlds, customBlockKeys, stackAmounts, task -> {
            org.bukkit.scheduler.BukkitTask scheduled = PaperSchedulers.runTimer(plugin, task, 0L, 1L);
            return scheduled == null ? TaskHandle.noop() : scheduled::cancel;
        });
    }

    IslandLevelScanService(
        Plugin plugin,
        Supplier<ActiveIslandRegistry> activeIslands,
        CoreApiClient client,
        PaperWorldGateway worlds,
        CustomBlockKeyService customBlockKeys,
        TickScheduler scheduler
    ) {
        this(plugin, activeIslands, client, worlds, customBlockKeys, StackAmountService.physicalOnly(), scheduler);
    }

    IslandLevelScanService(
        Plugin plugin,
        Supplier<ActiveIslandRegistry> activeIslands,
        CoreApiClient client,
        PaperWorldGateway worlds,
        CustomBlockKeyService customBlockKeys,
        StackAmountService stackAmounts,
        TickScheduler scheduler
    ) {
        this.activeIslands = activeIslands;
        this.runtimeCommands = client.runtimeCommands();
        this.worlds = worlds;
        this.customBlockKeys = customBlockKeys == null ? CustomBlockKeyService.vanillaOnly() : customBlockKeys;
        this.stackAmounts = stackAmounts == null ? StackAmountService.physicalOnly() : stackAmounts;
        this.scheduler = scheduler;
    }

    public CompletableFuture<Void> rescanIsland(UUID islandId) {
        if (stopped) {
            return CompletableFuture.failedFuture(new CancellationException("Island level scan service is stopped"));
        }
        ActiveIslandRegistry registry = activeIslands.get();
        ActiveIslandRegistry.ActiveIsland active = registry == null ? null : registry.find(islandId).orElse(null);
        if (active == null) {
            return CompletableFuture.completedFuture(null);
        }
        ScanJob existing = inFlight.get(islandId);
        if (existing != null) {
            return existing.future;
        }
        ScanJob created = new ScanJob(islandId, active);
        ScanJob winner = inFlight.putIfAbsent(islandId, created);
        if (winner != null) {
            return winner.future;
        }
        try {
            created.start();
        } catch (RuntimeException | LinkageError error) {
            created.fail(error);
        }
        return created.future;
    }

    public void recordBlockDelta(UUID islandId, String materialKey, long delta) {
        if (islandId == null || materialKey == null || materialKey.isBlank() || delta == 0L || stopped) {
            return;
        }
        if (limitCounts != null) {
            limitCounts.recordBlockDelta(islandId, materialKey, delta);
        }
        synchronized (writeLock) {
            mutationVersions.merge(islandId, 1L, Long::sum);
            enqueueWriteLocked(islandId, () -> runtimeCommands.recordBlockDelta(islandId, materialKey, delta));
        }
    }

    @Override
    public void stop() {
        stopped = true;
        CancellationException cancellation = new CancellationException("CloudIslands is stopping");
        List.copyOf(inFlight.values()).forEach(job -> job.cancel(cancellation));
        inFlight.clear();
    }

    int inFlightCount() {
        return inFlight.size();
    }

    int mutationStateCount() {
        synchronized (writeLock) {
            return mutationVersions.size();
        }
    }

    private final class ScanJob implements Runnable {
        private final UUID islandId;
        private final ActiveIslandRegistry.ActiveIsland active;
        private final CompletableFuture<Void> future = new CompletableFuture<>();
        private final Map<String, Long> counts = new HashMap<>();
        private long startingMutationVersion;
        private TaskHandle task = TaskHandle.noop();
        private World world;
        private IslandScanCursor cursor;
        private StackAmountService.StackSnapshot stackSnapshot;
        private final List<Entity> entities = new ArrayList<>();
        private final Set<Long> capturedChunks = new HashSet<>();
        private final Set<UUID> capturedEntityIds = new HashSet<>();
        private int entityIndex;

        private ScanJob(UUID islandId, ActiveIslandRegistry.ActiveIsland active) {
            this.islandId = islandId;
            this.active = active;
        }

        private void start() {
            synchronized (writeLock) {
                startingMutationVersion = mutationVersions.getOrDefault(islandId, 0L);
            }
            TaskHandle scheduled = scheduler.repeatEveryTick(this);
            this.task = scheduled == null ? TaskHandle.noop() : scheduled;
            if (future.isDone()) {
                this.task.cancel();
            }
        }

        @Override
        public void run() {
            if (future.isDone()) {
                task.cancel();
                return;
            }
            try {
                if (stopped) {
                    cancel(new CancellationException("Island level scan service is stopped"));
                    return;
                }
                if (!sameActivationStillActive()) {
                    cancel(new CancellationException("Island activation changed during level scan: " + islandId));
                    return;
                }
                if (world == null && !initializeWorld()) {
                    completeWithoutReplacement();
                    return;
                }
                long deadline = System.nanoTime() + MAX_TICK_NANOS;
                int blocks = 0;
                while (cursor.hasNext() && blocks < MAX_BLOCKS_PER_TICK && System.nanoTime() < deadline) {
                    org.bukkit.block.Block block = world.getBlockAt(cursor.x(), cursor.y(), cursor.z());
                    captureChunkEntities(cursor.x(), cursor.z());
                    Material type = block.getType();
                    if (!isAir(type)) {
                        String stackKey = stackSnapshot.blockKeyOverride(block);
                        String blockKey = stackKey.isBlank() ? customBlockKeys.blockKey(block) : stackKey;
                        long amount = stackSnapshot.blockAmount(block);
                        counts.merge(blockKey, amount, Long::sum);
                        String limitCountKey = IslandBlockLimitKeys.countKey(type);
                        if (limitCountKey != null) {
                            counts.merge(limitCountKey, amount, Long::sum);
                        }
                    }
                    cursor.advance();
                    blocks++;
                }
                if (cursor.hasNext()) {
                    return;
                }
                int checkedEntities = 0;
                while (entityIndex < entities.size() && checkedEntities < MAX_ENTITIES_PER_TICK && System.nanoTime() < deadline) {
                    Entity entity = entities.get(entityIndex++);
                    long amount = stackSnapshot.entityAmount(entity);
                    counts.merge(customBlockKeys.entityKey(entity), amount, Long::sum);
                    if (IslandEntityLimitKeys.counts(entity)) {
                        counts.merge(IslandEntityLimitKeys.COUNT_KEY, amount, Long::sum);
                    }
                    checkedEntities++;
                }
                if (entityIndex >= entities.size()) {
                    submitReplacement();
                }
            } catch (RuntimeException | LinkageError error) {
                fail(error);
            }
        }

        private boolean initializeWorld() {
            world = worlds.world(active.worldName());
            if (world == null) {
                return false;
            }
            int half = Math.max(1, active.islandSize() / 2);
            cursor = new IslandScanCursor(
                active.originX() - half,
                active.originX() + half,
                world.getMinHeight(),
                world.getMaxHeight() - 1,
                active.originZ() - half,
                active.originZ() + half
            );
            stackSnapshot = stackAmounts.snapshot(
                world,
                active.originX() - half,
                active.originX() + half,
                active.originZ() - half,
                active.originZ() + half
            );
            return true;
        }

        private void captureChunkEntities(int blockX, int blockZ) {
            int chunkX = Math.floorDiv(blockX, 16);
            int chunkZ = Math.floorDiv(blockZ, 16);
            long chunkKey = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
            if (!capturedChunks.add(chunkKey)) {
                return;
            }
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            for (Entity entity : chunk.getEntities()) {
                if (entity == null) {
                    continue;
                }
                UUID entityId = entity.getUniqueId();
                if (entityId == null || capturedEntityIds.contains(entityId)) {
                    continue;
                }
                Location location = entity.getLocation();
                if (location != null
                    && cursor.contains(location.getBlockX(), location.getBlockZ())
                    && capturedEntityIds.add(entityId)) {
                    entities.add(entity);
                }
            }
        }

        private boolean sameActivationStillActive() {
            ActiveIslandRegistry registry = activeIslands.get();
            ActiveIslandRegistry.ActiveIsland current = registry == null ? null : registry.find(islandId).orElse(null);
            return current != null
                && current.fencingToken() == active.fencingToken()
                && Objects.equals(current.worldName(), active.worldName());
        }

        private void submitReplacement() {
            task.cancel();
            CompletableFuture<Void> replacement;
            Map<String, Long> replacementCounts = Map.copyOf(counts);
            synchronized (writeLock) {
                long currentMutationVersion = mutationVersions.getOrDefault(islandId, 0L);
                if (currentMutationVersion != startingMutationVersion) {
                    fail(new ConcurrentModificationException(
                        "Island blocks changed during the tick-batched level scan; a later scan will reconcile " + islandId
                    ));
                    return;
                }
                replacement = enqueueWriteLocked(
                    islandId,
                    () -> runtimeCommands.replaceBlockCounts(islandId, replacementCounts)
                );
            }
            replacement
                .whenComplete((_result, error) -> {
                    if (error == null) {
                        if (limitCounts != null) {
                            limitCounts.replaceBlockCounts(islandId, replacementCounts);
                        }
                        completeSuccessfully();
                    } else {
                        fail(error);
                    }
                });
        }

        private void completeWithoutReplacement() {
            task.cancel();
            completeSuccessfully();
        }

        private void completeSuccessfully() {
            cleanupBeforeCompletion();
            future.complete(null);
        }

        private void fail(Throwable error) {
            task.cancel();
            cleanupBeforeCompletion();
            future.completeExceptionally(error);
        }

        private void cancel(CancellationException cancellation) {
            task.cancel();
            cleanupBeforeCompletion();
            future.completeExceptionally(cancellation);
        }

        private void cleanupBeforeCompletion() {
            inFlight.remove(islandId, this);
            synchronized (writeLock) {
                cleanupMutationVersionLocked(islandId);
            }
        }
    }

    @FunctionalInterface
    interface TickScheduler {
        TaskHandle repeatEveryTick(Runnable task);
    }

    private static boolean isAir(Material material) {
        return material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private CompletableFuture<Void> enqueueWriteLocked(UUID islandId, Supplier<? extends CompletableFuture<?>> operation) {
        CompletableFuture<Void> previous = writeTails.getOrDefault(islandId, CompletableFuture.completedFuture(null));
        CompletableFuture<Void> operationFuture = previous.handle((_ignored, _error) -> null)
            .thenComposeAsync(_ignored -> {
                try {
                    CompletableFuture<?> result = operation.get();
                    return result == null
                        ? CompletableFuture.completedFuture(null)
                        : result.thenApply(_value -> (Void) null);
                } catch (RuntimeException | LinkageError error) {
                    return CompletableFuture.failedFuture(error);
                }
            });
        CompletableFuture<Void> tracked = new CompletableFuture<>();
        writeTails.put(islandId, tracked);
        operationFuture.whenComplete((_ignored, error) -> {
            synchronized (writeLock) {
                writeTails.remove(islandId, tracked);
                cleanupMutationVersionLocked(islandId);
            }
            if (error == null) {
                tracked.complete(null);
            } else {
                tracked.completeExceptionally(error);
            }
        });
        return tracked;
    }

    private void cleanupMutationVersionLocked(UUID islandId) {
        if (!inFlight.containsKey(islandId) && !writeTails.containsKey(islandId)) {
            mutationVersions.remove(islandId);
        }
    }
}
