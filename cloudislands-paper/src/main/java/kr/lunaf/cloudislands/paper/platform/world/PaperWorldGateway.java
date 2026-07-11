package kr.lunaf.cloudislands.paper.platform.world;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import org.bukkit.Location;
import org.bukkit.World;

public interface PaperWorldGateway {
    World world(String worldName);

    default Location worldSpawn(String worldName) {
        return null;
    }

    default CompletableFuture<Optional<Location>> safeDestination(Location requested, IslandRegion boundary) {
        return CompletableFuture.completedFuture(Optional.ofNullable(requested));
    }
}
