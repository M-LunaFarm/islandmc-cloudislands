package kr.seungmin.satisskyfactory.hook;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

public final class CloudIslandsSkyblockProvider implements SkyblockProvider {
    private final JavaPlugin plugin;
    private boolean available;

    public CloudIslandsSkyblockProvider(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean enable() {
        PluginManager plugins = plugin.getServer().getPluginManager();
        available = plugins.getPlugin("CloudIslands") != null;
        if (!available) {
            plugin.getLogger().severe("CloudIslands provider selected, but CloudIslands plugin was not found.");
        }
        return available;
    }

    @Override
    public void configure(boolean allowCoopBuild, boolean protectSpawnIsland, boolean requireIslandMember) {
    }

    @Override
    public Optional<IslandRef> getIslandAt(Location location) {
        return Optional.empty();
    }

    @Override
    public Optional<IslandRef> getIslandOf(Player player) {
        return Optional.empty();
    }

    @Override
    public Optional<IslandRef> getIslandByUuid(UUID islandUuid) {
        return Optional.empty();
    }

    @Override
    public Optional<Location> getIslandCenter(IslandRef island) {
        return Optional.empty();
    }

    @Override
    public UUID getIslandUuid(IslandRef island) {
        return island.islandUuid();
    }

    @Override
    public UUID getIslandOwnerUuid(IslandRef island) {
        return island.ownerUuid();
    }

    @Override
    public boolean canBuildFactory(Player player, Location location) {
        return false;
    }

    @Override
    public boolean isLocationInsidePlayerIsland(Player player, Location location) {
        return false;
    }

    @Override
    public boolean isPlayerIslandMember(Player player, IslandRef island) {
        return false;
    }
}
