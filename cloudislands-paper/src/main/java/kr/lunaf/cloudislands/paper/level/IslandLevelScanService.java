package kr.lunaf.cloudislands.paper.level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public final class IslandLevelScanService {
    private final Plugin plugin;
    private final Supplier<ActiveIslandRegistry> activeIslands;
    private final CoreApiClient client;
    private final PaperWorldGateway worlds;

    public IslandLevelScanService(Plugin plugin, Supplier<ActiveIslandRegistry> activeIslands, CoreApiClient client) {
        this(plugin, activeIslands, client, new BukkitWorldGateway(plugin));
    }

    IslandLevelScanService(Plugin plugin, Supplier<ActiveIslandRegistry> activeIslands, CoreApiClient client, PaperWorldGateway worlds) {
        this.plugin = plugin;
        this.activeIslands = activeIslands;
        this.client = client;
        this.worlds = worlds;
    }

    public CompletableFuture<Void> rescanIsland(UUID islandId) {
        ActiveIslandRegistry registry = activeIslands.get();
        ActiveIslandRegistry.ActiveIsland active = registry == null ? null : registry.find(islandId).orElse(null);
        if (active == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            try {
                World world = worlds.world(active.worldName());
                if (world == null) {
                    future.complete(null);
                    return;
                }
                Map<String, Long> counts = scan(world, active);
                client.replaceBlockCounts(islandId, counts)
                    .thenRun(() -> future.complete(null))
                    .exceptionally(error -> {
                        future.completeExceptionally(error);
                        return null;
                    });
            } catch (RuntimeException exception) {
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    private Map<String, Long> scan(World world, ActiveIslandRegistry.ActiveIsland active) {
        Map<String, Long> counts = new HashMap<>();
        int half = Math.max(1, active.islandSize() / 2);
        int minX = active.originX() - half;
        int maxX = active.originX() + half;
        int minZ = active.originZ() - half;
        int maxZ = active.originZ() + half;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (!type.isAir()) {
                        counts.merge(type.getKey().toString(), 1L, Long::sum);
                    }
                }
            }
        }
        for (Entity entity : world.getEntities()) {
            Location location = entity.getLocation();
            if (location.getBlockX() >= minX && location.getBlockX() <= maxX && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ) {
                counts.merge("entity:" + entity.getType().getKey(), 1L, Long::sum);
            }
        }
        return counts;
    }
}
