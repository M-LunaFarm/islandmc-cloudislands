package kr.lunaf.cloudislands.paper.session;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamChatModeRegistry {
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();

    public boolean enabled(UUID playerUuid) {
        return playerUuid != null && enabledPlayers.contains(playerUuid);
    }

    public boolean toggle(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        if (enabledPlayers.remove(playerUuid)) {
            return false;
        }
        enabledPlayers.add(playerUuid);
        return true;
    }

    public boolean set(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return false;
        }
        if (enabled) {
            enabledPlayers.add(playerUuid);
        } else {
            enabledPlayers.remove(playerUuid);
        }
        return enabled;
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
