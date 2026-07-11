package kr.lunaf.cloudislands.paper.command;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PendingWarehouseOperations {
    private final Set<UUID> players = ConcurrentHashMap.newKeySet();

    boolean acquire(UUID playerUuid) {
        return players.add(playerUuid);
    }

    void release(UUID playerUuid) {
        players.remove(playerUuid);
    }
}
