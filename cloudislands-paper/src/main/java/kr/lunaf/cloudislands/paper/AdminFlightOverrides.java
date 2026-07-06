package kr.lunaf.cloudislands.paper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class AdminFlightOverrides {
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    public void set(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return;
        }
        if (enabled) {
            enabledPlayers.add(playerUuid);
        } else {
            enabledPlayers.remove(playerUuid);
        }
    }

    public boolean enabled(Player player) {
        return player != null && enabledPlayers.contains(player.getUniqueId());
    }

    public void clear(UUID playerUuid) {
        if (playerUuid != null) {
            enabledPlayers.remove(playerUuid);
        }
    }

    public void clearAll() {
        enabledPlayers.clear();
    }
}
