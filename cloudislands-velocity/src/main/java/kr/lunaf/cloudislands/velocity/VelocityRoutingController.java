package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.RouteTicket;
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

    public void createIsland(Player player, String templateId) {
        coreApiClient.createIsland(player.getUniqueId(), templateId).thenAccept(result -> {
            if (result == null || !result.accepted()) {
                String code = result == null ? "FAILED" : result.code();
                player.sendMessage(Component.text(messageForCreateFailure(code)));
                return;
            }
            player.sendActionBar(Component.text("섬을 생성하고 있습니다."));
            if (result.ticket() != null) {
                route(player, result.ticket(), "섬으로 이동하지 못했습니다.");
            }
        });
    }

    public void routeHome(Player player) {
        coreApiClient.createHomeTicket(player.getUniqueId()).thenAccept(ticket -> route(player, ticket, "현재 섬 서비스 일부 기능이 점검 중입니다."));
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        coreApiClient.createVisitTicket(player.getUniqueId(), targetIslandId).thenAccept(ticket -> route(player, ticket, "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요."));
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        coreApiClient.createWarpTicket(player.getUniqueId(), targetIslandId, warpName).thenAccept(ticket -> route(player, ticket, "해당 워프로 이동할 수 없습니다."));
    }

    public void invite(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "초대를 생성하지 못했습니다." : "섬 초대를 보냈습니다.")));
    }

    public void listInvites(Player player) {
        coreApiClient.listPendingInvites(player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "대기 중인 초대가 없습니다." : body)));
    }

    public void acceptInvite(Player player, UUID inviteId) {
        coreApiClient.acceptIslandInvite(inviteId, player.getUniqueId()).thenRun(() -> player.sendMessage(Component.text("섬 초대를 수락했습니다.")));
    }

    public void declineInvite(Player player, UUID inviteId) {
        coreApiClient.declineIslandInvite(inviteId, player.getUniqueId()).thenRun(() -> player.sendMessage(Component.text("섬 초대를 거절했습니다.")));
    }

    private void route(Player player, RouteTicket ticket, String failureMessage) {
        if (ticket == null) {
            fallback(player, failureMessage);
            return;
        }
        coreApiClient.publishRouteSession(ticket).thenRun(() -> {
            String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
            connectWithTicket(player, targetServerName);
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

    private String messageForCreateFailure(String code) {
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> "이미 섬을 보유하고 있습니다.";
            case "NODE_UNAVAILABLE" -> "현재 섬 서버가 혼잡합니다. 잠시 후 다시 시도해주세요.";
            default -> "섬 생성에 실패했습니다.";
        };
    }
}
