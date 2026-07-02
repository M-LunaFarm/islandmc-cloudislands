package kr.lunaf.cloudislands.paper.command;

import java.util.List;

final class IslandCommandCatalog {
    private static final List<String> COMMAND_ALIASES = List.of(
        "help", "도움말", "commands", "command", "command-list", "명령어", "명령어목록", "menu", "메뉴",
        "create-menu", "templates", "생성메뉴", "템플릿",
        "info", "정보", "list", "my", "my-islands", "목록", "내섬", "create", "생성", "delete", "삭제", "reset", "리셋", "danger", "위험작업",
        "sethome", "셋홈", "homes", "home-menu", "home-list", "홈관리", "홈목록", "home", "홈",
        "warps", "warp-menu", "warp-list", "워프", "워프관리", "워프목록", "public-warps", "publicwarplist", "공개워프목록", "warp", "setwarp", "워프설정",
        "delwarp", "deletewarp", "워프삭제", "warp-public", "publicwarp", "워프공개", "warp-private", "privatewarp", "워프비공개",
        "public", "공개", "private", "비공개", "lock", "잠금", "unlock", "잠금해제",
        "fly", "비행", "keepinventory", "keepinv", "인벤보존", "pvp", "피빕", "publicwarps", "공개워프",
        "visit", "randomvisit", "random-visit", "public-islands", "publicislands", "visit-list", "visitor-stats", "visitorstats", "visitors", "방문", "랜덤방문", "공개섬", "방문목록", "방문통계", "방문자통계",
        "reviews", "review-list", "rate", "review", "delete-review", "review-delete", "reviewdel", "reviewrank", "평가", "후기", "후기삭제", "평가삭제", "평가목록", "후기목록", "평가랭킹", "후기랭킹",
        "level", "레벨", "worth", "value", "가치", "blocks", "block-details", "block-counts", "블록상세", "블록목록", "rank", "ranking", "rank-list", "worthrank", "valuerank", "랭킹", "랭킹목록", "가치랭킹", "levelcalc", "recalculate", "레벨계산",
        "bank", "bank-balance", "은행", "은행잔액", "deposit", "bank-deposit", "입금", "withdraw", "bank-withdraw", "출금",
        "warehouse", "warehouse-list", "warehouse-deposit", "warehouse-withdraw", "storage-box", "chest", "island-chest", "islandchest", "창고", "창고목록", "창고입금", "창고출금",
        "upgrade", "upgrades", "upgrade-menu", "upgrade-list", "buyupgrade", "upgrade-buy", "업그레이드", "업그레이드목록", "업그레이드구매",
        "generator", "generator-info", "생성기", "생성기정보",
        "mission", "missions", "mission-menu", "mission-list", "미션", "미션목록",
        "challenge", "challenges", "challenge-menu", "challenge-list", "챌린지", "챌린지목록",
        "chat", "chat-menu", "islandchat", "채팅", "teamchat", "team-chat", "팀채팅", "log", "logs", "log-menu", "log-list", "로그", "로그목록",
        "biome", "biome-menu", "biome-info", "바이옴", "바이옴정보", "size", "크기", "border", "border-ui", "border-color", "border-visible", "경계", "경계표시", "경계색상",
        "limit", "limits", "limit-menu", "limit-list", "제한", "제한목록", "setlimit", "limit-set", "제한설정",
        "hoppers", "호퍼", "spawners", "스포너", "entities", "엔티티", "redstone", "레드스톤",
        "snapshot", "snapshots", "snapshot-menu", "snapshot-list", "스냅샷", "스냅샷목록", "snapshot-create", "snapshot-request", "스냅샷생성",
        "snapshot-restore", "restore", "rollback", "스냅샷복원", "복원", "롤백",
        "members", "member-menu", "member-list", "멤버", "멤버관리", "멤버목록", "invite", "초대", "invites", "invite-menu", "invite-list", "초대목록",
        "accept", "invite-accept", "초대수락", "decline", "invite-decline", "초대거절",
        "kick", "remove-member", "추방", "trust", "coop", "co-op", "신뢰", "협동", "untrust", "신뢰해제",
        "promote", "승급", "demote", "강등", "setrole", "role-set", "역할설정", "roles", "role-menu", "role-list", "role-upsert", "role-edit", "role-reset", "역할", "역할목록", "역할편집", "역할초기화", "transfer", "양도",
        "ban", "밴", "unban", "pardon", "밴해제", "kickvisitor", "방문자추방", "bans", "ban-menu", "ban-list", "banlist", "밴목록",
        "settings", "setting", "설정", "language", "locale", "언어", "name", "setname", "rename", "이름", "이름설정",
        "flags", "flag-menu", "flag-list", "flag", "setflag", "flag-set", "플래그", "플래그설정", "플래그목록",
        "permissions", "permission-menu", "permission-list", "permission", "perms", "setpermission", "permission-set", "permission-exception", "permission-exception-list", "권한", "권한설정", "권한목록", "권한예외", "권한예외목록"
    );

    private static final List<String> COMMAND_HELP = List.of(
        "섬 help [page]",
        "섬 도움말 [category] [page]",
        "섬 command list [page]",
        "섬",
        "섬 메뉴",
        "섬 생성메뉴",
        "섬 템플릿",
        "섬 생성 [template]",
        "섬 정보",
        "섬 목록",
        "섬 내섬",
        "섬 홈 [name]",
        "섬 홈목록",
        "섬 셋홈 [name]",
        "섬 방문 <섬|플레이어|random>",
        "섬 랜덤방문",
        "섬 공개섬 [limit]",
        "섬 방문통계 [limit]",
        "섬 후기",
        "섬 평가 <islandUuid|current> <1-5> [후기]",
        "섬 후기삭제 [islandUuid|current]",
        "섬 공개",
        "섬 비공개",
        "섬 잠금",
        "섬 잠금해제",
        "섬 워프목록",
        "섬 워프 <name>",
        "섬 워프설정 <name> [category]",
        "섬 워프삭제 <name>",
        "섬 워프공개 <name>",
        "섬 워프비공개 <name>",
        "섬 공개워프목록 [category] [query]",
        "섬 비행 [true|false|on|off]",
        "섬 인벤보존 [true|false|on|off]",
        "섬 피빕 [true|false|on|off]",
        "섬 공개워프 [true|false|on|off]",
        "섬 랭킹 [limit]",
        "섬 랭킹 worth [limit]",
        "섬 가치랭킹 [limit]",
        "섬 레벨",
        "섬 레벨계산",
        "섬 가치",
        "섬 블록상세 [limit]",
        "섬 크기",
        "섬 경계",
        "섬 바이옴 [biomeKey]",
        "섬 은행",
        "섬 입금 <amount>",
        "섬 출금 <amount>",
        "섬 창고",
        "섬 chest",
        "섬 창고입금 <material> <amount>",
        "섬 창고출금 <material> <amount>",
        "섬 업그레이드",
        "섬 업그레이드목록",
        "섬 업그레이드구매 <upgradeKey>",
        "섬 생성기",
        "섬 미션 [missionKey]",
        "섬 챌린지 [challengeKey]",
        "섬 채팅 <message>",
        "섬 팀채팅 <message>",
        "섬 설정",
        "섬 언어 <ko_kr|en_us>",
        "섬 이름 <name>",
        "섬 권한",
        "섬 권한설정 <role> <permission> <true|false|허용|거부>",
        "섬 플래그",
        "섬 권한예외 <player> <permission> <true|false|허용|거부>",
        "섬 권한예외목록",
        "섬 제한 [limitKey value]",
        "섬 호퍼 <limit>",
        "섬 스포너 <limit>",
        "섬 엔티티 <limit>",
        "섬 레드스톤 <limit>",
        "섬 멤버",
        "섬 초대 <player>",
        "섬 초대목록",
        "섬 초대수락 <플레이어|섬|inviteId>",
        "섬 초대거절 <플레이어|섬|inviteId>",
        "섬 추방 <player>",
        "섬 승급 <player>",
        "섬 강등 <player>",
        "섬 역할설정 <player> <role>",
        "섬 역할목록",
        "섬 역할편집 <role> <weight> <displayName>",
        "섬 역할초기화 <role>",
        "섬 양도 <player>",
        "섬 신뢰 <player> [duration]",
        "섬 협동 <player> [duration]",
        "섬 신뢰해제 <player>",
        "섬 밴 <player>",
        "섬 밴해제 <player>",
        "섬 밴목록",
        "섬 방문자추방 <player>",
        "섬 스냅샷 [reason]",
        "섬 스냅샷목록",
        "섬 복원 <snapshotNo>",
        "섬 로그",
        "섬 리셋 [reason]",
        "섬 삭제"
    );

    static final List<String> UPGRADE_KEYS = List.of(
        "size",
        "members",
        "warps",
        "hoppers",
        "spawners",
        "generator",
        "mob",
        "crop",
        "fly",
        "redstone",
        "bank",
        "border",
        "homes",
        "biome",
        "keep-inventory",
        "border-color"
    );

    static final List<HelpCategory> HELP_CATEGORIES = List.of(
        new HelpCategory("기본", List.of("기본", "basic", "start", "시작"), "섬 기본 명령어", List.of(
            "섬",
            "섬 메뉴",
            "섬 생성메뉴",
            "섬 템플릿",
            "섬 생성 [template]",
            "섬 목록",
            "섬 내섬",
            "섬 정보"
        )),
        new HelpCategory("멤버", List.of("멤버", "member", "members", "team", "팀"), "섬 멤버 명령어", List.of(
            "섬 멤버",
            "섬 초대 <player>",
            "섬 초대목록",
            "섬 초대수락 <플레이어|섬|inviteId>",
            "섬 초대거절 <플레이어|섬|inviteId>",
            "섬 추방 <player>",
            "섬 승급 <player>",
            "섬 강등 <player>",
            "섬 역할설정 <player> <role>",
            "섬 역할목록",
            "섬 역할편집 <role> <weight> <displayName>",
            "섬 역할초기화 <role>",
            "섬 양도 <player>",
            "섬 신뢰 <player> [duration]",
            "섬 협동 <player> [duration]",
            "섬 신뢰해제 <player>",
            "섬 밴 <player>",
            "섬 밴해제 <player>",
            "섬 밴목록",
            "섬 방문자추방 <player>"
        )),
        new HelpCategory("방문", List.of("방문", "visit", "visitor", "travel"), "섬 방문 명령어", List.of(
            "섬 홈 [name]",
            "섬 홈목록",
            "섬 셋홈 [name]",
            "섬 방문 <섬|플레이어|random>",
            "섬 랜덤방문",
            "섬 공개섬 [limit]",
            "섬 방문통계 [limit]",
            "섬 후기",
            "섬 평가 <islandUuid|current> <1-5> [후기]",
            "섬 후기삭제 [islandUuid|current]",
            "섬 워프목록",
            "섬 워프 <name>",
            "섬 워프설정 <name> [category]",
            "섬 워프삭제 <name>",
            "섬 워프공개 <name>",
            "섬 워프비공개 <name>",
            "섬 공개워프목록 [category] [query]"
        )),
        new HelpCategory("성장", List.of("성장", "growth", "progression", "경제", "economy"), "섬 성장 명령어", List.of(
            "섬 랭킹 [limit]",
            "섬 랭킹 worth [limit]",
            "섬 가치랭킹 [limit]",
            "섬 레벨",
            "섬 레벨계산",
            "섬 가치",
            "섬 블록상세 [limit]",
            "섬 은행",
            "섬 입금 <amount>",
            "섬 출금 <amount>",
            "섬 창고",
            "섬 chest",
            "섬 창고입금 <material> <amount>",
            "섬 창고출금 <material> <amount>",
            "섬 업그레이드",
            "섬 업그레이드목록",
            "섬 업그레이드구매 <upgradeKey>",
            "섬 생성기",
            "섬 미션 [missionKey]",
            "섬 챌린지 [challengeKey]"
        )),
        new HelpCategory("설정", List.of("설정", "setting", "settings", "config"), "섬 설정 명령어", List.of(
            "섬 공개",
            "섬 비공개",
            "섬 잠금",
            "섬 잠금해제",
            "섬 비행 [true|false|on|off]",
            "섬 인벤보존 [true|false|on|off]",
            "섬 피빕 [true|false|on|off]",
            "섬 공개워프 [true|false|on|off]",
            "섬 크기",
            "섬 경계",
            "섬 바이옴 [biomeKey]",
            "섬 설정",
            "섬 언어 <ko_kr|en_us>",
            "섬 이름 <name>",
            "섬 권한",
            "섬 권한설정 <role> <permission> <true|false|허용|거부>",
            "섬 플래그",
            "섬 권한예외 <player> <permission> <true|false|허용|거부>",
            "섬 권한예외목록",
            "섬 제한 [limitKey value]",
            "섬 호퍼 <limit>",
            "섬 스포너 <limit>",
            "섬 엔티티 <limit>",
            "섬 레드스톤 <limit>"
        )),
        new HelpCategory("관리자", List.of("관리자", "admin", "manage", "관리"), "섬 관리 명령어", List.of(
            "섬 채팅 <message>",
            "섬 팀채팅 <message>",
            "섬 스냅샷 [reason]",
            "섬 스냅샷목록",
            "섬 복원 <snapshotNo>",
            "섬 로그",
            "섬 리셋 [reason]",
            "섬 삭제"
        ))
    );

    static final List<IslandCommandDescriptor> DESCRIPTORS = List.of(
        new IslandCommandDescriptor(
            "island.command.registry",
            COMMAND_ALIASES,
            "기본",
            "IslandCommandPermission.fromSubcommand",
            "섬",
            COMMAND_HELP,
            "island-command-registry-description",
            "IslandCommandPermission.fromGuiActionId",
            RequiredIslandState.ANY,
            "IslandCommandRouter",
            "IslandCommandTabCompleter"
        )
    );

    static final List<String> SUBCOMMANDS = DESCRIPTORS.stream()
        .flatMap(descriptor -> descriptor.aliases().stream())
        .distinct()
        .toList();

    static final List<String> HELP_COMMANDS = DESCRIPTORS.stream()
        .flatMap(descriptor -> descriptor.helpCommands().stream())
        .distinct()
        .toList();

    static List<String> helpCategoryNames() {
        return HELP_CATEGORIES.stream()
            .map(HelpCategory::name)
            .toList();
    }

    static List<String> upgradeKeys() {
        return UPGRADE_KEYS;
    }

    static HelpCategory helpCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        for (HelpCategory category : HELP_CATEGORIES) {
            if (category.aliases().stream().anyMatch(alias -> alias.toLowerCase(java.util.Locale.ROOT).equals(lower))) {
                return category;
            }
        }
        return null;
    }

    private IslandCommandCatalog() {
    }

    record IslandCommandDescriptor(
        String id,
        List<String> aliases,
        String category,
        String permission,
        String usage,
        List<String> examples,
        String descriptionKey,
        String guiActionId,
        RequiredIslandState requiredIslandState,
        String handler,
        String suggestionProvider
    ) {
        IslandCommandDescriptor {
            id = blankDefault(id, "island.command");
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            category = blankDefault(category, "기본");
            permission = blankDefault(permission, "IslandCommandPermission.fromSubcommand");
            usage = blankDefault(usage, "섬");
            examples = examples == null ? List.of() : List.copyOf(examples);
            descriptionKey = blankDefault(descriptionKey, "island-command-description");
            guiActionId = blankDefault(guiActionId, "IslandCommandPermission.fromGuiActionId");
            requiredIslandState = requiredIslandState == null ? RequiredIslandState.ANY : requiredIslandState;
            handler = blankDefault(handler, "IslandCommandRouter");
            suggestionProvider = blankDefault(suggestionProvider, "IslandCommandTabCompleter");
        }

        List<String> helpCommands() {
            return examples.isEmpty() ? List.of(usage) : examples;
        }
    }

    enum RequiredIslandState {
        ANY,
        NO_ISLAND,
        OWNS_ISLAND,
        VISITING_ISLAND,
        ADMIN
    }

    record HelpCategory(String name, List<String> aliases, String title, List<String> commands) {
    }

    private static String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
