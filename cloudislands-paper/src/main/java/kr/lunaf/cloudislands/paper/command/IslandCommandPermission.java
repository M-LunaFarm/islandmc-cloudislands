package kr.lunaf.cloudislands.paper.command;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public enum IslandCommandPermission {
    MENU("cloudislands.island.menu", "menu", "메뉴"),
    CREATE("cloudislands.island.create", "create", "생성", "create-menu", "templates", "생성메뉴", "템플릿"),
    DELETE("cloudislands.island.delete", "delete", "삭제"),
    RESET("cloudislands.island.reset", "reset", "리셋", "danger", "위험작업"),
    HOME("cloudislands.island.home", "home", "homes", "home-menu", "home-list", "홈", "홈관리", "홈목록"),
    SET_HOME("cloudislands.island.sethome", "sethome", "셋홈"),
    WARP("cloudislands.island.warp", "warp", "warps", "warp-menu", "warp-list", "public-warps", "publicwarplist", "visit", "randomvisit", "random-visit", "public-islands", "publicislands", "visit-list", "방문", "랜덤방문", "공개섬", "방문목록", "워프", "워프관리", "워프목록", "공개워프목록"),
    SET_WARP("cloudislands.island.setwarp", "setwarp", "delwarp", "deletewarp", "warp-public", "publicwarp", "warp-private", "privatewarp", "워프설정", "워프삭제", "워프공개", "워프비공개"),
    BANK("cloudislands.island.bank", "bank", "bank-balance", "은행", "은행잔액"),
    BANK_DEPOSIT("cloudislands.island.bank.deposit", "deposit", "bank-deposit", "입금"),
    BANK_WITHDRAW("cloudislands.island.bank.withdraw", "withdraw", "bank-withdraw", "출금"),
    WAREHOUSE("cloudislands.island.warehouse", "warehouse", "warehouse-list", "storage-box", "창고", "창고목록"),
    WAREHOUSE_DEPOSIT("cloudislands.island.warehouse.deposit", "warehouse-deposit", "창고입금"),
    WAREHOUSE_WITHDRAW("cloudislands.island.warehouse.withdraw", "warehouse-withdraw", "창고출금"),
    MEMBERS("cloudislands.island.members", "members", "member-menu", "member-list", "roles", "role-menu", "role-list", "invites", "invite-menu", "invite-list", "bans", "ban-menu", "ban-list", "banlist", "멤버", "멤버관리", "멤버목록", "역할", "역할목록", "초대목록", "밴목록"),
    INVITE("cloudislands.island.invite", "invite", "초대"),
    INVITE_RESPOND("cloudislands.island.invite.respond", "accept", "invite-accept", "decline", "invite-decline", "초대수락", "초대거절"),
    KICK("cloudislands.island.kick", "kick", "remove-member", "untrust", "ban", "unban", "pardon", "kickvisitor", "추방", "신뢰해제", "밴", "밴해제", "방문자추방"),
    TRUST("cloudislands.island.trust", "trust", "coop", "co-op", "신뢰", "협동"),
    PROMOTE("cloudislands.island.promote", "promote", "setrole", "role-set", "role-upsert", "role-edit", "role-reset", "승급", "역할설정", "역할편집", "역할초기화"),
    DEMOTE("cloudislands.island.demote", "demote", "강등"),
    TRANSFER("cloudislands.island.transfer", "transfer", "양도"),
    SETTINGS("cloudislands.island.settings", "settings", "setting", "public", "private", "lock", "unlock", "fly", "keepinventory", "keepinv", "pvp", "publicwarps", "language", "locale", "name", "setname", "rename", "flags", "flag-menu", "flag-list", "flag", "setflag", "flag-set", "설정", "공개", "비공개", "잠금", "잠금해제", "비행", "인벤보존", "피빕", "공개워프", "언어", "이름", "이름설정", "플래그", "플래그설정", "플래그목록"),
    PERMISSIONS("cloudislands.island.permissions", "permissions", "permission-menu", "permission-list", "permission", "perms", "setpermission", "permission-set", "permission-exception", "permission-exception-list", "권한", "권한설정", "권한목록", "권한예외", "권한예외목록"),
    SNAPSHOT("cloudislands.island.snapshot", "snapshot", "snapshots", "snapshot-menu", "snapshot-list", "snapshot-create", "snapshot-request", "스냅샷", "스냅샷목록", "스냅샷생성"),
    RESTORE("cloudislands.island.restore", "snapshot-restore", "restore", "rollback", "스냅샷복원", "복원", "롤백"),
    REVIEW("cloudislands.island.review", "reviews", "review-list", "rate", "review", "delete-review", "review-delete", "reviewdel", "reviewrank", "평가", "후기", "후기삭제", "평가삭제", "평가목록", "후기목록", "평가랭킹", "후기랭킹"),
    VISITOR_STATS("cloudislands.island.visitor-stats", "visitor-stats", "visitorstats", "visitors", "방문통계", "방문자통계"),
    PROGRESSION("cloudislands.island.progression", "level", "worth", "value", "blocks", "block-details", "block-counts", "rank", "ranking", "rank-list", "worthrank", "valuerank", "levelcalc", "recalculate", "upgrade", "upgrades", "upgrade-menu", "upgrade-list", "buyupgrade", "upgrade-buy", "generator", "generator-info", "mission", "missions", "mission-menu", "mission-list", "challenge", "challenges", "challenge-menu", "challenge-list", "레벨", "가치", "블록상세", "블록목록", "랭킹", "랭킹목록", "가치랭킹", "레벨계산", "업그레이드", "업그레이드목록", "업그레이드구매", "생성기", "생성기정보", "미션", "미션목록", "챌린지", "챌린지목록"),
    CHAT("cloudislands.island.chat", "chat", "chat-menu", "islandchat", "teamchat", "team-chat", "log", "logs", "log-menu", "log-list", "채팅", "팀채팅", "로그", "로그목록"),
    ENVIRONMENT("cloudislands.island.environment", "biome", "biome-menu", "biome-info", "size", "border", "border-ui", "border-color", "border-visible", "limit", "limits", "limit-menu", "limit-list", "setlimit", "limit-set", "hoppers", "spawners", "entities", "redstone", "바이옴", "바이옴정보", "크기", "경계", "경계표시", "경계색상", "제한", "제한목록", "제한설정", "호퍼", "스포너", "엔티티", "레드스톤");

    private static final String ADMIN_BYPASS = "cloudislands.admin.bypass";
    private final String node;
    private final Set<String> aliases;

    IslandCommandPermission(String node, String... aliases) {
        this.node = node;
        this.aliases = Arrays.stream(aliases)
            .map(IslandCommandPermission::normalize)
            .collect(Collectors.toUnmodifiableSet());
    }

    public String node() {
        return node;
    }

    public boolean allows(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.hasPermission(ADMIN_BYPASS) || player.hasPermission(node);
    }

    static boolean hasAccess(CommandSender sender, String subcommand) {
        IslandCommandPermission permission = fromSubcommand(subcommand);
        return permission == null || permission.allows(sender);
    }

    static IslandCommandPermission fromSubcommand(String subcommand) {
        String normalized = normalize(subcommand);
        if (normalized.isBlank()) {
            return MENU;
        }
        for (IslandCommandPermission permission : values()) {
            if (permission.aliases.contains(normalized)) {
                return permission;
            }
        }
        return null;
    }

    public static IslandCommandPermission fromGuiActionId(String actionId) {
        String id = normalize(actionId);
        if (id.isBlank() || id.equals("gui.close")) {
            return null;
        }
        if (id.equals("island.main.open")) {
            return MENU;
        }
        if (id.startsWith("island.create")) {
            return CREATE;
        }
        if (id.startsWith("island.danger.reset")) {
            return RESET;
        }
        if (id.startsWith("island.danger.delete")) {
            return DELETE;
        }
        if (id.startsWith("island.danger")) {
            return DELETE;
        }
        if (id.startsWith("island.home.set")) {
            return SET_HOME;
        }
        if (id.startsWith("island.home") || id.startsWith("island.homes")) {
            return HOME;
        }
        if (id.startsWith("island.warp.delete") || id.startsWith("island.warp.public")) {
            return SET_WARP;
        }
        if (id.startsWith("island.warp") || id.startsWith("island.warps") || id.startsWith("island.visit")) {
            return WARP;
        }
        if (id.startsWith("island.bank.deposit")) {
            return BANK_DEPOSIT;
        }
        if (id.startsWith("island.bank.withdraw")) {
            return BANK_WITHDRAW;
        }
        if (id.startsWith("island.bank")) {
            return BANK;
        }
        if (id.startsWith("island.warehouse.deposit")) {
            return WAREHOUSE_DEPOSIT;
        }
        if (id.startsWith("island.warehouse.withdraw")) {
            return WAREHOUSE_WITHDRAW;
        }
        if (id.startsWith("island.warehouse")) {
            return WAREHOUSE;
        }
        if (id.startsWith("island.member.invite")) {
            return INVITE;
        }
        if (id.startsWith("island.invite")) {
            return INVITE_RESPOND;
        }
        if (id.startsWith("island.member.remove") || id.startsWith("island.ban") || id.startsWith("island.visitor.kick")) {
            return KICK;
        }
        if (id.startsWith("island.member.promote") || id.startsWith("island.member.role") || id.startsWith("island.role")) {
            return PROMOTE;
        }
        if (id.startsWith("island.member.demote")) {
            return DEMOTE;
        }
        if (id.startsWith("island.members") || id.startsWith("island.roles") || id.startsWith("island.bans")) {
            return MEMBERS;
        }
        if (id.startsWith("island.permissions")) {
            return PERMISSIONS;
        }
        if (id.startsWith("island.settings") || id.startsWith("island.public") || id.startsWith("island.lock") || id.startsWith("island.flag")) {
            return SETTINGS;
        }
        if (id.startsWith("island.snapshot.restore")) {
            return RESTORE;
        }
        if (id.startsWith("island.snapshot") || id.startsWith("island.snapshots")) {
            return SNAPSHOT;
        }
        if (id.startsWith("island.review") || id.startsWith("island.reviews")) {
            return REVIEW;
        }
        if (id.startsWith("island.visitor-stats")) {
            return VISITOR_STATS;
        }
        if (id.startsWith("island.ranking") || id.startsWith("island.level") || id.startsWith("island.worth") || id.startsWith("island.upgrade") || id.startsWith("island.upgrades") || id.startsWith("island.mission") || id.startsWith("island.missions")) {
            return PROGRESSION;
        }
        if (id.startsWith("island.chat") || id.startsWith("island.logs") || id.startsWith("island.log")) {
            return CHAT;
        }
        if (id.startsWith("island.biome") || id.startsWith("island.limit") || id.startsWith("island.limits") || id.startsWith("island.border")) {
            return ENVIRONMENT;
        }
        if (id.startsWith("island.help")) {
            return MENU;
        }
        if (id.startsWith("island.info") || id.startsWith("island.list")) {
            return MENU;
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
