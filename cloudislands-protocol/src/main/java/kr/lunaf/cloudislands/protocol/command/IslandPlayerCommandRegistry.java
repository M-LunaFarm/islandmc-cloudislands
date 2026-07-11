package kr.lunaf.cloudislands.protocol.command;

import java.util.List;

public final class IslandPlayerCommandRegistry {
    public static final String REGISTRY_POLICY = "paper-and-velocity-player-help-share-this-canonical-command-registry";

    private static final List<CommandHelpCategory> HELP_CATEGORIES = List.of(
        new CommandHelpCategory("기본", List.of("기본", "basic", "start", "시작"), "섬 기본 명령어", List.of(
            "섬", "섬 메뉴", "섬 생성메뉴", "섬 템플릿", "섬 생성 [template]", "섬 목록", "섬 내섬", "섬 정보", "섬 show [player|island]"
        )),
        new CommandHelpCategory("멤버", List.of("멤버", "member", "members", "team", "팀"), "섬 멤버 명령어", List.of(
            "섬 멤버", "섬 초대 <player>", "섬 초대목록", "섬 초대수락 <플레이어|섬|inviteId>", "섬 초대거절 <플레이어|섬|inviteId>", "섬 탈퇴 confirm", "섬 추방 <player>", "섬 승급 <player>", "섬 강등 <player>", "섬 역할설정 <player> <role>", "섬 역할목록", "섬 역할편집 <role> <weight> <displayName>", "섬 역할초기화 <role>", "섬 양도 <player>", "섬 신뢰 <player> [duration]", "섬 협동 <player> [duration]", "섬 신뢰해제 <player>", "섬 밴 <player>", "섬 밴해제 <player>", "섬 밴목록", "섬 방문자추방 <player>"
        )),
        new CommandHelpCategory("방문", List.of("방문", "visit", "visitor", "travel"), "섬 방문 명령어", List.of(
            "섬 홈 [name]", "섬 홈목록", "섬 셋홈 [name]", "섬 setteleport [name]", "섬 방문 <섬|플레이어|random>", "섬 랜덤방문", "섬 공개섬 [limit]", "섬 방문통계 [limit]", "섬 후기", "섬 ratings [limit]", "섬 평가 <islandUuid|current> <1-5> [후기]", "섬 후기삭제 [islandUuid|current]", "섬 워프목록", "섬 워프 <name>", "섬 워프설정 <name> [category]", "섬 워프삭제 <name>", "섬 워프공개 <name>", "섬 워프비공개 <name>", "섬 공개워프목록 [category] [query]"
        )),
        new CommandHelpCategory("성장", List.of("성장", "growth", "progression", "경제", "economy"), "섬 성장 명령어", List.of(
            "섬 랭킹 [limit]", "섬 top [limit]", "섬 leaderboard [limit]", "섬 랭킹 worth [limit]", "섬 가치랭킹 [limit]", "섬 레벨", "섬 레벨계산", "섬 가치", "섬 블록상세 [limit]", "섬 values [player|island] [limit]", "섬 counts [player|island] [limit]", "섬 은행", "섬 balance [player|island]", "섬 bal [player|island]", "섬 money [player|island]", "섬 입금 <amount>", "섬 출금 <amount>", "섬 창고", "섬 chest", "섬 창고입금 <material> <amount>", "섬 창고출금 <material> <amount>", "섬 업그레이드", "섬 업그레이드목록", "섬 업그레이드구매 <upgradeKey>", "섬 rankup <upgradeKey>", "섬 생성기", "섬 미션 [missionKey]", "섬 챌린지 [challengeKey]"
        )),
        new CommandHelpCategory("설정", List.of("설정", "setting", "settings", "config"), "섬 설정 명령어", List.of(
            "섬 공개", "섬 비공개", "섬 잠금", "섬 잠금해제", "섬 비행 [true|false|on|off]", "섬 인벤보존 [true|false|on|off]", "섬 피빕 [true|false|on|off]", "섬 공개워프 [true|false|on|off]", "섬 크기", "섬 경계", "섬 바이옴 [biomeKey]", "섬 설정", "섬 언어 <ko_kr|en_us>", "섬 이름 <name>", "섬 setdiscord <handle|clear>", "섬 setpaypal <value|clear>", "섬 권한", "섬 권한설정 <role> <permission> <true|false|허용|거부>", "섬 플래그", "섬 권한예외 <player> <permission> <true|false|허용|거부>", "섬 권한예외목록", "섬 제한 [limitKey value]", "섬 toggle blocks", "섬 호퍼 <limit>", "섬 스포너 <limit>", "섬 엔티티 <limit>", "섬 레드스톤 <limit>"
        )),
        new CommandHelpCategory("관리자", List.of("관리자", "admin", "manage", "관리"), "섬 관리 명령어", List.of(
            "섬 채팅 <message>", "섬 팀채팅 <message>", "섬 스냅샷 [reason]", "섬 스냅샷목록", "섬 복원 <snapshotNo>", "섬 로그", "섬 리셋 [reason]", "섬 삭제"
        ))
    );

    private static final List<CommandDescriptor> DESCRIPTORS = List.of(
        descriptor("island.help", List.of("help", "도움말", "commands", "command", "command-list", "명령어", "명령어목록"), "기본", "cloudislands.island.menu", "섬 help [page]", List.of("섬 help [page]", "섬 도움말 [category] [page]", "섬 command list [page]"), "island-command-help-description", "island.help.open", "ANY", "IslandCommandRouter", "helpRootSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.menu", List.of("menu", "panel", "manager", "cp", "메뉴"), "기본", "cloudislands.island.menu", "섬", List.of("섬", "섬 메뉴", "섬 panel", "섬 manager", "섬 cp"), "island-command-menu-description", "island.main.open", "ANY", "IslandCommandRouter", "IslandCommandCatalog.SUBCOMMANDS", CommandExecutionTarget.BOTH),
        descriptor("island.lifecycle", List.of("create-menu", "templates", "생성메뉴", "템플릿", "create", "생성", "delete", "disband", "삭제", "reset", "리셋", "danger", "위험작업"), "기본", "IslandCommandPermission.fromSubcommand", "섬 생성 [template]", List.of("섬 생성메뉴", "섬 템플릿", "섬 생성 [template]", "섬 리셋 [reason]", "섬 삭제", "섬 disband confirm"), "island-command-lifecycle-description", "island.create.open|island.danger.open", "ANY", "IslandLifecycleCommandHandler", "templateSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.overview", List.of("info", "show", "정보", "list", "my", "my-islands", "목록", "내섬"), "기본", "cloudislands.island.menu", "섬 정보", List.of("섬 정보", "섬 show [player|island]", "섬 목록", "섬 내섬"), "island-command-overview-description", "island.info.open|island.list.open", "ANY", "IslandOverviewCommandHandler", "IslandCommandCatalog.SUBCOMMANDS", CommandExecutionTarget.BOTH),
        descriptor("island.home-warp", List.of("sethome", "setteleport", "setspawnpoint", "셋홈", "homes", "home-menu", "home-list", "홈관리", "홈목록", "home", "teleport", "tp", "go", "홈", "warps", "warp-menu", "warp-list", "워프", "워프관리", "워프목록", "public-warps", "publicwarplist", "공개워프목록", "warp", "setwarp", "워프설정", "delwarp", "deletewarp", "warp-delete", "워프삭제", "warp-public", "publicwarp", "워프공개", "warp-private", "privatewarp", "워프비공개"), "방문", "IslandCommandPermission.fromSubcommand", "섬 홈 [name]", List.of("섬 홈 [name]", "섬 teleport [home]", "섬 tp [home]", "섬 go [home]", "섬 홈목록", "섬 셋홈 [name]", "섬 setteleport [name]", "섬 setspawnpoint [name]", "섬 워프목록", "섬 워프 <name>", "섬 워프설정 <name> [category]", "섬 워프삭제 <name>", "섬 워프공개 <name>", "섬 워프비공개 <name>", "섬 공개워프목록 [category] [query]"), "island-command-home-warp-description", "island.home.open|island.warp.open", "OWNS_ISLAND", "IslandHomeWarpCommandHandler", "warpAndHomeSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.settings", List.of("public", "공개", "private", "비공개", "lock", "잠금", "unlock", "잠금해제", "fly", "비행", "keepinventory", "keepinv", "인벤보존", "pvp", "피빕", "publicwarps", "공개워프", "settings", "setting", "설정", "language", "locale", "언어", "name", "setname", "rename", "이름", "이름설정", "setdiscord", "discord", "디스코드", "setpaypal", "paypal", "페이팔", "flags", "flag-menu", "flag-list", "flag", "setflag", "flag-set", "플래그", "플래그설정", "플래그목록"), "설정", "IslandCommandPermission.fromSubcommand", "섬 설정", List.of("섬 공개", "섬 비공개", "섬 잠금", "섬 잠금해제", "섬 비행 [true|false|on|off]", "섬 인벤보존 [true|false|on|off]", "섬 피빕 [true|false|on|off]", "섬 공개워프 [true|false|on|off]", "섬 설정", "섬 언어 <ko_kr|en_us>", "섬 이름 <name>", "섬 setdiscord <handle|clear>", "섬 setpaypal <value|clear>", "섬 플래그"), "island-command-settings-description", "island.settings.open|island.flag.open", "OWNS_ISLAND", "IslandSettingsCommandHandler", "settingsSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.visit-review", List.of("visit", "randomvisit", "random-visit", "public-islands", "publicislands", "visit-list", "visitor-stats", "visitorstats", "visitors", "방문", "랜덤방문", "공개섬", "방문목록", "방문통계", "방문자통계", "reviews", "review-list", "ratings", "rate", "review", "delete-review", "review-delete", "reviewdel", "reviewrank", "평가", "후기", "후기삭제", "평가삭제", "평가목록", "후기목록", "평가랭킹", "후기랭킹"), "방문", "IslandCommandPermission.fromSubcommand", "섬 방문 <섬|플레이어|random>", List.of("섬 방문 <섬|플레이어|random>", "섬 랜덤방문", "섬 공개섬 [limit]", "섬 방문통계 [limit]", "섬 후기", "섬 ratings [limit]", "섬 평가 <islandUuid|current> <1-5> [후기]", "섬 후기삭제 [islandUuid|current]"), "island-command-visit-review-description", "island.visit.open|island.review.open", "ANY", "IslandVisitReviewCommandHandler", "visitReviewSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.progression", List.of("level", "레벨", "worth", "value", "values", "counts", "가치", "blocks", "block-details", "block-counts", "블록상세", "블록목록", "rank", "ranking", "top", "leaderboard", "rank-list", "worthrank", "valuerank", "랭킹", "랭킹목록", "가치랭킹", "levelcalc", "recalculate", "레벨계산", "upgrade", "upgrades", "upgrade-menu", "upgrade-list", "buyupgrade", "upgrade-buy", "rankup", "업그레이드", "업그레이드목록", "업그레이드구매", "generator", "generator-info", "생성기", "생성기정보", "mission", "missions", "mission-menu", "mission-list", "미션", "미션목록", "challenge", "challenges", "challenge-menu", "challenge-list", "챌린지", "챌린지목록"), "성장", "IslandCommandPermission.fromSubcommand", "섬 레벨", List.of("섬 랭킹 [limit]", "섬 top [limit]", "섬 leaderboard [limit]", "섬 랭킹 worth [limit]", "섬 가치랭킹 [limit]", "섬 레벨", "섬 레벨계산", "섬 가치", "섬 블록상세 [limit]", "섬 values [player|island] [limit]", "섬 counts [player|island] [limit]", "섬 업그레이드", "섬 업그레이드목록", "섬 업그레이드구매 <upgradeKey>", "섬 rankup <upgradeKey>", "섬 생성기", "섬 미션 [missionKey]", "섬 챌린지 [challengeKey]"), "island-command-progression-description", "island.ranking.open|island.upgrades.open|island.missions.open", "OWNS_ISLAND", "IslandProgressionCommandHandler", "progressionSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.bank", List.of("bank", "bank-balance", "balance", "bal", "money", "은행", "은행잔액", "deposit", "bank-deposit", "입금", "withdraw", "bank-withdraw", "출금"), "성장", "IslandCommandPermission.fromSubcommand", "섬 은행", List.of("섬 은행", "섬 balance [player|island]", "섬 bal [player|island]", "섬 money [player|island]", "섬 입금 <amount>", "섬 출금 <amount>"), "island-command-bank-description", "island.bank.open", "ANY", "IslandBankCommandHandler", "amountSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.warehouse", List.of("warehouse", "warehouse-list", "warehouse-deposit", "warehouse-withdraw", "storage-box", "chest", "island-chest", "islandchest", "창고", "창고목록", "창고입금", "창고출금"), "성장", "IslandCommandPermission.fromSubcommand", "섬 창고", List.of("섬 창고", "섬 chest", "섬 창고입금 <material> <amount>", "섬 창고출금 <material> <amount>"), "island-command-warehouse-description", "island.warehouse.open", "OWNS_ISLAND", "IslandWarehouseCommandHandler", "warehouseSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.chat-log", List.of("chat", "chat-menu", "islandchat", "채팅", "teamchat", "team-chat", "teamchat-toggle", "팀채팅", "log", "logs", "log-menu", "log-list", "로그", "로그목록"), "관리자", "IslandCommandPermission.fromSubcommand", "섬 채팅 <message>", List.of("섬 채팅 <message>", "섬 팀채팅 <message>", "섬 teamchat toggle", "섬 로그"), "island-command-chat-log-description", "island.chat.open|island.logs.open", "OWNS_ISLAND", "IslandChatLogCommandHandler", "chatLogSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.environment", List.of("biome", "biome-menu", "biome-info", "바이옴", "바이옴정보", "size", "크기", "border", "border-ui", "border-color", "border-visible", "toggle", "toggleblocks", "경계", "경계표시", "경계색상", "토글", "limit", "limits", "limit-menu", "limit-list", "제한", "제한목록", "setlimit", "limit-set", "제한설정", "hoppers", "호퍼", "spawners", "스포너", "entities", "엔티티", "redstone", "레드스톤"), "설정", "IslandCommandPermission.fromSubcommand", "섬 크기", List.of("섬 크기", "섬 경계", "섬 toggle border", "섬 toggle blocks", "섬 toggleblocks", "섬 바이옴 [biomeKey]", "섬 제한 [limitKey value]", "섬 호퍼 <limit>", "섬 스포너 <limit>", "섬 엔티티 <limit>", "섬 레드스톤 <limit>"), "island-command-environment-description", "island.biome.open|island.border.open|island.limits.open", "OWNS_ISLAND", "IslandEnvironmentCommandHandler", "environmentSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.snapshot", List.of("snapshot", "snapshots", "snapshot-menu", "snapshot-list", "스냅샷", "스냅샷목록", "snapshot-create", "snapshot-request", "스냅샷생성", "snapshot-restore", "restore", "rollback", "스냅샷복원", "복원", "롤백"), "관리자", "IslandCommandPermission.fromSubcommand", "섬 스냅샷 [reason]", List.of("섬 스냅샷 [reason]", "섬 스냅샷목록", "섬 복원 <snapshotNo>"), "island-command-snapshot-description", "island.snapshot.open", "OWNS_ISLAND", "IslandSnapshotCommandHandler", "snapshotSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.membership", List.of("members", "coops", "member-menu", "member-list", "team", "showteam", "online", "멤버", "멤버관리", "멤버목록", "invite", "초대", "invites", "invite-menu", "invite-list", "초대목록", "accept", "invite-accept", "초대수락", "decline", "invite-decline", "초대거절", "leave", "탈퇴", "kick", "remove-member", "추방", "trust", "coop", "co-op", "신뢰", "협동", "untrust", "uncoop", "신뢰해제", "promote", "승급", "demote", "강등", "setrole", "role-set", "역할설정", "roles", "role-menu", "role-list", "role-upsert", "role-edit", "role-reset", "역할", "역할목록", "역할편집", "역할초기화", "transfer", "양도", "ban", "밴", "unban", "pardon", "밴해제", "kickvisitor", "방문자추방", "bans", "ban-menu", "ban-list", "banlist", "밴목록"), "멤버", "IslandCommandPermission.fromSubcommand", "섬 멤버", List.of("섬 멤버", "섬 coops", "섬 team [player|island]", "섬 showteam [player|island]", "섬 online [player|island]", "섬 초대 <player>", "섬 초대목록", "섬 초대수락 <플레이어|섬|inviteId>", "섬 초대거절 <플레이어|섬|inviteId>", "섬 탈퇴 confirm", "섬 추방 <player>", "섬 승급 <player>", "섬 강등 <player>", "섬 역할설정 <player> <role>", "섬 역할목록", "섬 역할편집 <role> <weight> <displayName>", "섬 역할초기화 <role>", "섬 양도 <player>", "섬 신뢰 <player> [duration]", "섬 협동 <player> [duration]", "섬 신뢰해제 <player>", "섬 밴 <player>", "섬 밴해제 <player>", "섬 밴목록", "섬 방문자추방 <player>"), "island-command-membership-description", "island.members.open|island.roles.open|island.bans.open", "ANY", "IslandMembershipCommandHandler", "memberRoleAndPlayerSuggestions", CommandExecutionTarget.BOTH),
        descriptor("island.permissions", List.of("permissions", "permission-menu", "permission-list", "permission", "perms", "setpermission", "permission-set", "permission-exception", "permission-exception-list", "권한", "권한설정", "권한목록", "권한예외", "권한예외목록"), "설정", "IslandCommandPermission.fromSubcommand", "섬 권한", List.of("섬 권한", "섬 권한설정 <role> <permission> <true|false|허용|거부>", "섬 권한예외 <player> <permission> <true|false|허용|거부>", "섬 권한예외목록"), "island-command-permissions-description", "island.permissions.open", "OWNS_ISLAND", "IslandMembershipCommandHandler", "permissionSuggestions", CommandExecutionTarget.BOTH)
    );

    public static List<CommandDescriptor> playerDescriptors() {
        return DESCRIPTORS;
    }

    public static List<CommandHelpCategory> helpCategories() {
        return HELP_CATEGORIES;
    }

    public static List<String> playerCommands() {
        return DESCRIPTORS.stream()
            .flatMap(descriptor -> descriptor.helpCommands().stream())
            .distinct()
            .toList();
    }

    private static CommandDescriptor descriptor(
        String id,
        List<String> aliases,
        String category,
        String permission,
        String usage,
        List<String> helpCommands,
        String descriptionKey,
        String guiActionId,
        String requiredIslandState,
        String handler,
        String suggestionProvider,
        CommandExecutionTarget executionTarget
    ) {
        return new CommandDescriptor(
            id,
            aliases.stream().map(CommandAlias::new).toList(),
            category,
            new CommandPermission(permission),
            usage,
            helpCommands,
            descriptionKey,
            guiActionId,
            requiredIslandState,
            handler,
            suggestionProvider,
            executionTarget
        );
    }

    private IslandPlayerCommandRegistry() {
    }
}
