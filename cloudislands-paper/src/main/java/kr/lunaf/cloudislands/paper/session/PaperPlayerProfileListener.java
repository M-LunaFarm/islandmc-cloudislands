package kr.lunaf.cloudislands.paper.session;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.PlayerProfileCommandClient;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PaperPlayerProfileListener implements Listener {
    private final PlayerProfileCommandClient playerProfiles;
    private final PlayerLocaleCache locales;

    public PaperPlayerProfileListener(CoreApiClient coreApiClient) {
        this(coreApiClient, null);
    }

    public PaperPlayerProfileListener(CoreApiClient coreApiClient, PlayerLocaleCache locales) {
        this.playerProfiles = coreApiClient.playerProfileCommands();
        this.locales = locales;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        java.util.UUID playerUuid = event.getPlayer().getUniqueId();
        String playerLocale = event.getPlayer().getLocale();
        if (locales != null) {
            locales.remember(playerUuid, playerLocale);
        }
        playerProfiles.touch(playerUuid, event.getPlayer().getName(), playerLocale)
            .thenAccept(profile -> {
                if (locales != null) {
                    String locale = profile.locale().isBlank() ? playerLocale : profile.locale();
                    locales.remember(playerUuid, locale);
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
