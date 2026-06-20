package kr.lunaf.cloudislands.paper.command;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.failure.CoreApiDegradedModePolicy;
import kr.lunaf.cloudislands.common.feature.PlayerRouteTicketView;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.coreclient.CoreMutationContext;
import kr.lunaf.cloudislands.coreclient.CoreMutationMetadata;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy;
import kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy;
import kr.lunaf.cloudislands.protocol.route.RoutePreparationProgressPolicy;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.gui.ConfirmationTokenPolicy;
import kr.lunaf.cloudislands.paper.gui.DangerousGuiActionPolicy;
import kr.lunaf.cloudislands.paper.gui.GuiStateMenus;
import kr.lunaf.cloudislands.paper.gui.IslandBanMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBiomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandConfirmationMenu;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
import kr.lunaf.cloudislands.paper.gui.IslandDangerMenu;
import kr.lunaf.cloudislands.paper.gui.IslandFlagMenu;
import kr.lunaf.cloudislands.paper.gui.IslandHomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandInfoMenu;
import kr.lunaf.cloudislands.paper.gui.IslandInviteMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLimitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMainMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMemberMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMyIslandsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandPermissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandRoleMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSettingsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandWarpMenu;
import kr.lunaf.cloudislands.paper.gui.GuiClick;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import kr.lunaf.cloudislands.paper.platform.player.BukkitPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.player.PaperPlayerGateway;
import kr.lunaf.cloudislands.paper.platform.world.BukkitWorldGateway;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

final class IslandCommandBackend implements CommandExecutor, Listener {
    static final List<String> SUBCOMMANDS = List.of(
        "help", "도움말", "commands", "command", "command-list", "명령어", "명령어목록", "menu", "메뉴",
        "create-menu", "templates", "생성메뉴", "템플릿",
        "info", "정보", "list", "my", "my-islands", "목록", "내섬", "create", "생성", "delete", "삭제", "reset", "리셋", "danger", "위험작업",
        "sethome", "셋홈", "homes", "home-menu", "home-list", "홈관리", "홈목록", "home", "홈",
        "warps", "warp-menu", "warp-list", "워프", "워프관리", "워프목록", "public-warps", "publicwarplist", "공개워프목록", "warp", "setwarp", "워프설정",
        "delwarp", "deletewarp", "워프삭제", "warp-public", "publicwarp", "워프공개", "warp-private", "privatewarp", "워프비공개",
        "public", "공개", "private", "비공개", "lock", "잠금", "unlock", "잠금해제",
        "fly", "비행", "keepinventory", "keepinv", "인벤보존", "pvp", "피빕", "publicwarps", "공개워프",
        "visit", "randomvisit", "random-visit", "public-islands", "publicislands", "visit-list", "방문", "랜덤방문", "공개섬", "방문목록",
        "reviews", "review-list", "rate", "review", "reviewrank", "평가", "후기", "평가목록", "후기목록", "평가랭킹", "후기랭킹",
        "level", "레벨", "worth", "value", "가치", "blocks", "block-details", "block-counts", "블록상세", "블록목록", "rank", "ranking", "rank-list", "worthrank", "valuerank", "랭킹", "랭킹목록", "가치랭킹", "levelcalc", "recalculate", "레벨계산",
        "bank", "bank-balance", "은행", "은행잔액", "deposit", "bank-deposit", "입금", "withdraw", "bank-withdraw", "출금",
        "warehouse", "warehouse-list", "warehouse-deposit", "warehouse-withdraw", "storage-box", "창고", "창고목록", "창고입금", "창고출금",
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
        "kick", "remove-member", "추방", "trust", "신뢰", "untrust", "신뢰해제",
        "promote", "승급", "demote", "강등", "setrole", "role-set", "역할설정", "roles", "role-menu", "role-list", "role-upsert", "role-edit", "role-reset", "역할", "역할목록", "역할편집", "역할초기화", "transfer", "양도",
        "ban", "밴", "unban", "pardon", "밴해제", "kickvisitor", "방문자추방", "bans", "ban-menu", "ban-list", "banlist", "밴목록",
        "settings", "setting", "설정", "name", "setname", "rename", "이름", "이름설정",
        "flags", "flag-menu", "flag-list", "flag", "setflag", "flag-set", "플래그", "플래그설정", "플래그목록",
        "permissions", "permission-menu", "permission-list", "permission", "perms", "setpermission", "permission-set", "permission-exception", "permission-exception-list", "권한", "권한설정", "권한목록", "권한예외", "권한예외목록"
    );
    static final List<String> HELP_COMMANDS = List.of(
        "섬 help [page]",
        "섬 command list [page]",
        "섬",
        "섬 메뉴",
        "섬 생성메뉴",
        "섬 템플릿",
        "섬 생성 [template]",
        "섬 목록",
        "섬 내섬",
        "섬 홈 [name]",
        "섬 홈목록",
        "섬 셋홈 [name]",
        "섬 방문 <섬|플레이어|random>",
        "섬 랜덤방문",
        "섬 공개섬 [limit]",
        "섬 후기",
        "섬 평가 <islandUuid|current> <1-5> [후기]",
        "섬 공개",
        "섬 비공개",
        "섬 잠금",
        "섬 잠금해제",
        "섬 워프목록",
        "섬 워프 <name>",
        "섬 워프설정 <name>",
        "섬 워프삭제 <name>",
        "섬 워프공개 <name>",
        "섬 워프비공개 <name>",
        "섬 공개워프목록",
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
        "섬 이름 <name>",
        "섬 권한",
        "섬 권한설정 <role> <permission> <true|false|허용|거부>",
        "섬 플래그",
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
        "섬 신뢰 <player>",
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
    private final Plugin plugin;
    private final CoreApiClient coreApiClient;
    private final ProtectionController protection;
    private final int routeWaitSeconds;
    private final String fallbackServerName;
    private final Map<UUID, BossBar> routeBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, StagedPermissionChange>> stagedPermissionChanges = new ConcurrentHashMap<>();
    private final IslandLevelScanService levelScanService;
    private final IslandBankCommandHandler bankCommands;
    private final IslandSnapshotCommandHandler snapshotCommands;
    private final IslandWarehouseCommandHandler warehouseCommands;
    private final IslandChatLogCommandHandler chatLogCommands;
    private final IslandProgressionCommandHandler progressionCommands;
    private final MessageRenderer messages;
    private final PlayerLocaleCache locales;
    private final String configuredNodeId;
    private final PaperPlayerGateway players;
    private final PaperWorldGateway worlds;

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection) {
        this(plugin, coreApiClient, protection, 20);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, "Lobby");
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, null);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, new BukkitPlayerGateway(), new BukkitWorldGateway(plugin));
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PaperPlayerGateway players, PaperWorldGateway worlds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, null, players, worlds);
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds) {
        this(plugin, coreApiClient, protection, routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages, locales, players, worlds, "island-1");
    }

    IslandCommandBackend(Plugin plugin, CoreApiClient coreApiClient, ProtectionController protection, int routeWaitSeconds, String fallbackServerName, IslandLevelScanService levelScanService, EconomyBridge economyBridge, MessageRenderer messages, PlayerLocaleCache locales, PaperPlayerGateway players, PaperWorldGateway worlds, String configuredNodeId) {
        this.plugin = plugin;
        this.coreApiClient = coreApiClient;
        this.protection = protection;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.fallbackServerName = fallbackServerName == null || fallbackServerName.isBlank() ? "Lobby" : fallbackServerName;
        this.levelScanService = levelScanService;
        this.bankCommands = new IslandBankCommandHandler(plugin, coreApiClient, economyBridge, new IslandBankCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.snapshotCommands = new IslandSnapshotCommandHandler(plugin, coreApiClient, new IslandSnapshotCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public String actionResultMessage(String action, UUID id, String body) {
                return IslandCommandBackend.this.actionResultMessage(action, id, body);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }

            @Override
            public void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
                IslandCommandBackend.this.openConfirmation(player, title, description, material, confirmName, confirmAction, data, confirmLore, cancelAction);
            }

            @Override
            public boolean confirmationAccepted(Player player, String actionId, Map<String, String> data, GuiClick click) {
                return IslandCommandBackend.this.confirmationAccepted(player, actionId, data, click);
            }
        });
        this.warehouseCommands = new IslandWarehouseCommandHandler(coreApiClient, new IslandWarehouseCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public String coreWriteFailureMessage(Throwable error, String fallback) {
                return IslandCommandBackend.this.coreWriteFailureMessage(error, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }
        });
        this.chatLogCommands = new IslandChatLogCommandHandler(plugin, coreApiClient, new IslandChatLogCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutate(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.progressionCommands = new IslandProgressionCommandHandler(plugin, coreApiClient, levelScanService, new IslandProgressionCommandHandler.Runtime() {
            @Override
            public java.util.Optional<UUID> currentIsland(Player player, String missingMessage) {
                return IslandCommandBackend.this.currentIsland(player, missingMessage);
            }

            @Override
            public boolean allowed(Player player, IslandPermission permission) {
                return IslandCommandBackend.this.allowed(player, permission);
            }

            @Override
            public void message(Player player, String message) {
                IslandCommandBackend.this.message(player, message);
            }

            @Override
            public String routeMessage(String key, String fallback) {
                return IslandCommandBackend.this.routeMessage(key, fallback);
            }

            @Override
            public String playerCodeMessage(String code, String fallback) {
                return IslandCommandBackend.this.playerCodeMessage(code, fallback);
            }

            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
                return IslandCommandBackend.this.mutateIdempotent(auditAction, operation);
            }

            @Override
            public MessageRenderer messagesFor(Player player) {
                return IslandCommandBackend.this.messagesFor(player);
            }
        });
        this.messages = messages;
        this.locales = locales;
        this.configuredNodeId = configuredNodeId == null || configuredNodeId.isBlank() ? "island-1" : configuredNodeId;
        this.players = players;
        this.worlds = worlds;
    }

    private <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.request(auditAction), operation);
    }

    private <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation) {
        return CoreMutationContext.with(CoreMutationMetadata.idempotent(auditAction), operation);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(routeMessage("player-only-command", "플레이어만 사용할 수 있습니다."));
            return true;
        }
        if (args.length == 0) {
            sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, 1);
            return true;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        int commandListPage = commandListPage(args);
        if (commandListPage > 0) {
            sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, commandListPage);
            return true;
        }
        if (subcommand.equals("menu") || subcommand.equals("메뉴")) {
            sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, 1);
            return true;
        }
        if (subcommand.equals("create-menu") || subcommand.equals("templates") || subcommand.equals("생성메뉴") || subcommand.equals("템플릿")) {
            IslandCreateMenu.open(plugin, coreApiClient, player, messagesFor(player));
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
            IslandMyIslandsMenu.open(plugin, coreApiClient, player, messagesFor(player));
            return true;
        }
        if (subcommand.equals("delete") || subcommand.equals("삭제")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                deleteIsland(player);
            } else {
                IslandDangerMenu.open(player, messagesFor(player));
            }
            return true;
        }
        if (subcommand.equals("reset") || subcommand.equals("리셋")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("confirm")) {
                resetIsland(player, args.length > 2 ? joined(args, 2) : "player-reset");
            } else {
                IslandDangerMenu.open(player, messagesFor(player));
            }
            return true;
        }
        if (subcommand.equals("danger") || subcommand.equals("위험작업")) {
            IslandDangerMenu.open(player, messagesFor(player));
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
            if (args.length > 1) {
                listPublicWarps(player, args[1], args.length > 2 ? joined(args, 2) : "");
            } else {
                IslandWarpMenu.openPublic(plugin, coreApiClient, player, messages);
            }
            return true;
        }
        if (subcommand.equals("warp")) {
            if (args.length > 2) {
                UUID islandId = uuid(args[1]);
                if (islandId == null) {
                    message(player, routeMessage("input-island-uuid-invalid", "섬 UUID가 올바르지 않습니다."));
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
                message(player, routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            teleportWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("setwarp") || subcommand.equals("워프설정")) {
            if (args.length < 2) {
                message(player, routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            setWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("delwarp") || subcommand.equals("deletewarp") || subcommand.equals("워프삭제")) {
            if (args.length < 2) {
                message(player, routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            deleteWarp(player, args[1]);
            return true;
        }
        if (subcommand.equals("warp-public") || subcommand.equals("publicwarp") || subcommand.equals("워프공개")) {
            if (args.length < 2) {
                message(player, routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
                return true;
            }
            setWarpPublicAccess(player, args[1], true);
            return true;
        }
        if (subcommand.equals("warp-private") || subcommand.equals("privatewarp") || subcommand.equals("워프비공개")) {
            if (args.length < 2) {
                message(player, routeMessage("input-warp-name-required", "워프 이름을 입력해주세요."));
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
                IslandVisitMenu.open(plugin, coreApiClient, player, messagesFor(player));
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
        if (subcommand.equals("public-islands") || subcommand.equals("publicislands") || subcommand.equals("visit-list") || subcommand.equals("공개섬") || subcommand.equals("방문목록")) {
            listPublicIslands(player, rankingLimit(args, 1));
            return true;
        }
        if (subcommand.equals("reviews") || subcommand.equals("review-list") || subcommand.equals("후기") || subcommand.equals("후기목록")) {
            listIslandReviews(player, args.length > 1 ? integer(args[1], 10) : 10);
            return true;
        }
        if (subcommand.equals("rate") || subcommand.equals("review") || subcommand.equals("평가")) {
            if (args.length < 3) {
                message(player, routeMessage("input-review-required", "평가할 섬과 1~5점 평점을 입력해주세요."));
                return true;
            }
            rateIslandReview(player, args[1], integer(args[2], 0), args.length > 3 ? joined(args, 3) : "");
            return true;
        }
        if (progressionCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (bankCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (warehouseCommands.handleCommand(player, subcommand, args)) {
            return true;
        }
        if (chatLogCommands.handleCommand(player, subcommand, args)) {
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
        if (subcommand.equals("border") || subcommand.equals("border-ui") || subcommand.equals("경계")) {
            handleIslandBorder(player, args);
            return true;
        }
        if (subcommand.equals("border-color") || subcommand.equals("경계색상")) {
            if (args.length < 2) {
                message(player, "경계 색상을 입력해주세요. 예: /섬 경계색상 blue");
                return true;
            }
            setIslandBorderFlag(player, IslandFlag.BORDER_COLOR, normalizeBorderColor(args[1]), true);
            return true;
        }
        if (subcommand.equals("border-visible") || subcommand.equals("경계표시")) {
            if (args.length < 2) {
                message(player, "경계 표시 여부를 입력해주세요. 예: /섬 경계표시 켜기");
                return true;
            }
            setIslandBorderFlag(player, IslandFlag.BORDER_VISIBLE, flagToggleValue(args, 1), true);
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
                message(player, routeMessage("input-limit-key-value-required", "제한 키와 값을 입력해주세요."));
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
        if (snapshotCommands.handleCommand(player, subcommand, args)) {
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
                message(player, routeMessage("input-invite-player-required", "초대할 플레이어를 입력해주세요."));
                return true;
            }
            inviteIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("invites") || subcommand.equals("invite-menu") || subcommand.equals("초대목록")) {
            IslandInviteMenu.open(plugin, coreApiClient, player, messagesFor(player));
            return true;
        }
        if (subcommand.equals("invite-list")) {
            listPendingInvites(player);
            return true;
        }
        if (subcommand.equals("accept") || subcommand.equals("invite-accept") || subcommand.equals("초대수락")) {
            if (args.length < 2) {
                message(player, routeMessage("input-invite-accept-target-required", "수락할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요."));
                return true;
            }
            acceptIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("decline") || subcommand.equals("invite-decline") || subcommand.equals("초대거절")) {
            if (args.length < 2) {
                message(player, routeMessage("input-invite-decline-target-required", "거절할 초대 ID, 섬 ID/이름, 또는 초대한 플레이어를 입력해주세요."));
                return true;
            }
            declineIslandInviteTarget(player, args[1]);
            return true;
        }
        if (subcommand.equals("kick") || subcommand.equals("remove-member") || subcommand.equals("추방")) {
            if (args.length < 2) {
                message(player, routeMessage("input-remove-player-required", "추방할 플레이어를 입력해주세요."));
                return true;
            }
            removeIslandMember(player, args[1]);
            return true;
        }
        if (subcommand.equals("trust") || subcommand.equals("신뢰")) {
            if (args.length < 2) {
                message(player, routeMessage("input-trust-player-required", "신뢰할 플레이어를 입력해주세요."));
                return true;
            }
            if (args.length > 2) {
                trustIslandMemberTemporary(player, args[1], args[2]);
            } else {
                setIslandMemberRole(player, args[1], IslandRole.TRUSTED, "섬 신뢰 멤버로 설정했습니다.");
            }
            return true;
        }
        if (subcommand.equals("untrust") || subcommand.equals("신뢰해제")) {
            if (args.length < 2) {
                message(player, routeMessage("input-untrust-player-required", "신뢰 해제할 플레이어를 입력해주세요."));
                return true;
            }
            setIslandMemberRole(player, args[1], IslandRole.MEMBER, "섬 신뢰를 해제했습니다.");
            return true;
        }
        if (subcommand.equals("promote") || subcommand.equals("승급")) {
            if (args.length < 2) {
                message(player, routeMessage("input-promote-player-required", "승급할 플레이어를 입력해주세요."));
                return true;
            }
            setIslandMemberRole(player, args[1], IslandRole.MODERATOR, "섬 멤버를 승급했습니다.");
            return true;
        }
        if (subcommand.equals("demote") || subcommand.equals("강등")) {
            if (args.length < 2) {
                message(player, routeMessage("input-demote-player-required", "강등할 플레이어를 입력해주세요."));
                return true;
            }
            setIslandMemberRole(player, args[1], IslandRole.MEMBER, "섬 멤버를 강등했습니다.");
            return true;
        }
        if (subcommand.equals("setrole") || subcommand.equals("role-set") || subcommand.equals("역할설정")) {
            if (args.length < 3) {
                message(player, routeMessage("input-member-role-required", "역할을 바꿀 플레이어와 역할을 입력해주세요."));
                return true;
            }
            String roleKey = roleKey(args[2]);
            if (!editableRoleKey(roleKey)) {
                message(player, routeMessage("input-member-role-invalid", "올바른 멤버 역할을 입력해주세요. 예: MEMBER, MODERATOR, BUILDER"));
                return true;
            }
            setIslandMemberRole(player, args[1], roleKey, "섬 멤버 역할을 " + roleKey + "(으)로 변경했습니다.");
            return true;
        }
        if (subcommand.equals("roles") || subcommand.equals("role-menu") || subcommand.equals("역할")) {
            openIslandRoleMenu(player);
            return true;
        }
        if (subcommand.equals("role-list") || subcommand.equals("역할목록")) {
            listIslandRoles(player);
            return true;
        }
        if (subcommand.equals("role-upsert") || subcommand.equals("role-edit") || subcommand.equals("역할편집")) {
            if (args.length < 4) {
                message(player, routeMessage("input-role-edit-required", "역할, 가중치, 표시 이름을 입력해주세요."));
                return true;
            }
            String roleKey = roleKey(args[1]);
            if (!editableRoleKey(roleKey)) {
                message(player, routeMessage("input-role-edit-invalid", "편집 가능한 멤버 역할을 입력해주세요. 예: BUILDER"));
                return true;
            }
            upsertIslandRole(player, roleKey, integer(args[2], defaultRoleWeight(roleKey)), joined(args, 3));
            return true;
        }
        if (subcommand.equals("role-reset") || subcommand.equals("역할초기화")) {
            if (args.length < 2) {
                message(player, routeMessage("input-role-reset-required", "초기화할 역할을 입력해주세요."));
                return true;
            }
            String roleKey = roleKey(args[1]);
            if (!editableRoleKey(roleKey)) {
                message(player, routeMessage("input-role-reset-invalid", "초기화 가능한 멤버 역할을 입력해주세요. 예: BUILDER"));
                return true;
            }
            resetIslandRole(player, roleKey);
            return true;
        }
        if (subcommand.equals("transfer") || subcommand.equals("양도")) {
            if (args.length < 2) {
                message(player, routeMessage("input-transfer-player-required", "양도할 플레이어를 입력해주세요."));
                return true;
            }
            transferIslandOwnership(player, args[1]);
            return true;
        }
        if (subcommand.equals("ban") || subcommand.equals("밴")) {
            if (args.length < 2) {
                message(player, routeMessage("input-ban-player-required", "밴할 플레이어를 입력해주세요."));
                return true;
            }
            banIslandVisitor(player, args[1], args.length > 2 ? joined(args, 2) : "");
            return true;
        }
        if (subcommand.equals("unban") || subcommand.equals("pardon") || subcommand.equals("밴해제")) {
            if (args.length < 2) {
                message(player, routeMessage("input-pardon-player-required", "밴 해제할 플레이어를 입력해주세요."));
                return true;
            }
            pardonIslandVisitor(player, args[1]);
            return true;
        }
        if (subcommand.equals("kickvisitor") || subcommand.equals("방문자추방")) {
            if (args.length < 2) {
                message(player, routeMessage("input-kick-visitor-required", "추방할 방문자를 입력해주세요."));
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
        if (subcommand.equals("name") || subcommand.equals("setname") || subcommand.equals("rename") || subcommand.equals("이름") || subcommand.equals("이름설정")) {
            if (args.length < 2) {
                message(player, routeMessage("input-island-name-required", "새 섬 이름을 입력해주세요."));
                return true;
            }
            setIslandName(player, joined(args, 1));
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
                message(player, routeMessage("input-flag-value-required", "플래그와 값을 입력해주세요."));
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
        if (subcommand.equals("permission-exception-list") || subcommand.equals("권한예외목록")) {
            listIslandPermissions(player);
            return true;
        }
        if (subcommand.equals("permission-exception") || subcommand.equals("권한예외")) {
            if (args.length < 4) {
                message(player, "플레이어, 권한, 허용 여부를 입력해주세요. 예: /섬 권한예외 Steve BUILD 허용");
                return true;
            }
            setIslandPermissionOverride(player, args[1], args[2], args[3]);
            return true;
        }
        if (subcommand.equals("setpermission") || subcommand.equals("permission-set") || subcommand.equals("권한설정")) {
            if (args.length < 4) {
                message(player, routeMessage("input-permission-set-required", "역할, 권한, 허용 여부를 입력해주세요."));
                return true;
            }
            setIslandPermission(player, args[1], args[2], args[3]);
            return true;
        }
        sendCommandList(player, label, "섬 명령어 목록", HELP_COMMANDS, 1);
        return true;
    }

    void executeGuiAction(Player player, String actionId, Map<String, String> data, GuiClick click) {
        if (actionId == null || actionId.isBlank()) {
            return;
        }
        if (bankCommands.handleGuiAction(player, actionId, data == null ? Map.of() : data)) {
            return;
        }
        if (snapshotCommands.handleGuiAction(player, actionId, data == null ? Map.of() : data, click)) {
            return;
        }
        if (chatLogCommands.handleGuiAction(player, actionId, data == null ? Map.of() : data)) {
            return;
        }
        if (progressionCommands.handleGuiAction(player, actionId, data == null ? Map.of() : data)) {
            return;
        }
        switch (actionId) {
            case "island.main.open" -> sendCommandList(player, "섬", "섬 명령어 목록", HELP_COMMANDS, 1);
            case "island.create.open" -> IslandCreateMenu.open(plugin, coreApiClient, player, messagesFor(player));
            case "island.create" -> createIsland(player, data.getOrDefault("templateId", "default"));
            case "island.info.open" -> openIslandInfoMenu(player);
            case "island.list.open" -> IslandMyIslandsMenu.open(plugin, coreApiClient, player, messagesFor(player));
            case "island.home" -> {
                if (click.right()) {
                    openIslandHomeMenu(player);
                } else {
                    teleportHome(player, data.getOrDefault("homeName", "default"));
                }
            }
            case "island.homes.open" -> openIslandHomeMenu(player);
            case "island.home.set" -> setHome(player, data.getOrDefault("homeName", "default"));
            case "island.warps.open" -> openIslandWarpMenu(player);
            case "island.warp.teleport" -> {
                String islandId = data.getOrDefault("islandId", "");
                String warpName = data.getOrDefault("warpName", "default");
                UUID uuid = uuid(islandId);
                if (uuid != null) {
                    routeWarp(player, uuid, warpName);
                } else {
                    teleportWarp(player, warpName);
                }
            }
            case "island.warp.delete.prepare" -> openConfirmation(player,
                routeMessage("warp-delete-confirm-title", "워프 삭제 확인"),
                routeMessage("warp-delete-confirm-description", "워프를 삭제하면 해당 이름으로 이동할 수 없습니다."),
                Material.ENDER_PEARL,
                routeMessage("warp-delete-confirm-name", "워프 삭제"),
                "island.warp.delete.confirm",
                Map.of("warpName", data.getOrDefault("warpName", "default")),
                routeMessage("warp-delete-confirm-lore", "클릭하면 Core에 워프 삭제를 요청합니다."),
                "island.warps.open");
            case "island.warp.delete.confirm" -> {
                if (confirmationAccepted(player, "island.warp.delete.confirm", data, click)) {
                    deleteWarp(player, data.getOrDefault("warpName", "default"));
                }
            }
            case "island.warp.public" -> setWarpPublicAccess(player, data.getOrDefault("warpName", "default"), true);
            case "island.warp.private" -> setWarpPublicAccess(player, data.getOrDefault("warpName", "default"), false);
            case "island.warp.public.toggle" -> setWarpPublicAccess(player, data.getOrDefault("warpName", "default"), !Boolean.parseBoolean(data.getOrDefault("publicAccess", "false")));
            case "island.visit.open" -> IslandVisitMenu.open(plugin, coreApiClient, player, messagesFor(player));
            case "island.visit.random" -> routeRandomVisit(player);
            case "island.visit.public.open" -> listPublicIslands(player, 10);
            case "island.visit.target" -> routeVisitTarget(player, data.getOrDefault("target", ""));
            case "island.members.open" -> openIslandMemberMenu(player);
            case "island.member.detail" -> {
                message(player, routeMessage("member-detail-title", "멤버 상세"));
                message(player, "- " + routeMessage("member-detail-player", "플레이어: ") + data.getOrDefault("playerName", data.getOrDefault("playerUuid", "")));
                message(player, "- " + routeMessage("member-detail-role", "역할: ") + data.getOrDefault("role", "unknown"));
                message(player, "- " + routeMessage("member-detail-presence", "네트워크 상태: ") + data.getOrDefault("presenceState", "UNKNOWN"));
                message(player, "- " + routeMessage("member-detail-last-seen", "마지막 활동: ") + data.getOrDefault("lastSeenAt", routeMessage("member-detail-last-seen-empty", "기록 없음")));
            }
            case "island.member.role" -> listIslandMembers(player);
            case "island.members.page" -> openIslandMemberMenu(player, (int) longValue(data.getOrDefault("page", "0"), 0L));
            case "island.member.invite", "island.member.invite.help" -> message(player, routeMessage("member-invite-help", "멤버 초대는 /섬 초대 <플레이어> 로 요청합니다."));
            case "island.member.list" -> listIslandMembers(player);
            case "island.member.promote.prepare" -> openConfirmation(player,
                routeMessage("member-promote-confirm-title", "멤버 승급 확인"),
                routeMessage("member-promote-confirm-description", "선택한 플레이어를 MODERATOR 역할로 변경합니다."),
                Material.EMERALD,
                routeMessage("member-promote-confirm-name", "승급 확인"),
                "island.member.promote",
                Map.of("playerUuid", data.getOrDefault("playerUuid", "")),
                routeMessage("member-promote-confirm-lore", "클릭하면 Core에 역할 변경을 요청합니다."),
                "island.members.open");
            case "island.member.promote" -> {
                if (confirmationAccepted(player, "island.member.promote", data, click)) {
                    setIslandMemberRole(player, data.getOrDefault("playerUuid", ""), IslandRole.MODERATOR, "섬 멤버를 승급했습니다.");
                }
            }
            case "island.member.demote.prepare" -> openConfirmation(player,
                routeMessage("member-demote-confirm-title", "멤버 강등 확인"),
                routeMessage("member-demote-confirm-description", "선택한 플레이어를 MEMBER 역할로 변경합니다."),
                Material.IRON_INGOT,
                routeMessage("member-demote-confirm-name", "강등 확인"),
                "island.member.demote",
                Map.of("playerUuid", data.getOrDefault("playerUuid", "")),
                routeMessage("member-demote-confirm-lore", "클릭하면 Core에 역할 변경을 요청합니다."),
                "island.members.open");
            case "island.member.demote" -> {
                if (confirmationAccepted(player, "island.member.demote", data, click)) {
                    setIslandMemberRole(player, data.getOrDefault("playerUuid", ""), IslandRole.MEMBER, "섬 멤버를 강등했습니다.");
                }
            }
            case "island.member.remove.prepare" -> openConfirmation(player,
                routeMessage("member-remove-confirm-title", "멤버 추방 확인"),
                routeMessage("member-remove-confirm-description", "선택한 플레이어를 섬 멤버에서 제거합니다."),
                Material.BARRIER,
                routeMessage("member-remove-confirm-name", "멤버 추방"),
                "island.member.remove.confirm",
                Map.of("playerUuid", data.getOrDefault("playerUuid", "")),
                routeMessage("member-remove-confirm-lore", "클릭하면 Core에 멤버 추방을 요청합니다."),
                "island.members.open");
            case "island.member.remove.confirm" -> {
                if (confirmationAccepted(player, "island.member.remove.confirm", data, click)) {
                    removeIslandMember(player, data.getOrDefault("playerUuid", ""));
                }
            }
            case "island.invites.open" -> IslandInviteMenu.open(plugin, coreApiClient, player, messagesFor(player));
            case "island.invite.accept" -> acceptIslandInviteTarget(player, data.getOrDefault("inviteId", ""));
            case "island.invite.decline" -> declineIslandInviteTarget(player, data.getOrDefault("inviteId", ""));
            case "island.bans.open" -> openIslandBanMenu(player);
            case "island.bans.list" -> listIslandBans(player);
            case "island.ban.pardon.prepare" -> openConfirmation(player,
                routeMessage("ban-pardon-confirm-title", "밴 해제 확인"),
                routeMessage("ban-pardon-confirm-description", "선택한 방문자의 밴을 해제합니다."),
                Material.MILK_BUCKET,
                routeMessage("ban-pardon-confirm-name", "밴 해제"),
                "island.ban.pardon.confirm",
                Map.of("playerUuid", data.getOrDefault("playerUuid", "")),
                routeMessage("ban-pardon-confirm-lore", "클릭하면 Core에 밴 해제를 요청합니다."),
                "island.bans.open");
            case "island.ban.pardon.confirm" -> {
                if (confirmationAccepted(player, "island.ban.pardon.confirm", data, click)) {
                    pardonIslandVisitor(player, data.getOrDefault("playerUuid", ""));
                }
            }
            case "island.permissions.open" -> openIslandPermissionMenu(player);
            case "island.permissions.page" -> openIslandPermissionMenu(player, (int) longValue(data.getOrDefault("page", "0"), 0L), (int) longValue(data.getOrDefault("rolePage", "0"), 0L));
            case "island.permissions.list" -> listIslandPermissions(player);
            case "island.permissions.save" -> saveStagedIslandPermissions(player);
            case "island.permissions.reset" -> resetStagedIslandPermissions(player);
            case "island.permissions.set" -> stageIslandPermission(player, data.getOrDefault("role", ""), data.getOrDefault("permission", ""), click.right() ? "false" : "true");
            case "island.roles.open" -> openIslandRoleMenu(player);
            case "island.role.weight.adjust" -> adjustIslandRoleWeight(player, data.getOrDefault("role", ""), data.getOrDefault("weight", "0"), data.getOrDefault("displayName", ""), click);
            case "island.roles.list" -> listIslandRoles(player);
            case "island.settings.open" -> openIslandSettings(player);
            case "island.public.toggle" -> setIslandPublicAccess(player, !click.right());
            case "island.lock.toggle" -> setIslandLocked(player, click.right());
            case "island.flags.open" -> openIslandFlagMenu(player);
            case "island.flags.list" -> listIslandFlags(player);
            case "island.flag.set" -> setIslandFlag(player, data.getOrDefault("flag", ""), click.right() ? "false" : "true");
            case "island.biome.open" -> openIslandBiomeMenu(player);
            case "island.biome.show" -> showIslandBiome(player);
            case "island.biome.set" -> setIslandBiome(player, data.getOrDefault("biomeKey", ""));
            case "island.limits.open" -> openIslandLimitMenu(player);
            case "island.limits.list" -> listIslandLimits(player);
            case "island.limit.set" -> setIslandLimit(player, data.getOrDefault("limitKey", ""), longValue(data.getOrDefault("value", "0"), 0L));
            case "island.danger.open" -> IslandDangerMenu.open(player, messagesFor(player));
            case "island.danger.reset.prepare" -> IslandDangerMenu.openResetConfirm(player, messagesFor(player));
            case "island.danger.delete.prepare" -> IslandDangerMenu.openDeleteConfirm(player, messagesFor(player));
            case "island.danger.reset.confirm" -> {
                if (dangerConfirmed(player, data, click, DangerousGuiActionPolicy.RESET_OPERATION, DangerousGuiActionPolicy.RESET_TOKEN)) {
                    resetIsland(player, data.getOrDefault("reason", "player-reset"));
                }
            }
            case "island.danger.delete.confirm" -> {
                if (dangerConfirmed(player, data, click, DangerousGuiActionPolicy.DELETE_OPERATION, DangerousGuiActionPolicy.DELETE_TOKEN)) {
                    deleteIsland(player);
                }
            }
            case "admin.node.open" -> openAdminNodeMenu(player, adminNodeId(data));
            case "admin.node.list" -> listAdminNodes(player);
            case "admin.node.info" -> refreshAdminNodeInfo(player, adminNodeId(data));
            case "admin.node.islands" -> listAdminNodeIslands(player, adminNodeId(data));
            case "admin.node.drain" -> drainAdminNode(player, adminNodeId(data));
            case "admin.node.undrain" -> undrainAdminNode(player, adminNodeId(data));
            case "admin.node.sweep" -> sweepAdminNode(player, adminNodeId(data));
            case "admin.node.kickall.prepare" -> openAdminNodeKickAllConfirmation(player, adminNodeId(data));
            case "admin.node.shutdown-safe.prepare" -> openAdminNodeShutdownConfirmation(player, adminNodeId(data));
            case "admin.node.kickall.confirm" -> {
                if (confirmationAccepted(player, "admin.node.kickall.confirm", data, click)) {
                    String nodeId = adminNodeId(data);
                    kickAllAdminNode(player, nodeId, data.getOrDefault("reason", "admin-gui"));
                }
            }
            case "admin.node.shutdown-safe.confirm" -> {
                if (confirmationAccepted(player, "admin.node.shutdown-safe.confirm", data, click)) {
                    String nodeId = adminNodeId(data);
                    shutdownAdminNodeSafely(player, nodeId, data.getOrDefault("reason", "admin-gui"));
                }
            }
            case "admin.island.where.prompt",
                "admin.island.migrate.prompt" ->
                message(player, routeMessage("admin-node-direct-required", "섬 UUID와 대상 노드 입력이 필요한 관리 작업입니다. 관리자 명령 도움말을 확인해주세요."));
            case "gui.close" -> player.closeInventory();
            default -> message(player, routeMessage("gui-action-unknown", "알 수 없는 GUI 작업입니다: ") + actionId);
        }
    }

    private void openConfirmation(Player player, String title, String description, Material material, String confirmName, String confirmAction, Map<String, String> data, String confirmLore, String cancelAction) {
        IslandConfirmationMenu.open(player, messagesFor(player), IslandConfirmationMenu.Confirmation.of(
            title,
            description,
            material,
            confirmName,
            confirmAction,
            ConfirmationTokenPolicy.withToken(confirmAction, data),
            confirmLore,
            cancelAction
        ));
    }

    private boolean confirmationAccepted(Player player, String actionId, Map<String, String> data, GuiClick click) {
        if (ConfirmationTokenPolicy.confirmed(actionId, data, click)) {
            return true;
        }
        message(player, routeMessage("confirmation-token-invalid", "확인 토큰이 올바르지 않습니다. 확인 화면을 다시 열어주세요."));
        return false;
    }

    private boolean dangerConfirmed(Player player, Map<String, String> data, GuiClick click, String operation, String token) {
        if (DangerousGuiActionPolicy.confirmed(data, click, operation, token)) {
            return true;
        }
        message(player, routeMessage("danger-confirm-token-invalid", "위험 작업 확인 토큰이 올바르지 않습니다. 확인 화면을 다시 열어주세요."));
        return false;
    }

    private String adminNodeId(Map<String, String> data) {
        String configured = configuredNodeId;
        String nodeId = data == null ? configured : data.getOrDefault("nodeId", configured);
        return nodeId == null || nodeId.isBlank() ? configured : nodeId;
    }

    private void openAdminNodeMenu(Player player, String nodeId) {
        AdminNodeMenu.open(player, nodeId, messagesFor(player));
    }

    private void listAdminNodes(Player player) {
        coreApiClient.listNodes()
            .thenAccept(body -> message(player, routeMessage("admin-node-list-result-prefix", "노드 목록: ") + adminNodeBodySummary(body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-list-failed", "노드 목록을 불러오지 못했습니다.", error));
    }

    private void refreshAdminNodeInfo(Player player, String nodeId) {
        coreApiClient.nodeInfo(nodeId)
            .thenAccept(body -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> AdminNodeMenu.open(player, nodeId, body, messagesFor(player))))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-info-failed", "노드 정보를 불러오지 못했습니다.", error));
    }

    private void listAdminNodeIslands(Player player, String nodeId) {
        coreApiClient.nodeIslands(nodeId, 50)
            .thenAccept(body -> message(player, routeMessage("admin-node-islands-result-prefix", "노드 섬 현황: ") + adminNodeBodySummary(body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-islands-failed", "노드 섬 현황을 불러오지 못했습니다.", error));
    }

    private void drainAdminNode(Player player, String nodeId) {
        mutate("admin.node.drain", () -> coreApiClient.drainNode(nodeId))
            .thenAccept(body -> message(player, adminNodeActionMessage("Node drain", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node drain 실패", error));
    }

    private void undrainAdminNode(Player player, String nodeId) {
        mutate("admin.node.undrain", () -> coreApiClient.undrainNode(nodeId))
            .thenAccept(body -> message(player, adminNodeActionMessage("Node undrain", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node undrain 실패", error));
    }

    private void sweepAdminNode(Player player, String nodeId) {
        mutate("admin.node.sweep", () -> coreApiClient.sweepNode(nodeId))
            .thenAccept(body -> message(player, adminNodeActionMessage("Node sweep", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-action-failed", "Node sweep 실패", error));
    }

    private void kickAllAdminNode(Player player, String nodeId, String reason) {
        mutateIdempotent("admin.node.kickall", () -> coreApiClient.kickAllNode(nodeId, reason))
            .thenAccept(body -> message(player, adminNodeActionMessage("Node kickall", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-danger-action-failed", "Node kickall 실패", error));
    }

    private void shutdownAdminNodeSafely(Player player, String nodeId, String reason) {
        mutateIdempotent("admin.node.shutdown-safe", () -> coreApiClient.shutdownNodeSafely(nodeId, reason))
            .thenAccept(body -> message(player, adminNodeActionMessage("Node shutdown-safe", nodeId, body)))
            .exceptionally(error -> adminNodeFailure(player, "admin-node-danger-action-failed", "Node shutdown-safe 실패", error));
    }

    private void openAdminNodeKickAllConfirmation(Player player, String nodeId) {
        openConfirmation(player,
            routeMessage("admin-node-kickall-confirm-title", "노드 플레이어 이동 확인"),
            routeMessage("admin-node-kickall-confirm-description", "현재 노드의 접속자를 로비로 이동합니다."),
            Material.IRON_DOOR,
            routeMessage("admin-node-kickall-confirm-name", "로비 이동 실행"),
            "admin.node.kickall.confirm",
            Map.of("nodeId", nodeId, "reason", "admin-gui"),
            routeMessage("admin-node-kickall-confirm-lore", "클릭하면 Core에 노드 플레이어 이동을 요청합니다."),
            "admin.node.open");
    }

    private void openAdminNodeShutdownConfirmation(Player player, String nodeId) {
        openConfirmation(player,
            routeMessage("admin-node-shutdown-confirm-title", "노드 안전 종료 확인"),
            routeMessage("admin-node-shutdown-confirm-description", "Drain 후 접속자를 로비로 이동하고 안전 종료를 요청합니다."),
            Material.BELL,
            routeMessage("admin-node-shutdown-confirm-name", "안전 종료 실행"),
            "admin.node.shutdown-safe.confirm",
            Map.of("nodeId", nodeId, "reason", "admin-gui"),
            routeMessage("admin-node-shutdown-confirm-lore", "클릭하면 Core에 노드 안전 종료를 요청합니다."),
            "admin.node.open");
    }

    private Void adminNodeFailure(Player player, String key, String fallback, Throwable error) {
        message(player, routeMessage(key, fallback));
        return null;
    }

    private String adminNodeActionMessage(String label, String nodeId, String body) {
        return actionResultMessage(label, nodeId, body);
    }

    private String adminNodeBodySummary(String body) {
        if (body == null || body.isBlank()) {
            return routeMessage("admin-node-empty-response", "응답 없음");
        }
        String code = text(body, "code");
        if (!code.isBlank()) {
            return "code=" + code;
        }
        String nodeId = text(body, "nodeId");
        if (!nodeId.isBlank()) {
            return "node=" + compactId(nodeId);
        }
        return body.length() > 180 ? body.substring(0, 180) + "..." : body;
    }

    private void sendCommandList(Player player, String title, List<String> commands, int page) {
        sendCommandList(player, "섬", title, commands, page);
    }

    private void sendCommandList(Player player, String label, String title, List<String> commands, int page) {
        List<String> labelledCommands = commands.stream()
            .map(command -> command.replaceFirst("^섬", label))
            .toList();
        CommandListPolicy.Page commandPage = CommandListPolicy.page(labelledCommands, page, label + " command list");
        String headerTitle = routeMessage("command-list-title", title + " ");
        String headerSuffix = routeMessage("command-list-suffix", CommandListPolicy.HEADER_SUFFIX);
        player.sendMessage(headerTitle + commandPage.page() + "/" + commandPage.pages() + " commands=" + commandPage.rangeSummary() + headerSuffix);
        for (String command : commandPage.entries()) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + command);
        }
        if (commandPage.previousCommand() != null) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.previousCommand());
        }
        if (commandPage.nextCommand() != null) {
            player.sendMessage(CommandListPolicy.ENTRY_PREFIX + commandPage.nextCommand());
        }
    }

    private int helpPage(String[] args, int index) {
        if (args.length <= index) {
            return 1;
        }
        return (int) number(args[index], 1L);
    }

    private int commandListPage(String[] args) {
        if (args.length == 0) {
            return 0;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (first.equals("command") && args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return helpPage(args, 2);
        }
        if (first.equals("help") || first.equals("도움말") || first.equals("commands") || first.equals("command") || first.equals("command-list") || first.equals("명령어") || first.equals("명령어목록")) {
            return helpPage(args, 1);
        }
        return 0;
    }

    private void createIsland(Player player, String templateId) {
        mutate("island.create", () -> coreApiClient.createIsland(player.getUniqueId(), templateId))
            .thenAccept(result -> {
                if (!result.accepted()) {
                    message(player, playerCodeMessage(result.code(), "섬 생성을 시작하지 못했습니다."));
                    return;
                }
                message(player, "섬 생성을 시작했습니다.");
            })
            .exceptionally(error -> {
                message(player, coreWriteFailureMessage(error, "섬 생성을 시작하지 못했습니다."));
                return null;
            });
    }

    private void deleteIsland(Player player) {
        currentIsland(player, "섬 안에서만 섬을 삭제할 수 있습니다.").ifPresent(islandId -> {
            mutateIdempotent("island.delete", () -> coreApiClient.deleteIsland(player.getUniqueId(), islandId))
                .thenAccept(result -> {
                    if (!result.accepted()) {
                        message(player, playerCodeMessage(result.code(), "섬을 삭제하지 못했습니다."));
                        return;
                    }
                    message(player, "섬 삭제를 요청했습니다.");
                })
                .exceptionally(error -> {
                    message(player, coreWriteFailureMessage(error, "섬을 삭제하지 못했습니다."));
                    return null;
                });
        });
    }

    private void resetIsland(Player player, String reason) {
        currentIsland(player, "섬 안에서만 섬을 리셋할 수 있습니다.").ifPresent(islandId -> {
            mutateIdempotent("island.reset", () -> coreApiClient.resetIslandResult(islandId, player.getUniqueId(), reason))
                .thenAccept(body -> message(player, actionResultMessage("섬 리셋 요청", islandId, body)))
                .exceptionally(error -> {
                    message(player, coreWriteFailureMessage(error, "섬을 리셋하지 못했습니다."));
                    return null;
                });
        });
    }

    private void setHome(Player player, String name) {
        currentIsland(player, "섬 안에서만 홈을 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.SET_HOME)) {
                message(player, routeMessage("home-set-denied", "섬 홈을 설정할 권한이 없습니다."));
                return;
            }
            mutate("island.home.set", () -> coreApiClient.setIslandHomeResult(islandId, player.getUniqueId(), name, location(player.getLocation())))
                .thenAccept(body -> message(player, actionResultMessage("섬 홈 설정 " + name, name, body)))
                .exceptionally(error -> {
                    message(player, "섬 홈을 설정하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarp(Player player, String name) {
        currentIsland(player, "섬 안에서만 워프를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                message(player, routeMessage("warp-set-denied", "섬 워프를 설정할 권한이 없습니다."));
                return;
            }
            mutate("island.warp.set", () -> coreApiClient.setIslandWarpResult(islandId, player.getUniqueId(), name, location(player.getLocation()), false))
                .thenAccept(body -> message(player, actionResultMessage("섬 워프 설정 " + name, name, body)))
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
        currentIsland(player, "섬 안에서만 홈 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandHomeMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private void openIslandWarpMenu(Player player) {
        currentIsland(player, "섬 안에서만 워프 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandWarpMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private void teleportHome(Player player, String name) {
        currentIsland(player, "섬 안에서만 홈으로 이동할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.INTERACT)) {
                message(player, routeMessage("home-teleport-denied", "섬 홈으로 이동할 권한이 없습니다."));
                return;
            }
            coreApiClient.listIslandHomes(islandId)
                .thenAccept(body -> teleport(player, point(body, name, player.getWorld().getName()), "홈을 찾을 수 없습니다.", "섬 홈으로 이동했습니다."))
                .exceptionally(error -> {
                    if (coreUnavailable(error) && teleportLocalDefaultHome(player)) {
                        return null;
                    }
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
                            message(player, routeMessage("warp-teleport-denied", "섬 워프로 이동할 권한이 없습니다."));
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
                message(player, routeMessage("warp-delete-denied", "섬 워프를 삭제할 권한이 없습니다."));
                return;
            }
            mutateIdempotent("island.warp.delete", () -> coreApiClient.deleteIslandWarpResult(islandId, player.getUniqueId(), name))
                .thenAccept(body -> message(player, actionResultMessage("섬 워프 삭제 " + name, name, body)))
                .exceptionally(error -> {
                    message(player, "섬 워프를 삭제하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setWarpPublicAccess(Player player, String name, boolean publicAccess) {
        currentIsland(player, "섬 안에서만 워프 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_WARPS)) {
                message(player, routeMessage("warp-access-denied", "섬 워프 공개 상태를 변경할 권한이 없습니다."));
                return;
            }
            mutate("island.warp.public-access.set", () -> coreApiClient.setIslandWarpPublicAccessResult(islandId, player.getUniqueId(), name, publicAccess))
                .thenAccept(body -> message(player, actionResultMessage(publicAccess ? "섬 워프 공개 " + name : "섬 워프 비공개 " + name, name, body)))
                .exceptionally(error -> {
                    message(player, "섬 워프 공개 상태를 변경하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandPublicAccess(Player player, boolean publicAccess) {
        currentIsland(player, "섬 안에서만 공개 상태를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                message(player, routeMessage("access-change-denied", "섬 공개 상태를 변경할 권한이 없습니다."));
                return;
            }
            mutate("island.public-access.set", () -> coreApiClient.setIslandPublicAccessResult(islandId, player.getUniqueId(), publicAccess))
                .thenAccept(body -> {
                    message(player, actionResultMessage(publicAccess ? "섬 공개 설정" : "섬 비공개 설정", islandId, body));
                    if (!resultRejected(body)) {
                        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> openIslandSettings(player));
                    }
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
                message(player, routeMessage("lock-change-denied", "섬 잠금 상태를 변경할 권한이 없습니다."));
                return;
            }
            mutate("island.locked.set", () -> coreApiClient.setIslandLockedResult(islandId, player.getUniqueId(), locked))
                .thenAccept(body -> {
                    message(player, actionResultMessage(locked ? "섬 잠금 설정" : "섬 잠금 해제", islandId, body));
                    if (!resultRejected(body)) {
                        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> openIslandSettings(player));
                    }
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
                UUID ownerUuid = uuid(text(body, "playerUuid"));
                if (ownerUuid != null) {
                    routeTicket(player, mutate("route.ticket.visit.owner", () -> coreApiClient.createVisitTicketForOwner(player.getUniqueId(), ownerUuid)), "해당 섬에 방문할 수 없습니다.");
                } else {
                    routeVisit(player, primaryIslandId);
                }
                return;
            }
            routeVisitName(player, target);
        }).exceptionally(error -> {
            routeVisitName(player, target);
            return null;
        });
    }

    private void routeVisitName(Player player, String islandName) {
        routeTicket(player, mutate("route.ticket.visit.name", () -> coreApiClient.createVisitTicket(player.getUniqueId(), islandName)), "해당 섬에 방문할 수 없습니다.");
    }

    private void routeVisit(Player player, UUID islandId) {
        routeTicket(player, mutate("route.ticket.visit", () -> coreApiClient.createVisitTicket(player.getUniqueId(), islandId)), "해당 섬에 방문할 수 없습니다.");
    }

    private void routeWarp(Player player, UUID islandId, String warpName) {
        routeTicket(player, mutate("route.ticket.warp", () -> coreApiClient.createWarpTicket(player.getUniqueId(), islandId, warpName)), "해당 워프로 이동할 수 없습니다.");
    }

    private void routeRandomVisit(Player player) {
        routeTicket(player, mutate("route.ticket.random-visit", () -> coreApiClient.createRandomVisitTicket(player.getUniqueId())), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    private void listPublicIslands(Player player, int limit) {
        int cappedLimit = Math.max(1, Math.min(limit, 100));
        coreApiClient.listPublicIslands(cappedLimit)
            .thenAccept(body -> message(player, publicIslandListMessage(body)))
            .exceptionally(error -> {
                message(player, "공개 섬 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private void listPublicWarps(Player player, String category, String query) {
        coreApiClient.listPublicWarps(20, category, query)
            .thenAccept(body -> message(player, publicWarpListMessage(body, category, query)))
            .exceptionally(error -> {
                message(player, "공개 워프 목록을 불러오지 못했습니다.");
                return null;
            });
    }

    private void listIslandReviews(Player player, int limit) {
        currentIsland(player, "섬 안에서만 후기를 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandReviews(islandId, Math.max(1, Math.min(limit, 100)))
                .thenAccept(body -> message(player, reviewListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 후기를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void rateIslandReview(Player player, String target, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            message(player, routeMessage("input-review-rating-invalid", "평점은 1~5 사이여야 합니다."));
            return;
        }
        UUID islandId = uuid(target);
        if (islandId == null && (target.equalsIgnoreCase("current") || target.equals("현재"))) {
            currentIsland(player, "섬 안에서만 현재 섬을 평가할 수 있습니다.").ifPresent(current -> submitIslandReview(player, current, rating, comment));
            return;
        }
        if (islandId == null) {
            message(player, routeMessage("input-island-uuid-invalid", "섬 UUID가 올바르지 않습니다."));
            return;
        }
        submitIslandReview(player, islandId, rating, comment);
    }

    private void submitIslandReview(Player player, UUID islandId, int rating, String comment) {
        mutateIdempotent("island.review.set", () -> coreApiClient.setIslandReview(islandId, player.getUniqueId(), rating, comment))
            .thenAccept(body -> {
                String code = text(body, "code");
                if (!code.isBlank()) {
                    message(player, playerCodeMessage(code, "섬 평가를 저장하지 못했습니다."));
                    return;
                }
                message(player, "섬 평가 저장 완료: " + rating + "/5");
            })
            .exceptionally(error -> {
                message(player, coreWriteFailureMessage(error, "섬 평가를 저장하지 못했습니다."));
                return null;
            });
    }

    private void routeTicket(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        ticketFuture.thenAccept(ticket -> routeTicket(player, ticket, failureMessage, 0)).exceptionally(error -> {
            clearRouteLoading(player);
            message(player, routeFailureMessage(error, failureMessage));
            return null;
        });
    }

    private String routeFailureMessage(Throwable error, String fallback) {
        if (coreUnavailable(error)) {
            return routeMessage("core-service-maintenance", CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE);
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return playerCodeMessage(coreError.code(), fallback);
            }
            if (current instanceof java.io.IOException) {
                return CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE;
            }
            current = current.getCause();
        }
        return fallback;
    }

    private String playerCodeMessage(String code, String fallback) {
        if (code == null || code.isBlank()) {
            return fallback;
        }
        String policyMessage = RouteFailureMessagePolicy.playerMessage(code, fallback);
        if (!java.util.Objects.equals(policyMessage, fallback) || !RouteFailureMessagePolicy.FALLBACK_CATEGORY.equals(RouteFailureMessagePolicy.playerSafeCategory(code))) {
            return policyMessage;
        }
        return switch (code) {
            case "OWNER_ROLE_PROTECTED" -> "섬 소유자는 소유권 양도로만 변경할 수 있습니다.";
            case "MEMBER_ROLE_UNAVAILABLE" -> "멤버 역할로 사용할 수 없는 값입니다.";
            case "VISITOR_BAN_DENIED" -> "섬 멤버는 방문자 밴으로 처리할 수 없습니다.";
            case "REVIEW_OWNER_DENIED" -> "자기 섬은 평가할 수 없습니다.";
            case "REVIEW_RATING_INVALID" -> "평점은 1~5 사이여야 합니다.";
            case "INSUFFICIENT_ITEMS" -> "섬 창고 수량이 부족합니다.";
            default -> fallback;
        };
    }

    private void routeTicket(Player player, RouteTicket ticket, String failureMessage, int attempt) {
        if (ticket.state().name().equals("READY")) {
            String target = routeTargetName(ticket);
            showRouteLoading(player, 1.0f, routeMessage(player, "route-loading-complete", target + " 로딩 완료", "target", target));
            player.sendActionBar(routeComponent(player, "route-ready", "잠시 후 " + target + "으로 이동합니다.", "target", target));
            publishAndConnect(player, ticket, failureMessage);
            return;
        }
        if (attempt >= routeWaitSeconds) {
            clearRouteLoading(player);
            message(player, failureMessage);
            return;
        }
        int progress = RoutePreparationProgressPolicy.preparingPercent(attempt);
        String target = RoutePreparationProgressPolicy.safeTargetName(routeTargetName(ticket));
        String progressValue = Integer.toString(progress);
        showRouteLoading(player, RoutePreparationProgressPolicy.preparingProgress(attempt), routeMessage(player, "route-loading-progress", RoutePreparationProgressPolicy.loadingTitle(target, attempt), "target", target, "progress", progressValue));
        player.sendActionBar(routeComponent(player, "route-preparing-progress", RoutePreparationProgressPolicy.preparingActionBar(target, attempt), "target", target, "progress", progressValue));
        CompletableFuture.runAsync(() -> coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
            if (status.isPresent()) {
                routeTicket(player, status.get(), failureMessage, attempt + 1);
            } else {
                clearRouteLoading(player);
                message(player, failureMessage);
            }
        }).exceptionally(error -> {
            clearRouteLoading(player);
            message(player, routeFailureMessage(error, failureMessage));
            return null;
        }), CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
    }

    private Component routeComponent(String key, String fallback, String... variables) {
        return Component.text(playerMessage(routeMessage(key, fallback, variables)));
    }

    private Component routeComponent(Player player, String key, String fallback, String... variables) {
        return Component.text(playerMessage(routeMessage(player, key, fallback, variables)));
    }

    private MessageRenderer messagesFor(Player player) {
        return messages == null || player == null ? messages : messages.forLocale(locales == null ? player.getLocale() : locales.locale(player));
    }

    private String routeMessage(String key, String fallback, String... variables) {
        String rendered = messages == null ? "" : messages.plain(key, variables);
        return rendered.isBlank() ? fallback : rendered;
    }

    private String routeMessage(Player player, String key, String fallback, String... variables) {
        MessageRenderer playerMessages = messagesFor(player);
        String rendered = playerMessages == null ? "" : playerMessages.plain(key, variables);
        return rendered.isBlank() ? fallback : rendered;
    }

    private void publishAndConnect(Player player, RouteTicket ticket, String failureMessage) {
        mutate("route.session.publish", () -> coreApiClient.publishRouteSession(ticket)).thenRun(() -> {
            clearRouteLoading(player);
            connectWithTicket(player, ticket, ticket.payload().getOrDefault("targetServerName", ticket.targetNode()));
        }).exceptionally(error -> {
            clearRouteLoading(player);
            clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
            message(player, routeFailureMessage(error, failureMessage));
            return null;
        });
    }

    private void showRouteLoading(Player player, float progress, String title) {
        BossBar bossBar = routeBossBars.computeIfAbsent(player.getUniqueId(), ignored -> {
            BossBar created = BossBar.bossBar(Component.text(playerMessage(title)), progress, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
            player.showBossBar(created);
            return created;
        });
        bossBar.name(Component.text(playerMessage(title)));
        bossBar.progress(Math.max(0.0f, Math.min(1.0f, progress)));
    }

    private void clearRouteLoading(Player player) {
        BossBar bossBar = routeBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearRouteLoading(event.getPlayer());
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        clearRouteLoading(event.getPlayer());
    }

    private void connectWithTicket(Player player, RouteTicket ticket, String targetServerName) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (targetServerName == null || targetServerName.isBlank()) {
                clearFailedRoute(ticket, "TARGET_SERVER_NOT_FOUND");
                message(player, routeMessage("route-command-failed", "섬으로 이동하지 못했습니다."));
                return;
            }
            if (!canUseBungeeConnect()) {
                clearFailedRoute(ticket, "BUNGEE_CONNECT_UNAVAILABLE");
                message(player, routeMessage("route-command-publish-failed", "섬 이동 경로를 준비하지 못했습니다."));
                return;
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeUTF("Connect");
                output.writeUTF(targetServerName);
                player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
                message(player, routeMessage("route-command-started", "섬으로 이동합니다."));
            } catch (IOException | RuntimeException exception) {
                clearFailedRoute(ticket, "PLUGIN_MESSAGE_FAILED");
                message(player, routeMessage("route-command-failed", "섬으로 이동하지 못했습니다."));
            }
        });
    }

    private void clearFailedRoute(RouteTicket ticket) {
        clearFailedRoute(ticket, "PLUGIN_MESSAGE_FAILED");
    }

    private void clearFailedRoute(RouteTicket ticket, String reason) {
        mutate("route.clear", () -> coreApiClient.clearRoute(ticket.playerUuid(), ticket.ticketId(), reason == null || reason.isBlank() ? "PLUGIN_MESSAGE_FAILED" : reason)).exceptionally(error -> null);
    }

    private String routeTargetName(RouteTicket ticket) {
        if (ticket == null) {
            return "섬";
        }
        return switch (PlayerRouteTicketView.from(ticket).destination()) {
            case "my-island" -> "내 섬";
            case "other-island" -> "다른 사람 섬";
            case "island-ranking" -> "섬 랭킹";
            case "island-visit" -> "방문할 섬";
            case "island-settings" -> "섬 설정";
            case "island-warps" -> "섬 워프";
            default -> "섬";
        };
    }

    private void connectPlayerToServer(Player player, String targetServerName, String successMessage, String failureMessage) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (targetServerName == null || targetServerName.isBlank()) {
                player.sendMessage(playerMessage(failureMessage));
                return;
            }
            if (!canUseBungeeConnect()) {
                player.sendMessage(playerMessage(failureMessage));
                return;
            }
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeUTF("Connect");
                output.writeUTF(targetServerName);
                player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
                player.sendMessage(playerMessage(successMessage));
            } catch (IOException | RuntimeException exception) {
                player.sendMessage(playerMessage(failureMessage));
            }
        });
    }

    private boolean canUseBungeeConnect() {
        return plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord");
    }

    private void openIslandInfoMenu(Player player) {
        currentIsland(player, "섬 안에서만 정보를 확인할 수 있습니다.").ifPresent(islandId -> IslandInfoMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
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
        currentIsland(player, "섬 안에서만 바이옴 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandBiomeMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private void setIslandBiome(Player player, String biomeKey) {
        currentIsland(player, "섬 안에서만 바이옴을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.SET_BIOME)) {
                message(player, routeMessage("biome-set-denied", "섬 바이옴을 변경할 권한이 없습니다."));
                return;
            }
            mutate("island.biome.set", () -> coreApiClient.setIslandBiomeResult(islandId, player.getUniqueId(), biomeKey))
                .thenAccept(body -> message(player, actionResultMessage("섬 바이옴 변경 " + biomeKey, biomeKey, body)))
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
            CompletableFuture<String> info = coreApiClient.islandInfo(islandId);
            CompletableFuture<String> flags = coreApiClient.listIslandFlags(islandId);
            info.thenCombine(flags, (infoBody, flagBody) -> borderSummary(infoBody, flagBody))
                .thenAccept(summary -> message(player, summary))
                .exceptionally(error -> {
                    message(player, "섬 경계를 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void handleIslandBorder(Player player, String[] args) {
        if (args.length < 2) {
            showIslandBorder(player);
            applyIslandBorder(player, false);
            return;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("apply") || mode.equals("적용")) {
            applyIslandBorder(player, true);
            return;
        }
        if (mode.equals("hide") || mode.equals("hidden") || mode.equals("숨김")) {
            setIslandBorderFlag(player, IslandFlag.BORDER_VISIBLE, "false", true);
            return;
        }
        if (mode.equals("show") || mode.equals("visible") || mode.equals("표시")) {
            String value = args.length > 2 ? flagToggleValue(args, 2) : "true";
            setIslandBorderFlag(player, IslandFlag.BORDER_VISIBLE, value, true);
            return;
        }
        if (mode.equals("color") || mode.equals("색상")) {
            if (args.length < 3) {
                message(player, "경계 색상을 입력해주세요. 예: /섬 경계 색상 blue");
                return;
            }
            setIslandBorderFlag(player, IslandFlag.BORDER_COLOR, normalizeBorderColor(args[2]), true);
            return;
        }
        if (mode.equals("warning") || mode.equals("경고")) {
            if (args.length < 3) {
                message(player, "경계 경고 거리를 입력해주세요. 예: /섬 경계 경고 8");
                return;
            }
            setIslandBorderFlag(player, IslandFlag.BORDER_WARNING_BLOCKS, Long.toString(Math.max(0L, longValue(args[2], 0L))), true);
            return;
        }
        if (mode.equals("policy") || mode.equals("정책")) {
            if (args.length < 3) {
                message(player, "경계 정책을 입력해주세요. 예: /섬 경계 정책 visible");
                return;
            }
            setIslandBorderFlag(player, IslandFlag.BORDER_POLICY, normalizeBorderPolicy(args[2]), true);
            return;
        }
        showIslandBorder(player);
    }

    private void setIslandBorderFlag(Player player, IslandFlag flag, String value, boolean applyAfterSave) {
        currentIsland(player, "섬 안에서만 경계 정책을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                message(player, routeMessage("flag-set-denied", "섬 플래그를 변경할 권한이 없습니다."));
                return;
            }
            mutate("island.flag.set", () -> coreApiClient.setIslandFlagResult(islandId, player.getUniqueId(), flag, value))
                .thenAccept(body -> {
                    message(player, actionResultMessage("섬 경계 정책 변경 " + flag.name() + "=" + value, flag.name(), body));
                    if (applyAfterSave && !resultRejected(body)) {
                        applyIslandBorder(player, true);
                    }
                })
                .exceptionally(error -> {
                    message(player, coreWriteFailureMessage(error, "섬 경계 정책을 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private void applyIslandBorder(Player player, boolean announce) {
        currentIsland(player, "섬 안에서만 경계를 적용할 수 있습니다.").ifPresent(islandId -> {
            java.util.Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
            if (region.isEmpty()) {
                message(player, "섬 경계 위치를 확인하지 못했습니다.");
                return;
            }
            CompletableFuture<String> info = coreApiClient.islandInfo(islandId);
            CompletableFuture<String> flags = coreApiClient.listIslandFlags(islandId);
            info.thenCombine(flags, (infoBody, flagBody) -> new BorderView(infoBody, flagBody, region.get()))
                .thenAccept(view -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> applyIslandBorderSync(player, view, announce)))
                .exceptionally(error -> {
                    message(player, "섬 경계 UI를 적용하지 못했습니다.");
                    return null;
                });
        });
    }

    private void applyIslandBorderSync(Player player, BorderView view, boolean announce) {
        boolean visible = borderVisible(view.flags());
        String policy = flagValue(view.flags(), IslandFlag.BORDER_POLICY, visible ? "visible" : "hidden");
        if (!visible || policy.equalsIgnoreCase("hidden")) {
            player.setWorldBorder(null);
            if (announce) {
                message(player, "섬 경계 UI를 숨겼습니다.");
            }
            return;
        }
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(view.region().originX(), view.region().originZ());
        border.setSize(Math.max(1.0D, decimal(view.info(), "border")));
        border.setWarningDistance(Math.max(0, (int) longValue(flagValue(view.flags(), IslandFlag.BORDER_WARNING_BLOCKS, "8"), 8L)));
        border.setWarningTime(5);
        player.setWorldBorder(border);
        if (announce) {
            message(player, "섬 경계 UI 적용: 색상=" + flagValue(view.flags(), IslandFlag.BORDER_COLOR, "blue") + ", 정책=" + policy + ", 크기=" + (long) decimal(view.info(), "border"));
        }
    }

    private String borderSummary(String infoBody, String flagBody) {
        return "섬 경계: 크기=" + (long) decimal(infoBody, "border")
            + ", 표시=" + (borderVisible(flagBody) ? "켜짐" : "꺼짐")
            + ", 색상=" + flagValue(flagBody, IslandFlag.BORDER_COLOR, "blue")
            + ", 정책=" + flagValue(flagBody, IslandFlag.BORDER_POLICY, "visible")
            + ", 경고거리=" + flagValue(flagBody, IslandFlag.BORDER_WARNING_BLOCKS, "8");
    }

    private boolean borderVisible(String flagBody) {
        String value = flagValue(flagBody, IslandFlag.BORDER_VISIBLE, "true");
        return !value.equalsIgnoreCase("false")
            && !value.equalsIgnoreCase("off")
            && !value.equals("0")
            && !value.equalsIgnoreCase("hide")
            && !value.equalsIgnoreCase("hidden")
            && !value.equals("숨김");
    }

    private String flagValue(String body, IslandFlag flag, String fallback) {
        String value = text(body, flag.name());
        return value.isBlank() ? fallback : value;
    }

    private String normalizeBorderColor(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "red", "빨강" -> "red";
            case "green", "초록" -> "green";
            case "aqua", "cyan", "하늘" -> "aqua";
            case "yellow", "노랑" -> "yellow";
            case "purple", "보라" -> "purple";
            default -> "blue";
        };
    }

    private String normalizeBorderPolicy(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("hidden") || normalized.equals("hide") || normalized.equals("숨김")) {
            return "hidden";
        }
        if (normalized.equals("warning") || normalized.equals("warn") || normalized.equals("경고")) {
            return "warning";
        }
        return "visible";
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
        currentIsland(player, "섬 안에서만 제한 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandLimitMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private void setNamedIslandLimit(Player player, String limitKey, String[] args) {
        if (args.length < 2) {
            message(player, routeMessage("input-limit-value-required", "제한 값을 입력해주세요."));
            return;
        }
        setIslandLimit(player, limitKey, longValue(args[1], 0L));
    }

    private void setIslandLimit(Player player, String limitKey, long value) {
        currentIsland(player, "섬 안에서만 제한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_UPGRADES)) {
                message(player, routeMessage("limit-set-denied", "섬 제한을 변경할 권한이 없습니다."));
                return;
            }
            mutate("island.limit.set", () -> coreApiClient.setIslandLimit(islandId, player.getUniqueId(), limitKey, value))
                .thenAccept(body -> {
                    if (resultRejected(body)) {
                        message(player, playerCodeMessage(text(body, "code"), "섬 제한을 변경하지 못했습니다."));
                        return;
                    }
                    message(player, "섬 제한 변경 완료: " + text(body, "limitKey") + " = " + (long) decimal(body, "value"));
                })
                .exceptionally(error -> {
                    message(player, "섬 제한을 변경하지 못했습니다.");
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
        openIslandMemberMenu(player, 0);
    }

    private void openIslandMemberMenu(Player player, int page) {
        currentIsland(player, "섬 안에서만 멤버 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandMemberMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player), page));
    }

    private void inviteIslandMember(Player player, String target) {
        currentIsland(player, "섬 안에서만 플레이어를 초대할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_MEMBERS)) {
                message(player, routeMessage("member-invite-denied", "섬 멤버를 초대할 권한이 없습니다."));
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
        mutate("island.invite.create", () -> coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid))
            .thenAccept(body -> message(player, actionResultMessage("섬 초대", text(body, "inviteId"), body)))
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
            message(player, routeMessage("input-invite-id-invalid", "올바른 초대 ID를 입력해주세요."));
            return;
        }
        mutate("island.invite.accept", () -> coreApiClient.acceptIslandInviteResult(inviteId, player.getUniqueId()))
            .thenAccept(body -> message(player, inviteActionMessage("섬 초대 수락", inviteId, body)))
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
            message(player, routeMessage("input-invite-id-invalid", "올바른 초대 ID를 입력해주세요."));
            return;
        }
        mutate("island.invite.decline", () -> coreApiClient.declineIslandInviteResult(inviteId, player.getUniqueId()))
            .thenAccept(body -> message(player, inviteActionMessage("섬 초대 거절", inviteId, body)))
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
            .handle((body, error) -> error == null ? uuid(text(body, "playerUuid")) : null)
            .thenCompose(playerUuid -> {
                if (playerUuid == null) {
                    return resolveInviteIslandName(player, target);
                }
                return coreApiClient.listPendingInvites(player.getUniqueId()).thenCompose(invites -> {
                    UUID inviteId = findInviteId(invites, playerUuid);
                    return inviteId == null ? resolveInviteIslandName(player, target) : CompletableFuture.completedFuture(inviteId);
                });
            });
    }

    private CompletableFuture<UUID> resolveInviteIslandName(Player player, String islandName) {
        return coreApiClient.islandInfoByName(islandName)
            .thenCompose(body -> coreApiClient.listPendingInvites(player.getUniqueId()).thenApply(invites -> findInviteId(invites, uuid(text(body, "islandId")))));
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
                message(player, routeMessage("member-remove-denied", "섬 멤버를 추방할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.member.remove", () -> coreApiClient.removeIslandMemberResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> message(player, actionResultMessage("섬 멤버 제거", targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, "섬 멤버를 제거하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void setIslandMemberRole(Player player, String target, IslandRole role, String successMessage) {
        setIslandMemberRole(player, target, role.name(), successMessage);
    }

    private void setIslandMemberRole(Player player, String target, String roleKey, String successMessage) {
        currentIsland(player, "섬 안에서만 멤버 역할을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("member-role-denied", "섬 멤버 역할을 변경할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutate("island.member.role.set", () -> coreApiClient.setIslandMemberResult(islandId, player.getUniqueId(), targetUuid, roleKey))
                    .thenAccept(body -> message(player, actionResultMessage(successMessage, targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, "섬 멤버 역할을 변경하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void trustIslandMemberTemporary(Player player, String target, String duration) {
        long seconds = parseDurationSeconds(duration, 3600L);
        if (seconds <= 0L) {
            message(player, "신뢰 기간을 올바르게 입력해주세요. 예: 30m, 2h, 1d");
            return;
        }
        currentIsland(player, "섬 안에서만 임시 신뢰를 설정할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("member-role-denied", "섬 멤버 역할을 변경할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutate("island.member.temp-trust", () -> coreApiClient.trustIslandMemberTemporary(islandId, player.getUniqueId(), targetUuid, seconds))
                    .thenAccept(body -> message(player, actionResultMessage("섬 임시 신뢰 설정 " + formatDuration(seconds), targetUuid, body) + " 만료=" + text(body, "expiresAt")))
                    .exceptionally(error -> {
                        message(player, "섬 임시 신뢰를 설정하지 못했습니다.");
                        return null;
                    });
            });
        });
    }

    private void transferIslandOwnership(Player player, String target) {
        currentIsland(player, "섬 안에서만 소유권을 양도할 수 있습니다.").ifPresent(islandId -> {
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.ownership.transfer", () -> coreApiClient.transferIslandOwnershipResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> message(player, actionResultMessage("섬 소유권 양도", targetUuid, body)))
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
                message(player, routeMessage("visitor-ban-denied", "섬 방문자를 밴할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.visitor.ban", () -> coreApiClient.banIslandVisitorResult(islandId, player.getUniqueId(), targetUuid, reason))
                    .thenAccept(body -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        if (resultRejected(body)) {
                            player.sendMessage(playerMessage(actionResultMessage("섬 방문자 밴", targetUuid, body)));
                            return;
                        }
                        moveVisitorToFallback(islandId, targetUuid, "섬에서 밴되어 로비로 이동합니다.", "섬에서 밴되어 로비로 이동하지 못했습니다.");
                        player.sendMessage(playerMessage(actionResultMessage("섬 방문자 밴", targetUuid, body)));
                    }))
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
                message(player, routeMessage("visitor-pardon-denied", "섬 방문자 밴을 해제할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.visitor.pardon", () -> coreApiClient.pardonIslandVisitorResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> message(player, actionResultMessage("섬 방문자 밴 해제", targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, "섬 방문자 밴을 해제하지 못했습니다.");
                        return null;
                    });
                });
        });
    }

    private void kickIslandVisitor(Player player, String target) {
        currentIsland(player, "섬 안에서만 방문자를 추방할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.KICK_VISITOR)) {
                message(player, routeMessage("visitor-kick-denied", "섬 방문자를 추방할 권한이 없습니다."));
                return;
            }
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutateIdempotent("island.visitor.kick", () -> coreApiClient.kickIslandVisitorResult(islandId, player.getUniqueId(), targetUuid))
                    .thenAccept(body -> kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        if (resultRejected(body)) {
                            player.sendMessage(playerMessage(actionResultMessage("섬 방문자 추방", targetUuid, body)));
                            return;
                        }
                        if (plugin.getServer().getPlayer(targetUuid) == null) {
                            message(player, routeMessage("visitor-kick-target-offline", "방문자 추방을 기록했습니다. 대상 플레이어는 현재 온라인이 아닙니다."));
                            return;
                        }
                        if (!moveVisitorToFallback(islandId, targetUuid, "섬에서 추방되어 로비로 이동합니다.", "섬에서 추방되어 로비로 이동하지 못했습니다.")) {
                            message(player, routeMessage("visitor-kick-target-not-on-island", "방문자 추방을 기록했습니다. 대상 플레이어는 현재 이 섬에 없습니다."));
                            return;
                        }
                        player.sendMessage(playerMessage(actionResultMessage("섬 방문자 추방", targetUuid, body)));
                    }))
                    .exceptionally(error -> {
                        message(player, "섬 방문자를 추방하지 못했습니다.");
                        return null;
                    });
            }).exceptionally(error -> {
                message(player, "대상 플레이어를 찾지 못했습니다.");
                return null;
            });
        });
    }

    private boolean moveVisitorToFallback(UUID islandId, UUID targetUuid, String successMessage, String failureMessage) {
        Player targetPlayer = plugin.getServer().getPlayer(targetUuid);
        if (targetPlayer == null) {
            return false;
        }
        UUID targetIslandId = protection.islandAt(targetPlayer.getLocation().getBlock()).orElse(null);
        if (!islandId.equals(targetIslandId)) {
            return false;
        }
        connectPlayerToServer(targetPlayer, fallbackServerName, successMessage, failureMessage);
        return true;
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
        currentIsland(player, "섬 안에서만 밴 목록을 확인할 수 있습니다.").ifPresent(islandId -> IslandBanMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private String flagToggleValue(String[] args, int index) {
        if (args.length <= index) {
            return "true";
        }
        String value = args[index].toLowerCase(Locale.ROOT);
        if (value.equals("on") || value.equals("true") || value.equals("yes") || value.equals("1") || value.equals("enable") || value.equals("enabled") || value.equals("켜기") || value.equals("허용") || value.equals("활성")) {
            return "true";
        }
        if (value.equals("off") || value.equals("false") || value.equals("no") || value.equals("0") || value.equals("disable") || value.equals("disabled") || value.equals("끄기") || value.equals("거부") || value.equals("비활성")) {
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
        currentIsland(player, "섬 안에서만 플래그 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandFlagMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private void setIslandFlag(Player player, String flagName, String value) {
        currentIsland(player, "섬 안에서만 플래그를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                message(player, routeMessage("flag-set-denied", "섬 플래그를 변경할 권한이 없습니다."));
                return;
            }
            IslandFlag flag = islandFlag(flagName);
            if (flag == null) {
                message(player, routeMessage("input-flag-invalid", "올바른 섬 플래그를 입력해주세요."));
                return;
            }
            mutate("island.flag.set", () -> coreApiClient.setIslandFlagResult(islandId, player.getUniqueId(), flag, value))
                .thenAccept(body -> message(player, actionResultMessage("섬 플래그 변경 " + flag.name() + "=" + value, flag.name(), body)))
                .exceptionally(error -> {
                    message(player, coreWriteFailureMessage(error, "섬 플래그를 변경하지 못했습니다."));
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

    private void listIslandRoles(Player player) {
        currentIsland(player, "섬 안에서만 역할을 확인할 수 있습니다.").ifPresent(islandId -> {
            coreApiClient.listIslandRoles(islandId)
                .thenAccept(body -> message(player, roleListMessage(body)))
                .exceptionally(error -> {
                    message(player, "섬 역할을 불러오지 못했습니다.");
                    return null;
                });
        });
    }

    private void openIslandPermissionMenu(Player player) {
        openIslandPermissionMenu(player, 0);
    }

    private void openIslandPermissionMenu(Player player, int page) {
        openIslandPermissionMenu(player, page, 0);
    }

    private void openIslandPermissionMenu(Player player, int page, int rolePage) {
        currentIsland(player, "섬 안에서만 권한 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandPermissionMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player), page, rolePage));
    }

    private void openIslandRoleMenu(Player player) {
        currentIsland(player, "섬 안에서만 역할 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandRoleMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private void stageIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        currentIsland(player, "섬 안에서만 권한을 변경할 수 있습니다.").ifPresent(_islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            String roleKey = roleKey(roleName);
            IslandPermission permission = islandPermission(permissionName);
            if (roleKey.isBlank() || permission == null) {
                message(player, routeMessage("input-permission-set-invalid", "올바른 역할과 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            StagedPermissionChange change = new StagedPermissionChange(roleKey, permission, allowed);
            stagedPermissionChanges.computeIfAbsent(player.getUniqueId(), _uuid -> new ConcurrentHashMap<>()).put(change.key(), change);
            message(player, routeMessage("permission-stage-success-prefix", "권한 변경을 임시 저장했습니다. 저장 버튼을 눌러 반영하세요: ")
                + roleKey + ":" + permission.name() + "=" + allowed);
        });
    }

    private void resetStagedIslandPermissions(Player player) {
        stagedPermissionChanges.remove(player.getUniqueId());
        message(player, routeMessage("permission-stage-reset", "임시 권한 변경을 취소했습니다."));
        openIslandPermissionMenu(player);
    }

    private void saveStagedIslandPermissions(Player player) {
        Map<String, StagedPermissionChange> staged = stagedPermissionChanges.getOrDefault(player.getUniqueId(), Map.of());
        if (staged.isEmpty()) {
            message(player, routeMessage("permission-stage-empty", "저장할 권한 변경이 없습니다."));
            return;
        }
        currentIsland(player, "섬 안에서만 권한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            List<CompletableFuture<String>> writes = staged.values().stream()
                .map(change -> mutate("island.permission.batch-save", () -> coreApiClient.setIslandPermissionResult(islandId, player.getUniqueId(), change.roleKey(), change.permission(), change.allowed())))
                .toList();
            GuiStateMenus.openSaving(plugin, player, messagesFor(player), routeMessage("permission-save-title", "권한 저장"));
            CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new))
                .thenAccept(_ignored -> {
                    stagedPermissionChanges.remove(player.getUniqueId());
                    kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        GuiStateMenus.openSuccess(plugin, player, messagesFor(player), routeMessage("permission-save-title", "권한 저장"), routeMessage("permission-save-success", "권한 변경을 저장했습니다."), "island.permissions.open");
                    });
                })
                .exceptionally(error -> {
                    kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
                        GuiStateMenus.openConflict(plugin, player, messagesFor(player), routeMessage("permission-save-title", "권한 저장"), coreWriteFailureMessage(error, routeMessage("permission-save-failed", "권한 변경을 저장하지 못했습니다.")), "island.permissions.save", "island.permissions.open");
                    });
                    return null;
                });
        });
    }

    private void upsertIslandRole(Player player, IslandRole role, int weight, String displayName) {
        upsertIslandRole(player, role.name(), weight, displayName);
    }

    private void upsertIslandRole(Player player, String roleKey, int weight, String displayName) {
        currentIsland(player, "섬 안에서만 역할을 편집할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("role-edit-denied", "섬 역할을 편집할 권한이 없습니다."));
                return;
            }
            mutate("island.role.upsert", () -> coreApiClient.upsertIslandRole(islandId, player.getUniqueId(), roleKey, weight, displayName.isBlank() ? roleKey : displayName))
                .thenAccept(body -> message(player, "섬 역할 저장 완료: " + text(body, "role") + " weight=" + (long) decimal(body, "weight") + " name=" + text(body, "displayName")))
                .exceptionally(error -> {
                    message(player, "섬 역할을 저장하지 못했습니다.");
                    return null;
                });
        });
    }

    private void adjustIslandRoleWeight(Player player, String roleName, String weightValue, String displayName, GuiClick click) {
        String roleKey = roleKey(roleName);
        if (!editableRoleKey(roleKey)) {
            message(player, routeMessage("input-role-invalid", "올바른 역할을 입력해주세요."));
            return;
        }
        if (click.shift()) {
            resetIslandRole(player, roleKey);
            return;
        }
        int currentWeight = (int) Math.max(0L, Math.min(100L, longValue(weightValue, 0L)));
        int updatedWeight = Math.max(0, Math.min(100, currentWeight + (click.right() ? -1 : 1)));
        upsertIslandRole(player, roleKey, updatedWeight, displayName);
    }

    private void resetIslandRole(Player player, IslandRole role) {
        resetIslandRole(player, role.name());
    }

    private void resetIslandRole(Player player, String roleKey) {
        currentIsland(player, "섬 안에서만 역할을 초기화할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("role-reset-denied", "섬 역할을 초기화할 권한이 없습니다."));
                return;
            }
            mutateIdempotent("island.role.reset", () -> coreApiClient.resetIslandRole(islandId, player.getUniqueId(), roleKey))
                .thenAccept(body -> message(player, "섬 역할 초기화 완료: " + text(body, "role")))
                .exceptionally(error -> {
                    message(player, "섬 역할을 초기화하지 못했습니다.");
                    return null;
                });
        });
    }

    private void setIslandPermission(Player player, String roleName, String permissionName, String allowedValue) {
        currentIsland(player, "섬 안에서만 권한을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            String roleKey = roleKey(roleName);
            IslandPermission permission = islandPermission(permissionName);
            if (roleKey.isBlank() || permission == null) {
                message(player, routeMessage("input-permission-set-invalid", "올바른 역할과 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            mutate("island.permission.set", () -> coreApiClient.setIslandPermissionResult(islandId, player.getUniqueId(), roleKey, permission, allowed))
                .thenAccept(body -> message(player, actionResultMessage("섬 권한 변경 " + roleKey + ":" + permission.name() + "=" + allowed, roleKey, body)))
                .exceptionally(error -> {
                    message(player, coreWriteFailureMessage(error, "섬 권한을 변경하지 못했습니다."));
                    return null;
                });
        });
    }

    private void setIslandPermissionOverride(Player player, String target, String permissionName, String allowedValue) {
        currentIsland(player, "섬 안에서만 권한 예외를 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_ROLES)) {
                message(player, routeMessage("permission-set-denied", "섬 권한을 변경할 권한이 없습니다."));
                return;
            }
            IslandPermission permission = islandPermission(permissionName);
            if (permission == null) {
                message(player, routeMessage("input-permission-set-invalid", "올바른 권한을 입력해주세요."));
                return;
            }
            boolean allowed = booleanValue(allowedValue);
            resolvePlayerUuid(target).thenAccept(targetUuid -> {
                mutate("island.permission.override.set", () -> coreApiClient.setIslandPermissionOverride(islandId, player.getUniqueId(), targetUuid, permission, allowed))
                    .thenAccept(body -> message(player, actionResultMessage("섬 권한 예외 변경 " + permission.name() + "=" + allowed, targetUuid, body)))
                    .exceptionally(error -> {
                        message(player, coreWriteFailureMessage(error, "섬 권한 예외를 변경하지 못했습니다."));
                        return null;
                    });
            });
        });
    }

    private record StagedPermissionChange(String roleKey, IslandPermission permission, boolean allowed) {
        private String key() {
            return roleKey + ":" + permission.name();
        }
    }

    private void openIslandSettings(Player player) {
        currentIsland(player, "섬 안에서만 설정 메뉴를 열 수 있습니다.").ifPresent(islandId -> IslandSettingsMenu.open(plugin, coreApiClient, player, islandId, messagesFor(player)));
    }

    private void setIslandName(Player player, String name) {
        currentIsland(player, "섬 안에서만 이름을 변경할 수 있습니다.").ifPresent(islandId -> {
            if (!allowed(player, IslandPermission.MANAGE_FLAGS)) {
                message(player, routeMessage("name-change-denied", "섬 이름을 변경할 권한이 없습니다."));
                return;
            }
            mutate("island.name.set", () -> coreApiClient.setIslandNameResult(islandId, player.getUniqueId(), name))
                .thenAccept(body -> {
                    message(player, actionResultMessage("섬 이름 변경", name, body));
                    if (!resultRejected(body)) {
                        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> openIslandSettings(player));
                    }
                })
                .exceptionally(error -> {
                    message(player, "섬 이름을 변경하지 못했습니다.");
                    return null;
                });
        });
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
            "",
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

    private String publicIslandListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "공개 섬이 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = body.indexOf("\"islands\"");
        while (index >= 0 && index < body.length() && entries.size() < 20) {
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
                String name = text(object, "name");
                long level = (long) decimal(object, "level");
                String worth = text(object, "worth");
                if (worth.isBlank()) {
                    worth = Long.toString((long) decimal(object, "worth"));
                }
                entries.add((entries.size() + 1) + ". " + (name.isBlank() ? "이름 없는 섬" : name) + " (ID=" + compactId(islandId) + ", 레벨=" + level + ", 가치=" + worth + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "공개 섬이 없습니다." : "공개 섬: " + String.join(" | ", entries);
    }

    private String publicWarpListMessage(String body, String category, String query) {
        if (body == null || body.isBlank()) {
            return "공개 워프가 없습니다.";
        }
        List<String> entries = new ArrayList<>();
        int index = body.indexOf("\"warps\"");
        while (index >= 0 && index < body.length() && entries.size() < 20) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String name = text(object, "name");
            String islandId = text(object, "islandId");
            if (!name.isBlank() && !islandId.isBlank()) {
                String warpCategory = text(object, "category");
                entries.add((entries.size() + 1) + ". " + name + " (섬=" + compactId(islandId) + ", 카테고리=" + (warpCategory.isBlank() ? "default" : warpCategory) + ")");
            }
            index = objectEnd + 1;
        }
        String suffix = (category == null || category.isBlank() ? "" : " category=" + category)
            + (query == null || query.isBlank() ? "" : " query=" + query);
        return entries.isEmpty() ? "공개 워프가 없습니다." + suffix : "공개 워프" + suffix + ": " + String.join(" | ", entries);
    }

    private String reviewListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "섬 후기가 없습니다.";
        }
        long count = (long) decimal(body, "count");
        String average = String.format(Locale.ROOT, "%.2f", decimal(body, "average"));
        List<String> entries = new ArrayList<>();
        int index = body.indexOf("\"reviews\"");
        while (index >= 0 && index < body.length() && entries.size() < 10) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String reviewerUuid = text(object, "reviewerUuid");
            long rating = (long) decimal(object, "rating");
            String comment = text(object, "comment");
            if (!reviewerUuid.isBlank()) {
                entries.add(compactId(reviewerUuid) + "=" + rating + "/5" + (comment.isBlank() ? "" : " " + comment));
            }
            index = objectEnd + 1;
        }
        if (entries.isEmpty()) {
            return "섬 후기가 없습니다.";
        }
        return "섬 후기: 평균=" + average + " 개수=" + count + " | " + String.join(" | ", entries);
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
                entries.add(key + " 값=" + (long) decimal(object, "value"));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 제한이 없습니다." : "섬 제한: " + String.join(", ", entries);
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
            String expiresAt = text(object, "expiresAt");
            if (!playerUuid.isBlank()) {
                entries.add(compactId(playerUuid) + (role.isBlank() ? "" : " 역할=" + role) + (expiresAt.isBlank() ? "" : " 만료=" + expiresAt));
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
                entries.add(compactId(inviteId) + (islandId.isBlank() ? "" : " 섬=" + compactId(islandId)) + (inviterUuid.isBlank() ? "" : " 초대한사람=" + compactId(inviterUuid)));
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
        List<String> overrides = new ArrayList<>();
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
                entries.add(role + ":" + permission + "=" + (bool(object, "allowed") ? "허용" : "거부"));
            } else {
                String playerUuid = text(object, "playerUuid");
                if (!playerUuid.isBlank() && !permission.isBlank()) {
                    overrides.add(compactId(playerUuid) + ":" + permission + "=" + (bool(object, "allowed") ? "허용" : "거부"));
                }
            }
            index = objectEnd + 1;
        }
        String base = entries.isEmpty() ? "섬 권한 규칙이 없습니다." : "섬 권한: " + String.join(", ", entries);
        return overrides.isEmpty() ? base : base + " / 예외: " + String.join(", ", overrides);
    }

    private String roleListMessage(String body) {
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
            if (!role.isBlank()) {
                entries.add(role + "(weight=" + (long) decimal(object, "weight") + ", name=" + text(object, "displayName") + ")");
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 커스텀 역할이 없습니다." : "섬 역할: " + String.join(", ", entries);
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

    private long number(String value, long fallback) {
        return longValue(value, fallback);
    }

    private int rankingLimit(String[] args, int index) {
        if (args.length <= index) {
            return 10;
        }
        return (int) number(args[index], 10L);
    }

    private long parseDurationSeconds(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (normalized.endsWith("m")) {
            multiplier = 60L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("h")) {
            multiplier = 3600L;
            normalized = normalized.substring(0, normalized.length() - 1);
        } else if (normalized.endsWith("d")) {
            multiplier = 86400L;
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        long amount = longValue(normalized, -1L);
        if (amount <= 0L) {
            return -1L;
        }
        return Math.max(60L, Math.min(amount * multiplier, 2_592_000L));
    }

    private String formatDuration(long seconds) {
        if (seconds % 86400L == 0L) {
            return (seconds / 86400L) + "d";
        }
        if (seconds % 3600L == 0L) {
            return (seconds / 3600L) + "h";
        }
        if (seconds % 60L == 0L) {
            return (seconds / 60L) + "m";
        }
        return seconds + "s";
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
            return IslandRole.valueOf(roleKey(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String roleKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private boolean editableRoleKey(String roleKey) {
        return !roleKey.isBlank()
            && roleKey.matches("[A-Z0-9_]{1,32}")
            && !roleKey.equals(IslandRole.OWNER.name())
            && !roleKey.equals(IslandRole.VISITOR.name())
            && !roleKey.equals(IslandRole.BANNED.name());
    }

    private int defaultRoleWeight(String roleKey) {
        IslandRole role = islandRole(roleKey);
        return role == null ? 100 : role.ordinal();
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
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (point == null) {
                player.sendMessage(missingMessage);
                return;
            }
            java.util.Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
            String worldName = region.map(IslandRegion::world).orElse(point.worldName());
            World world = worlds.world(worldName);
            if (world == null) {
                message(player, routeMessage("route-target-world-missing", "대상 월드를 찾을 수 없습니다."));
                return;
            }
            double targetX = region.map(value -> value.originX() + point.x()).orElse(point.x());
            double targetZ = region.map(value -> value.originZ() + point.z()).orElse(point.z());
            players.teleport(player, new Location(world, targetX, point.y(), targetZ, point.yaw(), point.pitch()));
            player.sendMessage(successMessage);
        });
    }

    private boolean teleportLocalDefaultHome(Player player) {
        java.util.Optional<IslandRegion> region = protection.regionAt(player.getLocation().getBlock());
        if (region.isEmpty()) {
            return false;
        }
        IslandRegion current = region.get();
        teleport(
            player,
            new Point(current.world(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F, false),
            routeMessage("route-target-world-missing", "대상 월드를 찾을 수 없습니다."),
            routeMessage("core-service-home-fallback", CoreApiDegradedModePolicy.HOME_FALLBACK_MESSAGE)
        );
        return true;
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

    private String inviteActionMessage(String label, UUID inviteId, String body) {
        return actionResultMessage(label, inviteId, body);
    }

    private String actionResultMessage(String label, UUID targetId, String body) {
        return actionResultMessage(label, targetId == null ? "" : targetId.toString(), body);
    }

    private String actionResultMessage(String label, String targetId, String body) {
        boolean rejected = resultRejected(body);
        String code = text(body == null ? "" : body, "code");
        StringBuilder builder = new StringBuilder(label)
            .append(rejected ? " 실패" : " 완료");
        if (targetId != null && !targetId.isBlank()) {
            builder.append(": 대상=").append(compactId(targetId));
        }
        if (!code.isBlank()) {
            builder.append(" 사유=").append(code);
        }
        return builder.toString();
    }

    private boolean resultRejected(String body) {
        return body == null || body.contains("\"error\"") || body.contains("\"accepted\":false") || body.contains("\"applied\":false");
    }

    private String compactId(String value) {
        return value != null && value.length() == 36 && value.indexOf('-') > 0 ? value.substring(0, 8) : value;
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
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(playerMessage(message)));
    }

    private String playerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 요청을 처리하지 못했습니다." : message;
        return PlayerRouteMessagePolicy.sanitize(value);
    }

    private String coreWriteFailureMessage(Throwable error, String fallback) {
        return coreUnavailable(error)
            ? routeMessage("core-service-maintenance", CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE)
            : fallback;
    }

    private boolean coreUnavailable(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof CoreApiException exception) {
            return exception.status() == 0 || exception.status() >= 500;
        }
        return current instanceof java.io.IOException
            || current instanceof java.net.ConnectException
            || current instanceof java.net.http.HttpTimeoutException
            || current instanceof java.net.http.HttpConnectTimeoutException;
    }

    private record Point(String worldName, double x, double y, double z, float yaw, float pitch, boolean publicAccess) {}
    private record BorderView(String info, String flags, IslandRegion region) {}
}
