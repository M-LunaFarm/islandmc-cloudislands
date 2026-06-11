package kr.lunaf.cloudislands.paper;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.paper.event.IslandPermissionCheckEvent;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
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
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        boolean blocked = event.getReplacedBlockStates().stream().anyMatch(state -> denied(event.getPlayer(), state.getBlock(), IslandPermission.BUILD));
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.placed(islandId, event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            event.setCancelled(denied(event.getPlayer(), event.getClickedBlock(), interactionPermission(event.getClickedBlock().getType())));
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
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && event.getInventory().getLocation() != null) {
            event.setCancelled(denied(player, event.getInventory().getLocation().getBlock(), IslandPermission.OPEN_CONTAINER));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player player = attackingPlayer(event.getDamager());
        if (player != null) {
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
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        Optional<UUID> fromIsland = protection.islandAt(event.getFrom().getBlock());
        if (fromIsland.isEmpty()) {
            return;
        }
        Optional<UUID> toIsland = protection.islandAt(event.getTo().getBlock());
        if (toIsland.isEmpty() || !toIsland.get().equals(fromIsland.get())) {
            event.setCancelled(true);
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
    public void onShear(PlayerShearEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getEntity().getLocation().getBlock(), IslandPermission.INTERACT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getEntity().getLocation().getBlock(), IslandPermission.INTERACT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getEntity().getLocation().getBlock(), IslandPermission.INTERACT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        if (event.getAttacker() instanceof Player player) {
            event.setCancelled(denied(player, event.getVehicle().getLocation().getBlock(), IslandPermission.BREAK));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        IslandFlag flag = explosionFlag(event.getEntityType());
        event.blockList().removeIf(block -> !explosionAllowed(block, flag));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !explosionAllowed(block, IslandFlag.EXPLOSION));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        event.setCancelled(!protection.checkSystemFlag(event.getToBlock(), liquidFlag(event.getBlock().getType())).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluidLevel(FluidLevelChangeEvent event) {
        event.setCancelled(!protection.checkSystemFlag(event.getBlock(), liquidFlag(event.getBlock().getType())).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        event.setCancelled(!protection.checkSystemFlag(event.getBlock(), IslandFlag.FIRE_SPREAD).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        event.setCancelled(!protection.checkSystemFlag(event.getBlock(), IslandFlag.FIRE_SPREAD).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        event.setCancelled(!protection.checkSystemFlag(event.getBlock(), IslandFlag.FIRE_SPREAD).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        event.setCancelled(!protection.checkSystemFlag(event.getBlock(), IslandFlag.LEAF_DECAY).allowed());
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getBlock().getType().name().contains("ICE")) {
            event.setCancelled(!protection.checkSystemFlag(event.getBlock(), IslandFlag.ICE_MELT).allowed());
        }
    }

    private boolean denied(Player player, Block block, IslandPermission permission) {
        PermissionResult result = protection.checkBlock(player.getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), permission, player.hasPermission("cloudislands.admin.bypass"));
        protection.islandAt(block).ifPresent(islandId -> Bukkit.getPluginManager().callEvent(new IslandPermissionCheckEvent(islandId, player.getUniqueId(), player, block, permission, result)));
        return !result.allowed();
    }

    private Player attackingPlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private IslandPermission interactionPermission(Material type) {
        String name = type.name();
        if (name.endsWith("_DOOR") || name.endsWith("_TRAPDOOR") || name.endsWith("_FENCE_GATE")) {
            return IslandPermission.USE_DOOR;
        }
        if (name.endsWith("_BUTTON")) {
            return IslandPermission.USE_BUTTON;
        }
        if (name.endsWith("_PRESSURE_PLATE")) {
            return IslandPermission.USE_PRESSURE_PLATE;
        }
        if (name.equals("LEVER") || name.equals("REDSTONE_WIRE") || name.endsWith("REPEATER") || name.endsWith("COMPARATOR")) {
            return IslandPermission.USE_REDSTONE;
        }
        if (name.equals("SPAWNER")) {
            return IslandPermission.USE_SPAWNER;
        }
        if (name.equals("ANVIL") || name.equals("CHIPPED_ANVIL") || name.equals("DAMAGED_ANVIL")) {
            return IslandPermission.USE_ANVIL;
        }
        if (name.equals("ENCHANTING_TABLE")) {
            return IslandPermission.USE_ENCHANT_TABLE;
        }
        if (name.equals("BREWING_STAND")) {
            return IslandPermission.USE_BREWING_STAND;
        }
        return IslandPermission.INTERACT;
    }

    private IslandFlag explosionFlag(EntityType type) {
        if (type == EntityType.CREEPER) {
            return IslandFlag.CREEPER_DAMAGE;
        }
        if (type == EntityType.PRIMED_TNT || type == EntityType.MINECART_TNT) {
            return IslandFlag.TNT_DAMAGE;
        }
        if (type == EntityType.WITHER || type == EntityType.WITHER_SKULL) {
            return IslandFlag.WITHER_DAMAGE;
        }
        return IslandFlag.EXPLOSION;
    }

    private boolean explosionAllowed(Block block, IslandFlag detailFlag) {
        return protection.checkSystemFlag(block, IslandFlag.EXPLOSION).allowed()
            && protection.checkSystemFlag(block, detailFlag).allowed();
    }

    private IslandFlag liquidFlag(Material type) {
        return type == Material.LAVA ? IslandFlag.LAVA_FLOW : IslandFlag.WATER_FLOW;
    }
}
