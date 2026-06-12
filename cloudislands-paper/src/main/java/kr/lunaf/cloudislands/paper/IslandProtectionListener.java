package kr.lunaf.cloudislands.paper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.paper.event.IslandPermissionCheckEvent;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import net.kyori.adventure.text.Component;
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
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

public final class IslandProtectionListener implements Listener {
    private final ProtectionController protection;
    private final BlockDeltaReporter blockDeltas;
    private final long denyMessageCooldownMs;
    private final Map<UUID, Long> denyMessageTimes = new ConcurrentHashMap<>();

    public IslandProtectionListener(ProtectionController protection, BlockDeltaReporter blockDeltas) {
        this(protection, blockDeltas, 1000L);
    }

    public IslandProtectionListener(ProtectionController protection, BlockDeltaReporter blockDeltas, long denyMessageCooldownMs) {
        this.protection = protection;
        this.blockDeltas = blockDeltas;
        this.denyMessageCooldownMs = Math.max(0L, denyMessageCooldownMs);
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
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), IslandPermission.INTERACT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), IslandPermission.INTERACT));
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
    public void onInventoryDrag(InventoryDragEvent event) {
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
    public void onHangingPlace(HangingPlaceEvent event) {
        boolean blocked = denied(event.getPlayer(), event.getBlock(), IslandPermission.BUILD);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.entityPlaced(islandId, event.getEntity().getType()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = attackingPlayer(event.getRemover());
        if (player != null) {
            boolean blocked = denied(player, event.getEntity().getLocation().getBlock(), IslandPermission.BREAK);
            event.setCancelled(blocked);
            if (!blocked) {
                protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityRemoved(islandId, event.getEntity().getType()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (event.getPlayer() == null) {
            return;
        }
        boolean blocked = denied(event.getPlayer(), event.getBlock(), IslandPermission.BUILD);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.entityPlaced(islandId, event.getEntity().getType()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityRemoved(islandId, event.getEntity().getType()));
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
        Player player = attackingPlayer(event.getAttacker());
        if (player != null) {
            event.setCancelled(denied(player, event.getVehicle().getLocation().getBlock(), IslandPermission.BREAK));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.setCancelled(event.getBlocks().stream().anyMatch(block -> !sameIsland(block, block.getRelative(event.getDirection()))));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.setCancelled(event.getBlocks().stream().anyMatch(block -> !sameIsland(block, block.getRelative(event.getDirection().getOppositeFace()))));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (event.getSource().getLocation() != null && event.getDestination().getLocation() != null) {
            event.setCancelled(!sameIsland(event.getSource().getLocation().getBlock(), event.getDestination().getLocation().getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        IslandFlag flag = explosionFlag(event.getEntityType());
        event.blockList().removeIf(block -> !explosionAllowed(block, flag));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN) {
            event.setCancelled(!protection.checkSystemFlag(event.getBlock(), IslandFlag.ENDERMAN_GRIEF).allowed());
        }
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
        boolean denied = !result.allowed();
        if (denied) {
            sendDenyMessage(player, permission);
        }
        return denied;
    }

    private void sendDenyMessage(Player player, IslandPermission permission) {
        long now = System.currentTimeMillis();
        Long last = denyMessageTimes.get(player.getUniqueId());
        if (last != null && now - last < denyMessageCooldownMs) {
            return;
        }
        denyMessageTimes.put(player.getUniqueId(), now);
        player.sendActionBar(Component.text(denyMessage(permission)));
    }

    private String denyMessage(IslandPermission permission) {
        return switch (permission) {
            case BUILD, BREAK, PLACE_LIQUID, BREAK_LIQUID -> "이 섬에서 블록을 변경할 권한이 없습니다.";
            case OPEN_CONTAINER -> "이 섬에서 보관함을 열 권한이 없습니다.";
            case ATTACK_PLAYER, ATTACK_MOB -> "이 섬에서 대상을 공격할 권한이 없습니다.";
            case PICKUP_ITEM, DROP_ITEM -> "이 섬에서 아이템을 옮길 권한이 없습니다.";
            default -> "이 섬에서 사용할 권한이 없습니다.";
        };
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

    private boolean sameIsland(Block source, Block target) {
        Optional<UUID> sourceIsland = protection.islandAt(source);
        Optional<UUID> targetIsland = protection.islandAt(target);
        return sourceIsland.equals(targetIsland);
    }

    private IslandFlag liquidFlag(Material type) {
        return type == Material.LAVA ? IslandFlag.LAVA_FLOW : IslandFlag.WATER_FLOW;
    }
}
