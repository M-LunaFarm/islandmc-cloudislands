package kr.lunaf.cloudislands.paper.session;

import org.bukkit.entity.Player;

/** Prevents delayed chat work from crossing a disconnect or reconnect boundary. */
final class ChatPlayerIdentityPolicy {
    private ChatPlayerIdentityPolicy() {
    }

    static boolean isCurrent(Player expectedPlayer, Player activePlayer) {
        return expectedPlayer != null
            && activePlayer == expectedPlayer
            && activePlayer.isOnline();
    }
}
