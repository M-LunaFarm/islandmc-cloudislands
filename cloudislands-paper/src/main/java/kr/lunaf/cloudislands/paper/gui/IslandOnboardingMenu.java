package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class IslandOnboardingMenu {
    private IslandOnboardingMenu() {
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, Runnable fallback) {
        if (plugin == null || client == null || player == null) {
            openFallback(fallback);
            return;
        }
        client.navigation().playerIslands(player.getUniqueId())
            .thenAccept(islands -> openForState(plugin, client, player, messages, islands, fallback))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> openFallback(fallback));
                return null;
            });
    }

    private static void openForState(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages, List<CoreGuiViews.PlayerIslandView> islands, Runnable fallback) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (islands == null) {
                openFallback(fallback);
                return;
            }
            if (islands.isEmpty()) {
                IslandCreateMenu.open(plugin, client, player, messages);
                return;
            }
            IslandMyIslandsMenu.open(plugin, client, player, messages);
        });
    }

    private static void openFallback(Runnable fallback) {
        if (fallback != null) {
            fallback.run();
        }
    }
}
