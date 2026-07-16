package kr.lunaf.cloudislands.paper.session;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.PlayerProfileCommandClient;
import kr.lunaf.cloudislands.paper.PlayerIslandFlightService;
import kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PaperPlayerProfileListener implements Listener {
    private final org.bukkit.plugin.Plugin plugin;
    private final PlayerProfileCommandClient playerProfiles;
    private final PlayerLocaleCache locales;
    private final PlayerFlightPreferenceRegistry flightPreferences;
    private final PlayerIslandFlightService flightService;

    public PaperPlayerProfileListener(CoreApiClient coreApiClient) {
        this(coreApiClient, null);
    }

    public PaperPlayerProfileListener(CoreApiClient coreApiClient, PlayerLocaleCache locales) {
        this(null, coreApiClient, locales, null, null);
    }

    public PaperPlayerProfileListener(org.bukkit.plugin.Plugin plugin, CoreApiClient coreApiClient, PlayerLocaleCache locales, PlayerFlightPreferenceRegistry flightPreferences, PlayerIslandFlightService flightService) {
        this.plugin = plugin;
        this.playerProfiles = coreApiClient.playerProfileCommands();
        this.locales = locales;
        this.flightPreferences = flightPreferences;
        this.flightService = flightService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerProfileSession playerSession = PlayerProfileSession.capture(event.getPlayer());
        java.util.UUID playerUuid = playerSession.playerUuid();
        String playerLocale = PlayerLocaleCache.clientLocale(playerSession.expectedPlayer());
        if (locales != null) {
            locales.remember(playerUuid, playerLocale);
        }
        playerProfiles.touch(playerUuid, playerSession.expectedPlayer().getName(), playerLocale)
            .thenAccept(profile -> runMain(() -> {
                org.bukkit.entity.Player activePlayer = plugin == null
                    ? playerSession.expectedPlayer()
                    : plugin.getServer().getPlayer(playerUuid);
                if (!playerSession.isCurrent(activePlayer)) {
                    return;
                }
                if (locales != null) {
                    String locale = profile.locale().isBlank() ? playerLocale : profile.locale();
                    locales.remember(playerUuid, locale);
                }
                if (flightPreferences != null) {
                    flightPreferences.remember(playerUuid, profile.islandFlyEnabled());
                }
                if (flightService != null) {
                    flightService.refresh(activePlayer, activePlayer.getLocation().getBlock());
                }
            }))
            .exceptionally(error -> null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (locales != null) {
            locales.forget(event.getPlayer().getUniqueId());
        }
        if (flightService != null) {
            flightService.clear(event.getPlayer());
        } else if (flightPreferences != null) {
            flightPreferences.forget(event.getPlayer().getUniqueId());
        }
    }

    private void runMain(Runnable task) {
        if (plugin == null) {
            task.run();
        } else {
            PaperSchedulers.run(plugin, task);
        }
    }
}
