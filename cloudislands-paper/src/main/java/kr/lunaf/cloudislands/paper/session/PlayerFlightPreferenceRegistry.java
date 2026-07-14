package kr.lunaf.cloudislands.paper.session;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class PlayerFlightPreferenceRegistry {
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> knownPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> managedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();

    public void remember(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return;
        }
        if (enabled) {
            enabledPlayers.add(playerUuid);
        } else {
            enabledPlayers.remove(playerUuid);
        }
        knownPlayers.add(playerUuid);
    }

    public boolean enabled(Player player) {
        return player != null && enabled(player.getUniqueId());
    }

    public boolean enabled(UUID playerUuid) {
        return playerUuid != null && enabledPlayers.contains(playerUuid);
    }

    public boolean known(UUID playerUuid) {
        return playerUuid != null && knownPlayers.contains(playerUuid);
    }

    public boolean beginUpdate(UUID playerUuid) {
        return playerUuid != null && pendingUpdates.add(playerUuid);
    }

    public void finishUpdate(UUID playerUuid) {
        if (playerUuid != null) {
            pendingUpdates.remove(playerUuid);
        }
    }

    public boolean managed(Player player) {
        return player != null && managed(player.getUniqueId());
    }

    public boolean managed(UUID playerUuid) {
        return playerUuid != null && managedPlayers.contains(playerUuid);
    }

    public void markManaged(Player player) {
        if (player != null) {
            markManaged(player.getUniqueId());
        }
    }

    public void markManaged(UUID playerUuid) {
        if (playerUuid != null) {
            managedPlayers.add(playerUuid);
        }
    }

    public void clearManaged(Player player) {
        if (player != null) {
            clearManaged(player.getUniqueId());
        }
    }

    public void clearManaged(UUID playerUuid) {
        if (playerUuid != null) {
            managedPlayers.remove(playerUuid);
        }
    }

    public void forget(UUID playerUuid) {
        if (playerUuid != null) {
            enabledPlayers.remove(playerUuid);
            knownPlayers.remove(playerUuid);
            managedPlayers.remove(playerUuid);
            pendingUpdates.remove(playerUuid);
        }
    }

    public void clearAll() {
        enabledPlayers.clear();
        knownPlayers.clear();
        managedPlayers.clear();
        pendingUpdates.clear();
    }
}
