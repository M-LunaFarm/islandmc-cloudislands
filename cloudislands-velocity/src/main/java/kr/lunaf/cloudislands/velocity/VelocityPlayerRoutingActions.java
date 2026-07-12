package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.coreclient.TemplateView;
import net.kyori.adventure.text.Component;

public final class VelocityPlayerRoutingActions extends VelocityActionSupport {
    VelocityPlayerRoutingActions(VelocityActionContext context) {
        super(context);
    }

    public void createIsland(Player player, String templateId) {
        if (!allowPlayerAction(player, CREATE_COOLDOWN, "섬 생성 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        coreApiClient.templates().get(normalizedTemplateId).thenCompose(template -> {
            if (!templateAllowed(player, template)) {
                player.sendMessage(messages.component("island-create-template-permission-denied"));
                return CompletableFuture.completedFuture(policyHandled());
            }
            if (paidTemplate(template)) {
                player.sendMessage(messages.component("island-create-paid-paper-required"));
                return CompletableFuture.completedFuture(policyHandled());
            }
            progressPresenter.status(player, messages.text("island-create-node-search"), messages.text("island-create-starting"));
            return coreApiClient.lifecycle().createIsland(player.getUniqueId(), normalizedTemplateId);
        }).thenAccept(result -> {
            if (result != null && "VELOCITY_POLICY_HANDLED".equals(result.code())) {
                return;
            }
            if (result == null || !result.accepted()) {
                player.sendMessage(Component.text(messageForCreateFailure(result == null ? "FAILED" : result.code())));
                return;
            }
            progressPresenter.status(player, messages.text("island-create-restoring"), messages.text("island-route-moving"));
            if (result.ticket() != null) {
                route(player, result.ticket(), "섬으로 이동하지 못했습니다.");
            }
        }).exceptionally(error -> {
            player.sendMessage(messages.component("island-service-maintenance"));
            return null;
        });
    }

    static boolean templateAllowed(Player player, TemplateView template) {
        return template.requiredPermission().isBlank() || player.hasPermission(template.requiredPermission());
    }

    static boolean paidTemplate(TemplateView template) {
        try {
            return new BigDecimal(template.creationCost()).signum() > 0;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static CreateIslandResult policyHandled() {
        return new CreateIslandResult(false, "VELOCITY_POLICY_HANDLED", null, null);
    }

    public void deleteIsland(Player player, UUID islandId) {
        if (!allowPlayerAction(player, DELETE_COOLDOWN, "섬 삭제 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        withResolvedIsland(player, islandId, "삭제할 섬을 찾지 못했습니다.", "섬 삭제를 처리하지 못했습니다.", resolved ->
            coreApiClient.lifecycle().deleteIsland(player.getUniqueId(), resolved).thenAccept(result -> {
                if (result != null && result.accepted()) {
                    player.sendMessage(Component.text("섬을 삭제했습니다."));
                    return;
                }
                player.sendMessage(Component.text("섬을 삭제할 수 없습니다."));
            }).exceptionally(error -> {
                player.sendMessage(Component.text("섬 삭제를 처리하지 못했습니다."));
                return null;
            }));
    }

    public void resetIsland(Player player, UUID islandId, String reason) {
        if (!allowPlayerAction(player, RESET_COOLDOWN, "섬 리셋 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        withResolvedIsland(player, islandId, "리셋할 섬을 찾지 못했습니다.", "섬 리셋을 요청하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.lifecycle().resetIsland(resolved, player.getUniqueId(), reason).thenApply(result -> islandMessages.actionResult("Island reset", resolved.toString(), result)), "섬 리셋을 요청하지 못했습니다."));
    }

    public void showMyIsland(Player player) {
        sendTextResult(player, coreApiClient.islands().getIslandByOwner(player.getUniqueId()).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void showIsland(Player player, String target) {
        targetResolver.resolveIslandId(target)
            .thenAccept(islandId -> {
                if (islandId.equals(new UUID(0L, 0L))) {
                    player.sendMessage(Component.text("정보를 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                    return;
                }
                sendTextResult(player, coreApiClient.islands().getIsland(islandId).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
            })
            .exceptionally(error -> {
                player.sendMessage(Component.text("정보를 확인할 섬 또는 플레이어를 찾지 못했습니다."));
                return null;
            });
    }

    public void showIslandSettings(Player player, UUID islandId) {
        showResolvedIsland(player, islandId, "섬 설정", view -> islandMessages.islandInfo(view));
    }

    public void showIslandLevel(Player player, UUID islandId) {
        showResolvedIsland(player, islandId, "섬 레벨", view -> islandMessages.islandStat("섬 레벨", "level", view));
    }

    public void showIslandWorth(Player player, UUID islandId) {
        showResolvedIsland(player, islandId, "섬 가치", view -> islandMessages.islandStat("섬 가치", "worth", view));
    }

    public void showIslandSize(Player player, UUID islandId) {
        showResolvedIsland(player, islandId, "섬 크기", view -> islandMessages.islandStat("섬 크기", "size", view));
    }

    public void showIslandBorder(Player player, UUID islandId) {
        showResolvedIsland(player, islandId, "섬 경계", view -> islandMessages.islandStat("섬 경계", "border", view));
    }

    public void showBiome(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "바이옴을 확인할 섬을 찾지 못했습니다.", "섬 바이옴을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.environment().islandBiome(resolved).thenApply(view -> islandMessages.biomeInfo(resolved, view)), "섬 바이옴을 불러오지 못했습니다."));
    }

    public void setBiome(Player player, UUID islandId, String biomeKey) {
        withResolvedIsland(player, islandId, "바이옴을 변경할 섬을 찾지 못했습니다.", "섬 바이옴을 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.environmentCommands().setBiome(resolved, player.getUniqueId(), biomeKey).thenApply(result -> islandMessages.environmentAction("섬 바이옴 변경", result)), "섬 바이옴을 변경하지 못했습니다."));
    }

    public void routeHome(Player player, String homeName) {
        if (!allowPlayerAction(player, HOME_COOLDOWN, "섬 홈 이동 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        progressPresenter.status(player, messages.text("island-home-preparing"), messages.text("island-route-moving"));
        routeFuture(player, coreApiClient.navigationCommands().createHomeTicket(player.getUniqueId(), homeName), "현재 섬 서비스 일부 기능이 점검 중입니다.");
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        if (!allowPlayerAction(player, VISIT_COOLDOWN, "섬 방문 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        progressPresenter.status(player, messages.text("island-visit-preparing"), messages.text("island-route-moving"));
        routeFuture(player, coreApiClient.navigationCommands().createVisitTicket(player.getUniqueId(), targetIslandId), "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    public void routeVisitOwner(Player player, UUID ownerUuid) {
        if (ownerUuid.equals(new UUID(0L, 0L))) {
            player.sendMessage(Component.text("방문할 플레이어를 찾을 수 없습니다."));
            return;
        }
        if (!allowPlayerAction(player, VISIT_COOLDOWN, "섬 방문 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        progressPresenter.status(player, messages.text("island-visit-preparing"), messages.text("island-route-moving"));
        routeFuture(player, coreApiClient.navigationCommands().createVisitTicketForOwner(player.getUniqueId(), ownerUuid), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitName(Player player, String islandName) {
        if (islandName == null || islandName.isBlank()) {
            player.sendMessage(Component.text("방문할 섬 이름을 입력해주세요."));
            return;
        }
        if (!allowPlayerAction(player, VISIT_COOLDOWN, "섬 방문 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        progressPresenter.status(player, messages.text("island-visit-preparing"), messages.text("island-route-moving"));
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

    public void setPlayerLocale(Player player, String value) {
        String locale = PlayerIslandProfile.normalizeLocale(value);
        sendTextResult(player, coreApiClient.playerProfileCommands().setLocale(player.getUniqueId(), locale)
            .thenApply(profile -> {
                String applied = profile == null || profile.locale() == null || profile.locale().isBlank() ? locale : PlayerIslandProfile.normalizeLocale(profile.locale());
                return "언어 설정을 변경했습니다. locale=" + applied;
            }), "언어 설정을 변경하지 못했습니다.");
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

    public void selectIsland(Player player, String target) {
        targetResolver.resolveIslandId(target)
            .thenCompose(islandId -> coreApiClient.playerProfileCommands().selectPrimaryIsland(player.getUniqueId(), islandId))
            .thenAccept(profile -> player.sendMessage(Component.text("기본 섬을 선택했습니다.")))
            .exceptionally(error -> {
                player.sendMessage(Component.text("소속된 섬만 기본 섬으로 선택할 수 있습니다."));
                return null;
            });
    }

    public void routeRandomVisit(Player player) {
        if (!allowPlayerAction(player, VISIT_COOLDOWN, "섬 방문 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        progressPresenter.status(player, messages.text("island-visit-preparing"), messages.text("island-route-moving"));
        routeFuture(player, coreApiClient.navigationCommands().createRandomVisitTicket(player.getUniqueId()), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    public void listPublicIslands(Player player, int limit) {
        sendTextResult(player, coreApiClient.navigation().publicIslands(Math.max(1, Math.min(limit, 100))).thenApply(islandMessages::publicIslands), "공개 섬 목록을 불러오지 못했습니다.");
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        if (!allowPlayerAction(player, VISIT_COOLDOWN, "섬 워프 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        progressPresenter.status(player, messages.text("island-visit-preparing"), messages.text("island-route-moving"));
        withResolvedIsland(player, targetIslandId, "워프가 속한 섬을 찾지 못했습니다.", "해당 워프로 이동할 수 없습니다.",
            resolved -> routeFuture(player, coreApiClient.routingCommands().createWarpTicket(player.getUniqueId(), resolved, warpName), "해당 워프로 이동할 수 없습니다."));
    }

    public void listWarps(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "워프를 확인할 섬을 찾지 못했습니다.", "섬 워프를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.homeWarps().warps(resolved).thenApply(warps -> islandMessages.warpList("섬 워프", warps)), "섬 워프를 불러오지 못했습니다."));
    }

    public void listPublicWarps(Player player) {
        sendTextResult(player, coreApiClient.homeWarps().publicWarps(27, "", "").thenApply(warps -> islandMessages.warpList("공개 워프", warps)), "공개 워프를 불러오지 못했습니다.");
    }

    public void setWarp(Player player, UUID islandId, String name, boolean publicAccess) {
        player.sendMessage(Component.text("워프 설정은 현재 위치를 확인할 수 있는 Paper 서버에서만 실행할 수 있습니다."));
    }

    public void deleteWarp(Player player, UUID islandId, String name) {
        withResolvedIsland(player, islandId, "워프를 삭제할 섬을 찾지 못했습니다.", "섬 워프를 삭제하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.homeWarpCommands().deleteWarp(resolved, player.getUniqueId(), name).thenApply(result -> islandMessages.homeWarpAction("섬 워프 삭제", result)), "섬 워프를 삭제하지 못했습니다."));
    }

    public void setWarpPublicAccess(Player player, UUID islandId, String name, boolean publicAccess) {
        withResolvedIsland(player, islandId, "워프 공개 상태를 변경할 섬을 찾지 못했습니다.", "섬 워프 공개 상태를 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.homeWarpCommands().setWarpPublicAccess(resolved, player.getUniqueId(), name, publicAccess).thenApply(result -> islandMessages.homeWarpAction(publicAccess ? "섬 워프 공개" : "섬 워프 비공개", result)), "섬 워프 공개 상태를 변경하지 못했습니다."));
    }

    private void showResolvedIsland(Player player, UUID islandId, String label, java.util.function.Function<kr.lunaf.cloudislands.coreclient.CoreGuiViews.IslandInfoView, String> formatter) {
        withResolvedIsland(player, islandId, label + "을 확인할 섬을 찾지 못했습니다.", label + "을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.islands().getIsland(resolved).thenApply(formatter), label + "을 불러오지 못했습니다."));
    }
}
