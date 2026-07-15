package kr.lunaf.cloudislands.paper;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/** Captures the player and block state that authorized a delayed boundary return. */
record BoundaryReturnRequest(
    Player expectedPlayer,
    UUID worldId,
    int blockX,
    int blockY,
    int blockZ
) {
    static BoundaryReturnRequest capture(Player player, Location origin) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(origin, "origin");
        World world = Objects.requireNonNull(origin.getWorld(), "origin.world");
        return new BoundaryReturnRequest(
            player,
            world.getUID(),
            origin.getBlockX(),
            origin.getBlockY(),
            origin.getBlockZ()
        );
    }

    boolean isCurrent(Player activePlayer) {
        if (activePlayer == null || activePlayer != expectedPlayer || !activePlayer.isOnline()) {
            return false;
        }
        Location current = activePlayer.getLocation();
        World currentWorld = current == null ? null : current.getWorld();
        return currentWorld != null
            && worldId.equals(currentWorld.getUID())
            && blockX == current.getBlockX()
            && blockY == current.getBlockY()
            && blockZ == current.getBlockZ();
    }
}
