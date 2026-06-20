package kr.lunaf.cloudislands.paper.platform.player;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface PaperPlayerGateway {
    Player onlinePlayer(UUID playerUuid);

    boolean teleport(Player player, Location target);
}
