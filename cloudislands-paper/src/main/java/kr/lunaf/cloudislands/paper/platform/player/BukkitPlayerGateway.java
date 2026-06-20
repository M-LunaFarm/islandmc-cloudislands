package kr.lunaf.cloudislands.paper.platform.player;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class BukkitPlayerGateway implements PaperPlayerGateway {
    @Override
    public Player onlinePlayer(UUID playerUuid) {
        return Bukkit.getPlayer(playerUuid);
    }

    @Override
    public boolean teleport(Player player, Location target) {
        return player != null && player.teleport(target);
    }
}
