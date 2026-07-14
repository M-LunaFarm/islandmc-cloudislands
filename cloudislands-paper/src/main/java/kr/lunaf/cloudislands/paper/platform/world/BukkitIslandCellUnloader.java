package kr.lunaf.cloudislands.paper.platform.world;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.paper.activation.IslandCellUnloader;
import kr.lunaf.cloudislands.paper.activation.IslandCellUnloadException;
import kr.lunaf.cloudislands.paper.activation.IslandCellRange;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Evicts every live chunk in a cell before its Anvil files are replaced offline. */
public final class BukkitIslandCellUnloader implements IslandCellUnloader {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final PlatformScheduler scheduler;
    private final Duration timeout;
    private final AtomicLong operationSequence = new AtomicLong();
    private final ConcurrentMap<Long, PendingUnload> pendingUnloads = new ConcurrentHashMap<>();

    public BukkitIslandCellUnloader(Plugin plugin) {
        this(new BukkitPlatformScheduler(plugin), DEFAULT_TIMEOUT);
    }

    BukkitIslandCellUnloader(PlatformScheduler scheduler, Duration timeout) {
        this.scheduler = scheduler;
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public void unload(IslandCellRange range) throws IOException {
        if (range == null || range.worldName() == null || range.worldName().isBlank()) {
            throw new IslandCellUnloadException("cannot unload a cell without a target world");
        }
        if (Bukkit.isPrimaryThread()) {
            unloadOnServerThread(range);
            return;
        }
        PendingUnload pending = new PendingUnload(operationSequence.incrementAndGet(), range, new CompletableFuture<>());
        pendingUnloads.put(pending.id(), pending);
        try {
            scheduler.runGlobal(() -> completeUnload(pending));
        } catch (RuntimeException error) {
            pendingUnloads.remove(pending.id(), pending);
            throw new IslandCellUnloadException("failed to schedule island cell unload " + range.worldName(), error);
        }
        await(range, pending.completion());
    }

    @Override
    public void prepareShutdown() throws IOException {
        if (!Bukkit.isPrimaryThread()) {
            throw new IOException("island cell shutdown drain must run on the Paper global thread");
        }
        IOException failure = null;
        for (PendingUnload pending : List.copyOf(pendingUnloads.values())) {
            completeUnload(pending);
            try {
                await(pending.range(), pending.completion());
            } catch (IOException error) {
                if (failure == null) {
                    failure = new IOException("failed to drain pending island cell unloads");
                }
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void completeUnload(PendingUnload pending) {
        if (!pendingUnloads.remove(pending.id(), pending)) {
            return;
        }
        try {
            unloadOnServerThread(pending.range());
            pending.completion().complete(null);
        } catch (Throwable error) {
            pending.completion().completeExceptionally(error);
        }
    }

    private void unloadOnServerThread(IslandCellRange range) throws IOException {
        World world = Bukkit.getWorld(range.worldName());
        if (world == null) {
            throw new IslandCellUnloadException("target island world is not loaded: " + range.worldName());
        }
        for (Player player : world.getPlayers()) {
            int chunkX = player.getLocation().getBlockX() >> 4;
            int chunkZ = player.getLocation().getBlockZ() >> 4;
            if (inside(range, chunkX, chunkZ)) {
                throw new IslandCellUnloadException("island cell still contains player " + player.getUniqueId());
            }
        }
        for (int chunkX = range.minChunkX(); chunkX <= range.maxChunkX(); chunkX++) {
            for (int chunkZ = range.minChunkZ(); chunkZ <= range.maxChunkZ(); chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ) && !world.unloadChunk(chunkX, chunkZ, false)) {
                    throw new IslandCellUnloadException("Paper refused to unload island chunk " + chunkX + "," + chunkZ);
                }
            }
        }
        for (int chunkX = range.minChunkX(); chunkX <= range.maxChunkX(); chunkX++) {
            for (int chunkZ = range.minChunkZ(); chunkZ <= range.maxChunkZ(); chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    throw new IslandCellUnloadException("island chunk remained loaded after unload " + chunkX + "," + chunkZ);
                }
            }
        }
    }

    private boolean inside(IslandCellRange range, int chunkX, int chunkZ) {
        return chunkX >= range.minChunkX() && chunkX <= range.maxChunkX()
            && chunkZ >= range.minChunkZ() && chunkZ <= range.maxChunkZ();
    }

    private void await(IslandCellRange range, CompletableFuture<Void> completion) throws IOException {
        try {
            completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IslandCellUnloadException("interrupted while unloading island cell " + range.islandId(), exception);
        } catch (TimeoutException exception) {
            throw new IslandCellUnloadException("timed out unloading island cell " + range.islandId(), exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IslandCellUnloadException unloadException) {
                throw unloadException;
            }
            throw new IslandCellUnloadException("failed to unload island cell " + range.islandId(), cause);
        }
    }

    private record PendingUnload(long id, IslandCellRange range, CompletableFuture<Void> completion) {
    }
}
