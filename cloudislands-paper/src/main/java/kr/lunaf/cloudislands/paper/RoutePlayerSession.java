package kr.lunaf.cloudislands.paper;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Fences delayed route work to the exact Player connection that requested it. */
public record RoutePlayerSession(UUID playerUuid, Player expectedPlayer) {
    public RoutePlayerSession {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(expectedPlayer, "expectedPlayer");
        if (!playerUuid.equals(expectedPlayer.getUniqueId())) {
            throw new IllegalArgumentException("playerUuid must match expectedPlayer");
        }
    }

    public static RoutePlayerSession capture(Player player) {
        Objects.requireNonNull(player, "player");
        return new RoutePlayerSession(player.getUniqueId(), player);
    }

    public boolean isCurrent(Player activePlayer) {
        return activePlayer == expectedPlayer && activePlayer.isOnline();
    }
}
