package kr.lunaf.cloudislands.paper.command;

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
    private final IslandTargetResolver targetResolver;
    private final Runtime runtime;

    IslandOverviewCommandHandler(Plugin plugin, CoreApiClient coreApiClient, Runtime runtime) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.targetResolver = new IslandTargetResolver(coreApiClient);
        this.runtime = runtime;
    }

    boolean handleCommand(Player player, String subcommand, String[] args) {
        if (subcommand.equals("info") || subcommand.equals("show") || subcommand.equals("정보")) {
            if (args.length > 1) {
                openTargetInfo(player, args[1]);
            } else {
                openInfoMenu(player);
            }
            return true;
        }
        if (subcommand.equals("info-target")) {
            if (args.length < 2) {
                openInfoMenu(player);
            } else {
                openTargetInfo(player, args[1]);
            }
            return true;
        }
        if (subcommand.equals("list") || subcommand.equals("my") || subcommand.equals("my-islands") || subcommand.equals("목록") || subcommand.equals("내섬")) {
            IslandMyIslandsMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
            return true;
        }
        if (subcommand.equals("select") || subcommand.equals("switch") || subcommand.equals("선택") || subcommand.equals("섬선택")) {
            if (args.length < 2) {
                runtime.message(player, runtime.routeMessage("overview-selection-required", "선택할 섬 또는 플레이어를 입력해주세요."));
                return true;
            }
            selectIsland(player, args[1]);
            return true;
        }
        return false;
    }

    boolean handleGuiAction(Player player, GuiAction action) {
        if (action instanceof GuiAction.InfoOpen) {
            openInfoMenu(player);
            return true;
        }
        if (action instanceof GuiAction.IslandListOpen) {
            IslandMyIslandsMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player));
            return true;
        }
        return false;
    }

    private void openInfoMenu(Player player) {
        runtime.currentIsland(player, runtime.routeMessage("overview-info-island-required", "섬 안에서만 정보를 확인할 수 있습니다.")).ifPresent(islandId -> IslandInfoMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void openTargetInfo(Player player, String target) {
        targetResolver.resolve(target)
            .thenAccept(islandId -> IslandInfoMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)))
            .exceptionally(error -> {
                runtime.message(player, runtime.routeMessage("overview-target-not-found", "정보를 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                return null;
            });
    }

    private void selectIsland(Player player, String target) {
        targetResolver.resolve(target)
            .thenCompose(islandId -> coreApiClient.playerProfileCommands().selectPrimaryIsland(player.getUniqueId(), islandId))
            .thenAccept(profile -> runtime.message(player, runtime.routeMessage("overview-island-selected", "기본 섬을 선택했습니다.")))
            .exceptionally(error -> {
                runtime.message(player, runtime.routeMessage("overview-island-select-failed", "소속된 섬만 기본 섬으로 선택할 수 있습니다."));
                return null;
            });
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        String routeMessage(String key, String fallback);

        void message(Player player, String message);

        MessageRenderer messagesFor(Player player);
    }
}
