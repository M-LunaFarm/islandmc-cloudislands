package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class IslandProtectionListener implements Listener {
    private final ProtectionController protection;

    public IslandProtectionListener(ProtectionController protection) {
        this.protection = protection;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        event.setCancelled(!protection.checkBlock(event.getPlayer().getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), IslandPermission.BREAK).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        event.setCancelled(!protection.checkBlock(event.getPlayer().getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), IslandPermission.BUILD).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        event.setCancelled(!protection.checkBlock(event.getPlayer().getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), IslandPermission.INTERACT).allowed());
    }
}
