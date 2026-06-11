package kr.lunaf.cloudislands.paper.session;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.RouteTicketConsumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PaperRouteSessionListener implements Listener {
    private final CoreApiClient coreApiClient;
    private final RouteTicketConsumer ticketConsumer;
    private final String nodeId;

    public PaperRouteSessionListener(CoreApiClient coreApiClient, RouteTicketConsumer ticketConsumer, String nodeId) {
        this.coreApiClient = coreApiClient;
        this.ticketConsumer = ticketConsumer;
        this.nodeId = nodeId;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        coreApiClient.consumeRouteSession(event.getPlayer().getUniqueId(), nodeId).thenAccept(session -> session.ifPresent(value -> ticketConsumer.consumeAndTeleport(value.ticketId(), value.playerUuid(), value.nonce())));
    }
}
