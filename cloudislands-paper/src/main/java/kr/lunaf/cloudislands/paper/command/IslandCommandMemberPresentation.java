package kr.lunaf.cloudislands.paper.command;

import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.gui.IslandBanMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMemberMenu;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandCommandMemberPresentation {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final ProtectionController protection;
    private final IslandCommandMessenger messages;
    private final IslandCommandIslandContext islandContext;
    private final IslandRoutingCommandHandler routingCommands;

    IslandCommandMemberPresentation(
        Plugin plugin,
        CoreApiClient coreApiClient,
        ProtectionController protection,
        IslandCommandMessenger messages,
        IslandCommandIslandContext islandContext,
        IslandRoutingCommandHandler routingCommands
    ) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.protection = protection;
        this.messages = messages;
        this.islandContext = islandContext;
        this.routingCommands = routingCommands;
    }

    void openMemberMenu(Player player) {
        openMemberMenu(player, 0);
    }

    void openMemberMenu(Player player, int page) {
        islandContext.currentIsland(player, "섬 안에서만 멤버 메뉴를 열 수 있습니다.")
            .ifPresent(islandId -> IslandMemberMenu.open(plugin, coreApiClient, player, islandId, messages.messagesFor(player), page));
    }

    void openBanMenu(Player player) {
        islandContext.currentIsland(player, "섬 안에서만 밴 목록을 확인할 수 있습니다.")
            .ifPresent(islandId -> IslandBanMenu.open(plugin, coreApiClient, player, islandId, messages.messagesFor(player)));
    }

    boolean moveVisitorToFallback(UUID islandId, UUID targetUuid, String successMessage, String failureMessage) {
        Player targetPlayer = plugin.getServer().getPlayer(targetUuid);
        if (targetPlayer == null) {
            return false;
        }
        UUID targetIslandId = protection.islandAt(targetPlayer.getLocation().getBlock()).orElse(null);
        if (!islandId.equals(targetIslandId)) {
            return false;
        }
        routingCommands.connectPlayerToFallback(targetPlayer, successMessage, failureMessage);
        return true;
    }
}
