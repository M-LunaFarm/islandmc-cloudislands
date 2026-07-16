package kr.lunaf.cloudislands.paper.session;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Fences an asynchronous profile load to the Player session that started it. */
record PlayerProfileSession(UUID playerUuid, Player expectedPlayer) {
    PlayerProfileSession {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(expectedPlayer, "expectedPlayer");
        if (!playerUuid.equals(expectedPlayer.getUniqueId())) {
            throw new IllegalArgumentException("playerUuid must match expectedPlayer");
        }
    }

    static PlayerProfileSession capture(Player player) {
        Objects.requireNonNull(player, "player");
        return new PlayerProfileSession(player.getUniqueId(), player);
    }

    boolean isCurrent(Player activePlayer) {
        return activePlayer == expectedPlayer && activePlayer.isOnline();
    }
}
