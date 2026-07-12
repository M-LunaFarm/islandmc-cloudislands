package kr.lunaf.cloudislands.paper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import io.papermc.paper.event.block.BlockBreakBlockEvent;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.common.protection.BlockSpreadPolicy;
import kr.lunaf.cloudislands.paper.event.IslandPermissionCheckEvent;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Fish;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LeashHitch;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.util.Vector;

public final class IslandProtectionListener implements Listener {
    private final ProtectionController protection;
    private final BlockDeltaReporter blockDeltas;
    private final long denyMessageCooldownMs;
    private final Map<IslandPermission, String> denyMessages;
    private final Map<UUID, Long> denyMessageTimes = new ConcurrentHashMap<>();

    public IslandProtectionListener(ProtectionController protection, BlockDeltaReporter blockDeltas) {
        this(protection, blockDeltas, 1000L);
    }

    public IslandProtectionListener(ProtectionController protection, BlockDeltaReporter blockDeltas, long denyMessageCooldownMs) {
        this(protection, blockDeltas, denyMessageCooldownMs, Map.of());
    }

    public IslandProtectionListener(ProtectionController protection, BlockDeltaReporter blockDeltas, long denyMessageCooldownMs, Map<IslandPermission, String> denyMessages) {
        this.protection = protection;
        this.blockDeltas = blockDeltas;
        this.denyMessageCooldownMs = Math.max(0L, denyMessageCooldownMs);
        this.denyMessages = denyMessages == null ? Map.of() : Map.copyOf(denyMessages);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        IslandPermission permission = event.getBlock().getType() == Material.SPAWNER ? IslandPermission.BREAK_SPAWNER : IslandPermission.BREAK;
        boolean blocked = denied(event.getPlayer(), event.getBlock(), permission);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.broken(islandId, event.getPlayer().getUniqueId(), event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        boolean blocked = denied(event.getPlayer(), event.getBlock(), IslandPermission.BUILD);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.placed(islandId, event.getPlayer().getUniqueId(), event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        boolean blocked = event.getReplacedBlockStates().stream().anyMatch(state -> denied(event.getPlayer(), state.getBlock(), IslandPermission.BUILD));
        event.setCancelled(blocked);
        if (!blocked) {
            event.getReplacedBlockStates().forEach(state ->
                protection.islandAt(state.getBlock()).ifPresent(islandId -> blockDeltas.placed(islandId, event.getPlayer().getUniqueId(), state.getBlock())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            event.setCancelled(denied(event.getPlayer(), event.getClickedBlock(), interactionPermission(event)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), entityInteractionPermission(event.getPlayer(), event.getRightClicked(), event.getHand())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), entityInteractionPermission(event.getPlayer(), event.getRightClicked(), event.getHand())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        boolean blocked = denied(event.getPlayer(), event.getBlock(), IslandPermission.PLACE_LIQUID);
        event.setCancelled(blocked);
        Material liquid = bucketLiquid(event.getBucket());
        if (!blocked && liquid != null) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.placed(islandId, event.getPlayer().getUniqueId(), liquid));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        boolean blocked = denied(event.getPlayer(), event.getBlock(), IslandPermission.BREAK_LIQUID);
        event.setCancelled(blocked);
        if (!blocked && (event.getBlock().getType() == Material.WATER || event.getBlock().getType() == Material.LAVA)) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.broken(islandId, event.getPlayer().getUniqueId(), event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEntity(PlayerBucketEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getEntity().getLocation().getBlock(), IslandPermission.PICKUP_ENTITY_BUCKET));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketDispense(BlockDispenseEvent event) {
        Material item = event.getItem().getType();
        if (item != Material.BUCKET && item != Material.WATER_BUCKET && item != Material.LAVA_BUCKET) {
            return;
        }
        Block target = dispenseTarget(event.getBlock(), event.getVelocity());
        if (!sameIsland(event.getBlock(), target)) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketDispenseCount(BlockDispenseEvent event) {
        Material item = event.getItem().getType();
        if (item != Material.BUCKET && item != Material.WATER_BUCKET && item != Material.LAVA_BUCKET) {
            return;
        }
        Block target = dispenseTarget(event.getBlock(), event.getVelocity());
        protection.islandAt(target).ifPresent(islandId -> {
            if (item == Material.BUCKET && (target.getType() == Material.WATER || target.getType() == Material.LAVA)) {
                blockDeltas.broken(islandId, target);
            } else if (item == Material.WATER_BUCKET) {
                blockDeltas.placed(islandId, Material.WATER);
            } else if (item == Material.LAVA_BUCKET) {
                blockDeltas.placed(islandId, Material.LAVA);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onTakeLecternBook(PlayerTakeLecternBookEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getLectern().getBlock(), IslandPermission.TAKE_LECTERN_BOOK));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpecialTeleport(PlayerTeleportEvent event) {
        String cause = event.getCause().name();
        IslandPermission permission = cause.equals("ENDER_PEARL")
            ? IslandPermission.ENDER_PEARL
            : cause.equals("CHORUS_FRUIT") ? IslandPermission.CHORUS_FRUIT : null;
        if (permission != null && event.getTo() != null) {
            event.setCancelled(denied(event.getPlayer(), event.getTo().getBlock(), permission));
        }
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
            IslandPermission permission = event.getEntity() instanceof Player ? IslandPermission.ATTACK_PLAYER
                : hangingPermission(event.getEntity(), IslandPermission.ATTACK_MOB);
            event.setCancelled(denied(player, event.getEntity().getLocation().getBlock(), permission));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player player) {
            event.setCancelled(denied(player, event.getEntity().getLocation().getBlock(), IslandPermission.ATTACK_MOB));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getRaid().getLocation().getBlock(), IslandPermission.TRIGGER_RAID));
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
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getArrow().getLocation().getBlock(), IslandPermission.PICKUP_ITEM));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Player player = attackingPlayer(event.getEntity());
        if (player == null) {
            return;
        }
        IslandPermission permission = switch (event.getEntityType()) {
            case FISHING_BOBBER -> IslandPermission.FISH;
            case TRIDENT -> IslandPermission.PICKUP_ITEM;
            case ENDER_PEARL -> IslandPermission.ENDER_PEARL;
            case WIND_CHARGE -> IslandPermission.WIND_CHARGE;
            default -> null;
        };
        if (permission != null) {
            event.setCancelled(denied(player, event.getEntity().getLocation().getBlock(), permission));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrostWalker(EntityBlockFormEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(denied(player, event.getBlock().getLocation().getBlock(), IslandPermission.BUILD));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        boolean blocked = (event.getPlayer() != null && denied(event.getPlayer(), event.getBlock(), IslandPermission.FERTILIZE))
            || event.getBlocks().stream().anyMatch(state -> !sameIsland(event.getBlock(), state.getBlock()));
        event.setCancelled(blocked);
        if (!blocked) {
            event.getBlocks().forEach(state -> reportBlockReplacement(state.getBlock(), state.getType()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        Block source = event.getLocation().getBlock();
        if (!protection.checkSystemFlag(source, IslandFlag.TREE_GROWTH, true).allowed()) {
            event.setCancelled(true);
            return;
        }
        if (event.isFromBonemeal()) {
            return;
        }
        boolean crossesBoundary = event.getBlocks().stream().anyMatch(state -> !sameIsland(source, state.getBlock()));
        event.setCancelled(crossesBoundary);
        if (!crossesBoundary) {
            event.getBlocks().forEach(state -> reportBlockReplacement(state.getBlock(), state.getType()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        IslandPermission permission = hangingPermission(event.getEntity(), IslandPermission.BUILD);
        boolean blocked = denied(event.getPlayer(), event.getBlock(), permission);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.entityPlaced(islandId, event.getEntity().getType()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = attackingPlayer(event.getRemover());
        if (player != null) {
            IslandPermission permission = hangingPermission(event.getEntity(), IslandPermission.BREAK);
            boolean blocked = denied(player, event.getEntity().getLocation().getBlock(), permission);
            event.setCancelled(blocked);
            if (!blocked) {
                protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityRemoved(islandId, event.getEntity().getType()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreakAny(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) {
            return;
        }
        protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityRemoved(islandId, event.getEntity().getType()));
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (protection.migrating(event.getLocation().getBlock())) {
            event.setCancelled(true);
            return;
        }
        protection.islandAt(event.getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityPlaced(islandId, event.getEntity().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        if (protection.migrating(event.getEntity().getLocation().getBlock())) {
            return;
        }
        protection.islandAt(event.getEntity().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityRemoved(islandId, event.getEntity().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDropItem(EntityDropItemEvent event) {
        if (event.getEntity() instanceof Chicken
            && event.getItemDrop().getItemStack().getType() == Material.EGG
            && !protection.checkSystemFlag(event.getEntity().getLocation().getBlock(), IslandFlag.EGG_LAY, true).allowed()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getRightClicked().getLocation().getBlock(), IslandPermission.INTERACT));
    }

    @EventHandler(ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getEntity().getLocation().getBlock(), IslandPermission.ANIMAL_SHEAR));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            event.setCancelled(denied(player, event.getEntity().getLocation().getBlock(), IslandPermission.ANIMAL_BREED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        Block target = event.getCaught() == null ? event.getHook().getLocation().getBlock() : event.getCaught().getLocation().getBlock();
        event.setCancelled(denied(event.getPlayer(), target, IslandPermission.FISH));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            event.setCancelled(denied(player, event.getVehicle().getLocation().getBlock(), IslandPermission.ENTITY_RIDE));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getEntity().getLocation().getBlock(), IslandPermission.LEASH));
    }

    @EventHandler(ignoreCancelled = true)
    public void onUnleash(PlayerUnleashEntityEvent event) {
        event.setCancelled(denied(event.getPlayer(), event.getEntity().getLocation().getBlock(), IslandPermission.LEASH));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        protection.islandAt(event.getVehicle().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityPlaced(islandId, event.getVehicle().getType()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = attackingPlayer(event.getAttacker());
        if (player == null) {
            protection.islandAt(event.getVehicle().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityRemoved(islandId, event.getVehicle().getType()));
            return;
        }
        boolean blocked = denied(player, event.getVehicle().getLocation().getBlock(), IslandPermission.BREAK);
        event.setCancelled(blocked);
        if (!blocked) {
            protection.islandAt(event.getVehicle().getLocation().getBlock()).ifPresent(islandId -> blockDeltas.entityRemoved(islandId, event.getVehicle().getType()));
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
        Player source = attackingPlayer(event.getEntity());
        if (source != null && (event.getEntityType() == EntityType.WIND_CHARGE || event.getEntityType() == EntityType.BREEZE_WIND_CHARGE)
            && denied(source, event.getLocation().getBlock(), IslandPermission.WIND_CHARGE)) {
            event.setCancelled(true);
            return;
        }
        IslandFlag flag = explosionFlag(event.getEntityType());
        event.blockList().removeIf(block -> !explosionAllowed(block, flag));
        event.blockList().forEach(block ->
            protection.islandAt(block).ifPresent(islandId -> blockDeltas.broken(islandId, block)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN) {
            boolean allowed = protection.checkSystemFlag(event.getBlock(), IslandFlag.ENDERMAN_GRIEF).allowed();
            event.setCancelled(!allowed);
            if (allowed) {
                protection.islandAt(event.getBlock()).ifPresent(islandId -> {
                    if (event.getBlock().getType() != Material.AIR) {
                        blockDeltas.broken(islandId, event.getBlock());
                    }
                    if (event.getTo() != Material.AIR) {
                        blockDeltas.placed(islandId, event.getTo());
                    }
                });
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !explosionAllowed(block, IslandFlag.EXPLOSION));
        event.blockList().forEach(block ->
            protection.islandAt(block).ifPresent(islandId -> blockDeltas.broken(islandId, block)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onFluid(BlockFromToEvent event) {
        if (!sameIsland(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
            return;
        }
        boolean allowed = protection.checkSystemFlag(event.getToBlock(), liquidFlag(event.getBlock().getType())).allowed();
        event.setCancelled(!allowed);
        if (allowed) {
            reportBlockReplacement(event.getToBlock(), event.getBlock().getType());
        }
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
        boolean allowed = protection.checkSystemFlag(event.getBlock(), IslandFlag.FIRE_SPREAD).allowed();
        event.setCancelled(!allowed);
        if (allowed) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.broken(islandId, event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (!sameIsland(event.getSource(), event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (BlockSpreadPolicy.fireSpread(event.getSource().getType().name(), event.getNewState().getType().name())) {
            event.setCancelled(!protection.checkSystemFlag(event.getBlock(), IslandFlag.FIRE_SPREAD).allowed());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpreadCount(BlockSpreadEvent event) {
        reportBlockReplacement(event.getBlock(), event.getNewState().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrowCount(BlockGrowEvent event) {
        if (event.getBlock().getType() != event.getNewState().getType()) {
            reportBlockReplacement(event.getBlock(), event.getNewState().getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDependentBlockBreak(BlockBreakBlockEvent event) {
        protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.broken(islandId, event.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        boolean allowed = protection.checkSystemFlag(event.getBlock(), IslandFlag.LEAF_DECAY).allowed();
        event.setCancelled(!allowed);
        if (allowed) {
            protection.islandAt(event.getBlock()).ifPresent(islandId -> blockDeltas.broken(islandId, event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (event.getBlock().getType().getKey().getKey().contains("ice")) {
            boolean allowed = protection.checkSystemFlag(event.getBlock(), IslandFlag.ICE_MELT).allowed();
            event.setCancelled(!allowed);
            if (allowed) {
                reportBlockReplacement(event.getBlock(), event.getNewState().getType());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        denyMessageTimes.remove(event.getPlayer().getUniqueId());
    }

    private boolean denied(Player player, Block block, IslandPermission permission) {
        PermissionResult result = protection.checkBlock(player.getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), permission, player.hasPermission("cloudislands.admin.bypass"));
        protection.islandAt(block).ifPresent(islandId -> kr.lunaf.cloudislands.paper.platform.event.PaperEvents.call(new IslandPermissionCheckEvent(islandId, player.getUniqueId(), player, block, permission, result)));
        boolean denied = !result.allowed();
        if (denied) {
            sendDenyMessage(player, permission);
        }
        return denied;
    }

    private void reportBlockReplacement(Block block, Material newType) {
        protection.islandAt(block).ifPresent(islandId -> {
            if (block.getType() != Material.AIR) {
                blockDeltas.broken(islandId, block);
            }
            if (newType != Material.AIR) {
                blockDeltas.placed(islandId, newType);
            }
        });
    }

    private Material bucketLiquid(Material bucket) {
        return switch (bucket) {
            case WATER_BUCKET -> Material.WATER;
            case LAVA_BUCKET -> Material.LAVA;
            default -> null;
        };
    }

    private Block dispenseTarget(Block source, Vector velocity) {
        double x = velocity.getX();
        double y = velocity.getY();
        double z = velocity.getZ();
        double max = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (max == 0.0D) {
            return source;
        }
        int dx = Math.abs(x) == max ? (int) Math.signum(x) : 0;
        int dy = Math.abs(y) == max ? (int) Math.signum(y) : 0;
        int dz = Math.abs(z) == max ? (int) Math.signum(z) : 0;
        return source.getRelative(dx, dy, dz);
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
        String configured = denyMessages.get(permission);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
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

    private IslandPermission interactionPermission(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.FARMLAND) {
            return IslandPermission.BUILD;
        }
        if (event.getItem() != null && event.getItem().getType() == Material.BRUSH) {
            return IslandPermission.BRUSH;
        }
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.TURTLE_EGG) {
            return IslandPermission.TURTLE_EGG_TRAMPLE;
        }
        return interactionPermission(event.getClickedBlock().getType());
    }

    private IslandPermission entityInteractionPermission(Player player, org.bukkit.entity.Entity entity, org.bukkit.inventory.EquipmentSlot hand) {
        Material held = player.getInventory().getItem(hand).getType();
        if (entity instanceof ItemFrame) {
            return IslandPermission.ITEM_FRAME;
        }
        if (entity instanceof LeashHitch) {
            return IslandPermission.LEASH;
        }
        if (held == Material.NAME_TAG) {
            return IslandPermission.NAME_ENTITY;
        }
        if ((held == Material.FLINT_AND_STEEL || held == Material.FIRE_CHARGE) && entity instanceof Creeper) {
            return IslandPermission.IGNITE_CREEPER;
        }
        if (held == Material.WATER_BUCKET && (entity instanceof Fish || entity instanceof Axolotl)) {
            return IslandPermission.PICKUP_ENTITY_BUCKET;
        }
        if (held == Material.SHEARS) {
            return IslandPermission.ANIMAL_SHEAR;
        }
        if (held.getKey().getKey().endsWith("_dye") && entity instanceof Sheep) {
            return IslandPermission.DYE_SHEEP;
        }
        if (held == Material.SADDLE && (entity instanceof AbstractHorse || entity instanceof Steerable)) {
            return IslandPermission.SADDLE_ENTITY;
        }
        if (entity instanceof Animals animals && animals.isBreedItem(player.getInventory().getItem(hand))) {
            return IslandPermission.ANIMAL_BREED;
        }
        if (entity instanceof AbstractVillager) {
            return IslandPermission.VILLAGER_TRADE;
        }
        if (entity instanceof Vehicle || entity instanceof AbstractHorse || entity instanceof Steerable) {
            return IslandPermission.ENTITY_RIDE;
        }
        return IslandPermission.INTERACT;
    }

    private static IslandPermission hangingPermission(org.bukkit.entity.Entity entity, IslandPermission fallback) {
        if (entity instanceof ItemFrame) {
            return IslandPermission.ITEM_FRAME;
        }
        if (entity instanceof LeashHitch) {
            return IslandPermission.LEASH;
        }
        if (entity.getType() == EntityType.PAINTING) {
            return IslandPermission.PAINTING;
        }
        return fallback;
    }

    private IslandPermission interactionPermission(Material type) {
        String key = type.getKey().getKey();
        if (key.equals("sculk_sensor") || key.equals("calibrated_sculk_sensor") || key.equals("sculk_shrieker")) {
            return IslandPermission.SCULK_SENSOR;
        }
        if (key.endsWith("_door") || key.endsWith("_trapdoor") || key.endsWith("_fence_gate")) {
            return IslandPermission.USE_DOOR;
        }
        if (key.endsWith("_button")) {
            return IslandPermission.USE_BUTTON;
        }
        if (key.endsWith("_pressure_plate")) {
            return IslandPermission.USE_PRESSURE_PLATE;
        }
        if (key.equals("lever") || key.equals("redstone_wire") || key.endsWith("repeater") || key.endsWith("comparator")) {
            return IslandPermission.USE_REDSTONE;
        }
        if (key.equals("spawner")) {
            return IslandPermission.USE_SPAWNER;
        }
        if (key.equals("anvil") || key.equals("chipped_anvil") || key.equals("damaged_anvil")) {
            return IslandPermission.USE_ANVIL;
        }
        if (key.equals("enchanting_table")) {
            return IslandPermission.USE_ENCHANT_TABLE;
        }
        if (key.equals("brewing_stand")) {
            return IslandPermission.USE_BREWING_STAND;
        }
        return IslandPermission.INTERACT;
    }

    private IslandFlag explosionFlag(EntityType type) {
        if (type == EntityType.CREEPER) {
            return IslandFlag.CREEPER_DAMAGE;
        }
        if (type == EntityType.TNT || type == EntityType.TNT_MINECART) {
            return IslandFlag.TNT_DAMAGE;
        }
        if (type == EntityType.WITHER || type == EntityType.WITHER_SKULL) {
            return IslandFlag.WITHER_DAMAGE;
        }
        if (type == EntityType.FIREBALL) {
            return IslandFlag.GHAST_FIREBALL;
        }
        return IslandFlag.EXPLOSION;
    }

    private boolean explosionAllowed(Block block, IslandFlag detailFlag) {
        return protection.checkSystemFlag(block, IslandFlag.EXPLOSION).allowed()
            && protection.checkSystemFlag(block, detailFlag).allowed();
    }

    private boolean sameIsland(Block source, Block target) {
        if (protection.migrating(source) || protection.migrating(target)) {
            return false;
        }
        Optional<UUID> sourceIsland = protection.islandAt(source);
        Optional<UUID> targetIsland = protection.islandAt(target);
        return sourceIsland.equals(targetIsland);
    }

    private IslandFlag liquidFlag(Material type) {
        return type == Material.LAVA ? IslandFlag.LAVA_FLOW : IslandFlag.WATER_FLOW;
    }

}
