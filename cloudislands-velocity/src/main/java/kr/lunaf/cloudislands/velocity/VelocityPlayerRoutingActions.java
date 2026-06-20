package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.parseLong;
import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import net.kyori.adventure.text.Component;

public final class VelocityPlayerRoutingActions extends VelocityActionSupport {
    VelocityPlayerRoutingActions(VelocityActionContext context) {
        super(context);
    }

    public void createIsland(Player player, String templateId) {
        if (!allowRouteRequest(player)) {
            return;
        }
        coreApiClient.createIsland(player.getUniqueId(), templateId).thenAccept(result -> {
            if (result == null || !result.accepted()) {
                String code = result == null ? "FAILED" : result.code();
                player.sendMessage(Component.text(messageForCreateFailure(code)));
                return;
            }
            progressPresenter.actionBar(player, messages.text("island-create-starting"));
            if (result.ticket() != null) {
                route(player, result.ticket(), "섬으로 이동하지 못했습니다.");
            }
        }).exceptionally(error -> {
            player.sendMessage(messages.component("island-service-maintenance"));
            return null;
        });
    }

    public void deleteIsland(Player player, UUID islandId) {
        coreApiClient.deleteIsland(player.getUniqueId(), islandId).thenAccept(result -> {
            if (result != null && result.accepted()) {
                player.sendMessage(Component.text("섬을 삭제했습니다."));
                return;
            }
            player.sendMessage(Component.text("섬을 삭제할 수 없습니다."));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬 삭제를 처리하지 못했습니다."));
            return null;
        });
    }

    public void resetIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.resetIsland(islandId, player.getUniqueId(), reason).thenApply(body -> islandMessages.actionResult("Island reset", islandId.toString(), body)), "섬 리셋을 요청하지 못했습니다.");
    }

    public void showMyIsland(Player player) {
        sendBodyResult(player, coreApiClient.islandInfoByOwner(player.getUniqueId()).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void showIslandSettings(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(islandMessages::islandInfo), "섬 설정을 불러오지 못했습니다.");
    }

    public void showIslandLevel(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 레벨", "level", body)), "섬 레벨을 불러오지 못했습니다.");
    }

    public void showIslandWorth(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 가치", "worth", body)), "섬 가치를 불러오지 못했습니다.");
    }

    public void showIslandSize(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 크기", "size", body)), "섬 크기를 불러오지 못했습니다.");
    }

    public void showIslandBorder(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 경계", "border", body)), "섬 경계를 불러오지 못했습니다.");
    }

    public void showBiome(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandBiome(islandId).thenApply(islandMessages::biomeInfo), "섬 바이옴을 불러오지 못했습니다.");
    }

    public void setBiome(Player player, UUID islandId, String biomeKey) {
        sendActionResult(player, coreApiClient.setIslandBiome(islandId, player.getUniqueId(), biomeKey), "섬 바이옴을 변경했습니다.", "섬 바이옴을 변경하지 못했습니다.");
    }

    public void routeHome(Player player, String homeName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createHomeTicket(player.getUniqueId(), homeName), "현재 섬 서비스 일부 기능이 점검 중입니다.");
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicket(player.getUniqueId(), targetIslandId), "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    public void routeVisitOwner(Player player, UUID ownerUuid) {
        if (ownerUuid.equals(new UUID(0L, 0L))) {
            player.sendMessage(Component.text("방문할 플레이어를 찾을 수 없습니다."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicketForOwner(player.getUniqueId(), ownerUuid), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitName(Player player, String islandName) {
        if (islandName == null || islandName.isBlank()) {
            player.sendMessage(Component.text("방문할 섬 이름을 입력해주세요."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicket(player.getUniqueId(), islandName), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitNamedTarget(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(Component.text("방문할 대상 이름을 입력해주세요."));
            return;
        }
        coreApiClient.playerInfoByName(targetName).thenAccept(body -> {
            UUID primaryIslandId = parseUuid(jsonValue(body, "primaryIslandId"));
            if (primaryIslandId.equals(new UUID(0L, 0L))) {
                routeVisitName(player, targetName);
                return;
            }
            UUID ownerUuid = parseUuid(jsonValue(body, "playerUuid"));
            if (ownerUuid.equals(new UUID(0L, 0L))) {
                routeVisit(player, primaryIslandId);
                return;
            }
            routeVisitOwner(player, ownerUuid);
        }).exceptionally(error -> {
            routeVisitName(player, targetName);
            return null;
        });
    }

    public void recordPlayerProfile(Player player) {
        coreApiClient.touchPlayerProfile(player.getUniqueId(), player.getUsername(), playerLocale(player))
            .exceptionally(error -> null);
    }

    static String playerLocale(Player player) {
        return player == null ? PlayerIslandProfile.normalizeLocale("") : normalizedLocale(player.getEffectiveLocale());
    }

    static String normalizedLocale(Locale locale) {
        if (locale == null || locale.getLanguage() == null || locale.getLanguage().isBlank()) {
            return PlayerIslandProfile.normalizeLocale("");
        }
        return PlayerIslandProfile.normalizeLocale(locale.toLanguageTag());
    }

    public void routePendingSession(Player player) {
        pendingRoutes.routePendingSession(player);
    }

    public void clearPlayerState(UUID playerUuid) {
        routeRequestGuard.clear(playerUuid);
    }

    public void listMyIslands(Player player) {
        coreApiClient.listPlayerIslands(player.getUniqueId())
            .thenAccept(body -> player.sendMessage(Component.text(islandMessages.playerIslands(body))))
            .exceptionally(error -> {
                player.sendMessage(Component.text("내 섬 목록을 불러오지 못했습니다."));
                return null;
            });
    }

    public void routeRandomVisit(Player player) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createRandomVisitTicket(player.getUniqueId()), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    public void listPublicIslands(Player player, int limit) {
        sendBodyResult(player, coreApiClient.listPublicIslands(Math.max(1, Math.min(limit, 100))).thenApply(islandMessages::publicIslands), "공개 섬 목록을 불러오지 못했습니다.");
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createWarpTicket(player.getUniqueId(), targetIslandId, warpName), "해당 워프로 이동할 수 없습니다.");
    }

    public void listWarps(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandWarps(islandId).thenApply(body -> islandMessages.warpList("섬 워프", body)), "섬 워프를 불러오지 못했습니다.");
    }

    public void listPublicWarps(Player player) {
        sendBodyResult(player, coreApiClient.listPublicWarps(27).thenApply(body -> islandMessages.warpList("공개 워프", body)), "공개 워프를 불러오지 못했습니다.");
    }

    public void setWarp(Player player, UUID islandId, String name, boolean publicAccess) {
        IslandLocation defaultLocation = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, defaultLocation, publicAccess), "섬 워프를 설정했습니다.", "섬 워프를 설정하지 못했습니다.");
    }

    public void deleteWarp(Player player, UUID islandId, String name) {
        sendActionResult(player, coreApiClient.deleteIslandWarp(islandId, player.getUniqueId(), name), "섬 워프를 삭제했습니다.", "섬 워프를 삭제하지 못했습니다.");
    }

    public void setWarpPublicAccess(Player player, UUID islandId, String name, boolean publicAccess) {
        sendActionResult(player, coreApiClient.setIslandWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess), publicAccess ? "섬 워프를 공개했습니다." : "섬 워프를 비공개로 변경했습니다.", "섬 워프 공개 상태를 변경하지 못했습니다.");
    }
}
