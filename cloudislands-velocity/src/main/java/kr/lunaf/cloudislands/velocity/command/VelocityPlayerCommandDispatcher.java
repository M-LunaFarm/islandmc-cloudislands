package kr.lunaf.cloudislands.velocity.command;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import net.kyori.adventure.text.Component;

final class VelocityPlayerCommandDispatcher extends VelocityCommandSupport {
    private final VelocityPlayerMembershipCommandDispatcher membership;
    VelocityPlayerCommandDispatcher(ProxyServer proxy, VelocityRoutingController routingController, VelocityConfig config) {
        super(proxy, routingController, config);
        this.membership = new VelocityPlayerMembershipCommandDispatcher(proxy, routingController, config);
    }

    public void dispatch(Player player, String[] args) {
        if (isCommandListRequest(args)) {
            sendCommandList(player, "섬 명령어 목록", IslandCommandCatalog.playerCommands(), commandListPage(args), "섬 command list");
            return;
        }
        if (args.length == 0) {
            sendCommandList(player, "섬 명령어 목록", IslandCommandCatalog.playerCommands(), 1, "섬 command list");
            return;
        }
        if (args.length > 0 && (args[0].equalsIgnoreCase("menu") || args[0].equals("메뉴"))) {
            sendCommandList(player, "섬 명령어 목록", IslandCommandCatalog.playerCommands(), 1, "섬 command list");
            return;
        }
        if (args[0].equalsIgnoreCase("home") || args[0].equals("홈")) {
            player.sendActionBar(Component.text("섬을 준비하는 중입니다."));
            playerRouting.routeHome(player, args.length > 1 ? args[1] : "default");
            return;
        }
        if (args[0].equalsIgnoreCase("info") || args[0].equals("정보")) {
            playerRouting.showMyIsland(player);
            return;
        }
        if (args[0].equalsIgnoreCase("list") || args[0].equalsIgnoreCase("my") || args[0].equalsIgnoreCase("my-islands") || args[0].equals("목록") || args[0].equals("내섬")) {
            playerRouting.listMyIslands(player);
            return;
        }
        if (args[0].equalsIgnoreCase("settings") || args[0].equalsIgnoreCase("setting") || args[0].equals("설정")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.showIslandSettings(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("name") || args[0].equalsIgnoreCase("setname") || args[0].equalsIgnoreCase("rename") || args[0].equals("이름") || args[0].equals("이름설정")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("새 섬 이름을 입력해주세요."));
                return;
            }
            playerMembership.setIslandName(player, new UUID(0L, 0L), joinArgs(args, 1));
            return;
        }
        if (args[0].equalsIgnoreCase("level") || args[0].equals("레벨")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.showIslandLevel(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("worth") || args[0].equals("value") || args[0].equals("가치")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.showIslandWorth(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("size") || args[0].equals("크기")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.showIslandSize(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("border") || args[0].equals("경계")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.showIslandBorder(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("biome-menu") || args[0].equalsIgnoreCase("biome-info") || args[0].equals("바이옴정보")) {
            UUID islandId = args.length > 1 && isUuid(args[1]) ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.showBiome(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("biome") || args[0].equals("바이옴")) {
            UUID islandId = args.length > 2 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 1) {
                playerRouting.setBiome(player, islandId, args.length > 2 ? args[2] : args[1]);
            } else {
                playerRouting.showBiome(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("homes") || args[0].equalsIgnoreCase("home-list") || args[0].equalsIgnoreCase("home-menu") || args[0].equals("홈목록") || args[0].equals("홈관리")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.listHomes(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("sethome") || args[0].equals("셋홈")) {
            boolean hasIslandId = args.length > 1 && isUuid(args[1]);
            UUID islandId = hasIslandId ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String name = args.length > (hasIslandId ? 2 : 1) ? args[hasIslandId ? 2 : 1] : "default";
            playerMembership.setHome(player, islandId, name);
            return;
        }
        if (args[0].equalsIgnoreCase("visit") || args[0].equals("방문")) {
            player.sendActionBar(Component.text("방문할 섬을 불러오는 중입니다."));
            if (args.length < 2) {
                playerRouting.routeRandomVisit(player);
                return;
            }
            if (args[1].equalsIgnoreCase("random") || args[1].equals("랜덤")) {
                playerRouting.routeRandomVisit(player);
                return;
            }
            UUID targetIslandId = parseUuidOrNil(args[1]);
            if (!targetIslandId.equals(new UUID(0L, 0L))) {
                playerRouting.routeVisit(player, targetIslandId);
                return;
            }
            playerRouting.routeVisitNamedTarget(player, args[1]);
            return;
        }
        if (args[0].equalsIgnoreCase("randomvisit") || args[0].equalsIgnoreCase("random-visit") || args[0].equals("랜덤방문")) {
            player.sendActionBar(Component.text("방문 가능한 공개 섬을 찾는 중입니다."));
            playerRouting.routeRandomVisit(player);
            return;
        }
        if (args[0].equalsIgnoreCase("public-islands") || args[0].equalsIgnoreCase("publicislands") || args[0].equalsIgnoreCase("visit-list") || args[0].equals("공개섬") || args[0].equals("방문목록")) {
            playerRouting.listPublicIslands(player, args.length > 1 ? (int) parseLongOrZero(args[1]) : 10);
            return;
        }
        if (args[0].equalsIgnoreCase("warps") || args[0].equalsIgnoreCase("warp-list") || args[0].equalsIgnoreCase("warp-menu") || args[0].equals("워프목록") || args[0].equals("워프관리")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.listWarps(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("publicwarplist") || args[0].equalsIgnoreCase("public-warps") || args[0].equals("공개워프목록")) {
            playerRouting.listPublicWarps(player);
            return;
        }
        if (args[0].equalsIgnoreCase("setwarp") || args[0].equals("워프설정")) {
            boolean hasIslandId = args.length > 1 && isUuid(args[1]);
            UUID islandId = hasIslandId ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > (hasIslandId ? 2 : 1) ? args[hasIslandId ? 2 : 1] : "default";
            playerRouting.setWarp(player, islandId, warpName, false);
            return;
        }
        if (args[0].equalsIgnoreCase("deletewarp") || args[0].equalsIgnoreCase("delwarp") || args[0].equals("워프삭제")) {
            boolean hasIslandId = args.length > 1 && isUuid(args[1]);
            UUID islandId = hasIslandId ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > (hasIslandId ? 2 : 1) ? args[hasIslandId ? 2 : 1] : "default";
            playerRouting.deleteWarp(player, islandId, warpName);
            return;
        }
        if (args[0].equalsIgnoreCase("publicwarp") || args[0].equalsIgnoreCase("warp-public") || args[0].equals("워프공개")) {
            boolean hasIslandId = args.length > 1 && isUuid(args[1]);
            UUID islandId = hasIslandId ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > (hasIslandId ? 2 : 1) ? args[hasIslandId ? 2 : 1] : "default";
            playerRouting.setWarpPublicAccess(player, islandId, warpName, true);
            return;
        }
        if (args[0].equalsIgnoreCase("privatewarp") || args[0].equalsIgnoreCase("warp-private") || args[0].equals("워프비공개")) {
            boolean hasIslandId = args.length > 1 && isUuid(args[1]);
            UUID islandId = hasIslandId ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > (hasIslandId ? 2 : 1) ? args[hasIslandId ? 2 : 1] : "default";
            playerRouting.setWarpPublicAccess(player, islandId, warpName, false);
            return;
        }
        if (args[0].equalsIgnoreCase("warp") || args[0].equals("워프")) {
            player.sendActionBar(Component.text("섬 워프로 이동하는 중입니다."));
            UUID targetIslandId = routeTargetIslandId(args, 1);
            String warpName = routeWarpName(args, 1, "default");
            playerRouting.routeWarp(player, targetIslandId, warpName);
            return;
        }
        if (membership.dispatch(player, args)) {
            return;
        }
        if (args[0].equalsIgnoreCase("bank") || args[0].equalsIgnoreCase("bank-balance") || args[0].equals("은행") || args[0].equals("은행잔액")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerMembership.showBank(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("deposit") || args[0].equalsIgnoreCase("bank-deposit") || args[0].equals("입금")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String amount = argumentAfterOptionalIsland(args, 1, "0");
            playerMembership.depositBank(player, islandId, amount);
            return;
        }
        if (args[0].equalsIgnoreCase("withdraw") || args[0].equalsIgnoreCase("bank-withdraw") || args[0].equals("출금")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String amount = argumentAfterOptionalIsland(args, 1, "0");
            playerMembership.withdrawBank(player, islandId, amount);
            return;
        }
        if (args[0].equalsIgnoreCase("worthrank") || args[0].equalsIgnoreCase("valuerank") || args[0].equals("가치랭킹") || (args.length > 1 && (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking") || args[0].equals("랭킹")) && (args[1].equalsIgnoreCase("worth") || args[1].equalsIgnoreCase("value") || args[1].equals("가치")))) {
            int limit = args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking") || args[0].equals("랭킹")
                ? (args.length > 2 ? (int) parseLongOrZero(args[2]) : 10)
                : (args.length > 1 ? (int) parseLongOrZero(args[1]) : 10);
            playerProgression.showWorthRanking(player, limit);
            return;
        }
        if (args[0].equalsIgnoreCase("rank") || args[0].equals("ranking") || args[0].equalsIgnoreCase("rank-list") || args[0].equals("랭킹") || args[0].equals("랭킹목록")) {
            playerProgression.showLevelRanking(player, args.length > 1 ? (int) parseLongOrZero(args[1]) : 10);
            return;
        }
        if (args[0].equalsIgnoreCase("levelcalc") || args[0].equalsIgnoreCase("recalculate") || args[0].equals("레벨계산")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerProgression.recalculateLevel(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("upgrade") || args[0].equalsIgnoreCase("upgrade-menu") || args[0].equals("업그레이드")) {
            playerProgression.listUpgradeRules(player);
            return;
        }
        if (args[0].equalsIgnoreCase("upgrades") || args[0].equalsIgnoreCase("upgrade-list") || args[0].equals("업그레이드목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerProgression.listUpgrades(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("generator") || args[0].equalsIgnoreCase("generator-info") || args[0].equals("생성기") || args[0].equals("생성기정보")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            playerProgression.showGenerator(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("buyupgrade") || args[0].equalsIgnoreCase("upgrade-buy") || args[0].equals("업그레이드구매")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            String upgradeKey = argumentAfterOptionalIsland(args, 1, "size");
            playerProgression.purchaseUpgrade(player, islandId, upgradeKey);
            return;
        }
        if (args[0].equalsIgnoreCase("mission-menu") || args[0].equalsIgnoreCase("mission-list") || args[0].equals("미션목록")) {
            UUID islandId = args.length > 1 && isUuid(args[1]) ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerProgression.listMissions(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("mission") || args[0].equals("missions") || args[0].equals("미션")) {
            UUID islandId = args.length > 1 && isUuid(args[1]) ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            int missionIndex = islandId.equals(new UUID(0L, 0L)) ? 1 : 2;
            if (args.length > missionIndex) {
                playerProgression.completeMission(player, islandId, args[missionIndex]);
            } else {
                playerProgression.listMissions(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("challenge-menu") || args[0].equalsIgnoreCase("challenge-list") || args[0].equals("챌린지목록")) {
            UUID islandId = args.length > 1 && isUuid(args[1]) ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerProgression.listChallenges(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("challenge") || args[0].equals("challenges") || args[0].equals("챌린지")) {
            UUID islandId = args.length > 1 && isUuid(args[1]) ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            int challengeIndex = islandId.equals(new UUID(0L, 0L)) ? 1 : 2;
            if (args.length > challengeIndex) {
                playerProgression.completeChallenge(player, islandId, args[challengeIndex]);
            } else {
                playerProgression.listChallenges(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("chat") || args[0].equalsIgnoreCase("islandchat") || args[0].equals("채팅")) {
            playerProgression.sendIslandChat(player, new UUID(0L, 0L), "ISLAND", joinArgs(args, 1));
            return;
        }
        if (args[0].equalsIgnoreCase("teamchat") || args[0].equalsIgnoreCase("team-chat") || args[0].equals("팀채팅")) {
            playerProgression.sendIslandChat(player, new UUID(0L, 0L), "TEAM", joinArgs(args, 1));
            return;
        }
        if (args[0].equalsIgnoreCase("limit-menu")) {
            UUID islandId = args.length > 1 && isUuid(args[1]) ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerProgression.listLimits(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("limits") || args[0].equalsIgnoreCase("limit") || args[0].equalsIgnoreCase("limit-list") || args[0].equalsIgnoreCase("setlimit") || args[0].equalsIgnoreCase("limit-set") || args[0].equals("제한") || args[0].equals("제한목록") || args[0].equals("제한설정")) {
            UUID islandId = islandIdArgument(args, 1);
            int valueIndex = hasIslandIdArgument(args, 1) ? 3 : 2;
            if (args.length > valueIndex) {
                playerProgression.setLimit(player, islandId, args[valueIndex - 1], parseLongOrZero(args[valueIndex]));
            } else {
                playerProgression.listLimits(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("hoppers") || args[0].equals("호퍼")) {
            UUID islandId = islandIdArgument(args, 1);
            playerProgression.setLimit(player, islandId, "HOPPER", parseLongOrZero(argumentAfterIslandId(args, 1, "0")));
            return;
        }
        if (args[0].equalsIgnoreCase("spawners") || args[0].equals("스포너")) {
            UUID islandId = islandIdArgument(args, 1);
            playerProgression.setLimit(player, islandId, "SPAWNER", parseLongOrZero(argumentAfterIslandId(args, 1, "0")));
            return;
        }
        if (args[0].equalsIgnoreCase("entities") || args[0].equals("엔티티")) {
            UUID islandId = islandIdArgument(args, 1);
            playerProgression.setLimit(player, islandId, "ENTITY", parseLongOrZero(argumentAfterIslandId(args, 1, "0")));
            return;
        }
        if (args[0].equalsIgnoreCase("redstone") || args[0].equals("레드스톤")) {
            UUID islandId = islandIdArgument(args, 1);
            playerProgression.setLimit(player, islandId, "REDSTONE", parseLongOrZero(argumentAfterIslandId(args, 1, "0")));
            return;
        }
        if (args[0].equalsIgnoreCase("snapshots") || args[0].equalsIgnoreCase("snapshot-menu") || args[0].equalsIgnoreCase("snapshot-list") || args[0].equals("스냅샷목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerProgression.listSnapshots(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("snapshot") || args[0].equalsIgnoreCase("snapshot-create") || args[0].equalsIgnoreCase("snapshot-request") || args[0].equals("스냅샷") || args[0].equals("스냅샷생성")) {
            boolean hasIslandId = args.length > 1 && isUuid(args[1]);
            UUID islandId = hasIslandId ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String reason = joinArgs(args, hasIslandId ? 2 : 1);
            if (reason.isBlank()) {
                reason = "MANUAL";
            }
            playerProgression.snapshot(player, islandId, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("restore") || args[0].equalsIgnoreCase("snapshot-restore") || args[0].equalsIgnoreCase("rollback") || args[0].equals("복원") || args[0].equals("스냅샷복원") || args[0].equals("롤백")) {
            UUID islandId = optionalIslandIdArgument(args, 1);
            long snapshotNo = parseLongOrZero(argumentAfterOptionalIsland(args, 1, "0"));
            playerProgression.restore(player, islandId, snapshotNo);
            return;
        }
        if (args[0].equalsIgnoreCase("create-menu") || args[0].equalsIgnoreCase("templates") || args[0].equals("생성메뉴") || args[0].equals("템플릿")) {
            adminActions.listTemplates(player);
            return;
        }
        if (args[0].equalsIgnoreCase("create") || args[0].equals("생성")) {
            String templateId = args.length > 1 ? args[1] : "default";
            player.sendActionBar(Component.text("섬 생성 요청을 접수했습니다."));
            playerRouting.createIsland(player, templateId);
            return;
        }
        if (args[0].equalsIgnoreCase("danger") || args[0].equals("위험작업")) {
            sendCommandList(player, "섬 위험 작업", List.of("섬 리셋 [reason]", "섬 삭제"), 1, "섬 command list");
            return;
        }
        if (args[0].equalsIgnoreCase("reset") || args[0].equals("리셋")) {
            boolean hasIslandId = args.length > 1 && isUuid(args[1]);
            UUID islandId = hasIslandId ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String reason = args.length > (hasIslandId ? 2 : 1) ? joinArgs(args, hasIslandId ? 2 : 1) : "PLAYER_RESET";
            playerRouting.resetIsland(player, islandId, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("delete") || args[0].equals("삭제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            playerRouting.deleteIsland(player, islandId);
            return;
        }
        sendCommandList(player, "섬 명령어 목록", IslandCommandCatalog.playerCommands(), 1, "섬 command list");
    }


}
