package kr.lunaf.cloudislands.velocity.routing;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.function.Function;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;
import kr.lunaf.cloudislands.velocity.metrics.VelocityRoutingMetrics;
import net.kyori.adventure.text.Component;

public final class PendingRouteService {
    private final CoreApiClient coreApiClient;
    private final RouteFallbackService fallbackService;
    private final VelocityRoutingMetrics metrics;
    private final Function<String, Component> playerMessage;

    public PendingRouteService(
            CoreApiClient coreApiClient,
            RouteFallbackService fallbackService,
            VelocityRoutingMetrics metrics,
            Function<String, Component> playerMessage) {
        this.coreApiClient = coreApiClient;
        this.fallbackService = fallbackService;
        this.metrics = metrics;
        this.playerMessage = playerMessage;
    }

    public void routePendingSession(Player player) {
        metrics.pendingRouteLookup();
        coreApiClient.findAnyRouteSession(player.getUniqueId()).thenAccept(session -> {
            if (session.isPresent()) {
                connectPendingSession(player, session.get());
            } else {
                metrics.pendingRouteMissing();
            }
        }).exceptionally(error -> {
            metrics.pendingRouteFailure();
            return null;
        });
    }

    private void connectPendingSession(Player player, PlayerRouteSession session) {
        RegisteredServer server = fallbackService.findServer(targetServerName(session));
        if (server == null) {
            metrics.pendingRouteFailure();
            coreApiClient.clearRoute(session.playerUuid(), session.ticketId(), "PENDING_TARGET_NOT_FOUND").exceptionally(error -> null);
            fallbackService.transfer(player, "이전 섬 이동 경로를 찾을 수 없어 로비로 이동합니다.");
            return;
        }
        player.sendActionBar(playerMessage.apply("이전 섬 이동을 이어갑니다."));
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                metrics.pendingRouteFailure();
                coreApiClient.clearRoute(session.playerUuid(), session.ticketId(), "PENDING_CONNECT_FAILED").exceptionally(error -> null);
                fallbackService.transfer(player, "이전 섬 이동을 이어가지 못해 로비로 이동합니다.");
                return;
            }
            metrics.pendingRouteResume();
        }).exceptionally(error -> {
            metrics.pendingRouteFailure();
            coreApiClient.clearRoute(session.playerUuid(), session.ticketId(), "PENDING_CONNECT_EXCEPTION").exceptionally(ignored -> null);
            fallbackService.transfer(player, "이전 섬 이동을 이어가지 못해 로비로 이동합니다.");
            return null;
        });
    }

    static String targetServerName(PlayerRouteSession session) {
        return session.targetServerName() == null || session.targetServerName().isBlank()
            ? session.targetNode()
            : session.targetServerName();
    }
}
