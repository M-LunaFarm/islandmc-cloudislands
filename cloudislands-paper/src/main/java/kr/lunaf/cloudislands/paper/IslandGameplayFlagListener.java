package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class IslandGameplayFlagListener implements Listener {
    private final ProtectionController protection;
    private final MessageRenderer messages;

    public IslandGameplayFlagListener(ProtectionController protection) {
        this(protection, null);
    }

    public IslandGameplayFlagListener(ProtectionController protection, MessageRenderer messages) {
        this.protection = protection;
        this.messages = messages;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block block = event.getTo() == null ? player.getLocation().getBlock() : event.getTo().getBlock();
        boolean allowed = protection.islandAt(block).isPresent() && islandFlagAllowed(block, IslandFlag.FLY);
        player.setAllowFlight(allowed);
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean denied = protection.islandAt(player.getLocation().getBlock()).isPresent() && !islandFlagAllowed(player.getLocation().getBlock(), IslandFlag.FLY);
        event.setCancelled(denied);
        if (denied) {
            player.sendActionBar(Component.text(message("flag-fly-denied", "이 섬에서는 비행할 수 없습니다.")));
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        updateFlight(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearManagedFlight(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!islandFlagAllowed(event.getEntity().getLocation().getBlock(), IslandFlag.KEEP_INVENTORY)) {
            return;
        }
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (protection.islandAt(event.getLocation().getBlock()).isPresent() && !islandFlagAllowed(event.getLocation().getBlock(), IslandFlag.MOB_SPAWN)) {
            event.setCancelled(true);
            return;
        }
        IslandFlag flag = spawnFlag(event);
        if (flag != null && protection.islandAt(event.getLocation().getBlock()).isPresent() && !islandFlagAllowed(event.getLocation().getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (attackingPlayer(event.getDamager()) == null || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Block block = victim.getLocation().getBlock();
        if (protection.islandAt(block).isPresent() && !islandFlagAllowed(block, IslandFlag.PVP)) {
            event.setCancelled(true);
            Player attacker = attackingPlayer(event.getDamager());
            if (attacker != null) {
                attacker.sendActionBar(Component.text(message("flag-pvp-denied", "이 섬에서는 PVP가 비활성화되어 있습니다.")));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.ENDERMAN && protection.islandAt(event.getBlock()).isPresent() && !islandFlagAllowed(event.getBlock(), IslandFlag.ENDERMAN_GRIEF)) {
            event.setCancelled(true);
        }
    }

    private IslandFlag spawnFlag(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster) {
            return IslandFlag.MONSTER_SPAWN;
        }
        if (event.getEntity() instanceof Animals || event.getEntity() instanceof WaterMob || event.getEntity() instanceof Ambient) {
            return IslandFlag.ANIMAL_SPAWN;
        }
        return null;
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

    private boolean islandFlagAllowed(Block block, IslandFlag flag) {
        return protection.islandAt(block).isPresent() && protection.checkSystemFlag(block, flag).allowed();
    }

    private String message(String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private void updateFlight(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Block block = player.getLocation().getBlock();
        boolean allowed = protection.islandAt(block).isPresent() && islandFlagAllowed(block, IslandFlag.FLY);
        player.setAllowFlight(allowed);
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
        }
    }

    private void clearManagedFlight(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }
}
