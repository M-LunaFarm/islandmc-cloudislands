package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class PaperOnlinePlayer {
    private PaperOnlinePlayer() {
    }

    static void run(Plugin plugin, UUID playerUuid, Consumer<Player> action) {
        if (plugin == null || playerUuid == null || action == null) {
            return;
        }
        PaperSchedulers.run(plugin, () -> {
            Player player = plugin.getServer().getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        });
    }
}
