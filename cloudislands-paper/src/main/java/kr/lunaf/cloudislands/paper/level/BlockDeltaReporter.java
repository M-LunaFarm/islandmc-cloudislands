package kr.lunaf.cloudislands.paper.level;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

public final class BlockDeltaReporter {
    private final Plugin plugin;
    private final CoreApiClient client;

    public BlockDeltaReporter(Plugin plugin, CoreApiClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    public void placed(UUID islandId, Block block) {
        report(islandId, block, 1L);
    }

    public void placed(UUID islandId, UUID actorUuid, Block block) {
        report(islandId, block, 1L);
        progressPlaced(islandId, actorUuid, block.getType());
    }

    public void broken(UUID islandId, Block block) {
        report(islandId, block, -1L);
    }

    public void broken(UUID islandId, UUID actorUuid, Block block) {
        report(islandId, block, -1L);
        progressBroken(islandId, actorUuid);
    }

    public void broken(UUID islandId, Material material) {
        report(islandId, material.getKey().toString(), -1L);
    }

    public void placed(UUID islandId, Material material) {
        report(islandId, material.getKey().toString(), 1L);
    }

    public void placed(UUID islandId, UUID actorUuid, Material material) {
        report(islandId, material.getKey().toString(), 1L);
        progressPlaced(islandId, actorUuid, material);
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
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runAsync(plugin, () -> client.recordBlockDelta(islandId, materialKey, delta));
    }

    private void progressPlaced(UUID islandId, UUID actorUuid, Material material) {
        if (actorUuid == null) {
            return;
        }
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runAsync(plugin, () -> {
            client.progressIslandMission(islandId, actorUuid, "first_blocks", "MISSION", 1L).exceptionally(error -> null);
            client.progressIslandMission(islandId, actorUuid, "daily_builder", "CHALLENGE", 1L).exceptionally(error -> null);
            if (isFarmBlock(material)) {
                client.progressIslandMission(islandId, actorUuid, "starter_farm", "MISSION", 1L).exceptionally(error -> null);
            }
        });
    }

    private void progressBroken(UUID islandId, UUID actorUuid) {
        if (actorUuid == null) {
            return;
        }
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.runAsync(plugin, () ->
            client.progressIslandMission(islandId, actorUuid, "daily_miner", "CHALLENGE", 1L).exceptionally(error -> null));
    }

    private boolean isFarmBlock(Material material) {
        String key = material.getKey().getKey();
        return key.equals("wheat")
            || key.equals("carrots")
            || key.equals("potatoes")
            || key.equals("beetroots")
            || key.equals("pumpkin_stem")
            || key.equals("melon_stem")
            || key.equals("sugar_cane")
            || key.equals("cactus")
            || key.equals("bamboo")
            || key.equals("cocoa")
            || key.equals("nether_wart");
    }
}
