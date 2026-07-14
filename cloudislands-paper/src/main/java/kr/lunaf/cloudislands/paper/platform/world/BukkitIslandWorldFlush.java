package kr.lunaf.cloudislands.paper.platform.world;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private final PlatformScheduler scheduler;
    private final Duration timeout;

    public BukkitIslandWorldFlush(Plugin plugin) {
        this(new BukkitPlatformScheduler(plugin), DEFAULT_TIMEOUT);
    }

    BukkitIslandWorldFlush(PlatformScheduler scheduler, Duration timeout) {
        this.scheduler = scheduler;
        this.timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public void flush(ActiveIslandRegistry.ActiveIsland activeIsland) throws IOException {
        if (activeIsland == null || activeIsland.worldName() == null || activeIsland.worldName().isBlank()) {
            throw new IOException("cannot flush island with no active world");
        }
        if (Bukkit.isPrimaryThread()) {
            flushWorld(activeIsland.worldName());
            return;
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            scheduler.runGlobal(() -> {
                try {
                    flushWorld(activeIsland.worldName());
                    completion.complete(null);
                } catch (Throwable error) {
                    completion.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            throw new IOException("failed to schedule island world flush " + activeIsland.worldName(), error);
        }
        try {
            completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while flushing island world " + activeIsland.worldName(), exception);
        } catch (TimeoutException exception) {
            throw new IOException("timed out flushing island world " + activeIsland.worldName(), exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("failed to flush island world " + activeIsland.worldName(), cause);
        }
    }

    private void flushWorld(String worldName) throws IOException {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IOException("active island world is not loaded: " + worldName);
        }
        try {
            world.save();
        } catch (RuntimeException error) {
            throw new IOException("Paper world save failed: " + worldName, error);
        }
    }
}
