package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.common.protection.IslandRegion;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class IslandBoundaryListener implements Listener {
    private final ProtectionController protection;

    public IslandBoundaryListener(ProtectionController protection) {
        this.protection = protection;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || event.getPlayer().isOp() || sameBlock(from, to)) {
            return;
        }
        java.util.Optional<IslandRegion> fromRegion = protection.regionAt(from.getBlock());
        if (fromRegion.isEmpty()) {
            return;
        }
        java.util.Optional<IslandRegion> toRegion = protection.regionAt(to.getBlock());
        if (toRegion.isPresent() && toRegion.get().islandId().equals(fromRegion.get().islandId())) {
            return;
        }
        IslandRegion region = fromRegion.get();
        event.setCancelled(true);
        event.getPlayer().teleport(new Location(from.getWorld(), region.originX() + 0.5D, from.getY(), region.originZ() + 0.5D, from.getYaw(), from.getPitch()));
        event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text("섬 경계 밖으로 이동할 수 없습니다."));
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getWorld().equals(to.getWorld())
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }
}
