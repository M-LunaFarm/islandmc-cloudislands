package kr.lunaf.cloudislands.paper.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeamChatModeRegistry {
    public enum Mode {
        GLOBAL,
        ISLAND,
        TEAM
    }

    private final Map<UUID, Mode> playerModes = new ConcurrentHashMap<>();

    public boolean enabled(UUID playerUuid) {
        return mode(playerUuid) == Mode.TEAM;
    }

    public boolean islandEnabled(UUID playerUuid) {
        return mode(playerUuid) == Mode.ISLAND;
    }

    public Mode mode(UUID playerUuid) {
        return playerUuid == null ? Mode.GLOBAL : playerModes.getOrDefault(playerUuid, Mode.GLOBAL);
    }

    public boolean toggle(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        if (enabled(playerUuid)) {
            playerModes.remove(playerUuid);
            return false;
        }
        playerModes.put(playerUuid, Mode.TEAM);
        return true;
    }

    public boolean toggleIsland(UUID playerUuid) {
        if (playerUuid == null) {
            return false;
        }
        if (islandEnabled(playerUuid)) {
            playerModes.remove(playerUuid);
            return false;
        }
        playerModes.put(playerUuid, Mode.ISLAND);
        return true;
    }

    public boolean set(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return false;
        }
        if (enabled) {
            playerModes.put(playerUuid, Mode.TEAM);
        } else if (enabled(playerUuid)) {
            playerModes.remove(playerUuid);
        }
        return enabled;
    }

    public boolean setIsland(UUID playerUuid, boolean enabled) {
        if (playerUuid == null) {
            return false;
        }
        if (enabled) {
            playerModes.put(playerUuid, Mode.ISLAND);
        } else if (islandEnabled(playerUuid)) {
            playerModes.remove(playerUuid);
        }
        return enabled;
    }

    public void clear(UUID playerUuid) {
        if (playerUuid != null) {
            playerModes.remove(playerUuid);
        }
    }

    public void clearAll() {
        playerModes.clear();
    }
}
