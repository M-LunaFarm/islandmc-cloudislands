package kr.lunaf.cloudislands.paper.command;

import kr.lunaf.cloudislands.paper.gui.IslandMainMenu;
import org.bukkit.entity.Player;

final class IslandCommandRouterFactory {
    private IslandCommandRouterFactory() {
    }

    static IslandCommandRouter create(
        IslandBankCommandHandler bankCommands,
        IslandSnapshotCommandHandler snapshotCommands,
        IslandWarehouseCommandHandler warehouseCommands,
        IslandChatLogCommandHandler chatLogCommands,
        IslandProgressionCommandHandler progressionCommands,
        IslandEnvironmentCommandHandler environmentCommands,
        IslandSettingsCommandHandler settingsCommands,
        IslandHomeWarpCommandHandler homeWarpCommands,
        IslandVisitReviewCommandHandler visitReviewCommands,
        IslandLifecycleCommandHandler lifecycleCommands,
        IslandOverviewCommandHandler overviewCommands,
        IslandMembershipCommandHandler membershipCommands,
        IslandAdminNodeCommandHandler adminCommands,
        IslandCommandMessenger messages
    ) {
        return create(
            bankCommands,
            snapshotCommands,
            warehouseCommands,
            chatLogCommands,
            progressionCommands,
            environmentCommands,
            settingsCommands,
            homeWarpCommands,
            visitReviewCommands,
            lifecycleCommands,
            overviewCommands,
            membershipCommands,
            adminCommands,
            messages,
            true
        );
    }

    static IslandCommandRouter create(
        IslandBankCommandHandler bankCommands,
        IslandSnapshotCommandHandler snapshotCommands,
        IslandWarehouseCommandHandler warehouseCommands,
        IslandChatLogCommandHandler chatLogCommands,
        IslandProgressionCommandHandler progressionCommands,
        IslandEnvironmentCommandHandler environmentCommands,
        IslandSettingsCommandHandler settingsCommands,
        IslandHomeWarpCommandHandler homeWarpCommands,
        IslandVisitReviewCommandHandler visitReviewCommands,
        IslandLifecycleCommandHandler lifecycleCommands,
        IslandOverviewCommandHandler overviewCommands,
        IslandMembershipCommandHandler membershipCommands,
        IslandAdminNodeCommandHandler adminCommands,
        IslandCommandMessenger messages,
        boolean guiMenusEnabled
    ) {
        return new IslandCommandRouter(
            bankCommands,
            snapshotCommands,
            warehouseCommands,
            chatLogCommands,
            progressionCommands,
            environmentCommands,
            settingsCommands,
            homeWarpCommands,
            visitReviewCommands,
            lifecycleCommands,
            overviewCommands,
            membershipCommands,
            adminCommands,
            new IslandCommandRouter.Runtime() {
                @Override
                public void message(Player player, String message) {
                    messages.message(player, message);
                }

                @Override
                public String routeMessage(String key, String fallback) {
                    return messages.routeMessage(key, fallback);
                }

                @Override
                public boolean openMainMenu(Player player) {
                    if (!guiMenusEnabled) {
                        return false;
                    }
                    try {
                        IslandMainMenu.open(player, messages.messagesFor(player));
                        return true;
                    } catch (RuntimeException exception) {
                        messages.message(player, messages.routeMessage("main-menu-open-failed", "메인 메뉴를 열 수 없습니다. 명령어 도움말을 표시합니다."));
                        return false;
                    }
                }

                @Override
                public boolean hasCommandPermission(Player player, IslandCommandPermission permission) {
                    return permission == null || permission.allows(player);
                }
            }
        );
    }
}
