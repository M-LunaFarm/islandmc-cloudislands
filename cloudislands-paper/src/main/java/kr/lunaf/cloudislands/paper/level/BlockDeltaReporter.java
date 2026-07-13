package kr.lunaf.cloudislands.paper.level;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RuntimeCommandClient;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomBlockKeyService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

public final class BlockDeltaReporter {
    private final Plugin plugin;
    private final RuntimeCommandClient runtimeCommands;
    private final CustomBlockKeyService customBlockKeys;
    private final IslandLevelScanService levelScanService;

    public BlockDeltaReporter(Plugin plugin, CoreApiClient client) {
        this(
            plugin,
            client,
            plugin instanceof kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin cloudIslands
                ? cloudIslands.customBlockKeys()
                : CustomBlockKeyService.discover(plugin.getServer()),
            plugin instanceof kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin cloudIslands
                ? cloudIslands.levelScanService()
                : null
        );
    }

    BlockDeltaReporter(Plugin plugin, CoreApiClient client, CustomBlockKeyService customBlockKeys) {
        this(plugin, client, customBlockKeys, null);
    }

    BlockDeltaReporter(Plugin plugin, CoreApiClient client, CustomBlockKeyService customBlockKeys, IslandLevelScanService levelScanService) {
        this.plugin = plugin;
        this.runtimeCommands = client.runtimeCommands();
        this.customBlockKeys = customBlockKeys == null ? CustomBlockKeyService.vanillaOnly() : customBlockKeys;
        this.levelScanService = levelScanService;
    }

    public void placed(UUID islandId, Block block) {
        report(islandId, block, 1L);
    }

    public void placed(UUID islandId, UUID actorUuid, Block block) {
        report(islandId, block, 1L);
    }

    public void broken(UUID islandId, Block block) {
        report(islandId, block, -1L);
    }

    public void broken(UUID islandId, UUID actorUuid, Block block) {
        report(islandId, block, -1L);
    }

    public void broken(UUID islandId, Material material) {
        report(islandId, material.getKey().toString(), -1L);
    }

    public void placed(UUID islandId, Material material) {
        report(islandId, material.getKey().toString(), 1L);
    }

    public void placed(UUID islandId, UUID actorUuid, Material material) {
        report(islandId, material.getKey().toString(), 1L);
    }

    public void entityPlaced(UUID islandId, EntityType entityType) {
        report(islandId, "entity:" + entityType.getKey(), 1L);
    }

    public void entityRemoved(UUID islandId, EntityType entityType) {
        report(islandId, "entity:" + entityType.getKey(), -1L);
    }

    private void report(UUID islandId, Block block, long delta) {
        report(islandId, customBlockKeys.blockKey(block), delta);
    }

    private void report(UUID islandId, String materialKey, long delta) {
        if (levelScanService != null) {
            levelScanService.recordBlockDelta(islandId, materialKey, delta);
            return;
        }
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runAsync(plugin, () -> runtimeCommands.recordBlockDelta(islandId, materialKey, delta));
    }

}
