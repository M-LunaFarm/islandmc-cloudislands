package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.session.PlayerFlightPreferenceRegistry;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class PlayerIslandFlightService {
    private final ProtectionController protection;
    private final PlayerFlightPreferenceRegistry preferences;
    private final AdminFlightOverrides adminOverrides;

    public PlayerIslandFlightService(ProtectionController protection, PlayerFlightPreferenceRegistry preferences, AdminFlightOverrides adminOverrides) {
        this.protection = protection;
        this.preferences = preferences;
        this.adminOverrides = adminOverrides;
    }

    public boolean preferenceEnabled(Player player) {
        return preferences.enabled(player);
    }

    public boolean preferenceKnown(java.util.UUID playerUuid) {
        return preferences.known(playerUuid);
    }

    public void rememberPreference(java.util.UUID playerUuid, boolean enabled) {
        preferences.remember(playerUuid, enabled);
    }

    public java.util.UUID beginUpdate(java.util.UUID playerUuid) {
        return preferences.beginUpdate(playerUuid);
    }

    public boolean finishUpdate(java.util.UUID playerUuid, java.util.UUID updateId) {
        return preferences.finishUpdate(playerUuid, updateId);
    }

    public boolean updateCurrent(java.util.UUID playerUuid, java.util.UUID updateId) {
        return preferences.updateCurrent(playerUuid, updateId);
    }

    public boolean canEnable(Player player) {
        return player != null && eligibleRegion(player, player.getLocation().getBlock());
    }

    public void applyPreference(Player player, boolean enabled) {
        if (player == null) {
            return;
        }
        preferences.remember(player.getUniqueId(), enabled);
        refresh(player, player.getLocation().getBlock());
        if (enabled && preferences.managed(player) && player.getAllowFlight()) {
            player.setFlying(true);
        }
    }

    public void refresh(Player player, Block block) {
        if (player == null || block == null || creativeFlight(player)) {
            return;
        }
        boolean adminAllowed = adminOverrides != null && adminOverrides.enabled(player);
        boolean personalAllowed = personalFlightAllowed(player, block);
        if (personalAllowed) {
            if (PlayerFlightOwnershipPolicy.claim(player.getAllowFlight(), true)) {
                player.setAllowFlight(true);
                preferences.markManaged(player);
            }
            return;
        }
        if (preferences.managed(player)) {
            preferences.clearManaged(player);
            if (PlayerFlightOwnershipPolicy.revoke(true, false, adminAllowed)) {
                if (player.isFlying()) {
                    player.setFlying(false);
                }
                player.setAllowFlight(false);
            }
        }
    }

    public boolean managedAndDenied(Player player) {
        return player != null
            && preferences.managed(player)
            && !personalFlightAllowed(player, player.getLocation().getBlock())
            && (adminOverrides == null || !adminOverrides.enabled(player));
    }

    public void clear(Player player) {
        if (player == null) {
            return;
        }
        boolean cloudManaged = preferences.managed(player) || (adminOverrides != null && adminOverrides.enabled(player));
        if (cloudManaged && !creativeFlight(player)) {
            if (player.isFlying()) {
                player.setFlying(false);
            }
            player.setAllowFlight(false);
        }
        if (adminOverrides != null) {
            adminOverrides.clear(player.getUniqueId());
        }
        preferences.forget(player.getUniqueId());
    }

    public void clearAll(Iterable<? extends Player> players) {
        if (players != null) {
            players.forEach(this::clear);
        }
        preferences.clearAll();
    }

    private boolean personalFlightAllowed(Player player, Block block) {
        return preferences.enabled(player) && eligibleRegion(player, block);
    }

    private boolean eligibleRegion(Player player, Block block) {
        return protection.islandAt(block).isPresent()
            && protection.checkSystemFlag(block, IslandFlag.FLY).allowed()
            && protection.checkBlock(player.getUniqueId(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), IslandPermission.FLY).allowed();
    }

    private static boolean creativeFlight(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }
}
