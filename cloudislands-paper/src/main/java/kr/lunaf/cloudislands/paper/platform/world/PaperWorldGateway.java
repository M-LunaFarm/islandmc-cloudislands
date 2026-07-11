package kr.lunaf.cloudislands.paper.platform.world;

import org.bukkit.Location;
import org.bukkit.World;

public interface PaperWorldGateway {
    World world(String worldName);

    default Location worldSpawn(String worldName) {
        return null;
    }
}
