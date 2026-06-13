package kr.seungmin.satisskyfactory.hook;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

public interface SkyblockProvider {
    boolean enable();

    void configure(boolean allowCoopBuild, boolean protectSpawnIsland, boolean requireIslandMember);

    Optional<IslandRef> getIslandAt(Location location);

    Optional<IslandRef> getIslandOf(Player player);

    Optional<IslandRef> getIslandByUuid(UUID islandUuid);

    Optional<Location> getIslandCenter(IslandRef island);

    UUID getIslandUuid(IslandRef island);

    UUID getIslandOwnerUuid(IslandRef island);

    boolean canBuildFactory(Player player, Location location);

    boolean isLocationInsidePlayerIsland(Player player, Location location);

    boolean isPlayerIslandMember(Player player, IslandRef island);
}
