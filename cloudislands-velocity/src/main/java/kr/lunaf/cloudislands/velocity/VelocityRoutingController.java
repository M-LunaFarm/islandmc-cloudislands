package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import net.kyori.adventure.text.Component;

public final class VelocityRoutingController {
    private final ProxyServer proxy;
    private final CoreApiClient coreApiClient;
    private final String fallbackServer;

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer) {
        this.proxy = proxy;
        this.coreApiClient = coreApiClient;
        this.fallbackServer = fallbackServer;
    }

    public void routeHome(Player player) {
        coreApiClient.createHomeTicket(player.getUniqueId()).thenAccept(ticket -> {
            if (ticket == null) {
                fallback(player, "현재 섬 서비스 일부 기능이 점검 중입니다.");
                return;
            }
            connectWithTicket(player, ticket.targetNode());
        });
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        coreApiClient.createVisitTicket(player.getUniqueId(), targetIslandId).thenAccept(ticket -> {
            if (ticket == null) {
                fallback(player, "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.");
                return;
            }
            connectWithTicket(player, ticket.targetNode());
        });
    }

    private void connectWithTicket(Player player, String targetServerName) {
        proxy.getServer(targetServerName).ifPresentOrElse(server -> connect(player, server), () -> fallback(player, "섬 서버를 찾을 수 없습니다."));
    }

    private void connect(Player player, RegisteredServer server) {
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                fallback(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.");
            }
        });
    }

    private void fallback(Player player, String message) {
        player.sendMessage(Component.text(message));
        proxy.getServer(fallbackServer).ifPresent(server -> player.createConnectionRequest(server).connectWithIndication());
    }
}
