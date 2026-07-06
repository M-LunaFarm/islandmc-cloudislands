package kr.lunaf.cloudislands.paper.platform.world;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class AdminWorldSpawnGateway {
    private final Plugin plugin;

    public AdminWorldSpawnGateway(Plugin plugin) {
        this.plugin = plugin;
    }

    public SpawnUpdateResult setFromPlayer(Player player) {
        return update(player.getLocation());
    }

    public SpawnUpdateResult set(String worldName, double x, double y, double z, float yaw) {
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return SpawnUpdateResult.notFound(worldName);
        }
        return update(new Location(world, x, y, z, yaw, 0.0F));
    }

    public List<String> worldNames() {
        List<String> names = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            names.add(world.getName());
        }
        return names;
    }

    private SpawnUpdateResult update(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return SpawnUpdateResult.notFound("");
        }
        boolean accepted = world.setSpawnLocation(location);
        return new SpawnUpdateResult(true, accepted, world.getName(), location.getX(), location.getY(), location.getZ(), location.getYaw());
    }

    public record SpawnUpdateResult(
        boolean worldFound,
        boolean accepted,
        String worldName,
        double x,
        double y,
        double z,
        float yaw
    ) {
        static SpawnUpdateResult notFound(String worldName) {
            return new SpawnUpdateResult(false, false, worldName == null ? "" : worldName, 0.0D, 0.0D, 0.0D, 0.0F);
        }
    }
}
