package kr.lunaf.cloudislands.paper.command;

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
            }
        );
    }
}
