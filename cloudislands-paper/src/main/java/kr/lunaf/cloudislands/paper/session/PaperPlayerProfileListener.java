package kr.lunaf.cloudislands.paper.session;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PaperPlayerProfileListener implements Listener {
    private final CoreApiClient coreApiClient;

    public PaperPlayerProfileListener(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        coreApiClient.touchPlayerProfile(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getPlayer().getLocale())
            .exceptionally(error -> null);
    }
}
