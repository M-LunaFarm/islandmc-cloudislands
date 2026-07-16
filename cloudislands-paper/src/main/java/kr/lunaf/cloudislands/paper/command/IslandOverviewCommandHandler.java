package kr.lunaf.cloudislands.paper.command;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.paper.gui.GuiAction;
import kr.lunaf.cloudislands.paper.gui.GuiSession;
import kr.lunaf.cloudislands.paper.gui.GuiSessions;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
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
        if (action instanceof GuiAction.IslandListPage page) {
            IslandMyIslandsMenu.open(plugin, coreApiClient, player, runtime.messagesFor(player), page.page());
            return true;
        }
        if (action instanceof GuiAction.SelectIslandTarget selectTarget) {
            selectIsland(player, selectTarget.target());
            return true;
        }
        return false;
    }

    private void openInfoMenu(Player player) {
        runtime.currentIsland(player, runtime.routeMessage("overview-info-island-required", "섬 안에서만 정보를 확인할 수 있습니다.")).ifPresent(islandId -> IslandInfoMenu.open(plugin, coreApiClient, player, islandId, runtime.messagesFor(player)));
    }

    private void openTargetInfo(Player player, String target) {
        UUID playerUuid = player.getUniqueId();
        MessageRenderer messages = runtime.messagesFor(player);
        GuiSession session = GuiSessions.begin(player, "island.info-target");
        GuiStateMenus.openLoading(plugin, player, session, messages,
            runtime.routeMessage("overview-target-loading", "섬 정보를 찾는 중입니다."));
        targetResolver.resolve(target)
            .thenAccept(islandId -> PaperOnlinePlayer.run(plugin, playerUuid, activePlayer -> {
                if (GuiSessions.isCurrent(activePlayer, session)) {
                    IslandInfoMenu.open(plugin, coreApiClient, activePlayer, islandId, messages);
                }
            }))
            .exceptionally(error -> {
                PaperOnlinePlayer.run(plugin, playerUuid, activePlayer -> GuiStateMenus.openError(plugin, activePlayer, session, messages,
                    runtime.routeMessage("overview-target-error-title", "섬 정보"),
                    runtime.routeMessage("overview-target-not-found", "정보를 확인할 섬 또는 플레이어를 찾지 못했습니다."),
                    "island.info.open", "island.main.open"));
                return null;
            });
    }

    private void selectIsland(Player player, String target) {
        UUID actorUuid = player.getUniqueId();
        coreApiClient.playerProfileCommands().reservePrimaryIslandSelection(actorUuid)
            .thenCombine(targetResolver.resolve(target), SelectionRequest::new)
            .thenCompose(request -> coreApiClient.playerProfileCommands().selectPrimaryIsland(actorUuid, request.islandId(), request.revision()))
            .thenAccept(profile -> deliverMessage(player, runtime.routeMessage("overview-island-selected", "기본 섬을 선택했습니다.")))
            .exceptionally(error -> {
                if (!superseded(error)) {
                    deliverMessage(player, runtime.routeMessage("overview-island-select-failed", "소속된 섬만 기본 섬으로 선택할 수 있습니다."));
                }
                return null;
            });
    }

    private void deliverMessage(Player player, String message) {
        PaperOnlinePlayer.run(plugin, player.getUniqueId(), activePlayer -> {
            if (activePlayer == player) {
                runtime.message(activePlayer, message);
            }
        });
    }

    private static boolean superseded(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current instanceof CoreApiException exception && exception.code().equals("ISLAND_SELECTION_SUPERSEDED");
    }

    private record SelectionRequest(long revision, UUID islandId) {
    }

    interface Runtime {
        Optional<UUID> currentIsland(Player player, String missingMessage);

        String routeMessage(String key, String fallback);

        void message(Player player, String message);

        MessageRenderer messagesFor(Player player);
    }
}
