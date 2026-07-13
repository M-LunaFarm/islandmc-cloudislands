package kr.lunaf.cloudislands.paper.platform.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class BukkitWorldGateway implements PaperWorldGateway {
    private final Plugin plugin;

    public BukkitWorldGateway(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public World world(String worldName) {
        return worldName == null ? null : plugin.getServer().getWorld(worldName);
    }

    @Override
    public Location worldSpawn(String worldName) {
        World world = world(worldName);
        return world == null ? null : world.getSpawnLocation();
    }

    @Override
    public CompletableFuture<Optional<Location>> safeDestination(Location requested, IslandRegion boundary) {
        if (requested == null || requested.getWorld() == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        World world = requested.getWorld();
        int minChunkX = Math.floorDiv(requested.getBlockX() - SafeTeleportResolver.HORIZONTAL_RADIUS, 16);
        int maxChunkX = Math.floorDiv(requested.getBlockX() + SafeTeleportResolver.HORIZONTAL_RADIUS, 16);
        int minChunkZ = Math.floorDiv(requested.getBlockZ() - SafeTeleportResolver.HORIZONTAL_RADIUS, 16);
        int maxChunkZ = Math.floorDiv(requested.getBlockZ() + SafeTeleportResolver.HORIZONTAL_RADIUS, 16);
        List<CompletableFuture<?>> loads = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                loads.add(world.getChunkAtAsync(chunkX, chunkZ, true));
            }
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
            .thenCompose(_ignored -> {
                CompletableFuture<Optional<Location>> result = new CompletableFuture<>();
                PaperSchedulers.run(plugin, () -> {
                    try {
                        result.complete(SafeTeleportResolver.resolve(requested, boundary));
                    } catch (RuntimeException error) {
                        result.completeExceptionally(error);
                    }
                });
                return result;
            });
    }
}
