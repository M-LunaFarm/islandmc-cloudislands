package kr.lunaf.cloudislands.paper.command;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.paper.ProtectionController;
import org.bukkit.Location;
import org.bukkit.entity.Player;

final class IslandCommandIslandContext {
    private final ProtectionController protection;

    IslandCommandIslandContext(ProtectionController protection) {
        this.protection = protection;
    }

    Optional<UUID> currentIsland(Player player, String missingMessage) {
        Optional<UUID> islandId = protection.islandAt(player.getLocation().getBlock());
        if (islandId.isEmpty()) {
            player.sendMessage(missingMessage);
        }
        return islandId;
    }

    boolean allowed(Player player, IslandPermission permission) {
        Location location = player.getLocation();
        return protection.checkBlock(
            player.getUniqueId(),
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            permission,
            player.isOp()
        ).allowed();
    }

    boolean publicWarpAllowed(Player player, IslandHomeWarpCommandHandler.Point point, boolean islandPublicAccess) {
        return point.publicAccess()
            && islandPublicAccess
            && protection.checkSystemFlag(player.getLocation().getBlock(), IslandFlag.PUBLIC_WARPS).allowed();
    }

    IslandLocation location(Location location) {
        Optional<IslandRegion> region = protection.regionAt(location.getBlock());
        return new IslandLocation(
            "",
            region.map(value -> location.getX() - value.originX()).orElse(location.getX()),
            location.getY(),
            region.map(value -> location.getZ() - value.originZ()).orElse(location.getZ()),
            location.getYaw(),
            location.getPitch()
        );
    }
}
