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
import kr.lunaf.cloudislands.paper.activation.StarterIslandGenerator;
import kr.lunaf.cloudislands.paper.platform.scheduler.BukkitPlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/** Creates the safe, dependency-free fallback island used by bundle-less templates. */
public final class BukkitStarterIslandGenerator implements StarterIslandGenerator {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final PlatformScheduler scheduler;
    private final Duration timeout;
    private final AtomicLong operationSequence = new AtomicLong();
    private final ConcurrentMap<Long, PendingGeneration> pendingGenerations = new ConcurrentHashMap<>();

    public BukkitStarterIslandGenerator(Plugin plugin) {
        this(new BukkitPlatformScheduler(plugin), DEFAULT_TIMEOUT);
    }

    BukkitStarterIslandGenerator(PlatformScheduler scheduler, Duration timeout) {
        this.scheduler = scheduler;
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
    }

    @Override
    public void generate(Plan plan) throws IOException {
        if (plan == null || plan.worldName() == null || plan.worldName().isBlank()) {
            throw new IOException("cannot generate a starter island without a target world");
        }
        PendingGeneration pending = new PendingGeneration(operationSequence.incrementAndGet(), plan, new CompletableFuture<>());
        pendingGenerations.put(pending.id(), pending);
        try {
            scheduler.runGlobal(() -> prepareChunk(pending));
        } catch (RuntimeException error) {
            pendingGenerations.remove(pending.id(), pending);
            throw new IOException("failed to schedule starter island generation " + plan.islandId(), error);
        }
        await(plan, pending.completion());
    }

    @Override
    public void prepareShutdown() throws IOException {
        if (!Bukkit.isPrimaryThread()) {
            throw new IOException("starter island shutdown drain must run on the Paper global thread");
        }
        IOException failure = null;
        for (PendingGeneration pending : List.copyOf(pendingGenerations.values())) {
            if (!pendingGenerations.remove(pending.id(), pending)) {
                continue;
            }
            try {
                World world = requireWorld(pending.plan());
                int chunkX = Math.floorDiv(pending.plan().blockX(), 16);
                int chunkZ = Math.floorDiv(pending.plan().blockZ(), 16);
                world.getChunkAt(chunkX, chunkZ);
                buildNow(pending.plan(), world);
                pending.completion().complete(null);
            } catch (Throwable error) {
                pending.completion().completeExceptionally(error);
            }
            try {
                await(pending.plan(), pending.completion());
            } catch (IOException error) {
                if (failure == null) {
                    failure = new IOException("failed to drain pending starter island generations");
                }
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void prepareChunk(PendingGeneration pending) {
        if (!pendingGenerations.containsKey(pending.id())) {
            return;
        }
        Plan plan = pending.plan();
        CompletableFuture<Void> completion = pending.completion();
        World world = Bukkit.getWorld(plan.worldName());
        if (world == null) {
            pendingGenerations.remove(pending.id(), pending);
            completion.completeExceptionally(new IOException("starter island world is not loaded: " + plan.worldName()));
            return;
        }
        int chunkX = Math.floorDiv(plan.blockX(), 16);
        int chunkZ = Math.floorDiv(plan.blockZ(), 16);
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((_chunk, error) -> {
            if (error != null) {
                pendingGenerations.remove(pending.id(), pending);
                completion.completeExceptionally(error);
                return;
            }
            try {
                scheduler.runGlobal(() -> completeBuild(pending, world));
            } catch (RuntimeException schedulingError) {
                pendingGenerations.remove(pending.id(), pending);
                completion.completeExceptionally(schedulingError);
            }
        });
    }

    private void completeBuild(PendingGeneration pending, World world) {
        if (!pendingGenerations.remove(pending.id(), pending)) {
            return;
        }
        try {
            buildNow(pending.plan(), world);
            pending.completion().complete(null);
        } catch (Throwable error) {
            pending.completion().completeExceptionally(error);
        }
    }

    private void buildNow(Plan plan, World world) throws IOException {
        int surfaceY = Math.max(world.getMinHeight() + 3, Math.min(plan.surfaceY(), world.getMaxHeight() - 2));
        try {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    int distance = Math.abs(x) + Math.abs(z);
                    if (distance > 5) {
                        continue;
                    }
                    world.getBlockAt(plan.blockX() + x, surfaceY, plan.blockZ() + z).setType(Material.GRASS_BLOCK, false);
                    world.getBlockAt(plan.blockX() + x, surfaceY - 1, plan.blockZ() + z).setType(Material.DIRT, false);
                    if (distance <= 3) {
                        world.getBlockAt(plan.blockX() + x, surfaceY - 2, plan.blockZ() + z).setType(Material.DIRT, false);
                    }
                }
            }
            world.getBlockAt(plan.blockX(), surfaceY - 3, plan.blockZ()).setType(Material.BEDROCK, false);
            world.getBlockAt(plan.blockX() + 2, surfaceY + 1, plan.blockZ()).setType(Material.OAK_SAPLING, false);
            populateStarterChest(world, plan.blockX() - 2, surfaceY + 1, plan.blockZ());
        } catch (Throwable error) {
            if (error instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("failed to build starter island " + plan.islandId(), error);
        }
    }

    private World requireWorld(Plan plan) throws IOException {
        World world = Bukkit.getWorld(plan.worldName());
        if (world == null) {
            throw new IOException("starter island world is not loaded: " + plan.worldName());
        }
        return world;
    }

    private void populateStarterChest(World world, int blockX, int blockY, int blockZ) throws IOException {
        org.bukkit.block.Block block = world.getBlockAt(blockX, blockY, blockZ);
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest chest)) {
            throw new IOException("failed to create built-in starter chest");
        }
        chest.getBlockInventory().clear();
        chest.getBlockInventory().addItem(
            new ItemStack(Material.LAVA_BUCKET),
            new ItemStack(Material.ICE, 2),
            new ItemStack(Material.OAK_SAPLING, 2),
            new ItemStack(Material.BONE_MEAL, 4),
            new ItemStack(Material.WHEAT_SEEDS, 4),
            new ItemStack(Material.MELON_SEEDS, 2),
            new ItemStack(Material.PUMPKIN_SEEDS, 2),
            new ItemStack(Material.CACTUS),
            new ItemStack(Material.SUGAR_CANE, 2)
        );
        chest.update(true, false);
    }

    private void await(Plan plan, CompletableFuture<Void> completion) throws IOException {
        try {
            completion.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while generating starter island " + plan.islandId(), exception);
        } catch (TimeoutException exception) {
            throw new IOException("timed out generating starter island " + plan.islandId(), exception);
        } catch (ExecutionException exception) {
            throw new IOException("failed to generate starter island " + plan.islandId(), exception.getCause());
        }
    }

    private record PendingGeneration(long id, Plan plan, CompletableFuture<Void> completion) {
    }
}
