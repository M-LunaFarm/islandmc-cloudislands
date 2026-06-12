package kr.lunaf.cloudislands.paper.command;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.gui.IslandBankMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBanMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBiomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandChatMenu;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
import kr.lunaf.cloudislands.paper.gui.IslandDangerMenu;
import kr.lunaf.cloudislands.paper.gui.IslandFlagMenu;
import kr.lunaf.cloudislands.paper.gui.IslandHomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandInfoMenu;
import kr.lunaf.cloudislands.paper.gui.IslandInviteMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLimitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLogMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMainMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMemberMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMyIslandsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandPermissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandRankingMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSettingsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSnapshotMenu;
import kr.lunaf.cloudislands.paper.gui.IslandUpgradeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandWarpMenu;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public final class IslandCommandController implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of(
        "menu", "메뉴",
        "create-menu", "templates", "생성메뉴", "템플릿",
        "info", "정보", "list", "my", "my-islands", "목록", "내섬", "create", "생성", "delete", "삭제", "reset", "리셋", "danger", "위험작업",
        "sethome", "셋홈", "homes", "home-menu", "home-list", "홈관리", "홈목록", "home", "홈",
        "warps", "warp-menu", "warp-list", "워프", "워프관리", "워프목록", "public-warps", "publicwarplist", "공개워프목록", "warp", "setwarp", "워프설정",
        "delwarp", "deletewarp", "워프삭제", "warp-public", "publicwarp", "워프공개", "warp-private", "privatewarp", "워프비공개",
        "public", "공개", "private", "비공개", "lock", "잠금", "unlock", "잠금해제",
        "fly", "비행", "keepinventory", "keepinv", "인벤보존", "pvp", "피빕", "publicwarps", "공개워프",
        "visit", "randomvisit", "random-visit", "방문", "랜덤방문",
        "level", "레벨", "worth", "value", "가치", "rank", "ranking", "rank-list", "worthrank", "valuerank", "랭킹", "랭킹목록", "가치랭킹", "levelcalc", "recalculate", "레벨계산",
        "bank", "bank-balance", "은행", "은행잔액", "deposit", "bank-deposit", "입금", "withdraw", "bank-withdraw", "출금",
        "upgrade", "upgrades", "upgrade-menu", "upgrade-list", "buyupgrade", "upgrade-buy", "업그레이드", "업그레이드목록", "업그레이드구매",
        "mission", "missions", "mission-menu", "mission-list", "미션", "미션목록",
        "challenge", "challenges", "challenge-menu", "challenge-list", "챌린지", "챌린지목록",
        "chat", "chat-menu", "islandchat", "채팅", "teamchat", "team-chat", "팀채팅", "log", "logs", "log-menu", "log-list", "로그", "로그목록",
        "biome", "biome-menu", "biome-info", "바이옴", "바이옴정보", "size", "크기", "border", "경계",
        "limit", "limits", "limit-menu", "limit-list", "제한", "제한목록", "setlimit", "limit-set", "제한설정",
        "hoppers", "호퍼", "spawners", "스포너", "entities", "엔티티", "redstone", "레드스톤",
        "snapshot", "snapshots", "snapshot-menu", "snapshot-list", "스냅샷", "스냅샷목록", "snapshot-create", "snapshot-request", "스냅샷생성",
        "snapshot-restore", "restore", "rollback", "스냅샷복원", "복원", "롤백",
        "members", "member-menu", "member-list", "멤버", "멤버관리", "멤버목록", "invite", "초대", "invites", "invite-menu", "invite-list", "초대목록",
        "accept", "invite-accept", "초대수락", "decline", "invite-decline", "초대거절",
        "kick", "remove-member", "추방", "trust", "신뢰", "untrust", "신뢰해제",
        "promote", "승급", "demote", "강등", "transfer", "양도",
        "ban", "밴", "unban", "pardon", "밴해제", "kickvisitor", "방문자추방", "bans", "ban-menu", "ban-list", "banlist", "밴목록",
        "settings", "setting", "설정",
        "flags", "flag-menu", "flag-list", "flag", "setflag", "flag-set", "플래그", "플래그설정", "플래그목록",
        "permissions", "permission-menu", "permission-list", "permission", "perms", "setpermission", "permission-set", "권한", "권한설정", "권한목록"
    );
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final ProtectionController protection;

    public IslandCommandController(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.protection = protection;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String typed = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String subcommand : SUBCOMMANDS) {
            if (typed.isBlank() || subcommand.toLowerCase(Locale.ROOT).startsWith(typed)) {
                matches.add(subcommand);
            }
        }
        return matches;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (args.length == 0) {
            IslandMainMenu.open(player);
            return true;
        }
        String subcommand = args[0].toLowerCase();
        if (subcommand.equals("menu") || subcommand.equals("메뉴")) {
            IslandMainMenu.open(player);
            return true;
        }
        if (subcommand.equals("create-menu") || subcommand.equals("templates") || subcommand.equals("생성메뉴") || subcommand.equals("템플릿")) {
            IslandCreateMenu.open(plugin, coreApiClient, player);
            return true;
        }
        if (subcommand.equals("create") || subcommand.equals("생성")) {
            createIsland(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("info") || subcommand.equals("정보")) {
            openIslandInfoMenu(player);
            return true;
        }
        if (subcommand.equals("list") || subcommand.equals("my") || subcommand.equals("my-islands") || subcommand.equals("목록") || subcommand.equals("내섬")) {
            IslandMyIslandsMenu.open(plugin, coreApiClient, player);
            return true;
        }
        if (subcommand.equals("delete") || subcommand.equals("삭제")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                deleteIsland(player);
            } else {
                IslandDangerMenu.open(player);
            }
            return true;
        }
        if (subcommand.equals("reset") || subcommand.equals("리셋")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                resetIsland(player, args.length > 2 ? joined(args, 2) : "player-reset");
            } else {
                IslandDangerMenu.open(player);
            }
            return true;
        }
        if (subcommand.equals("danger") || subcommand.equals("위험작업")) {
            IslandDangerMenu.open(player);
            return true;
        }
        if (subcommand.equals("sethome") || subcommand.equals("셋홈")) {
            setHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("homes") || subcommand.equals("home-menu") || subcommand.equals("홈관리")) {
            openIslandHomeMenu(player);
            return true;
        }
        if (subcommand.equals("home-list") || subcommand.equals("홈목록")) {
            listHomes(player);
            return true;
        }
        if (subcommand.equals("home") || subcommand.equals("홈")) {
            teleportHome(player, args.length > 1 ? args[1] : "default");
            return true;
        }
        if (subcommand.equals("warps") || subcommand.equals("warp-menu") || subcommand.equals("워프") || subcommand.equals("워프관리")) {
            if (args.length > 2) {
                UUID islandId = uuid(args[1]);
                if (islandId != null) {
                    routeWarp(player, islandId, args[2]);
                    return true;
                }
            }
            if (args.length == 2) {
                UUID islandId = uuid(args[1]);
                if (islandId != null) {
                    routeWarp(player, islandId, "default");
                    return true;
                }
            }
            if (args.length > 1) {
                teleportWarp(player, args[1]);
            } else {
                openIslandWarpMenu(player);
            }
            return true;
        }
        if (subcommand.equals("warp-list") || subcommand.equals("워프목록")) {
            listWarps(player);
            return true;
        }
        if (subcommand.equals("public-warps") || subcommand.equals("publicwarplist") || subcommand.equals("공개워프목록")) {
            IslandWarpMenu.openPublic(plugin, coreApiClient, player);
            return true;
        }
        if (subcommand.equals("warp")) {
            if (args.length > 2) {
                UUID islandId = uuid(args[1]);
                if (islandId == null) {
                    player.sendMessage("섬 UUID가 올바르지 않습니다.");
                    return true;
                }
                routeWarp(player, islandId, args[2]);
                return true;
            }
            if (args.length == 2) {
                UUID islandId = uuid(args[1]);
                if (islandId != null) {
                    routeWarp(player, islandId, "default");
                    return true;
                }
            }
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            teleportWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("setwarp") || subcommand.equals("워프설정")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            setWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("delwarp") || subcommand.equals("deletewarp") || subcommand.equals("워프삭제")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            deleteWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("warp-public") || subcommand.equals("publicwarp") || subcommand.equals("워프공개")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            setWarpPublicAccess(player, args[1], true);
            return true;
        }
        if (subcommand.equals("warp-private") || subcommand.equals("privatewarp") || subcommand.equals("워프비공개")) {
            if (args.length < 2) {
                player.sendMessage("워프 이름을 입력해주세요.");
                return true;
            }
            setWarpPublicAccess(player, args[1], false);
            return true;
        }
        if (subcommand.equals("public") || subcommand.equals("공개")) {
            setIslandPublicAccess(player, true);
            return true;
        }
        if (subcommand.equals("private") || subcommand.equals("비공개")) {
            setIslandPublicAccess(player, false);
            return true;
        }
        if (subcommand.equals("lock") || subcommand.equals("잠금")) {
            setIslandLocked(player, true);
            return true;
        }
        if (subcommand.equals("unlock") || subcommand.equals("잠금해제")) {
            setIslandLocked(player, false);
            return true;
        }
        if (subcommand.equals("visit") || subcommand.equals("방문")) {
            if (args.length < 2) {
                IslandVisitMenu.open(plugin, coreApiClient, player);
            } else if (args[1].equalsIgnoreCase("random") || args[1].equals("랜덤")) {
                routeRandomVisit(player);
            } else {
                routeVisitTarget(player, args[1]);
            }
            return true;
        }
        if (subcommand.equals("randomvisit") || subcommand.equals("random-visit") || subcommand.equals("랜덤방문")) {
            routeRandomVisit(player);
            return true;
        }
        if (subcommand.equals("level") || subcommand.equals("레벨")) {
            showIslandLevel(player);
            return true;
        }
        if (subcommand.equals("worth") || subcommand.equals("value") || subcommand.equals("가치")) {
            showIslandWorth(player);
            return true;
        }
        if (subcommand.equals("rank") || subcommand.equals("ranking") || subcommand.equals("랭킹")) {
            if (args.length > 1) {
                boolean worthRanking = args[1].equalsIgnoreCase("worth") || args[1].equals("가치");
                listIslandRanking(player, worthRanking);
            } else {
                IslandRankingMenu.open(plugin, coreApiClient, player);
            }
            return true;
        }
        if (subcommand.equals("rank-list") || subcommand.equals("랭킹목록")) {
            boolean worthRanking = args.length > 1 && (args[1].equalsIgnoreCase("worth") || args[1].equals("가치"));
            listIslandRanking(player, worthRanking);
            return true;
        }
        if (subcommand.equals("worthrank") || subcommand.equals("valuerank") || subcommand.equals("가치랭킹")) {
            listIslandRanking(player, true);
            return true;
        }
        if (subcommand.equals("levelcalc") || subcommand.equals("recalculate") || subcommand.equals("레벨계산")) {
            recalculateIslandLevel(player);
            return true;
        }
        if (subcommand.equals("bank") || subcommand.equals("은행")) {
            openIslandBankMenu(player);
            return true;
        }
        if (subcommand.equals("bank-balance") || subcommand.equals("은행잔액")) {
            showIslandBank(player);
            return true;
        }
        if (subcommand.equals("deposit") || subcommand.equals("bank-deposit") || subcommand.equals("입금")) {
            if (args.length < 2) {
                player.sendMessage("입금할 금액을 입력해주세요.");
                return true;
            }
            depositIslandBank(player, args[1]);
            return true;
        }
        if (subcommand.equals("withdraw") || subcommand.equals("bank-withdraw") || subcommand.equals("출금")) {
            if (args.length < 2) {
                player.sendMessage("출금할 금액을 입력해주세요.");
                return true;
            }
            withdrawIslandBank(player, args[1]);
            return true;
        }
        if (subcommand.equals("upgrade") || subcommand.equals("upgrades") || subcommand.equals("업그레이드")) {
            if (args.length > 1) {
                purchaseIslandUpgrade(player, args[1]);
            } else {
                openIslandUpgradeMenu(player);
            }
            return true;
        }
        if (subcommand.equals("upgrade-menu")) {
            openIslandUpgradeMenu(player);
            return true;
        }
        if (subcommand.equals("upgrade-list") || subcommand.equals("업그레이드목록")) {
            listIslandUpgrades(player);
            return true;
        }
        if (subcommand.equals("buyupgrade") || subcommand.equals("upgrade-buy") || subcommand.equals("업그레이드구매")) {
            if (args.length < 2) {
                player.sendMessage("구매할 업그레이드 키를 입력해주세요.");
                return true;
            }
            purchaseIslandUpgrade(player, args[1]);
            return true;
        }
        if (subcommand.equals("mission") || subcommand.equals("missions") || subcommand.equals("미션")) {
            if (args.length > 1) {
                completeIslandMission(player, args[1]);
            } else {
                openIslandMissionMenu(player, "MISSION");
            }
            return true;
        }
        if (subcommand.equals("mission-menu")) {
            openIslandMissionMenu(player, "MISSION");
            return true;
        }
        if (subcommand.equals("mission-list") || subcommand.equals("미션목록")) {
            listIslandMissions(player, "MISSION", "섬 미션");
            return true;
        }
        if (subcommand.equals("challenge") || subcommand.equals("challenges") || subcommand.equals("챌린지")) {
            if (args.length > 1) {
                completeIslandChallenge(player, args[1]);
            } else {
                openIslandMissionMenu(player, "CHALLENGE");
            }
            return true;
        }
        if (subcommand.equals("challenge-menu")) {
            openIslandMissionMenu(player, "CHALLENGE");
            return true;
        }
        if (subcommand.equals("challenge-list") || subcommand.equals("챌린지목록")) {
            listIslandMissions(player, "CHALLENGE", "섬 챌린지");
            return true;
        }
        if (subcommand.equals("chat") || subcommand.equals("islandchat") || subcommand.equals("채팅")) {
            if (args.length < 2) {
                IslandChatMenu.open(player);
                return true;
            }
            sendIslandChat(player, "ISLAND", joined(args, 1), "섬 채팅");
            return true;
        }
        if (subcommand.equals("chat-menu")) {
            IslandChatMenu.open(player);
            return true;
        }
        if (subcommand.equals("teamchat") || subcommand.equals("team-chat") || subcommand.equals("팀채팅")) {
            if (args.length < 2) {
                IslandChatMenu.open(player);
                return true;
            }
            sendIslandChat(player, "TEAM", joined(args, 1), "팀 채팅");
            return true;
        }
        if (subcommand.equals("log") || subcommand.equals("log-menu") || subcommand.equals("로그")) {
            openIslandLogMenu(player);
            return true;
        }
        if (subcommand.equals("logs") || subcommand.equals("log-list") || subcommand.equals("로그목록")) {
            listIslandLogs(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("biome") || subcommand.equals("바이옴")) {
            if (args.length > 1) {
                setIslandBiome(player, args[1]);
            } else {
                openIslandBiomeMenu(player);
            }
            return true;
        }
        if (subcommand.equals("biome-menu")) {
            openIslandBiomeMenu(player);
            return true;
        }
        if (subcommand.equals("biome-info") || subcommand.equals("바이옴정보")) {
            showIslandBiome(player);
            return true;
        }
        if (subcommand.equals("size") || subcommand.equals("크기")) {
            showIslandSize(player);
            return true;
        }
        if (subcommand.equals("border") || subcommand.equals("경계")) {
            showIslandBorder(player);
            return true;
        }
        if (subcommand.equals("limit") || subcommand.equals("limits") || subcommand.equals("limit-list") || subcommand.equals("제한") || subcommand.equals("제한목록")) {
            if (args.length > 2) {
                setIslandLimit(player, args[1], longValue(args[2], 0L));
            } else if (subcommand.equals("limit-list") || subcommand.equals("제한목록")) {
                listIslandLimits(player);
            } else {
                openIslandLimitMenu(player);
            }
            return true;
        }
        if (subcommand.equals("limit-menu")) {
            openIslandLimitMenu(player);
            return true;
        }
        if (subcommand.equals("setlimit") || subcommand.equals("limit-set") || subcommand.equals("제한설정")) {
            if (args.length < 3) {
                player.sendMessage("제한 키와 값을 입력해주세요.");
                return true;
            }
            setIslandLimit(player, args[1], longValue(args[2], 0L));
            return true;
        }
        if (subcommand.equals("hoppers") || subcommand.equals("호퍼")) {
            setNamedIslandLimit(player, "HOPPER", args);
            return true;
        }
        if (subcommand.equals("spawners") || subcommand.equals("스포너")) {
            setNamedIslandLimit(player, "SPAWNER", args);
            return true;
        }
        if (subcommand.equals("entities") || subcommand.equals("엔티티")) {
            setNamedIslandLimit(player, "ENTITY", args);
            return true;
        }
        if (subcommand.equals("redstone") || subcommand.equals("레드스톤")) {
            setNamedIslandLimit(player, "REDSTONE", args);
            return true;
        }
        if (subcommand.equals("snapshot") || subcommand.equals("snapshots") || subcommand.equals("snapshot-menu") || subcommand.equals("스냅샷")) {
            openIslandSnapshotMenu(player);
            return true;
        }
        if (subcommand.equals("snapshot-list") || subcommand.equals("스냅샷목록")) {
            listIslandSnapshots(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("snapshot-create") || subcommand.equals("snapshot-request") || subcommand.equals("스냅샷생성")) {
            requestIslandSnapshot(player, args.length > 1 ? joined(args, 1) : "manual");
            return true;
        }
        if (subcommand.equals("snapshot-restore") || subcommand.equals("restore") || subcommand.equals("rollback") || subcommand.equals("스냅샷복원") || subcommand.equals("복원") || subcommand.equals("롤백")) {
            if (args.length < 2) {
                player.sendMessage("복원할 스냅샷 번호를 입력해주세요.");
                return true;
            }
            restoreIslandSnapshot(player, longValue(args[1], 0L));
            return true;
        }
        if (subcommand.equals("members") || subcommand.equals("member-menu") || subcommand.equals("멤버") || subcommand.equals("멤버관리")) {
            openIslandMemberMenu(player);
            return true;
        }
        if (subcommand.equals("member-list") || subcommand.equals("멤버목록")) {
            listIslandMembers(player);
            return true;
        }
        if (subcommand.equals("invite") || subcommand.equals("초대")) {
            if (args.length < 2) {
                player.sendMessage("초대할 플레이어를 입력해주세요.");
                return true;
            }
            inviteIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("invites") || subcommand.equals("invite-menu") || subcommand.equals("초대목록")) {
            IslandInviteMenu.open(plugin, coreApiClient, player);
            return true;
        }
        if (subcommand.equals("invite-list")) {
            listPendingInvites(player);
            return true;
        }
        if (subcommand.equals("accept") || subcommand.equals("invite-accept") || subcommand.equals("초대수락")) {
            if (args.length < 2) {
                player.sendMessage("수락할 초대 ID, 섬 ID, 또는 초대한 플레이어를 입력해주세요.");
                return true;
            }
            acceptIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("decline") || subcommand.equals("invite-decline") || subcommand.equals("초대거절")) {
            if (args.length < 2) {
                player.sendMessage("거절할 초대 ID, 섬 ID, 또는 초대한 플레이어를 입력해주세요.");
                return true;
            }
            declineIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("kick") || subcommand.equals("remove-member") || subcommand.equals("추방")) {
            if (args.length < 2) {
                player.sendMessage("추방할 플레이어를 입력해주세요.");
                return true;
            }
            removeIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("trust") || subcommand.equals("신뢰")) {
            if (args.length < 2) {
                player.sendMessage("신뢰할 플레이어를 입력해주세요.");
                return true;
            }
            setIslandMemberRole(player, args[1], IslandRole.TRUSTED, "섬 신뢰 멤버로 설정했습니다.");
            return true;
        }
        if (subcommand.equals("untrust") || subcommand.equals("신뢰해제")) {
            if (args.length < 2) {
                player.sendMessage("신뢰 해제할 플레이어를 입력해주세요.");
                return true;
            }
            removeIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("promote") || subcommand.equals("승급")) {
            if (args.length < 2) {
                player.sendMessage("승급할 플레이어를 입력해주세요.");
                return true;
            }
            setIslandMemberRole(player, args[1], IslandRole.MODERATOR, "섬 멤버를 승급했습니다.");
            return true;
        }
        if (subcommand.equals("demote") || subcommand.equals("강등")) {
            if (args.length < 2) {
                player.sendMessage("강등할 플레이어를 입력해주세요.");
                return true;
            }
            setIslandMemberRole(player, args[1], IslandRole.MEMBER, "섬 멤버를 강등했습니다.");
            return true;
        }
        if (subcommand.equals("transfer") || subcommand.equals("양도")) {
            if (args.length < 2) {
                player.sendMessage("양도할 플레이어를 입력해주세요.");
                return true;
            }
            transferIslandOwnership(player, args[1]);
            return true;
        }
        if (subcommand.equals("ban") || subcommand.equals("밴")) {
            if (args.length < 2) {
                player.sendMessage("밴할 플레이어를 입력해주세요.");
                return true;
            }
            banIslandVisitor(player, args[1], args.length > 2 ? joined(args, 2) : "");
            return true;
        }
        if (subcommand.equals("unban") || subcommand.equals("pardon") || subcommand.equals("밴해제")) {
            if (args.length < 2) {
                player.sendMessage("밴 해제할 플레이어를 입력해주세요.");
                return true;
            }
            pardonIslandVisitor(player, args[1]);
            return true;
        }
        if (subcommand.equals("kickvisitor") || subcommand.equals("방문자추방")) {
            if (args.length < 2) {
                player.sendMessage("추방할 방문자를 입력해주세요.");
                return true;
            }
            kickIslandVisitor(player, args[1]);
            return true;
        }
        if (subcommand.equals("bans") || subcommand.equals("ban-menu") || subcommand.equals("banlist") || subcommand.equals("밴목록")) {
            openIslandBanMenu(player);
            return true;
        }
        if (subcommand.equals("ban-list")) {
            listIslandBans(player);
            return true;
        }
        if (subcommand.equals("settings") || subcommand.equals("setting") || subcommand.equals("설정")) {
            openIslandSettings(player);
            return true;
        }
        if (subcommand.equals("fly") || subcommand.equals("비행")) {
            setIslandFlag(player, "FLY", flagToggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("keepinventory") || subcommand.equals("keepinv") || subcommand.equals("인벤보존")) {
            setIslandFlag(player, "KEEP_INVENTORY", flagToggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("pvp") || subcommand.equals("피빕")) {
            setIslandFlag(player, "PVP", flagToggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("publicwarps") || subcommand.equals("공개워프")) {
            setIslandFlag(player, "PUBLIC_WARPS", flagToggleValue(args, 1));
            return true;
        }
        if (subcommand.equals("flags") || subcommand.equals("flag-menu") || subcommand.equals("flag") || subcommand.equals("플래그")) {
            if (args.length > 2) {
                setIslandFlag(player, args[1], args[2]);
            } else {
                openIslandFlagMenu(player);
            }
            return true;
        }
        if (subcommand.equals("flag-list") || subcommand.equals("플래그목록")) {
            listIslandFlags(player);
            return true;
        }
        if (subcommand.equals("setflag") || subcommand.equals("flag-set") || subcommand.equals("플래그설정")) {
            if (args.length < 3) {
                player.sendMessage("플래그와 값을 입력해주세요.");
                return true;
            }
            setIslandFlag(player, args[1], args[2]);
            return true;
        }
        if (subcommand.equals("permissions") || subcommand.equals("permission-menu") || subcommand.equals("permission") || subcommand.equals("perms") || subcommand.equals("권한")) {
            if (args.length > 3) {
                setIslandPermission(player, args[1], args[2], args[3]);
            } else {
                openIslandPermissionMenu(player);
            }
            return true;
        }
        if (subcommand.equals("permission-list") || subcommand.equals("권한목록")) {
            listIslandPermissions(player);
            return true;
        }
        if (subcommand.equals("setpermission") || subcommand.equals("permission-set") || subcommand.equals("권한설정")) {
            if (args.length < 4) {
                player.sendMessage("역할, 권한, 허용 여부를 입력해주세요.");
                return true;
            }
            setIslandPermission(player, args[1], args[2], args[3]);
            return true;
        }
        return false;
    }

    private void createIsland(Player player, String templateId) {
        coreApiClient.createIsland(player.getUniqueId(), templateId)
            .thenAccept(result -> {
                if (!result.accepted()) {
                    message(player, "섬 생성을 시작하지 못했습니다: " + result.code());
                    return;
                }
                message(player, "섬 생성을 시작했습니다: " + result.code());
            })
            .exceptionally(error -> {
                message(player, "섬 생성을 시작하지 못했습니다.");
                return null;
            });
    }

    private void deleteIsland(Player player) {
        currentIsland(player, "섬 안에서만 섬을 삭제할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.deleteIsland(player.getUniqueId(), islandId)
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        message(player, "섬을 삭제하지 못했습니다: " + result.code());
                        return;
                    }
                    message(player, "섬 삭제를 요청했습니다.");
                })
                .exceptionally(error -> {
                    message(player, "섬을 삭제하지 못했습니다.");
                    return null;
                });
        });
    }

    private void resetIsland(Player player, String reason) {
        currentIsland(player, "섬 안에서만 섬을 리셋할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.resetIsland(islandId, player.getUniqueId(), reason)
                .thenAccept(body -> message(player, "섬 리셋을 요청했습니다."))
                .exceptionally(error -> {
                    message(player, "섬을 리셋하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setHome(Player player, String name) {
        currentIsland(player, "섬 안에서만 홈을 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.SET_HOME)) {
                player.sendMessage("섬 홈을 설정할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, location(player.getLocation()))
                .thenRun(() -> message(player, "섬 홈을 설정했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 홈을 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                player.sendMessage("섬 워프를 설정할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, location(player.getLocation()), false)
                .thenRun(() -> message(player, "섬 워프를 설정했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 워프를 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listHomes(Player player) {
        currentIsland(player, "섬 안에서만 홈 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandHomes(islandId)
                .thenAccept(body -> message(player, pointListMessage(body, "섬 홈", "섬 홈이 없습니다.")))
                .exceptionally(error -> {
                    message(player, "섬 홈을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listWarps(Player player) {
        currentIsland(player, "섬 안에서만 워프 목록을 볼 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandWarps(islandId)
                .thenAccept(body -> message(player, pointListMessage(body, "섬 워프", "섬 워프가 없습니다.")))
                .exceptionally(error -> {
                    message(player, "섬 워프를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandHomeMenu(Player player) {
        currentIsland(player, "섬 안에서만 홈 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandHomeMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void openIslandWarpMenu(Player player) {
        currentIsland(player, "섬 안에서만 워프 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandWarpMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void teleportHome(Player player, String name) {
        currentIsland(player, "섬 안에서만 홈으로 이동할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.INTERACT)) {
                player.sendMessage("섬 홈으로 이동할 권한이 없습니다.");
                return;
            }
            coreApiClient.listIslandHomes(islandId)
                .thenAccept(body -> teleport(player, point(body, name, player.getWorld().getName()), "홈을 찾을 수 없습니다.", "섬 홈으로 이동했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 홈을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void teleportWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프로 이동할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandWarps(islandId)
                .thenAccept(body -> {
                    Point point = point(body, name, player.getWorld().getName());
                    if (point == null) {
                        teleport(player, null, "워프를 찾을 수 없습니다.", "섬 워프로 이동했습니다.");
                        return;
                    }
                    coreApiClient.islandInfo(islandId).thenAccept(info -> {
                        if (!publicWarpAllowed(player, point, info) && !allowed(player, IslandPermission.INTERACT)) {
                            message(player, "섬 워프로 이동할 권한이 없습니다.");
                            return;
                        }
                        teleport(player, point, "워프를 찾을 수 없습니다.", "섬 워프로 이동했습니다.");
                    }).exceptionally(error -> {
                        message(player, "섬 정보를 불러오지 못했습니다.");
                        return null;
                    });
                })
                .exceptionally(error -> {
                    message(player, "섬 워프를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void deleteWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프를 삭제할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                player.sendMessage("섬 워프를 삭제할 권한이 없습니다.");
                return;
            }
            coreApiClient.deleteIslandWarp(islandId, player.getUniqueId(), name)
                .thenRun(() -> message(player, "섬 워프를 삭제했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 워프를 삭제하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarpPublicAccess(Player player, String name, boolean publicAccess) {
        currentIsland(player, "섬 안에서만 워프 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                player.sendMessage("섬 워프 공개 상태를 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess)
                .thenRun(() -> message(player, publicAccess ? "섬 워프를 공개했습니다." : "섬 워프를 비공개로 변경했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 워프 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandPublicAccess(Player player, boolean publicAccess) {
        currentIsland(player, "섬 안에서만 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                player.sendMessage("섬 공개 상태를 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandPublicAccess(islandId, player.getUniqueId(), publicAccess)
                .thenRun(() -> {
                    message(player, publicAccess ? "섬을 공개했습니다." : "섬을 비공개로 변경했습니다.");
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.performCommand("섬 설정"));
                })
                .exceptionally(error -> {
                    message(player, "섬 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandLocked(Player player, boolean locked) {
        currentIsland(player, "섬 안에서만 잠금 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                player.sendMessage("섬 잠금 상태를 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandLocked(islandId, player.getUniqueId(), locked)
                .thenRun(() -> {
                    message(player, locked ? "섬을 잠갔습니다." : "섬 잠금을 해제했습니다.");
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.performCommand("섬 설정"));
                })
                .exceptionally(error -> {
                    message(player, "섬 잠금 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void routeVisitTarget(Player player, String target) {
        UUID islandId = uuid(target);
        if (islandId != null) {
            routeVisit(player, islandId);
            return;
        }
        coreApiClient.playerInfoByName(target).thenAccept(body -> {
            UUID primaryIslandId = uuid(text(body, "primaryIslandId"));
            if (primaryIslandId != null) {
                routeVisit(player, primaryIslandId);
                return;
            }
            routeVisitName(player, target);
        }).exceptionally(error -> {
            routeVisitName(player, target);
            return null;
        });
    }

    private void routeVisitName(Player player, String islandName) {
        coreApiClient.islandInfoByName(islandName).thenAccept(body -> {
            UUID islandId = uuid(text(body, "islandId"));
            if (islandId == null) {
                message(player, "방문할 섬을 찾을 수 없습니다.");
                return;
            }
            routeVisit(player, islandId);
        }).exceptionally(error -> {
            message(player, "방문할 섬을 불러오지 못했습니다.");
            return null;
        });
    }

    private void routeVisit(Player player, UUID islandId) {
        routeTicket(player, coreApiClient.createVisitTicket(player.getUniqueId(), islandId), "해당 섬에 방문할 수 없습니다.");
    }

    private void routeWarp(Player player, UUID islandId, String warpName) {
        routeTicket(player, coreApiClient.createWarpTicket(player.getUniqueId(), islandId, warpName), "해당 워프로 이동할 수 없습니다.");
    }

    private void routeRandomVisit(Player player) {
        routeTicket(player, coreApiClient.createRandomVisitTicket(player.getUniqueId()), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    private void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        ticketFuture.thenAccept(ticket -> routeTicket(player, ticket, failureMessage, 0)).exceptionally(error -> {
            message(player, failureMessage);
            return null;
        });
    }

    private void routeTicket(Player player, RouteTicket ticket, String failureMessage, int attempt) {
        if (ticket.state().name().equals("READY")) {
            publishAndConnect(player, ticket, failureMessage);
            return;
        }
        if (attempt >= 45) {
            message(player, failureMessage);
            return;
        }
        CompletableFuture.runAsync(() -> coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
            if (status.isPresent()) {
                routeTicket(player, status.get(), failureMessage, attempt + 1);
            } else {
                message(player, failureMessage);
            }
        }).exceptionally(error -> {
            message(player, failureMessage);
            return null;
        }), CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    private void publishAndConnect(Player player, RouteTicket ticket, String failureMessage) {
        coreApiClient.publishRouteSession(ticket).thenRun(() -> connectWithTicket(player, ticket.payload().getOrDefault("targetServerName", ticket.targetNode()))).exceptionally(error -> {
            message(player, failureMessage);
            return null;
        });
    }

    private void connectWithTicket(Player player, String targetServerName) {
        connectPlayerToServer(player, targetServerName, "섬으로 이동합니다.", "섬으로 이동하지 못했습니다.");
    }

    private void connectPlayerToServer(Player player, String targetServerName, String successMessage, String failureMessage) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (targetServerName == null || targetServerName.isBlank()) {
                player.sendMessage(failureMessage);
                return;
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeUTF("Connect");
                output.writeUTF(targetServerName);
                player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
                player.sendMessage(successMessage);
            } catch (IOException exception) {
                player.sendMessage(failureMessage);
            }
        });
    }

    private void showIslandLevel(Player player) {
        currentIsland(player, "섬 안에서만 레벨을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> message(player, "섬 레벨: " + (long) decimal(body, "level")))
                .exceptionally(error -> {
                    message(player, "섬 레벨을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandInfoMenu(Player player) {
        currentIsland(player, "섬 안에서만 정보를 확인할 수 있습니다.").ifPresent(islandId -> IslandInfoMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void showIslandWorth(Player player) {
        currentIsland(player, "섬 안에서만 가치를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> {
                    String worth = text(body, "worth");
                    message(player, "섬 가치: " + (worth.isBlank() ? "0" : worth));
                })
                .exceptionally(error -> {
                    message(player, "섬 가치를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandRanking(Player player, boolean worthRanking) {
        if (worthRanking) {
            coreApiClient.topIslandsByWorth(10)
                .thenAccept(body -> message(player, rankingMessage(body, "섬 가치 랭킹", "worth")))
                .exceptionally(error -> {
                    message(player, "섬 가치 랭킹을 불러오지 못했습니다.");
                    return null;
                });
            return;
        }
        coreApiClient.topIslandsByLevel(10)
            .thenAccept(body -> message(player, rankingMessage(body, "섬 레벨 랭킹", "level")))
            .exceptionally(error -> {
                message(player, "섬 레벨 랭킹을 불러오지 못했습니다.");
                return null;
            });
    }

    private void recalculateIslandLevel(Player player) {
        currentIsland(player, "섬 안에서만 레벨을 계산할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.START_LEVEL_CALC)) {
                player.sendMessage("섬 레벨을 계산할 권한이 없습니다.");
                return;
            }
            coreApiClient.recalculateIslandLevel(islandId, player.getUniqueId())
                .thenAccept(body -> {
                    String worth = text(body, "worth");
                    message(player, "섬 레벨 계산 완료: 레벨 " + (long) decimal(body, "level") + " / 가치 " + (worth.isBlank() ? "0" : worth));
                })
                .exceptionally(error -> {
                    message(player, "섬 레벨을 계산하지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandBank(Player player) {
        currentIsland(player, "섬 안에서만 은행을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandBank(islandId)
                .thenAccept(body -> message(player, "섬 은행 잔액: " + bankBalance(body)))
                .exceptionally(error -> {
                    message(player, "섬 은행을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandBankMenu(Player player) {
        currentIsland(player, "섬 안에서만 은행 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandBankMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void depositIslandBank(Player player, String amount) {
        currentIsland(player, "섬 안에서만 은행에 입금할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.DEPOSIT_BANK)) {
                player.sendMessage("섬 은행에 입금할 권한이 없습니다.");
                return;
            }
            coreApiClient.depositIslandBank(islandId, player.getUniqueId(), amount)
                .thenAccept(body -> {
                    if (body.contains("\"accepted\":false")) {
                        message(player, "섬 은행에 입금하지 못했습니다. 잔액: " + bankBalance(body));
                        return;
                    }
                    message(player, "섬 은행에 입금했습니다. 잔액: " + bankBalance(body));
                })
                .exceptionally(error -> {
                    message(player, "섬 은행에 입금하지 못했습니다.");
                    return null;
                });
        });
    }

    private void withdrawIslandBank(Player player, String amount) {
        currentIsland(player, "섬 안에서만 은행에서 출금할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.WITHDRAW_BANK)) {
                player.sendMessage("섬 은행에서 출금할 권한이 없습니다.");
                return;
            }
            coreApiClient.withdrawIslandBank(islandId, player.getUniqueId(), amount)
                .thenAccept(body -> {
                    if (body.contains("\"accepted\":false")) {
                        message(player, "섬 은행에서 출금하지 못했습니다. 잔액: " + bankBalance(body));
                        return;
                    }
                    message(player, "섬 은행에서 출금했습니다. 잔액: " + bankBalance(body));
                })
                .exceptionally(error -> {
                    message(player, "섬 은행에서 출금하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandUpgrades(Player player) {
        currentIsland(player, "섬 안에서만 업그레이드를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandUpgrades(islandId)
                .thenAccept(body -> message(player, upgradeListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 업그레이드를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandUpgradeMenu(Player player) {
        currentIsland(player, "섬 안에서만 업그레이드 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandUpgradeMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void purchaseIslandUpgrade(Player player, String upgradeKey) {
        currentIsland(player, "섬 안에서만 업그레이드를 구매할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                player.sendMessage("섬 업그레이드를 구매할 권한이 없습니다.");
                return;
            }
            coreApiClient.purchaseIslandUpgrade(islandId, player.getUniqueId(), upgradeKey)
                .thenAccept(body -> {
                    String key = text(body, "upgradeKey");
                    String cost = text(body, "cost");
                    if (body.contains("\"accepted\":false")) {
                        message(player, "섬 업그레이드를 구매하지 못했습니다: " + text(body, "code"));
                        return;
                    }
                    message(player, "섬 업그레이드 구매 완료: " + (key.isBlank() ? upgradeKey : key) + " Lv." + (long) decimal(body, "level") + " / 비용 " + (cost.isBlank() ? "0" : cost));
                })
                .exceptionally(error -> {
                    message(player, "섬 업그레이드를 구매하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandMissions(Player player, String kind, String label) {
        currentIsland(player, "섬 안에서만 " + label + "을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandMissions(islandId, kind)
                .thenAccept(body -> message(player, missionListMessage(body, label)))
                .exceptionally(error -> {
                    message(player, label + "을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandMissionMenu(Player player, String kind) {
        currentIsland(player, "섬 안에서만 과제 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandMissionMenu.open(plugin, coreApiClient, player, islandId, kind));
    }

    private void completeIslandMission(Player player, String missionKey) {
        completeIslandTask(player, missionKey, "섬 미션");
    }

    private void completeIslandChallenge(Player player, String missionKey) {
        completeIslandTask(player, missionKey, "섬 챌린지");
    }

    private void completeIslandTask(Player player, String missionKey, String label) {
        currentIsland(player, "섬 안에서만 " + label + "을 완료할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.completeIslandMission(islandId, player.getUniqueId(), missionKey)
                .thenAccept(body -> {
                    String title = text(body, "title");
                    String reward = text(body, "reward");
                    message(player, label + " 완료: " + (title.isBlank() ? missionKey : title) + (reward.isBlank() ? "" : " / 보상 " + reward));
                })
                .exceptionally(error -> {
                    message(player, label + "을 완료하지 못했습니다.");
                    return null;
                });
        });
    }

    private void sendIslandChat(Player player, String channel, String chatMessage, String label) {
        currentIsland(player, "섬 안에서만 " + label + "을 사용할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.sendIslandChat(islandId, player.getUniqueId(), channel, chatMessage)
                .thenAccept(body -> message(player, label + " 전송: " + text(body, "message")))
                .exceptionally(error -> {
                    message(player, label + "을 전송하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandLogs(Player player, int limit) {
        currentIsland(player, "섬 안에서만 로그를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandLogs(islandId, Math.max(1, Math.min(limit, 30)))
                .thenAccept(body -> message(player, logListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 로그를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandLogMenu(Player player) {
        currentIsland(player, "섬 안에서만 로그를 확인할 수 있습니다.").ifPresent(islandId -> IslandLogMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void showIslandBiome(Player player) {
        currentIsland(player, "섬 안에서만 바이옴을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandBiome(islandId)
                .thenAccept(body -> message(player, "섬 바이옴: " + text(body, "biomeKey")))
                .exceptionally(error -> {
                    message(player, "섬 바이옴을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandBiomeMenu(Player player) {
        currentIsland(player, "섬 안에서만 바이옴 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandBiomeMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void setIslandBiome(Player player, String biomeKey) {
        currentIsland(player, "섬 안에서만 바이옴을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.SET_BIOME)) {
                player.sendMessage("섬 바이옴을 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandBiome(islandId, player.getUniqueId(), biomeKey)
                .thenRun(() -> message(player, "섬 바이옴을 변경했습니다: " + biomeKey))
                .exceptionally(error -> {
                    message(player, "섬 바이옴을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandSize(Player player) {
        currentIsland(player, "섬 안에서만 크기를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> message(player, "섬 크기: " + (long) decimal(body, "size")))
                .exceptionally(error -> {
                    message(player, "섬 크기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void showIslandBorder(Player player) {
        currentIsland(player, "섬 안에서만 경계를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.islandInfo(islandId)
                .thenAccept(body -> message(player, "섬 경계: " + (long) decimal(body, "border")))
                .exceptionally(error -> {
                    message(player, "섬 경계를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandLimits(Player player) {
        currentIsland(player, "섬 안에서만 제한을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandLimits(islandId)
                .thenAccept(body -> message(player, limitListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 제한을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandLimitMenu(Player player) {
        currentIsland(player, "섬 안에서만 제한 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandLimitMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void setNamedIslandLimit(Player player, String limitKey, String[] args) {
        if (args.length < 2) {
            player.sendMessage("제한 값을 입력해주세요.");
            return;
        }
        setIslandLimit(player, limitKey, longValue(args[1], 0L));
    }

    private void setIslandLimit(Player player, String limitKey, long value) {
        currentIsland(player, "섬 안에서만 제한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                player.sendMessage("섬 제한을 변경할 권한이 없습니다.");
                return;
            }
            coreApiClient.setIslandLimit(islandId, player.getUniqueId(), limitKey, value)
                .thenAccept(body -> message(player, "섬 제한 변경 완료: " + text(body, "limitKey") + " = " + (long) decimal(body, "value")))
                .exceptionally(error -> {
                    message(player, "섬 제한을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandSnapshots(Player player, int limit) {
        currentIsland(player, "섬 안에서만 스냅샷을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandSnapshots(islandId, Math.max(1, Math.min(limit, 20)))
                .thenAccept(body -> message(player, snapshotListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 스냅샷을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandSnapshotMenu(Player player) {
        currentIsland(player, "섬 안에서만 스냅샷 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandSnapshotMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void requestIslandSnapshot(Player player, String reason) {
        currentIsland(player, "섬 안에서만 스냅샷을 생성할 수 있습니다.").ifPresent(islandId -> {
            if (!player.isOp()) {
                player.sendMessage("섬 스냅샷을 생성할 관리자 권한이 없습니다.");
                return;
            }
            coreApiClient.requestIslandSnapshot(islandId, reason)
                .thenRun(() -> message(player, "섬 스냅샷 생성을 요청했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 스냅샷 생성을 요청하지 못했습니다.");
                    return null;
                });
        });
    }

    private void restoreIslandSnapshot(Player player, long snapshotNo) {
        currentIsland(player, "섬 안에서만 스냅샷을 복원할 수 있습니다.").ifPresent(islandId -> {
            if (!player.isOp()) {
                player.sendMessage("섬 스냅샷을 복원할 관리자 권한이 없습니다.");
                return;
            }
            if (snapshotNo <= 0L) {
                player.sendMessage("올바른 스냅샷 번호를 입력해주세요.");
                return;
            }
            coreApiClient.restoreIslandSnapshot(islandId, snapshotNo)
                .thenRun(() -> message(player, "섬 스냅샷 복원을 요청했습니다."))
                .exceptionally(error -> {
                    message(player, "섬 스냅샷 복원을 요청하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandMembers(Player player) {
        currentIsland(player, "섬 안에서만 멤버를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandMembers(islandId)
                .thenAccept(body -> message(player, memberListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 멤버를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandMemberMenu(Player player) {
        currentIsland(player, "섬 안에서만 멤버 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandMemberMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void inviteIslandMember(Player player, String target) {
        currentIsland(player, "섬 안에서만 플레이어를 초대할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_MEMBERS)) {
                player.sendMessage("섬 멤버를 초대할 권한이 없습니다.");
                return;
            }
            Player online = plugin.getServer().getPlayerExact(target);
            UUID parsed = uuid(target);
            if (online != null || parsed != null) {
                sendIslandInvite(player, islandId, online == null ? parsed : online.getUniqueId());
                return;
            }
            coreApiClient.playerInfoByName(target).thenAccept(body -> {
                UUID profileUuid = uuid(text(body, "playerUuid"));
                sendIslandInvite(player, islandId, profileUuid == null ? plugin.getServer().getOfflinePlayer(target).getUniqueId() : profileUuid);
            }).exceptionally(error -> {
                sendIslandInvite(player, islandId, plugin.getServer().getOfflinePlayer(target).getUniqueId());
                return null;
            });
        });
    }

    private void sendIslandInvite(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid)
            .thenAccept(body -> message(player, "섬 초대를 보냈습니다: " + text(body, "inviteId")))
            .exceptionally(error -> {
                message(player, "섬 초대를 보내지 못했습니다.");
                return null;
            });
    }

    private void listPendingInvites(Player player) {
        coreApiClient.listPendingInvites(player.getUniqueId())
            .thenAccept(body -> message(player, inviteListMessage(body)))
            .exceptionally(error -> {
                message(player, "섬 초대 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private void acceptIslandInvite(Player player, UUID inviteId) {
        if (inviteId == null) {
            player.sendMessage("올바른 초대 ID를 입력해주세요.");
            return;
        }
        coreApiClient.acceptIslandInviteResult(inviteId, player.getUniqueId())
            .thenAccept(body -> message(player, body.contains("\"error\"") || body.contains("\"accepted\":false") ? "섬 초대를 수락하지 못했습니다." : "섬 초대를 수락했습니다."))
            .exceptionally(error -> {
                message(player, "섬 초대를 수락하지 못했습니다.");
                return null;
            });
    }

    private void acceptIslandInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId == null) {
                message(player, "대상 초대를 찾지 못했습니다.");
                return;
            }
            acceptIslandInvite(player, inviteId);
        }).exceptionally(error -> {
            message(player, "대상 초대를 찾지 못했습니다.");
            return null;
        });
    }

    private void declineIslandInvite(Player player, UUID inviteId) {
        if (inviteId == null) {
            player.sendMessage("올바른 초대 ID를 입력해주세요.");
            return;
        }
        coreApiClient.declineIslandInviteResult(inviteId, player.getUniqueId())
            .thenAccept(body -> message(player, body.contains("\"error\"") || body.contains("\"accepted\":false") ? "섬 초대를 거절하지 못했습니다." : "섬 초대를 거절했습니다."))
            .exceptionally(error -> {
                message(player, "섬 초대를 거절하지 못했습니다.");
                return null;
            });
    }

    private void declineIslandInviteTarget(Player player, String target) {
        resolveInviteTarget(player, target).thenAccept(inviteId -> {
            if (inviteId == null) {
                message(player, "대상 초대를 찾지 못했습니다.");
                return;
            }
            declineIslandInvite(player, inviteId);
        }).exceptionally(error -> {
            message(player, "대상 초대를 찾지 못했습니다.");
            return null;
        });
    }

    private CompletableFuture<UUID> resolveInviteTarget(Player player, String target) {
        UUID parsed = uuid(target);
        if (parsed != null) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> {
                UUID inviteId = findInviteId(body, parsed);
                return inviteId == null ? parsed : inviteId;
            });
        }
        Player online = plugin.getServer().getPlayerExact(target);
        if (online != null) {
            return coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(body -> findInviteId(body, online.getUniqueId()));
        }
        return coreApiClient.playerInfoByName(target)
            .thenCompose(body -> coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(invites -> findInviteId(invites, uuid(text(body, "playerUuid")))));
    }

    private UUID findInviteId(String body, UUID targetUuid) {
        if (body == null || targetUuid == null) {
            return null;
        }
        int index = 0;
        while (index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            UUID inviteId = uuid(text(object, "inviteId"));
            if (targetUuid.equals(inviteId) || targetUuid.equals(uuid(text(object, "islandId"))) || targetUuid.equals(uuid(text(object, "inviterUuid")))) {
                return inviteId;
            }
            index = objectEnd + 1;
        }
        return null;
    }

    private void removeIslandMember(Player player, String target) {
        currentIsland(player, "섬 안에서만 멤버를 추방할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_MEMBERS)) {
                player.sendMessage("섬 멤버를 추방할 권한이 없습니다.");
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                coreApiClient.removeIslandMemberResult(islandId, player.getUniqueId(), targetUuid)
                    .thenAccept(body -> message(player, "섬 멤버를 제거했습니다."))
                    .exceptionally(error -> {
                        message(player, "섬 멤버를 제거하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void setIslandMemberRole(Player player, String target, IslandRole role, String successMessage) {
        currentIsland(player, "섬 안에서만 멤버 역할을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                player.sendMessage("섬 멤버 역할을 변경할 권한이 없습니다.");
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                coreApiClient.setIslandMemberResult(islandId, player.getUniqueId(), targetUuid, role)
                    .thenAccept(body -> message(player, successMessage))
                    .exceptionally(error -> {
                        message(player, "섬 멤버 역할을 변경하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void transferIslandOwnership(Player player, String target) {
        currentIsland(player, "섬 안에서만 소유권을 양도할 수 있습니다.").ifPresent(islandId -> {
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                coreApiClient.transferIslandOwnershipResult(islandId, player.getUniqueId(), targetUuid)
                    .thenAccept(body -> message(player, "섬 소유권을 양도했습니다."))
                    .exceptionally(error -> {
                        message(player, "섬 소유권을 양도하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void banIslandVisitor(Player player, String target, String reason) {
        currentIsland(player, "섬 안에서만 방문자를 밴할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.BAN_VISITOR)) {
                player.sendMessage("섬 방문자를 밴할 권한이 없습니다.");
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                coreApiClient.banIslandVisitorResult(islandId, player.getUniqueId(), targetUuid, reason)
                    .thenAccept(body -> message(player, "섬 방문자를 밴했습니다."))
                    .exceptionally(error -> {
                        message(player, "섬 방문자를 밴하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void pardonIslandVisitor(Player player, String target) {
        currentIsland(player, "섬 안에서만 방문자 밴을 해제할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.BAN_VISITOR)) {
                player.sendMessage("섬 방문자 밴을 해제할 권한이 없습니다.");
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                coreApiClient.pardonIslandVisitorResult(islandId, player.getUniqueId(), targetUuid)
                    .thenAccept(body -> message(player, "섬 방문자 밴을 해제했습니다."))
                    .exceptionally(error -> {
                        message(player, "섬 방문자 밴을 해제하지 못했습니다.");
                        return null;
                    });
                });
        });
    }

    private void kickIslandVisitor(Player player, String target) {
        currentIsland(player, "섬 안에서만 방문자를 추방할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.BAN_VISITOR)) {
                player.sendMessage("섬 방문자를 추방할 권한이 없습니다.");
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player targetPlayer = plugin.getServer().getPlayer(targetUuid);
                if (targetPlayer == null) {
                    player.sendMessage("대상 플레이어가 온라인이 아닙니다.");
                    return;
                }
                UUID targetIslandId = protection.islandAt(targetPlayer.getLocation().getBlock()).orElse(null);
                if (!islandId.equals(targetIslandId)) {
                    player.sendMessage("해당 방문자는 이 섬에 없습니다.");
                    return;
                }
                connectPlayerToServer(targetPlayer, "Lobby", "섬에서 추방되어 로비로 이동합니다.", "섬에서 추방되어 로비로 이동하지 못했습니다.");
                player.sendMessage("방문자를 섬에서 추방했습니다.");
            })).exceptionally(error -> {
                message(player, "대상 플레이어를 찾지 못했습니다.");
                return null;
            });
        });
    }

    private void listIslandBans(Player player) {
        currentIsland(player, "섬 안에서만 밴 목록을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandBans(islandId)
                .thenAccept(body -> message(player, banListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 밴 목록을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandBanMenu(Player player) {
        currentIsland(player, "섬 안에서만 밴 목록을 확인할 수 있습니다.").ifPresent(islandId -> IslandBanMenu.open(plugin, coreApiClient, player, islandId));
    }

    private String flagToggleValue(String[] args, int index) {
        if (args.length <= index) {
            return "true";
        }
        String value = args[index].toLowerCase(Locale.ROOT);
        if (value.equals("on") || value.equals("true") || value.equals("yes") || value.equals("켜기")) {
            return "true";
        }
        if (value.equals("off") || value.equals("false") || value.equals("no") || value.equals("끄기")) {
            return "false";
        }
        return args[index];
    }

    private void listIslandFlags(Player player) {
        currentIsland(player, "섬 안에서만 플래그를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandFlags(islandId)
                .thenAccept(body -> message(player, flagListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 플래그를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandFlagMenu(Player player) {
        currentIsland(player, "섬 안에서만 플래그 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandFlagMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void setIslandFlag(Player player, String flagName, String value) {
        currentIsland(player, "섬 안에서만 플래그를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                player.sendMessage("섬 플래그를 변경할 권한이 없습니다.");
                return;
            }
            IslandFlag flag = islandFlag(flagName);
            if (flag == null) {
                player.sendMessage("올바른 섬 플래그를 입력해주세요.");
                return;
            }
            coreApiClient.setIslandFlagResult(islandId, player.getUniqueId(), flag, value)
                .thenAccept(body -> message(player, "섬 플래그를 변경했습니다: " + flag.name() + " = " + value))
                .exceptionally(error -> {
                    message(player, "섬 플래그를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void listIslandPermissions(Player player) {
        currentIsland(player, "섬 안에서만 권한을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandPermissions(islandId)
                .thenAccept(body -> message(player, permissionListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 권한을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandPermissionMenu(Player player) {
        currentIsland(player, "섬 안에서만 권한 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandPermissionMenu.open(plugin, coreApiClient, player, islandId));
    }

    private void setIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        currentIsland(player, "섬 안에서만 권한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                player.sendMessage("섬 권한을 변경할 권한이 없습니다.");
                return;
            }
            IslandRole role = islandRole(roleName);
            IslandPermission permission = islandPermission(permissionName);
            if (role == null || permission == null) {
                player.sendMessage("올바른 역할과 권한을 입력해주세요.");
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            coreApiClient.setIslandPermissionResult(islandId, player.getUniqueId(), role, permission, allowed)
                .thenAccept(body -> message(player, "섬 권한을 변경했습니다: " + role.name() + " " + permission.name() + " = " + allowed))
                .exceptionally(error -> {
                    message(player, "섬 권한을 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandSettings(Player player) {
        currentIsland(player, "섬 안에서만 설정 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandSettingsMenu.open(plugin, coreApiClient, player, islandId));
    }

    private java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
        java.util.Optional<UUID> islandId = protection.islandAt(player.getLocation().getBlock());
        if (islandId.isEmpty()) {
            player.sendMessage(missingMessage);
        }
        return islandId;
    }

    private boolean allowed(Player player, IslandPermission permission) {
        Location location = player.getLocation();
        return protection.checkBlock(
            player.getUniqueId(),
            location.getWorld().getName(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ(),
            permission,
            player.isOp()
        ).allowed();
    }

    private boolean publicWarpAllowed(Player player, Point point, String islandInfo) {
        return point.publicAccess()
            && bool(islandInfo, "publicAccess")
            && protection.checkSystemFlag(player.getLocation().getBlock(), IslandFlag.PUBLIC_WARPS).allowed();
    }

    private IslandLocation location(Location location) {
        java.util.Optional<IslandRegion> region = protection.regionAt(location.getBlock());
        return new IslandLocation(
            location.getWorld().getName(),
            region.map(value -> location.getX() - value.originX()).orElse(location.getX()),
            location.getY(),
            region.map(value -> location.getZ() - value.originZ()).orElse(location.getZ()),
            location.getYaw(),
            location.getPitch()
        );
    }

    private String pointListMessage(String body, String label, String emptyMessage) {
        List<String> names = names(body);
        return names.isEmpty() ? emptyMessage : label + ": " + String.join(", ", names);
    }

    private String rankingMessage(String body, String label, String valueKey) {
        if (body == null || body.isBlank()) {
            return label + ": 기록이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < body.length() && entries.size() < 10) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String value = valueKey.equals("worth") ? text(object, valueKey) : Long.toString((long) decimal(object, valueKey));
                entries.add((entries.size() + 1) + ". " + islandId + " (" + value + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? label + ": 기록이 없습니다." : label + ": " + String.join(" | ", entries);
    }

    private String bankBalance(String body) {
        String balance = text(body, "balance");
        return balance.isBlank() ? "0" : balance;
    }

    private String upgradeListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String key = text(object, "upgradeKey");
            if (!key.isBlank()) {
                entries.add(key + " Lv." + (long) decimal(object, "level"));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 업그레이드가 없습니다." : "섬 업그레이드: " + String.join(", ", entries);
    }

    private String missionListMessage(String body, String label) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String key = text(object, "missionKey");
            if (!key.isBlank()) {
                String title = text(object, "title");
                String state = bool(object, "completed") ? "완료" : ((long) decimal(object, "progress") + "/" + (long) decimal(object, "goal"));
                entries.add(key + "(" + (title.isBlank() ? key : title) + ", " + state + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? label + "이 없습니다." : label + ": " + String.join(", ", entries);
    }

    private String logListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String action = text(object, "action");
            if (!action.isBlank()) {
                String actor = text(object, "actorUuid");
                entries.add(action + (actor.isBlank() ? "" : " by " + actor));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 로그가 없습니다." : "섬 로그: " + String.join(" | ", entries);
    }

    private String limitListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String key = text(object, "limitKey");
            if (!key.isBlank()) {
                entries.add(key + "=" + (long) decimal(object, "value"));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 제한이 없습니다." : "섬 제한: " + String.join(", ", entries);
    }

    private String snapshotListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            long snapshotNo = (long) decimal(object, "snapshotNo");
            if (snapshotNo > 0L) {
                String reason = text(object, "reason");
                entries.add("#" + snapshotNo + (reason.isBlank() ? "" : " " + reason));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 스냅샷이 없습니다." : "섬 스냅샷: " + String.join(", ", entries);
    }

    private String memberListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String playerUuid = text(object, "playerUuid");
            String role = text(object, "role");
            if (!playerUuid.isBlank()) {
                entries.add(playerUuid + (role.isBlank() ? "" : " " + role));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 멤버가 없습니다." : "섬 멤버: " + String.join(", ", entries);
    }

    private String inviteListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String inviteId = text(object, "inviteId");
            String islandId = text(object, "islandId");
            String inviterUuid = text(object, "inviterUuid");
            if (!inviteId.isBlank()) {
                entries.add(inviteId + (islandId.isBlank() ? "" : " island=" + islandId) + (inviterUuid.isBlank() ? "" : " inviter=" + inviterUuid));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "대기 중인 섬 초대가 없습니다." : "섬 초대: " + String.join(", ", entries);
    }

    private String banListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String bannedUuid = text(object, "bannedUuid");
            String reason = text(object, "reason");
            if (!bannedUuid.isBlank()) {
                entries.add(bannedUuid + (reason.isBlank() ? "" : " " + reason));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 밴 목록이 비어 있습니다." : "섬 밴 목록: " + String.join(", ", entries);
    }

    private String flagListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "섬 플래그가 없습니다.";
        }
        int flagsStart = body.indexOf("\"flags\":{");
        if (flagsStart < 0) {
            return "섬 플래그가 없습니다.";
        }
        int objectStart = body.indexOf('{', flagsStart);
        int objectEnd = body.indexOf('}', objectStart);
        if (objectStart < 0 || objectEnd < 0) {
            return "섬 플래그가 없습니다.";
        }
        String flags = body.substring(objectStart + 1, objectEnd);
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < flags.length()) {
            int keyStart = flags.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = flags.indexOf('"', keyStart + 1);
            int valueStart = flags.indexOf('"', keyEnd + 1);
            int valueEnd = valueStart < 0 ? -1 : flags.indexOf('"', valueStart + 1);
            if (keyEnd < 0 || valueStart < 0 || valueEnd < 0) {
                break;
            }
            entries.add(flags.substring(keyStart + 1, keyEnd) + "=" + unescape(flags.substring(valueStart + 1, valueEnd)));
            index = valueEnd + 1;
        }
        return entries.isEmpty() ? "섬 플래그가 없습니다." : "섬 플래그: " + String.join(", ", entries);
    }

    private String permissionListMessage(String body) {
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String role = text(object, "role");
            String permission = text(object, "permission");
            if (!role.isBlank() && !permission.isBlank()) {
                entries.add(role + ":" + permission + "=" + bool(object, "allowed"));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 권한 규칙이 없습니다." : "섬 권한: " + String.join(", ", entries);
    }

    private String joined(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private int integer(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private UUID playerUuid(String value) {
        Player online = plugin.getServer().getPlayerExact(value);
        if (online != null) {
            return online.getUniqueId();
        }
        UUID parsed = uuid(value);
        if (parsed != null) {
            return parsed;
        }
        return plugin.getServer().getOfflinePlayer(value).getUniqueId();
    }

    private CompletableFuture<UUID> resolvePlayerUuid(String value) {
        Player online = plugin.getServer().getPlayerExact(value);
        if (online != null) {
            return CompletableFuture.completedFuture(online.getUniqueId());
        }
        UUID parsed = uuid(value);
        if (parsed != null) {
            return CompletableFuture.completedFuture(parsed);
        }
        return coreApiClient.playerInfoByName(value)
            .thenApply(body -> {
                UUID profileUuid = uuid(text(body, "playerUuid"));
                return profileUuid == null ? plugin.getServer().getOfflinePlayer(value).getUniqueId() : profileUuid;
            })
            .exceptionally(error -> plugin.getServer().getOfflinePlayer(value).getUniqueId());
    }

    private IslandFlag islandFlag(String value) {
        try {
            return IslandFlag.valueOf(value.toUpperCase().replace('-', '_'));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private IslandRole islandRole(String value) {
        try {
            return IslandRole.valueOf(value.toUpperCase().replace('-', '_'));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private IslandPermission islandPermission(String value) {
        try {
            return IslandPermission.valueOf(value.toUpperCase().replace('-', '_'));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean booleanValue(String value) {
        return value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("yes")
            || value.equalsIgnoreCase("on")
            || value.equals("1")
            || value.equals("허용");
    }

    private Point point(String body, String requestedName, String fallbackWorldName) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String target = requestedName == null || requestedName.isBlank() ? "default" : requestedName;
        int index = 0;
        while (index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                return null;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                return null;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            if (target.equalsIgnoreCase(text(object, "name"))) {
                return new Point(
                    text(object, "worldName").isBlank() ? fallbackWorldName : text(object, "worldName"),
                    decimal(object, "localX"),
                    decimal(object, "localY"),
                    decimal(object, "localZ"),
                    (float) decimal(object, "yaw"),
                    (float) decimal(object, "pitch"),
                    bool(object, "publicAccess")
                );
            }
            index = objectEnd + 1;
        }
        return null;
    }

    private void teleport(Player player, Point point, String missingMessage, String successMessage) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (point == null) {
                player.sendMessage(missingMessage);
                return;
            }
            World world = plugin.getServer().getWorld(point.worldName());
            if (world == null) {
                player.sendMessage("대상 월드를 찾을 수 없습니다.");
                return;
            }
            java.util.Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock())
                .filter(value -> value.world().equals(point.worldName()));
            double targetX = region.map(value -> value.originX() + point.x()).orElse(point.x());
            double targetZ = region.map(value -> value.originZ() + point.z()).orElse(point.z());
            player.teleport(new Location(world, targetX, point.y(), targetZ, point.yaw(), point.pitch()));
            player.sendMessage(successMessage);
        });
    }

    private String text(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = jsonStringEnd(json, valueStart);
        return end < 0 ? "" : unescape(json.substring(valueStart, end));
    }

    private double decimal(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length()) {
            char current = json.charAt(end);
            if ((current >= '0' && current <= '9') || current == '-' || current == '+' || current == '.') {
                end++;
                continue;
            }
            break;
        }
        try {
            return Double.parseDouble(json.substring(valueStart, end));
        } catch (RuntimeException ignored) {
            return 0.0D;
        }
    }

    private boolean bool(String json, String key) {
        String needle = "\"" + key + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return false;
        }
        int valueStart = start + needle.length();
        return json.startsWith("true", valueStart);
    }

    private List<String> names(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        String needle = "\"name\":\"";
        int index = 0;
        while (index < body.length()) {
            int start = body.indexOf(needle, index);
            if (start < 0) {
                break;
            }
            int valueStart = start + needle.length();
            int end = jsonStringEnd(body, valueStart);
            if (end < 0) {
                break;
            }
            names.add(unescape(body.substring(valueStart, end)));
            index = end + 1;
        }
        return names;
    }

    private int jsonStringEnd(String value, int start) {
        boolean escaped = false;
        for (int i = start; i < value.length(); i++) {
            char current = value.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return i;
            }
        }
        return -1;
    }

    private String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!escaped) {
                if (current == '\\') {
                    escaped = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                default -> builder.append(current);
            }
            escaped = false;
        }
        return builder.toString();
    }

    private void message(Player player, String message) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message));
    }

    private record Point(String worldName, double x, double y, double z, float yaw, float pitch, boolean publicAccess) {}
}
