package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

public final class IslandGameplayFlagListener implements Listener {
    private final ProtectionController protection;

    public IslandGameplayFlagListener(ProtectionController protection) {
        this.protection = protection;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (protection.islandAt(player.getLocation().getBlock()).isEmpty()) {
            return;
        }
        boolean allowed = islandFlagAllowed(player.getLocation().getBlock(), IslandFlag.FLY);
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
        event.setCancelled(protection.islandAt(player.getLocation().getBlock()).isPresent() && !islandFlagAllowed(player.getLocation().getBlock(), IslandFlag.FLY));
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
        IslandFlag flag = spawnFlag(event);
        if (flag != null && protection.islandAt(event.getLocation().getBlock()).isPresent() && !islandFlagAllowed(event.getLocation().getBlock(), flag)) {
            event.setCancelled(true);
        }
    }

    private IslandFlag spawnFlag(CreatureSpawnEvent event) {
        if (event.getEntity() instanceof Monster) {
            return IslandFlag.MONSTER_SPAWN;
        }
        if (event.getEntity() instanceof Animals) {
            return IslandFlag.ANIMAL_SPAWN;
        }
        return null;
    }

    private boolean islandFlagAllowed(Block block, IslandFlag flag) {
        return protection.islandAt(block).isPresent() && protection.checkSystemFlag(block, flag).allowed();
    }
}
