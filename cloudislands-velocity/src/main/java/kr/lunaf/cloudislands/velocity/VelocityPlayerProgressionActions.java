package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.parseLong;
import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import net.kyori.adventure.text.Component;

public final class VelocityPlayerProgressionActions extends VelocityActionSupport {
    VelocityPlayerProgressionActions(VelocityActionContext context) {
        super(context);
    }

    public void showLevelRanking(Player player, int limit) {
        sendBodyResult(player, coreApiClient.topIslandsByLevel(Math.max(1, Math.min(limit, 100))).thenApply(body -> islandMessages.rankingList("섬 랭킹", body)), "랭킹을 불러오지 못했습니다.");
    }

    public void showWorthRanking(Player player, int limit) {
        sendBodyResult(player, coreApiClient.topIslandsByWorth(Math.max(1, Math.min(limit, 100))).thenApply(body -> islandMessages.rankingList("가치 랭킹", body)), "가치 랭킹을 불러오지 못했습니다.");
    }

    public void recalculateLevel(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "레벨을 계산할 섬을 찾지 못했습니다.", "레벨 계산을 시작하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.recalculateIslandLevel(resolved, player.getUniqueId()).thenApply(islandMessages::levelRecalculation), "레벨 계산을 시작하지 못했습니다."));
    }

    public void listUpgradeRules(Player player) {
        sendBodyResult(player, coreApiClient.listUpgradeRules().thenApply(islandMessages::upgradeRules), "업그레이드 목록을 불러오지 못했습니다.");
    }

    public void listUpgrades(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "업그레이드를 확인할 섬을 찾지 못했습니다.", "섬 업그레이드를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandUpgrades(resolved).thenApply(islandMessages::upgradeList), "섬 업그레이드를 불러오지 못했습니다."));
    }

    public void showGenerator(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "생성기를 확인할 섬을 찾지 못했습니다.", "섬 생성기를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandUpgrades(resolved).thenApply(islandMessages::generatorInfo), "섬 생성기를 불러오지 못했습니다."));
    }

    public void purchaseUpgrade(Player player, UUID islandId, String upgradeKey) {
        withResolvedIsland(player, islandId, "업그레이드할 섬을 찾지 못했습니다.", "업그레이드에 실패했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.purchaseIslandUpgrade(resolved, player.getUniqueId(), upgradeKey).thenApply(islandMessages::upgradePurchase), "업그레이드에 실패했습니다."));
    }

    public void listMissions(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "미션을 확인할 섬을 찾지 못했습니다.", "미션 목록을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandMissions(resolved, "MISSION").thenApply(body -> islandMessages.missionList("섬 미션", body)), "미션 목록을 불러오지 못했습니다."));
    }

    public void listChallenges(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "챌린지를 확인할 섬을 찾지 못했습니다.", "챌린지 목록을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandMissions(resolved, "CHALLENGE").thenApply(body -> islandMessages.missionList("섬 챌린지", body)), "챌린지 목록을 불러오지 못했습니다."));
    }

    public void completeMission(Player player, UUID islandId, String missionKey) {
        withResolvedIsland(player, islandId, "미션을 완료할 섬을 찾지 못했습니다.", "미션을 완료하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.completeIslandMission(resolved, player.getUniqueId(), missionKey, "MISSION").thenApply(body -> islandMessages.missionResult("섬 미션", body)), "미션을 완료하지 못했습니다."));
    }

    public void completeChallenge(Player player, UUID islandId, String missionKey) {
        withResolvedIsland(player, islandId, "챌린지를 완료할 섬을 찾지 못했습니다.", "챌린지를 완료하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.completeIslandMission(resolved, player.getUniqueId(), missionKey, "CHALLENGE").thenApply(body -> islandMessages.missionResult("섬 챌린지", body)), "챌린지를 완료하지 못했습니다."));
    }

    public void listLimits(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "제한을 확인할 섬을 찾지 못했습니다.", "섬 제한을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandLimits(resolved).thenApply(islandMessages::limitList), "섬 제한을 불러오지 못했습니다."));
    }

    public void setLimit(Player player, UUID islandId, String limitKey, long value) {
        withResolvedIsland(player, islandId, "제한을 변경할 섬을 찾지 못했습니다.", "섬 제한을 변경하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.setIslandLimit(resolved, player.getUniqueId(), limitKey, value).thenApply(islandMessages::limitResult), "섬 제한을 변경하지 못했습니다."));
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

    private void sendIslandChatResolved(Player player, UUID islandId, String channel, String message, String label) {
        sendBodyResult(player, coreApiClient.sendIslandChat(islandId, player.getUniqueId(), channel, message).thenApply(body -> islandMessages.chatResult(label, body)), label + "을 전송하지 못했습니다.");
    }

    public void listSnapshots(Player player, UUID islandId) {
        withResolvedIsland(player, islandId, "스냅샷을 확인할 섬을 찾지 못했습니다.", "스냅샷 목록을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandSnapshots(resolved, 20).thenApply(this::snapshotListMessage), "스냅샷 목록을 불러오지 못했습니다."));
    }

    public void snapshot(Player player, UUID islandId, String reason) {
        withResolvedIsland(player, islandId, "스냅샷을 만들 섬을 찾지 못했습니다.", "섬 스냅샷을 요청하지 못했습니다.",
            resolved -> sendActionResult(player, coreApiClient.requestIslandSnapshot(resolved, reason), "섬 스냅샷을 요청했습니다.", "섬 스냅샷을 요청하지 못했습니다."));
    }

    public void restore(Player player, UUID islandId, long snapshotNo) {
        withResolvedIsland(player, islandId, "복원할 섬을 찾지 못했습니다.", "섬 복원을 요청하지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.restoreIslandSnapshotResult(resolved, snapshotNo).thenApply(body -> islandMessages.actionResult("Island restore", resolved.toString(), body)), "섬 복원을 요청하지 못했습니다."));
    }
}
