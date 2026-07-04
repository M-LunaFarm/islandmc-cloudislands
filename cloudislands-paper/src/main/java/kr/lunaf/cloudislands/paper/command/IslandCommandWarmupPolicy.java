package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import org.bukkit.Location;
import org.bukkit.World;

final class IslandCommandWarmupPolicy {
    static final String WARMUP_CANCELLED_MESSAGE_KEY = "island-command-warmup-cancelled";
    static final String WARMUP_PENDING_MESSAGE_KEY = "island-command-warmup-pending";
    static final String COMBAT_BLOCKED_MESSAGE_KEY = "island-command-combat-blocked";
    static final long COMBAT_LOCK_MILLIS = 10_000L;

    private final Map<UUID, PendingWarmup> pendingWarmups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> combatLockedUntil = new ConcurrentHashMap<>();

    boolean hasPending(UUID playerUuid) {
        return playerUuid != null && pendingWarmups.containsKey(playerUuid);
    }

    void start(UUID playerUuid, IslandCommandDelayPolicy.DelaySubject subject, BlockPosition position, TaskHandle task) {
        if (playerUuid == null || subject == null || position == null || task == null) {
            return;
        }
        Optional.ofNullable(pendingWarmups.put(playerUuid, new PendingWarmup(subject, position, task)))
            .ifPresent(PendingWarmup::cancelTask);
    }

    Optional<PendingWarmup> cancelOnMove(UUID playerUuid, BlockPosition position) {
        if (playerUuid == null || position == null) {
            return Optional.empty();
        }
        PendingWarmup pending = pendingWarmups.get(playerUuid);
        if (pending == null || pending.startPosition().equals(position)) {
            return Optional.empty();
        }
        return cancel(playerUuid);
    }

    Optional<PendingWarmup> cancel(UUID playerUuid) {
        if (playerUuid == null) {
            return Optional.empty();
        }
        PendingWarmup pending = pendingWarmups.remove(playerUuid);
        if (pending != null) {
            pending.cancelTask();
        }
        return Optional.ofNullable(pending);
    }

    boolean complete(UUID playerUuid) {
        return playerUuid != null && pendingWarmups.remove(playerUuid) != null;
    }

    void clear(UUID playerUuid) {
        cancel(playerUuid);
        if (playerUuid != null) {
            combatLockedUntil.remove(playerUuid);
        }
    }

    void markCombat(UUID playerUuid, long nowMillis) {
        if (playerUuid != null) {
            combatLockedUntil.put(playerUuid, nowMillis + COMBAT_LOCK_MILLIS);
        }
    }

    boolean combatBlocked(UUID playerUuid, long nowMillis) {
        if (playerUuid == null) {
            return false;
        }
        Long until = combatLockedUntil.get(playerUuid);
        if (until == null) {
            return false;
        }
        if (until <= nowMillis) {
            combatLockedUntil.remove(playerUuid, until);
            return false;
        }
        return true;
    }

    record PendingWarmup(IslandCommandDelayPolicy.DelaySubject subject, BlockPosition startPosition, TaskHandle task) {
        void cancelTask() {
            task.cancel();
        }
    }

    record BlockPosition(String worldName, int blockX, int blockY, int blockZ) {
        static BlockPosition from(Location location) {
            World world = location == null ? null : location.getWorld();
            String worldName = world == null ? "" : world.getName();
            return new BlockPosition(
                worldName,
                location == null ? 0 : location.getBlockX(),
                location == null ? 0 : location.getBlockY(),
                location == null ? 0 : location.getBlockZ()
            );
        }
    }
}
