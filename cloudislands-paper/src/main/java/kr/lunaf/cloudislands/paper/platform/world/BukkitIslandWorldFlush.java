package kr.lunaf.cloudislands.paper.platform.world;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.IslandWorldFlush;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Flushes live chunk, entity, and POI state before offline region-file export. */
public final class BukkitIslandWorldFlush implements IslandWorldFlush {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration AUTO_FLUSH_REUSE = Duration.ofSeconds(30);

    private final PlatformScheduler scheduler;
    private final Duration timeout;
    private final BooleanSupplier primaryThread;
    private final WorldSaver worldSaver;
    private final WorldFlushReusePolicy reusePolicy = new WorldFlushReusePolicy(AUTO_FLUSH_REUSE);
    private final ConcurrentMap<String, CompletableFuture<Void>> pendingFlushes = new ConcurrentHashMap<>();

    public BukkitIslandWorldFlush(Plugin plugin) {
        this(new BukkitPlatformScheduler(plugin), DEFAULT_TIMEOUT, Bukkit::isPrimaryThread, BukkitIslandWorldFlush::flushBukkitWorld);
    }

    BukkitIslandWorldFlush(PlatformScheduler scheduler, Duration timeout) {
        this(scheduler, timeout, Bukkit::isPrimaryThread, BukkitIslandWorldFlush::flushBukkitWorld);
    }

    BukkitIslandWorldFlush(PlatformScheduler scheduler, Duration timeout, BooleanSupplier primaryThread, WorldSaver worldSaver) {
        this.scheduler = scheduler;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
        this.primaryThread = primaryThread;
        this.worldSaver = worldSaver;
    }

    @Override
    public void flush(ActiveIslandRegistry.ActiveIsland activeIsland, String reason) throws IOException {
        if (activeIsland == null || activeIsland.worldName() == null || activeIsland.worldName().isBlank()) {
            throw new IOException("cannot flush island with no active world");
        }
        String worldName = activeIsland.worldName();
        if (primaryThread.getAsBoolean()) {
            completePending(worldName);
            if (!reusePolicy.requiresFlush(worldName, reason, System.currentTimeMillis())) {
                return;
            }
            worldSaver.flush(worldName);
            recordFlush(worldName);
            return;
        }
        long now = System.currentTimeMillis();
        if (!reusePolicy.requiresFlush(worldName, reason, now)) {
            return;
        }
        CompletableFuture<Void> created = new CompletableFuture<>();
        CompletableFuture<Void> completion = pendingFlushes.putIfAbsent(worldName, created);
        if (completion == null) {
            completion = created;
            CompletableFuture<Void> scheduled = completion;
            try {
                scheduler.runGlobal(() -> completeFlush(worldName, scheduled));
            } catch (RuntimeException error) {
                completion.completeExceptionally(error);
            }
        }
        try {
            completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while flushing island world " + worldName, exception);
        } catch (TimeoutException exception) {
            throw new IOException("timed out flushing island world " + worldName, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("failed to flush island world " + worldName, cause);
        } finally {
            pendingFlushes.remove(worldName, completion);
        }
    }

    @Override
    public void prepareShutdown(Iterable<ActiveIslandRegistry.ActiveIsland> activeIslands) throws IOException {
        if (!primaryThread.getAsBoolean()) {
            throw new IOException("shutdown world flush preparation must run on the Paper global thread");
        }
        IOException failure = null;
        for (String worldName : java.util.List.copyOf(pendingFlushes.keySet())) {
            try {
                completePending(worldName);
            } catch (IOException error) {
                failure = accumulate(failure, error);
            }
        }
        if (activeIslands != null) {
            for (ActiveIslandRegistry.ActiveIsland activeIsland : activeIslands) {
                try {
                    flush(activeIsland, "AUTO");
                } catch (IOException error) {
                    failure = accumulate(failure, error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private IOException accumulate(IOException current, IOException next) {
        if (current == null) {
            return next;
        }
        current.addSuppressed(next);
        return current;
    }

    private void recordFlush(String worldName) {
        reusePolicy.record(worldName, System.currentTimeMillis());
    }

    private void completePending(String worldName) throws IOException {
        CompletableFuture<Void> pending = pendingFlushes.get(worldName);
        if (pending == null) {
            return;
        }
        completeFlush(worldName, pending);
        if (pending.isCompletedExceptionally()) {
            try {
                pending.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while draining island world flush " + worldName, exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException("failed to drain island world flush " + worldName, cause);
            }
        }
    }

    private void completeFlush(String worldName, CompletableFuture<Void> completion) {
        if (completion.isDone()) {
            return;
        }
        try {
            worldSaver.flush(worldName);
            recordFlush(worldName);
            completion.complete(null);
        } catch (Throwable error) {
            completion.completeExceptionally(error);
        }
    }

    private static void flushBukkitWorld(String worldName) throws IOException {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IOException("active island world is not loaded: " + worldName);
        }
        try {
            world.save(true);
        } catch (RuntimeException error) {
            throw new IOException("Paper world save failed: " + worldName, error);
        }
    }

    @FunctionalInterface
    interface WorldSaver {
        void flush(String worldName) throws IOException;
    }
}
