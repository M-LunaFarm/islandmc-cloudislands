package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.ProgressionBlockDetailView;
import kr.lunaf.cloudislands.coreclient.ProgressionBlockDetailsView;
import kr.lunaf.cloudislands.coreclient.ReviewListView;
import kr.lunaf.cloudislands.coreclient.ReviewView;
import kr.lunaf.cloudislands.coreclient.WarehouseItemView;
import net.kyori.adventure.text.Component;

public final class VelocityPlayerProgressionActions extends VelocityActionSupport {
    VelocityPlayerProgressionActions(VelocityActionContext context) {
        super(context);
    }

    public void showLevelRanking(Player player, int limit) {
        sendTextResult(player, coreApiClient.progression().topLevel(Math.max(1, Math.min(limit, 100))).thenApply(rankings -> islandMessages.rankingList("섬 랭킹", rankings)), "랭킹을 불러오지 못했습니다.");
    }

    public void showWorthRanking(Player player, int limit) {
        sendTextResult(player, coreApiClient.progression().topWorth(Math.max(1, Math.min(limit, 100))).thenApply(rankings -> islandMessages.rankingList("가치 랭킹", rankings)), "가치 랭킹을 불러오지 못했습니다.");
    }

    public void showBankRanking(Player player, int limit) {
        sendTextResult(player, coreApiClient.progression().topBank(Math.max(1, Math.min(limit, 100))).thenApply(rankings -> islandMessages.rankingList("은행 랭킹", rankings)), "은행 랭킹을 불러오지 못했습니다.");
    }

    public void showReviewRanking(Player player, int limit) {
        sendTextResult(player, coreApiClient.progression().topReviews(Math.max(1, Math.min(limit, 100)))
            .thenApply(entries -> "평가 랭킹: " + entries.stream()
                .map(entry -> entry.islandId() + "=" + entry.averageRating() + "(" + entry.reviewCount() + ")")
                .reduce((left, right) -> left + ", " + right).orElse("비어 있음")), "평가 랭킹을 불러오지 못했습니다.");
    }

    public void showBlockDetails(Player player, UUID islandId, int limit) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "블록 상세를 확인할 섬을 찾지 못했습니다.", "섬 블록 상세를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progression().blockDetails(resolved, Math.max(1, Math.min(limit, 100))).thenApply(VelocityPlayerProgressionActions::blockDetailsMessage), "섬 블록 상세를 불러오지 못했습니다."));
    }

    public void listWarehouse(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "창고를 확인할 섬을 찾지 못했습니다.", "섬 창고를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.warehouse().listItems(resolved, 100).thenApply(VelocityPlayerProgressionActions::warehouseMessage), "섬 창고를 불러오지 못했습니다."));
    }

    public void depositWarehouse(Player player, UUID islandId, String materialKey, long amount) {
        player.sendMessage(messages.component("warehouse-deposit-paper-required"));
    }

    public void withdrawWarehouse(Player player, UUID islandId, String materialKey, long amount) {
        player.sendMessage(messages.component("warehouse-withdraw-paper-required"));
    }

    private static String warehouseMessage(List<WarehouseItemView> items) {
        if (items == null || items.isEmpty()) {
            return "섬 창고: 비어 있음";
        }
        return "섬 창고: " + items.stream()
            .map(item -> item.materialKey() + "=" + item.amount())
            .limit(100)
            .reduce((left, right) -> left + ", " + right)
            .orElse("비어 있음");
    }

    public void showBlockDetails(Player player, String target, int limit) {
        targetResolver.resolveIslandId(target)
            .thenAccept(islandId -> {
                if (islandId.equals(new UUID(0L, 0L))) {
                    player.sendMessage(Component.text("대상 플레이어 또는 섬을 찾지 못했습니다."));
                    return;
                }
                showBlockDetails(player, islandId, limit);
            })
            .exceptionally(error -> {
                player.sendMessage(Component.text("대상 섬을 확인하지 못했습니다."));
                return null;
            });
    }

    public void listReviews(Player player, UUID islandId, int limit) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "후기를 확인할 섬을 찾지 못했습니다.", "섬 후기를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.navigation().listReviews(resolved, Math.max(1, Math.min(limit, 100))).thenApply(VelocityPlayerProgressionActions::reviewListMessage), "섬 후기를 불러오지 못했습니다."));
    }

    public void rateReview(Player player, String target, int rating, String comment) {
        java.util.concurrent.CompletableFuture<UUID> island = target == null || target.isBlank() || target.equalsIgnoreCase("current")
            ? resolvePlayerIsland(player)
            : targetResolver.resolveIslandId(target);
        island.thenAccept(islandId -> {
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("평가할 섬을 찾지 못했습니다."));
                return;
            }
            sendTextResult(player, coreApiClient.navigationCommands().setReview(islandId, player.getUniqueId(), rating, comment)
                .thenApply(result -> "섬 평가: code=" + result.code() + " rating=" + rating), "섬을 평가하지 못했습니다.");
        }).exceptionally(error -> {
            player.sendMessage(Component.text("평가할 섬을 찾지 못했습니다."));
            return null;
        });
    }

    public void deleteReview(Player player, String target) {
        java.util.concurrent.CompletableFuture<UUID> island = target == null || target.isBlank() || target.equalsIgnoreCase("current")
            ? resolvePlayerIsland(player)
            : targetResolver.resolveIslandId(target);
        island.thenAccept(islandId -> {
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("평가를 삭제할 섬을 찾지 못했습니다."));
                return;
            }
            sendTextResult(player, coreApiClient.navigationCommands().deleteReview(islandId, player.getUniqueId())
                .thenApply(result -> "섬 평가 삭제: code=" + result.code()), "섬 평가를 삭제하지 못했습니다.");
        })
            .exceptionally(error -> {
                player.sendMessage(Component.text("평가를 삭제할 섬을 찾지 못했습니다."));
                return null;
            });
    }

    private java.util.concurrent.CompletableFuture<UUID> resolvePlayerIsland(Player player) {
        return coreApiClient.islands().getIslandByOwner(player.getUniqueId()).thenApply(view -> parseUuid(view.islandId()));
    }

    public void showVisitorStats(Player player, UUID islandId, int limit) {
        withResolvedIsland(player, islandId, "방문 통계를 확인할 섬을 찾지 못했습니다.", "방문 통계를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.visitorStats().stats(resolved, Math.max(1, Math.min(limit, 100)))
                .thenApply(stats -> "방문 통계: total=" + stats.totalVisits() + " unique=" + stats.uniqueVisitors() + " recent=" + stats.recentVisitors().size()), "방문 통계를 불러오지 못했습니다."));
    }

    public void recalculateLevel(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "레벨을 계산할 섬을 찾지 못했습니다.", "레벨 계산을 시작하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progressionCommands().recalculateLevel(resolved, player.getUniqueId()).thenApply(islandMessages::levelRecalculation), "레벨 계산을 시작하지 못했습니다."));
    }

    public void listUpgradeRules(Player player) {
        sendTextResult(player, coreApiClient.progression().upgradeRules().thenApply(islandMessages::upgradeRules), "업그레이드 목록을 불러오지 못했습니다.");
    }

    public void listUpgrades(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "업그레이드를 확인할 섬을 찾지 못했습니다.", "섬 업그레이드를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progression().upgrades(resolved).thenApply(islandMessages::upgradeList), "섬 업그레이드를 불러오지 못했습니다."));
    }

    public void showGenerator(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "생성기를 확인할 섬을 찾지 못했습니다.", "섬 생성기를 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progression().upgrades(resolved).thenApply(islandMessages::generatorInfo), "섬 생성기를 불러오지 못했습니다."));
    }

    public void purchaseUpgrade(Player player, UUID islandId, String upgradeKey) {
        withResolvedIsland(player, islandId, "업그레이드할 섬을 찾지 못했습니다.", "업그레이드에 실패했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progressionCommands().purchaseUpgrade(resolved, player.getUniqueId(), upgradeKey).thenApply(islandMessages::upgradePurchase), "업그레이드에 실패했습니다."));
    }

    public void listMissions(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "미션을 확인할 섬을 찾지 못했습니다.", "미션 목록을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progression().missions(resolved, "MISSION").thenApply(missions -> islandMessages.missionList("섬 미션", missions)), "미션 목록을 불러오지 못했습니다."));
    }

    public void listChallenges(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "챌린지를 확인할 섬을 찾지 못했습니다.", "챌린지 목록을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progression().missions(resolved, "CHALLENGE").thenApply(missions -> islandMessages.missionList("섬 챌린지", missions)), "챌린지 목록을 불러오지 못했습니다."));
    }

    public void completeMission(Player player, UUID islandId, String missionKey) {
        withResolvedIsland(player, islandId, "미션을 완료할 섬을 찾지 못했습니다.", "미션을 완료하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progressionCommands().completeMission(resolved, player.getUniqueId(), missionKey, "MISSION").thenApply(result -> islandMessages.missionResult("섬 미션", result)), "미션을 완료하지 못했습니다."));
    }

    public void completeChallenge(Player player, UUID islandId, String missionKey) {
        withResolvedIsland(player, islandId, "챌린지를 완료할 섬을 찾지 못했습니다.", "챌린지를 완료하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.progressionCommands().completeMission(resolved, player.getUniqueId(), missionKey, "CHALLENGE").thenApply(result -> islandMessages.missionResult("섬 챌린지", result)), "챌린지를 완료하지 못했습니다."));
    }

    public void listLimits(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "제한을 확인할 섬을 찾지 못했습니다.", "섬 제한을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.environment().limitViews(resolved).thenApply(islandMessages::limitList), "섬 제한을 불러오지 못했습니다."));
    }

    public void setLimit(Player player, UUID islandId, String limitKey, long value) {
        withResolvedIsland(player, islandId, "제한을 변경할 섬을 찾지 못했습니다.", "섬 제한을 변경하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.environmentCommands().setLimit(resolved, player.getUniqueId(), limitKey, value).thenApply(islandMessages::limitResult), "섬 제한을 변경하지 못했습니다."));
    }

    public void toggleBorder(Player player, UUID islandId, String requestedValue) {
        if (requestedValue != null && !requestedValue.isBlank()) {
            boolean enabled = "true".equals(toggleValue(requestedValue));
            sendTextResult(player, coreApiClient.playerProfileCommands().setWorldBorderEnabled(player.getUniqueId(), enabled)
                .thenApply(profile -> profile.worldBorderEnabled() ? "개인 섬 경계 표시를 켰습니다." : "개인 섬 경계 표시를 껐습니다."), "섬 경계 표시를 전환하지 못했습니다.");
            return;
        }
        sendTextResult(player, coreApiClient.playerProfiles().profile(player.getUniqueId())
            .thenCompose(profile -> coreApiClient.playerProfileCommands().setWorldBorderEnabled(player.getUniqueId(), !profile.worldBorderEnabled()))
            .thenApply(profile -> profile.worldBorderEnabled() ? "개인 섬 경계 표시를 켰습니다." : "개인 섬 경계 표시를 껐습니다."), "섬 경계 표시를 전환하지 못했습니다.");
    }

    public void toggleStackedBlocks(Player player, UUID islandId, String requestedValue) {
        if (requestedValue != null && !requestedValue.isBlank()) {
            boolean enabled = "true".equals(toggleValue(requestedValue));
            sendTextResult(player, coreApiClient.playerProfileCommands().setBlocksStackerEnabled(player.getUniqueId(), enabled)
                .thenApply(profile -> profile.blocksStackerEnabled() ? "개인 스택 블록 표시를 켰습니다." : "개인 스택 블록 표시를 껐습니다."), "스택 블록 표시를 전환하지 못했습니다.");
            return;
        }
        sendTextResult(player, coreApiClient.playerProfiles().profile(player.getUniqueId())
            .thenCompose(profile -> coreApiClient.playerProfileCommands().setBlocksStackerEnabled(player.getUniqueId(), !profile.blocksStackerEnabled()))
            .thenApply(profile -> profile.blocksStackerEnabled() ? "개인 스택 블록 표시를 켰습니다." : "개인 스택 블록 표시를 껐습니다."), "스택 블록 표시를 전환하지 못했습니다.");
    }

    public void setBorderColor(Player player, String requestedColor) {
        String color = kr.lunaf.cloudislands.api.model.PlayerIslandProfile.normalizeBorderColor(requestedColor);
        sendTextResult(player, coreApiClient.playerProfileCommands().setBorderColor(player.getUniqueId(), color)
            .thenCompose(profile -> profile.worldBorderEnabled()
                ? java.util.concurrent.CompletableFuture.completedFuture(profile)
                : coreApiClient.playerProfileCommands().setWorldBorderEnabled(player.getUniqueId(), true))
            .thenApply(profile -> "개인 섬 경계 색상을 변경했습니다: " + profile.borderColor()), "개인 섬 경계 색상을 변경하지 못했습니다.");
    }

    public void sendIslandChat(Player player, UUID islandId, String channel, String message) {
        if (message == null || message.isBlank()) {
            player.sendMessage(Component.text("보낼 메시지를 입력해주세요."));
            return;
        }
        String label = channel.equalsIgnoreCase("TEAM") ? "팀 채팅" : "섬 채팅";
        String stripped = message.strip();
        withResolvedIsland(player, islandId, "채팅을 보낼 섬을 찾지 못했습니다.", label + "을 전송하지 못했습니다.",
            resolved -> sendIslandChatResolved(player, resolved, channel, stripped, label));
    }

    public void showTeamChatMode(Player player) {
        player.sendMessage(Component.text("팀 채팅 모드: /섬 팀채팅 <message>로 팀 채널에 보낼 수 있습니다."));
    }

    public void showLocalChatMode(Player player) {
        player.sendMessage(Component.text("로컬 채팅 모드: Paper 서버에서 /섬 localchat으로 전환하거나 /섬 localchat <message>로 현재 섬 채널에 보낼 수 있습니다."));
    }

    private void sendIslandChatResolved(Player player, UUID islandId, String channel, String message, String label) {
        sendTextResult(player, coreApiClient.communicationCommands().sendChat(islandId, player.getUniqueId(), channel, message).thenApply(result -> islandMessages.chatResult(label, result)), label + "을 전송하지 못했습니다.");
    }

    private static String toggleValue(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("false") || normalized.equals("off") || normalized.equals("0") || normalized.equals("hide") || normalized.equals("hidden") || normalized.equals("끄기") || normalized.equals("숨김")) {
            return "false";
        }
        return "true";
    }

    public void listSnapshots(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "스냅샷을 확인할 섬을 찾지 못했습니다.", "스냅샷 목록을 불러오지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.snapshots().listSnapshots(resolved, 20).thenApply(this::snapshotListMessage), "스냅샷 목록을 불러오지 못했습니다."));
    }

    public void snapshot(Player player, UUID islandId, String reason) {
        if (!allowPlayerAction(player, SNAPSHOT_COOLDOWN, "섬 스냅샷 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        withResolvedIsland(player, islandId, "스냅샷을 만들 섬을 찾지 못했습니다.", "섬 스냅샷을 요청하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.snapshotCommands().requestSnapshot(resolved, reason).thenApply(result -> snapshotMessages.snapshotAction("섬 스냅샷 요청", result)), "섬 스냅샷을 요청하지 못했습니다."));
    }

    public void restore(Player player, UUID islandId, long snapshotNo) {
        if (!allowPlayerAction(player, RESTORE_COOLDOWN, "섬 복원 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요.")) {
            return;
        }
        withResolvedIsland(player, islandId, "복원할 섬을 찾지 못했습니다.", "섬 복원을 요청하지 못했습니다.",
            resolved -> sendTextResult(player, coreApiClient.snapshotCommands().restoreSnapshot(resolved, snapshotNo).thenApply(result -> snapshotMessages.snapshotAction("섬 복원", result)), "섬 복원을 요청하지 못했습니다."));
    }

    private static String blockDetailsMessage(ProgressionBlockDetailsView details) {
        if (details == null || details.blocks().isEmpty()) {
            return "섬 블록 기록이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        for (ProgressionBlockDetailView block : details.blocks()) {
            if (entries.size() >= 20) {
                break;
            }
            entries.add(block.materialKey() + " x" + block.count() + " 가치=" + block.totalWorth() + " 점수=" + block.levelPoints());
        }
        return entries.isEmpty()
            ? "섬 블록 기록이 없습니다."
            : "섬 블록상세: 총가치=" + details.totalWorth() + " 총점수=" + details.totalLevelPoints() + " | " + String.join(" | ", entries);
    }

    private static String reviewListMessage(ReviewListView reviews) {
        if (reviews == null || reviews.reviews().isEmpty()) {
            return "섬 후기가 없습니다.";
        }
        String average = String.format(Locale.ROOT, "%.2f", reviews.average());
        List<String> entries = reviews.reviews().stream()
            .limit(10)
            .filter(review -> !review.reviewerUuid().isBlank())
            .map(VelocityPlayerProgressionActions::reviewEntry)
            .toList();
        return entries.isEmpty()
            ? "섬 후기가 없습니다."
            : "섬 후기: 평균=" + average + " 개수=" + reviews.count() + " | " + String.join(" | ", entries);
    }

    private static String reviewEntry(ReviewView review) {
        return compactId(review.reviewerUuid()) + "=" + review.rating() + "/5" + (review.comment().isBlank() ? "" : " " + review.comment());
    }

    private static String compactId(String value) {
        if (value == null || value.length() <= 8) {
            return value == null ? "" : value;
        }
        return value.substring(0, 8);
    }
}
