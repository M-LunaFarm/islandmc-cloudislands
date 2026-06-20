package kr.lunaf.cloudislands.paper.world;

import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

public final class ShardWorldPreloader {
    private final Plugin plugin;
    private final PaperWorldGateway worlds;

    public ShardWorldPreloader(Plugin plugin) {
        this(plugin, new BukkitWorldGateway(plugin));
    }

    ShardWorldPreloader(Plugin plugin, PaperWorldGateway worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    public void preload(String worldName, int originX, int originZ, int radiusChunks) {
        World world = worlds.world(worldName);
        if (world == null) {
            plugin.getLogger().warning("Shard world is not loaded: " + worldName);
            return;
        }
        int centerChunkX = Math.floorDiv(originX, 16);
        int centerChunkZ = Math.floorDiv(originZ, 16);
        for (int x = centerChunkX - radiusChunks; x <= centerChunkX + radiusChunks; x++) {
            for (int z = centerChunkZ - radiusChunks; z <= centerChunkZ + radiusChunks; z++) {
                world.getChunkAtAsync(x, z);
            }
        }
    }
}
