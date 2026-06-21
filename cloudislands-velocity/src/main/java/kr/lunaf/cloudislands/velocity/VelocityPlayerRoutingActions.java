package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
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
        coreApiClient.lifecycle().createIsland(player.getUniqueId(), templateId).thenAccept(result -> {
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
        coreApiClient.lifecycle().deleteIsland(player.getUniqueId(), islandId).thenAccept(result -> {
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
        sendBodyResult(player, coreApiClient.lifecycle().resetIsland(islandId, player.getUniqueId(), reason).thenApply(result -> islandMessages.actionResult("Island reset", islandId.toString(), result)), "섬 리셋을 요청하지 못했습니다.");
    }

    public void showMyIsland(Player player) {
        sendBodyResult(player, coreApiClient.islands().getIslandByOwner(player.getUniqueId()).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void showIslandSettings(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islands().getIsland(islandId).thenApply(islandMessages::islandInfo), "섬 설정을 불러오지 못했습니다.");
    }

    public void showIslandLevel(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islands().getIsland(islandId).thenApply(view -> islandMessages.islandStat("섬 레벨", "level", view)), "섬 레벨을 불러오지 못했습니다.");
    }

    public void showIslandWorth(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islands().getIsland(islandId).thenApply(view -> islandMessages.islandStat("섬 가치", "worth", view)), "섬 가치를 불러오지 못했습니다.");
    }

    public void showIslandSize(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islands().getIsland(islandId).thenApply(view -> islandMessages.islandStat("섬 크기", "size", view)), "섬 크기를 불러오지 못했습니다.");
    }

    public void showIslandBorder(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islands().getIsland(islandId).thenApply(view -> islandMessages.islandStat("섬 경계", "border", view)), "섬 경계를 불러오지 못했습니다.");
    }

    public void showBiome(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.environment().islandBiome(islandId).thenApply(view -> islandMessages.biomeInfo(islandId, view)), "섬 바이옴을 불러오지 못했습니다.");
    }

    public void setBiome(Player player, UUID islandId, String biomeKey) {
        sendBodyResult(player, coreApiClient.environmentCommands().setBiome(islandId, player.getUniqueId(), biomeKey).thenApply(result -> islandMessages.environmentAction("섬 바이옴 변경", result)), "섬 바이옴을 변경하지 못했습니다.");
    }

    public void routeHome(Player player, String homeName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.navigationCommands().createHomeTicket(player.getUniqueId(), homeName), "현재 섬 서비스 일부 기능이 점검 중입니다.");
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.navigationCommands().createVisitTicket(player.getUniqueId(), targetIslandId), "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    public void routeVisitOwner(Player player, UUID ownerUuid) {
        if (ownerUuid.equals(new UUID(0L, 0L))) {
            player.sendMessage(Component.text("방문할 플레이어를 찾을 수 없습니다."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.navigationCommands().createVisitTicketForOwner(player.getUniqueId(), ownerUuid), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitName(Player player, String islandName) {
        if (islandName == null || islandName.isBlank()) {
            player.sendMessage(Component.text("방문할 섬 이름을 입력해주세요."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.navigationCommands().createVisitTicket(player.getUniqueId(), islandName), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitNamedTarget(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(Component.text("방문할 대상 이름을 입력해주세요."));
            return;
        }
        coreApiClient.navigation().playerProfileByName(targetName).thenAccept(profile -> {
            UUID primaryIslandId = parseUuid(profile.primaryIslandId());
            if (primaryIslandId.equals(new UUID(0L, 0L))) {
                routeVisitName(player, targetName);
                return;
            }
            UUID ownerUuid = parseUuid(profile.playerUuid());
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
        coreApiClient.playerProfileCommands().touch(player.getUniqueId(), player.getUsername(), playerLocale(player))
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
        coreApiClient.navigation().playerIslands(player.getUniqueId())
            .thenAccept(islands -> player.sendMessage(Component.text(islandMessages.playerIslands(islands))))
            .exceptionally(error -> {
                player.sendMessage(Component.text("내 섬 목록을 불러오지 못했습니다."));
                return null;
            });
    }

    public void routeRandomVisit(Player player) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.navigationCommands().createRandomVisitTicket(player.getUniqueId()), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    public void listPublicIslands(Player player, int limit) {
        sendBodyResult(player, coreApiClient.navigation().publicIslands(Math.max(1, Math.min(limit, 100))).thenApply(islandMessages::publicIslands), "공개 섬 목록을 불러오지 못했습니다.");
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.routingCommands().createWarpTicket(player.getUniqueId(), targetIslandId, warpName), "해당 워프로 이동할 수 없습니다.");
    }

    public void listWarps(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.homeWarps().warps(islandId).thenApply(warps -> islandMessages.warpList("섬 워프", warps)), "섬 워프를 불러오지 못했습니다.");
    }

    public void listPublicWarps(Player player) {
        sendBodyResult(player, coreApiClient.homeWarps().publicWarps(27, "", "").thenApply(warps -> islandMessages.warpList("공개 워프", warps)), "공개 워프를 불러오지 못했습니다.");
    }

    public void setWarp(Player player, UUID islandId, String name, boolean publicAccess) {
        IslandLocation defaultLocation = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendBodyResult(player, coreApiClient.homeWarpCommands().setWarp(islandId, player.getUniqueId(), name, defaultLocation, publicAccess).thenApply(result -> islandMessages.homeWarpAction("섬 워프 설정", result)), "섬 워프를 설정하지 못했습니다.");
    }

    public void deleteWarp(Player player, UUID islandId, String name) {
        sendBodyResult(player, coreApiClient.homeWarpCommands().deleteWarp(islandId, player.getUniqueId(), name).thenApply(result -> islandMessages.homeWarpAction("섬 워프 삭제", result)), "섬 워프를 삭제하지 못했습니다.");
    }

    public void setWarpPublicAccess(Player player, UUID islandId, String name, boolean publicAccess) {
        sendBodyResult(player, coreApiClient.homeWarpCommands().setWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess).thenApply(result -> islandMessages.homeWarpAction(publicAccess ? "섬 워프 공개" : "섬 워프 비공개", result)), "섬 워프 공개 상태를 변경하지 못했습니다.");
    }
}
