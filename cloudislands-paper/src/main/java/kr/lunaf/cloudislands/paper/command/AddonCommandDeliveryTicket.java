package kr.lunaf.cloudislands.paper.command;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Fences an asynchronous addon command result to its original plugin and player lifecycle. */
record AddonCommandDeliveryTicket(
    Plugin expectedPlugin,
    long lifecycleGeneration,
    Player expectedPlayer,
    UUID playerUuid
) {
    AddonCommandDeliveryTicket {
        Objects.requireNonNull(expectedPlugin, "expectedPlugin");
        Objects.requireNonNull(expectedPlayer, "expectedPlayer");
        Objects.requireNonNull(playerUuid, "playerUuid");
    }

    boolean isCurrent(Plugin activePlugin, long activeGeneration, Player activePlayer) {
        return activePlugin == expectedPlugin
            && activeGeneration == lifecycleGeneration
            && activePlayer == expectedPlayer
            && activePlayer.isOnline();
    }
}
