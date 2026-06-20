package kr.lunaf.cloudislands.paper.session;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PaperPlayerProfileListener implements Listener {
    private final CoreApiClient coreApiClient;
    private final PlayerLocaleCache locales;

    public PaperPlayerProfileListener(CoreApiClient coreApiClient) {
        this(coreApiClient, null);
    }

    public PaperPlayerProfileListener(CoreApiClient coreApiClient, PlayerLocaleCache locales) {
        this.coreApiClient = coreApiClient;
        this.locales = locales;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        java.util.UUID playerUuid = event.getPlayer().getUniqueId();
        String playerLocale = event.getPlayer().getLocale();
        if (locales != null) {
            locales.remember(playerUuid, playerLocale);
        }
        coreApiClient.touchPlayerProfile(playerUuid, event.getPlayer().getName(), playerLocale)
            .thenAccept(body -> {
                if (locales != null) {
                    locales.remember(playerUuid, PlayerLocaleCache.profileLocale(body, playerLocale));
                }
            })
            .exceptionally(error -> null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (locales != null) {
            locales.forget(event.getPlayer().getUniqueId());
        }
    }
}
