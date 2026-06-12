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
        if (to == null || event.getPlayer().hasPermission("cloudislands.admin.bypass") || sameBlock(from, to)) {
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
        boolean member = protection.memberOrTrusted(region.islandId(), event.getPlayer().getUniqueId());
        Location target = member ? memberSpawn(from, region) : visitorSpawn(from, region);
        event.setCancelled(true);
        event.getPlayer().teleport(target);
        event.getPlayer().sendActionBar(net.kyori.adventure.text.Component.text(member
            ? "섬 경계 밖으로 이동할 수 없어 섬 스폰으로 돌려보냈습니다."
            : "섬 경계 밖으로 이동할 수 없어 방문자 위치로 돌려보냈습니다."));
    }

    private Location memberSpawn(Location from, IslandRegion region) {
        return new Location(from.getWorld(), region.originX() + 0.5D, Math.max(from.getY(), 100.0D), region.originZ() + 0.5D, from.getYaw(), from.getPitch());
    }

    private Location visitorSpawn(Location from, IslandRegion region) {
        return new Location(from.getWorld(), region.originX() + 0.5D, Math.max(from.getY(), 100.0D), region.originZ() + 2.5D, 180.0F, 0.0F);
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getWorld().equals(to.getWorld())
            && from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }
}
