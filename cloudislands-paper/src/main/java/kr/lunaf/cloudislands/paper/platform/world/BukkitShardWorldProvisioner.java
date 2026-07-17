package kr.lunaf.cloudislands.paper.platform.world;

import java.io.IOException;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.lunaf.cloudislands.paper.activation.ShardWorldProvisioner;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

/** Loads shard worlds as empty shared canvases before cell files are mutated. */
public final class BukkitShardWorldProvisioner implements ShardWorldProvisioner {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final ChunkGenerator VOID_GENERATOR = new ChunkGenerator() {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        }
    };

    private final PlatformScheduler scheduler;
    private final Duration timeout;

    public BukkitShardWorldProvisioner(Plugin plugin) {
        this(new BukkitPlatformScheduler(plugin), DEFAULT_TIMEOUT);
    }

    BukkitShardWorldProvisioner(PlatformScheduler scheduler, Duration timeout) {
        this.scheduler = scheduler;
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public void ensureLoaded(String worldName) throws IOException {
        if (worldName == null || worldName.isBlank()) {
            throw new IOException("cannot provision a shard without a world name");
        }
        if (Bukkit.isPrimaryThread()) {
            load(worldName);
            return;
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            scheduler.runGlobal(() -> {
                try {
                    load(worldName);
                    completion.complete(null);
                } catch (Throwable error) {
                    completion.completeExceptionally(error);
                }
            });
        } catch (RuntimeException error) {
            throw new IOException("failed to schedule shard world provisioning " + worldName, error);
        }
        await(worldName, completion);
    }

    @SuppressWarnings("removal")
    private void load(String worldName) throws IOException {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return;
        }
        world = new WorldCreator(worldName)
            .environment(World.Environment.NORMAL)
            .generateStructures(false)
            .keepSpawnLoaded(TriState.FALSE)
            .generator(VOID_GENERATOR)
            .createWorld();
        if (world == null) {
            throw new IOException("Paper failed to load shard world " + worldName);
        }
    }

    private void await(String worldName, CompletableFuture<Void> completion) throws IOException {
        try {
            completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while provisioning shard world " + worldName, exception);
        } catch (TimeoutException exception) {
            throw new IOException("timed out provisioning shard world " + worldName, exception);
        } catch (ExecutionException exception) {
            throw new IOException("failed to provision shard world " + worldName, exception.getCause());
        }
    }
}
