package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

public final class IslandProtectionListener implements Listener {
    private final ProtectionController protection;
    private final BlockDeltaReporter blockDeltas;

    public IslandProtectionListener(ProtectionController protection, BlockDeltaReporter blockDeltas) {
        this.protection = protection;
        this.blockDeltas = blockDeltas;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        boolean blocked = denied(event.getPlayer(), event.getBlock(), IslandPermission.BREAK);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.broken(islandId, event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        boolean blocked = denied(event.getPlayer(), event.getBlock(), IslandPermission.BUILD);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.placed(islandId, event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            event.setCancelled(denied(event.getPlayer(), event.getClickedBlock(), IslandPermission.INTERACT));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getBlock(), IslandPermission.PLACE_LIQUID));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getBlock(), IslandPermission.BREAK_LIQUID));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getLocation() != null) {
            event.setCancelled(denied(player, event.getInventory().getLocation().getBlock(), IslandPermission.OPEN_CONTAINER));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            event.setCancelled(denied(player, event.getEntity().getLocation().getBlock(), event.getEntity() instanceof Player ? IslandPermission.ATTACK_PLAYER : IslandPermission.ATTACK_MOB));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getPlayer().getLocation().getBlock(), IslandPermission.DROP_ITEM));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(denied(player, event.getItem().getLocation().getBlock(), IslandPermission.PICKUP_ITEM));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getBlock(), IslandPermission.BUILD));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player) {
            event.setCancelled(denied(player, event.getEntity().getLocation().getBlock(), IslandPermission.BREAK));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), IslandPermission.INTERACT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getAttacker() instanceof Player player) {
            event.setCancelled(denied(player, event.getVehicle().getLocation().getBlock(), IslandPermission.BREAK));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protection.checkSystem(block, IslandPermission.BREAK).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protection.checkSystem(block, IslandPermission.BREAK).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        event.setCancelled(!protection.checkSystem(event.getToBlock(), IslandPermission.PLACE_LIQUID).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidLevel(FluidLevelChangeEvent event) {
        event.setCancelled(!protection.checkSystem(event.getBlock(), IslandPermission.PLACE_LIQUID).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        event.setCancelled(!protection.checkSystem(event.getBlock(), IslandPermission.BUILD).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        event.setCancelled(!protection.checkSystem(event.getBlock(), IslandPermission.BUILD).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(!protection.checkSystem(event.getBlock(), IslandPermission.BREAK).allowed());
    }

    private boolean denied(Player player, Block block, IslandPermission permission) {
        return !protection.checkBlock(player.getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), permission).allowed();
    }
}
