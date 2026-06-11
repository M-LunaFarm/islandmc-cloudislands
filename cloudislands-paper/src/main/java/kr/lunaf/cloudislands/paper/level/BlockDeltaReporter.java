package kr.lunaf.cloudislands.paper.level;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.block.Block;
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

    public void broken(UUID islandId, Block block) {
        report(islandId, block, -1L);
    }

    private void report(UUID islandId, Block block, long delta) {
        String materialKey = block.getType().getKey().toString();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> client.recordBlockDelta(islandId, materialKey, delta));
    }
}
