package kr.lunaf.cloudislands.paper.level;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RuntimeCommandClient;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

public final class BlockDeltaReporter {
    private final Plugin plugin;
    private final RuntimeCommandClient runtimeCommands;

    public BlockDeltaReporter(Plugin plugin, CoreApiClient client) {
        this.plugin = plugin;
        this.runtimeCommands = client.runtimeCommands();
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
        report(islandId, block.getType().getKey().toString(), delta);
    }

    private void report(UUID islandId, String materialKey, long delta) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runAsync(plugin, () -> runtimeCommands.recordBlockDelta(islandId, materialKey, delta));
    }

}
