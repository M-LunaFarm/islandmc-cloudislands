package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandRole;
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

    public void listMembers(Player player, UUID islandId) {
        coreApiClient.listIslandMembers(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "멤버 목록을 불러오지 못했습니다." : body)));
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, IslandRole role) {
        coreApiClient.setIslandMember(islandId, player.getUniqueId(), targetUuid, role).thenRun(() -> player.sendMessage(Component.text("섬 멤버 역할을 변경했습니다.")));
    }

    public void kickMember(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.removeIslandMember(islandId, player.getUniqueId(), targetUuid).thenRun(() -> player.sendMessage(Component.text("섬 멤버를 추방했습니다.")));
    }

    public void banVisitor(Player player, UUID islandId, UUID targetUuid, String reason) {
        coreApiClient.banIslandVisitor(islandId, player.getUniqueId(), targetUuid, reason).thenRun(() -> player.sendMessage(Component.text("방문자를 밴했습니다.")));
    }

    public void pardonVisitor(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.pardonIslandVisitor(islandId, player.getUniqueId(), targetUuid).thenRun(() -> player.sendMessage(Component.text("방문자 밴을 해제했습니다.")));
    }

    public void setPublicAccess(Player player, UUID islandId, boolean publicAccess) {
        coreApiClient.setIslandPublicAccess(islandId, player.getUniqueId(), publicAccess).thenRun(() -> player.sendMessage(Component.text(publicAccess ? "섬을 공개로 변경했습니다." : "섬을 비공개로 변경했습니다.")));
    }

    public void listIslandLogs(Player player, UUID islandId) {
        coreApiClient.listIslandLogs(islandId, 30).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 로그를 불러오지 못했습니다." : body)));
    }

    public void showLevelRanking(Player player) {
        coreApiClient.topIslandsByLevel(10).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "랭킹을 불러오지 못했습니다." : body)));
    }

    public void recalculateLevel(Player player, UUID islandId) {
        coreApiClient.recalculateIslandLevel(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "레벨 계산을 시작하지 못했습니다." : body)));
    }

    public void listUpgradeRules(Player player) {
        coreApiClient.listUpgradeRules().thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "업그레이드 목록을 불러오지 못했습니다." : body)));
    }

    public void listUpgrades(Player player, UUID islandId) {
        coreApiClient.listIslandUpgrades(islandId).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "섬 업그레이드를 불러오지 못했습니다." : body)));
    }

    public void purchaseUpgrade(Player player, UUID islandId, String upgradeKey) {
        coreApiClient.purchaseIslandUpgrade(islandId, player.getUniqueId(), upgradeKey).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "업그레이드에 실패했습니다." : body)));
    }

    public void listSnapshots(Player player, UUID islandId) {
        coreApiClient.listIslandSnapshots(islandId, 20).thenAccept(body -> player.sendMessage(Component.text(body == null || body.isBlank() ? "스냅샷 목록을 불러오지 못했습니다." : body)));
    }

    public void snapshot(Player player, UUID islandId, String reason) {
        coreApiClient.requestIslandSnapshot(islandId, reason).thenRun(() -> player.sendMessage(Component.text("섬 스냅샷을 요청했습니다.")));
    }

    public void restore(Player player, UUID islandId, long snapshotNo) {
        coreApiClient.restoreIslandSnapshot(islandId, snapshotNo).thenRun(() -> player.sendMessage(Component.text("섬 복원을 요청했습니다.")));
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
