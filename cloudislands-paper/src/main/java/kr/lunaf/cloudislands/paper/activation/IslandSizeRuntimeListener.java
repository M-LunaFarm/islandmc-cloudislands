package kr.lunaf.cloudislands.paper.activation;

import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.event.IslandLimitChangeEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Keeps active protection, scan, and snapshot bounds synchronized with Core size mutations. */
public final class IslandSizeRuntimeListener implements Listener {
    private final ActiveIslandRegistry activeIslands;
    private final ShardWorldManager shardWorldManager;
    private final ProtectionController protection;
    private final Logger logger;

    public IslandSizeRuntimeListener(
        ActiveIslandRegistry activeIslands,
        ShardWorldManager shardWorldManager,
        ProtectionController protection,
        Logger logger
    ) {
        this.activeIslands = activeIslands;
        this.shardWorldManager = shardWorldManager;
        this.protection = protection;
        this.logger = logger == null ? Logger.getLogger(IslandSizeRuntimeListener.class.getName()) : logger;
    }

    @EventHandler
    public void onLimitChange(IslandLimitChangeEvent event) {
        if (event == null || !sizeLimit(event.limitKey())) {
            return;
        }
        synchronize(event.islandId(), event.value());
    }

    SyncResult synchronize(UUID islandId, long requestedSize) {
        ActiveIslandRegistry.ActiveIsland current = activeIslands.find(islandId).orElse(null);
        if (current == null) {
            return SyncResult.NOT_ACTIVE;
        }
        if (requestedSize <= 0L || requestedSize > Integer.MAX_VALUE || !shardWorldManager.supportsIslandSize((int) requestedSize)) {
            protection.markMigrating(islandId);
            logger.severe(
                "CloudIslands refused unsafe live island resize for " + islandId
                    + ": requested=" + requestedSize + " cellSize=" + shardWorldManager.cellSize()
                    + ". The island is fenced until its Core size is corrected."
            );
            return SyncResult.FENCED_UNSAFE_SIZE;
        }
        ActiveIslandRegistry.ActiveIsland resized = activeIslands.resize(islandId, (int) requestedSize).orElse(null);
        if (resized == null) {
            return SyncResult.NOT_ACTIVE;
        }
        protection.registerIsland(
            resized.islandId(),
            resized.worldName(),
            resized.originX(),
            resized.originZ(),
            resized.islandSize(),
            resized.cellX(),
            resized.cellZ()
        );
        protection.clearMigrating(islandId);
        return SyncResult.APPLIED;
    }

    private boolean sizeLimit(String limitKey) {
        if (limitKey == null) {
            return false;
        }
        String normalized = limitKey.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("SIZE") || normalized.equals("ISLAND_SIZE");
    }

    enum SyncResult {
        APPLIED,
        NOT_ACTIVE,
        FENCED_UNSAFE_SIZE
    }
}
