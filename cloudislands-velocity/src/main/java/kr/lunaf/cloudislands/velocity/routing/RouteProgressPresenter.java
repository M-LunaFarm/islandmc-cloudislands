package kr.lunaf.cloudislands.velocity.routing;

import com.velocitypowered.api.proxy.Player;
import java.util.function.Function;
import kr.lunaf.cloudislands.protocol.route.RoutePreparationProgressPolicy;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class RouteProgressPresenter {
    private final boolean useActionBar;
    private final boolean useBossBarLoading;
    private final Function<String, Component> playerMessage;

    public RouteProgressPresenter(boolean useActionBar, boolean useBossBarLoading, Function<String, Component> playerMessage) {
        this.useActionBar = useActionBar;
        this.useBossBarLoading = useBossBarLoading;
        this.playerMessage = playerMessage;
    }

    public void actionBar(Player player, String message) {
        if (useActionBar) {
            player.sendActionBar(playerMessage.apply(message));
        }
    }

    public BossBar loadingBossBar(String title) {
        return BossBar.bossBar(playerMessage.apply(title), RoutePreparationProgressPolicy.preparingProgress(0), BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
    }

    public void showBossBar(Player player, BossBar bossBar) {
        if (useBossBarLoading) {
            player.showBossBar(bossBar);
        }
    }

    public void hideBossBar(Player player, BossBar bossBar) {
        if (useBossBarLoading) {
            player.hideBossBar(bossBar);
        }
    }

    public void preparing(Player player, BossBar bossBar, String bossBarTitle, String actionBarMessage, int attempt) {
        bossBar.progress(RoutePreparationProgressPolicy.preparingProgress(attempt));
        bossBar.name(playerMessage.apply(bossBarTitle));
        actionBar(player, actionBarMessage);
    }

    public void ready(Player player, BossBar bossBar, String message) {
        bossBar.progress(1.0F);
        bossBar.name(playerMessage.apply(message));
        actionBar(player, message);
    }

    public static String progressValue(int attempt) {
        return Integer.toString(RoutePreparationProgressPolicy.preparingPercent(attempt));
    }
}
