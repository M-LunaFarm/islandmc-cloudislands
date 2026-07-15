package kr.lunaf.cloudislands.paper.cache;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Fences delayed migration work to the Player session that requested it. */
record MigrationPlayerSession(UUID playerUuid, Player expectedPlayer) {
    MigrationPlayerSession {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(expectedPlayer, "expectedPlayer");
        if (!playerUuid.equals(expectedPlayer.getUniqueId())) {
            throw new IllegalArgumentException("playerUuid must match expectedPlayer");
        }
    }

    static MigrationPlayerSession capture(Player player) {
        Objects.requireNonNull(player, "player");
        return new MigrationPlayerSession(player.getUniqueId(), player);
    }

    boolean isCurrent(Player activePlayer) {
        return activePlayer == expectedPlayer && activePlayer.isOnline();
    }
}
