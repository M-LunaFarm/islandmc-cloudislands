package kr.lunaf.cloudislands.paper.platform.world;

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
}
