package kr.lunaf.cloudislands.paper.platform.world;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            scheduler.runGlobal(() -> prepareChunk(plan, completion));
        } catch (RuntimeException error) {
            throw new IOException("failed to schedule starter island generation " + plan.islandId(), error);
        }
        await(plan, completion);
    }

    private void prepareChunk(Plan plan, CompletableFuture<Void> completion) {
        World world = Bukkit.getWorld(plan.worldName());
        if (world == null) {
            completion.completeExceptionally(new IOException("starter island world is not loaded: " + plan.worldName()));
            return;
        }
        int chunkX = Math.floorDiv(plan.blockX(), 16);
        int chunkZ = Math.floorDiv(plan.blockZ(), 16);
        world.getChunkAtAsync(chunkX, chunkZ, true).whenComplete((_chunk, error) -> {
            if (error != null) {
                completion.completeExceptionally(error);
                return;
            }
            try {
                scheduler.runGlobal(() -> build(plan, world, completion));
            } catch (RuntimeException schedulingError) {
                completion.completeExceptionally(schedulingError);
            }
        });
    }

    private void build(Plan plan, World world, CompletableFuture<Void> completion) {
        try {
            int surfaceY = Math.max(world.getMinHeight() + 3, Math.min(plan.surfaceY(), world.getMaxHeight() - 2));
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
            completion.complete(null);
        } catch (Throwable error) {
            completion.completeExceptionally(error);
        }
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
}
