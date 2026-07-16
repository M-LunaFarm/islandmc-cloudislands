package kr.lunaf.cloudislands.paper;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Fences delayed player work to the exact connection that initiated it. */
public record PlayerConnectionSession(UUID playerUuid, Player expectedPlayer) {
    public PlayerConnectionSession {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(expectedPlayer, "expectedPlayer");
        if (!playerUuid.equals(expectedPlayer.getUniqueId())) {
            throw new IllegalArgumentException("playerUuid must match expectedPlayer");
        }
    }

    public static PlayerConnectionSession capture(Player player) {
        Objects.requireNonNull(player, "player");
        return new PlayerConnectionSession(player.getUniqueId(), player);
    }

    public boolean isCurrent(Player activePlayer) {
        return activePlayer == expectedPlayer && activePlayer.isOnline();
    }
}
