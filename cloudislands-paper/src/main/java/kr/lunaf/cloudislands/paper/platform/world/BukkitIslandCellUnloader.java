package kr.lunaf.cloudislands.paper.platform.world;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.lunaf.cloudislands.paper.activation.IslandCellUnloader;
import kr.lunaf.cloudislands.paper.activation.IslandCellUnloadException;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import kr.lunaf.cloudislands.paper.world.cell.CellPlacementPlan;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Evicts every live chunk in a cell before its Anvil files are replaced offline. */
public final class BukkitIslandCellUnloader implements IslandCellUnloader {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final PlatformScheduler scheduler;
    private final Duration timeout;

    public BukkitIslandCellUnloader(Plugin plugin) {
        this(new BukkitPlatformScheduler(plugin), DEFAULT_TIMEOUT);
    }

    BukkitIslandCellUnloader(PlatformScheduler scheduler, Duration timeout) {
        this.scheduler = scheduler;
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public void unload(CellPlacementPlan plan) throws IOException {
        if (plan == null || plan.worldName() == null || plan.worldName().isBlank()) {
            throw new IslandCellUnloadException("cannot unload a cell without a target world");
        }
        if (Bukkit.isPrimaryThread()) {
            unloadOnServerThread(plan);
            return;
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            scheduler.runGlobal(() -> {
                try {
                    unloadOnServerThread(plan);
                    completion.complete(null);
                } catch (Throwable error) {
                    completion.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            throw new IslandCellUnloadException("failed to schedule island cell unload " + plan.worldName(), error);
        }
        await(plan, completion);
    }

    private void unloadOnServerThread(CellPlacementPlan plan) throws IOException {
        World world = Bukkit.getWorld(plan.worldName());
        if (world == null) {
            throw new IslandCellUnloadException("target island world is not loaded: " + plan.worldName());
        }
        for (Player player : world.getPlayers()) {
            int chunkX = player.getLocation().getBlockX() >> 4;
            int chunkZ = player.getLocation().getBlockZ() >> 4;
            if (inside(plan, chunkX, chunkZ)) {
                throw new IslandCellUnloadException("island cell still contains player " + player.getUniqueId());
            }
        }
        for (int chunkX = plan.minChunkX(); chunkX <= plan.maxChunkX(); chunkX++) {
            for (int chunkZ = plan.minChunkZ(); chunkZ <= plan.maxChunkZ(); chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ) && !world.unloadChunk(chunkX, chunkZ, false)) {
                    throw new IslandCellUnloadException("Paper refused to unload island chunk " + chunkX + "," + chunkZ);
                }
            }
        }
        for (int chunkX = plan.minChunkX(); chunkX <= plan.maxChunkX(); chunkX++) {
            for (int chunkZ = plan.minChunkZ(); chunkZ <= plan.maxChunkZ(); chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    throw new IslandCellUnloadException("island chunk remained loaded after unload " + chunkX + "," + chunkZ);
                }
            }
        }
    }

    private boolean inside(CellPlacementPlan plan, int chunkX, int chunkZ) {
        return chunkX >= plan.minChunkX() && chunkX <= plan.maxChunkX()
            && chunkZ >= plan.minChunkZ() && chunkZ <= plan.maxChunkZ();
    }

    private void await(CellPlacementPlan plan, CompletableFuture<Void> completion) throws IOException {
        try {
            completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IslandCellUnloadException("interrupted while unloading island cell " + plan.islandId(), exception);
        } catch (TimeoutException exception) {
            throw new IslandCellUnloadException("timed out unloading island cell " + plan.islandId(), exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IslandCellUnloadException unloadException) {
                throw unloadException;
            }
            throw new IslandCellUnloadException("failed to unload island cell " + plan.islandId(), cause);
        }
    }
}
