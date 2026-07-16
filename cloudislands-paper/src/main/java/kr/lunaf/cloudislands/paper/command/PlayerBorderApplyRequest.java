package kr.lunaf.cloudislands.paper.command;

import java.util.Objects;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.PlayerConnectionSession;
import org.bukkit.entity.Player;

/** Fences a delayed personal world-border response to its connection, island, and request order. */
record PlayerBorderApplyRequest(
    PlayerConnectionSession playerSession,
    UUID islandId,
    long revision
) {
    PlayerBorderApplyRequest {
        Objects.requireNonNull(playerSession, "playerSession");
        Objects.requireNonNull(islandId, "islandId");
        if (revision <= 0L) {
            throw new IllegalArgumentException("revision must be positive");
        }
    }

    boolean isCurrent(Player activePlayer, UUID activeIslandId, long activeRevision) {
        return playerSession.isCurrent(activePlayer)
            && islandId.equals(activeIslandId)
            && revision == activeRevision;
    }
}
