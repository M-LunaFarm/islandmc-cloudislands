package kr.lunaf.cloudislands.paper.command;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.IslandInfoMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMyIslandsMenu;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class IslandOverviewCommandHandler {
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final Runtime runtime;

    IslandOverviewCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand) {
        if (subcommand.equals("info") || subcommand.equals("정보")) {
            openInfoMenu(player);
            return true;
        }
        if (subcommand.equals("list") || subcommand.equals("my") || subcommand.equals("my-islands") || subcommand.equals("목록") || subcommand.equals("내섬")) {
            IslandMyIslandsMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        String actionId = action.actionId();
        Map<String, String> data = action.data();
        return switch (actionId) {
            case "island.info.open" -> {
                openInfoMenu(player);
                yield true;
            }
            case "island.list.open" -> {
                IslandMyIslandsMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
                yield true;
            }
            default -> false;
        };
    }

    private void openInfoMenu(Player player) {
        runtime.currentIsland(player, "섬 안에서만 정보를 확인할 수 있습니다.").ifPresent(islandId -> IslandInfoMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        MessageRenderer messagesFor(Player player);
    }
}
