package kr.lunaf.cloudislands.paper.platform.world;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentQueryClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.environment.IslandBiomePaintPlan;
import kr.lunaf.cloudislands.paper.event.IslandActivateEvent;
import kr.lunaf.cloudislands.paper.event.IslandBiomeChangeEvent;
import kr.lunaf.cloudislands.paper.event.IslandDeactivateEvent;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public final class IslandBiomeRuntimeApplier implements Listener {
    private final Plugin plugin;
    private final ProtectionController protection;
    private final IslandEnvironmentQueryClient environmentQueries;
    private final Map<UUID, Long> generations = new ConcurrentHashMap<>();
    private final AtomicLong generationSequence = new AtomicLong();

    public IslandBiomeRuntimeApplier(Plugin plugin, ProtectionController protection, IslandEnvironmentQueryClient environmentQueries) {
        this.plugin = plugin;
        this.protection = protection;
        this.environmentQueries = environmentQueries;
    }

    @EventHandler
    public void onBiomeChange(IslandBiomeChangeEvent event) {
        long generation = nextGeneration(event.islandId());
        PaperSchedulers.run(plugin, () -> begin(event.islandId(), event.biomeKey(), generation));
    }

    @EventHandler
    public void onIslandActivate(IslandActivateEvent event) {
        UUID islandId = event.islandId();
        long generation = nextGeneration(islandId);
        environmentQueries.biome(islandId).whenComplete((biome, error) -> {
            if (!current(islandId, generation)) {
                return;
            }
            if (error != null || biome == null || biome.biomeKey() == null || biome.biomeKey().isBlank()) {
                generations.remove(islandId, generation);
                plugin.getLogger().warning("Cannot reconcile persisted island biome for " + islandId + " after activation: Core biome query failed or returned an empty key.");
                return;
            }
            if (!plugin.isEnabled()) {
                generations.remove(islandId, generation);
                return;
            }
            PaperSchedulers.run(plugin, () -> begin(islandId, biome.biomeKey(), generation));
        });
    }

    @EventHandler
    public void onIslandDeactivate(IslandDeactivateEvent event) {
        generations.remove(event.islandId());
    }

    private void begin(UUID islandId, String biomeKey, long generation) {
        if (!current(islandId, generation)) {
            return;
        }
        IslandRegion region = protection.region(islandId).orElse(null);
        NamespacedKey key = NamespacedKey.fromString(biomeKey == null ? "" : biomeKey);
        Biome biome = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
        World world = region == null ? null : Bukkit.getWorld(region.world());
        if (region == null || biome == null || world == null) {
            generations.remove(islandId, generation);
            plugin.getLogger().warning("Cannot apply island biome " + biomeKey + " for " + islandId + ": local region, world, or biome registry entry is unavailable.");
            return;
        }
        applyChunk(islandId, generation, region, world, biome, IslandBiomePaintPlan.chunkCoordinates(region), 0);
    }

    private void applyChunk(UUID islandId, long generation, IslandRegion region, World world, Biome biome, List<IslandBiomePaintPlan.ChunkCoordinate> chunks, int index) {
        if (!current(islandId, generation)) {
            return;
        }
        if (index >= chunks.size()) {
            generations.remove(islandId, generation);
            return;
        }
        IslandBiomePaintPlan.ChunkCoordinate coordinate = chunks.get(index);
        world.getChunkAtAsync(coordinate.x(), coordinate.z(), true).whenComplete((chunk, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            PaperSchedulers.runLater(plugin, () -> {
                if (!current(islandId, generation)) {
                    return;
                }
                if (error == null && chunk != null) {
                    paintChunk(region, world, biome, coordinate);
                    world.refreshChunk(coordinate.x(), coordinate.z());
                } else {
                    plugin.getLogger().warning("Failed to load chunk " + coordinate.x() + "," + coordinate.z() + " while applying biome for island " + islandId + ".");
                }
                applyChunk(islandId, generation, region, world, biome, chunks, index + 1);
            }, 1L);
        });
    }

    private static void paintChunk(IslandRegion region, World world, Biome biome, IslandBiomePaintPlan.ChunkCoordinate chunk) {
        int minX = Math.max(region.minX(), chunk.x() << 4);
        int maxX = Math.min(region.maxX(), (chunk.x() << 4) + 15);
        int minZ = Math.max(region.minZ(), chunk.z() << 4);
        int maxZ = Math.min(region.maxZ(), (chunk.z() << 4) + 15);
        int minY = IslandBiomePaintPlan.alignedStart(world.getMinHeight());
        for (int x = IslandBiomePaintPlan.alignedStart(minX); x <= maxX; x += IslandBiomePaintPlan.SAMPLE_STEP) {
            for (int z = IslandBiomePaintPlan.alignedStart(minZ); z <= maxZ; z += IslandBiomePaintPlan.SAMPLE_STEP) {
                for (int y = minY; y < world.getMaxHeight(); y += IslandBiomePaintPlan.SAMPLE_STEP) {
                    world.setBiome(x, y, z, biome);
                }
            }
        }
    }

    private boolean current(UUID islandId, long generation) {
        return generations.getOrDefault(islandId, -1L) == generation;
    }

    private long nextGeneration(UUID islandId) {
        long generation = generationSequence.incrementAndGet();
        generations.put(islandId, generation);
        return generation;
    }

}
