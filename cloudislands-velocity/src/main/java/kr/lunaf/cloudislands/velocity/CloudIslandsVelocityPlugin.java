package kr.lunaf.cloudislands.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

@Plugin(id = "cloudislands", name = "CloudIslands", version = "0.1.0", authors = {"LeeSeungmin"})
public final class CloudIslandsVelocityPlugin {
    private static final List<String> ALIASES = List.of("is", "island", "섬");
    private final ProxyServer proxy;
    private final Logger logger;
    private final VelocityRoutingController routingController;

    @Inject
    public CloudIslandsVelocityPlugin(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        CoreApiClient client = new JdkCoreApiClient(URI.create(System.getProperty("cloudislands.core", "https://core-api.internal:8443")), System.getenv().getOrDefault("CI_CORE_TOKEN", ""), Duration.ofSeconds(3));
        this.routingController = new VelocityRoutingController(proxy, client, "Lobby");
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        CommandManager commands = proxy.getCommandManager();
        SimpleCommand islandCommand = invocation -> {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                return;
            }
            dispatch(player, invocation.arguments());
        };
        commands.register(commands.metaBuilder("섬").aliases("is", "island").build(), islandCommand);
        logger.info("CloudIslands Velocity router enabled with aliases {}", ALIASES);
    }

    private void dispatch(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("home") || args[0].equals("홈")) {
            player.sendActionBar(Component.text("섬을 준비하는 중입니다."));
            routingController.routeHome(player, args.length > 1 ? args[1] : "default");
            return;
        }
        if (args[0].equalsIgnoreCase("homes") || args[0].equals("홈목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listHomes(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("sethome") || args[0].equals("셋홈")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String name = args.length > 2 ? args[2] : "default";
            routingController.setHome(player, islandId, name);
            return;
        }
        if (args[0].equalsIgnoreCase("visit") || args[0].equals("방문")) {
            player.sendActionBar(Component.text("방문할 섬을 불러오는 중입니다."));
            UUID targetIslandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.routeVisit(player, targetIslandId);
            return;
        }
        if (args[0].equalsIgnoreCase("randomvisit") || args[0].equals("랜덤방문")) {
            player.sendActionBar(Component.text("방문 가능한 공개 섬을 찾는 중입니다."));
            routingController.routeRandomVisit(player);
            return;
        }
        if (args[0].equalsIgnoreCase("warp") || args[0].equals("워프")) {
            player.sendActionBar(Component.text("섬 워프로 이동하는 중입니다."));
            UUID targetIslandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > 2 ? args[2] : "default";
            routingController.routeWarp(player, targetIslandId, warpName);
            return;
        }
        if (args[0].equalsIgnoreCase("invite") || args[0].equals("초대")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.invite(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("invites") || args[0].equals("초대목록")) {
            routingController.listInvites(player);
            return;
        }
        if (args[0].equalsIgnoreCase("accept") || args[0].equals("수락")) {
            UUID inviteId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.acceptInvite(player, inviteId);
            return;
        }
        if (args[0].equalsIgnoreCase("decline") || args[0].equals("거절")) {
            UUID inviteId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.declineInvite(player, inviteId);
            return;
        }
        if (args[0].equalsIgnoreCase("members") || args[0].equals("멤버")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listMembers(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("kick") || args[0].equals("추방")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.kickMember(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("promote") || args[0].equals("승급")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.setRole(player, islandId, targetUuid, IslandRole.MODERATOR);
            return;
        }
        if (args[0].equalsIgnoreCase("demote") || args[0].equals("강등")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.setRole(player, islandId, targetUuid, IslandRole.MEMBER);
            return;
        }
        if (args[0].equalsIgnoreCase("transfer") || args[0].equals("양도")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.transferOwnership(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("trust") || args[0].equals("신뢰")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.setRole(player, islandId, targetUuid, IslandRole.TRUSTED);
            return;
        }
        if (args[0].equalsIgnoreCase("untrust") || args[0].equals("신뢰해제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.kickMember(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("ban") || args[0].equals("밴")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            String reason = args.length > 3 ? args[3] : "island ban";
            routingController.banVisitor(player, islandId, targetUuid, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("unban") || args[0].equals("밴해제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.pardonVisitor(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("banlist") || args[0].equals("밴목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listBans(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("public") || args[0].equals("공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setPublicAccess(player, islandId, true);
            return;
        }
        if (args[0].equalsIgnoreCase("private") || args[0].equals("비공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setPublicAccess(player, islandId, false);
            return;
        }
        if (args[0].equalsIgnoreCase("lock") || args[0].equals("잠금")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLocked(player, islandId, true);
            return;
        }
        if (args[0].equalsIgnoreCase("unlock") || args[0].equals("잠금해제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLocked(player, islandId, false);
            return;
        }
        if (args[0].equalsIgnoreCase("permissions") || args[0].equals("권한")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listPermissions(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("setpermission") || args[0].equals("권한설정")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            IslandRole role = args.length > 2 ? parseRole(args[2]) : IslandRole.MEMBER;
            IslandPermission permission = args.length > 3 ? parsePermission(args[3]) : IslandPermission.BUILD;
            boolean allowed = args.length > 4 && Boolean.parseBoolean(args[4]);
            routingController.setPermission(player, islandId, role, permission, allowed);
            return;
        }
        if (args[0].equalsIgnoreCase("logs") || args[0].equals("로그")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listIslandLogs(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("bank") || args[0].equals("은행")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showBank(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("deposit") || args[0].equals("입금")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String amount = args.length > 2 ? args[2] : "0";
            routingController.depositBank(player, islandId, amount);
            return;
        }
        if (args[0].equalsIgnoreCase("withdraw") || args[0].equals("출금")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String amount = args.length > 2 ? args[2] : "0";
            routingController.withdrawBank(player, islandId, amount);
            return;
        }
        if (args[0].equalsIgnoreCase("rank") || args[0].equals("ranking") || args[0].equals("랭킹")) {
            routingController.showLevelRanking(player);
            return;
        }
        if (args[0].equalsIgnoreCase("levelcalc") || args[0].equals("레벨계산")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.recalculateLevel(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("upgrade") || args[0].equals("업그레이드")) {
            routingController.listUpgradeRules(player);
            return;
        }
        if (args[0].equalsIgnoreCase("upgrades") || args[0].equals("업그레이드목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listUpgrades(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("buyupgrade") || args[0].equals("업그레이드구매")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String upgradeKey = args.length > 2 ? args[2] : "size";
            routingController.purchaseUpgrade(player, islandId, upgradeKey);
            return;
        }
        if (args[0].equalsIgnoreCase("snapshots") || args[0].equals("스냅샷목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listSnapshots(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("snapshot") || args[0].equals("스냅샷")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String reason = args.length > 2 ? args[2] : "MANUAL";
            routingController.snapshot(player, islandId, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("restore") || args[0].equals("복원")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            long snapshotNo = args.length > 2 ? parseLongOrZero(args[2]) : 0L;
            routingController.restore(player, islandId, snapshotNo);
            return;
        }
        if (args[0].equalsIgnoreCase("create") || args[0].equals("생성")) {
            String templateId = args.length > 1 ? args[1] : "default";
            player.sendActionBar(Component.text("섬 생성 요청을 접수했습니다."));
            routingController.createIsland(player, templateId);
            return;
        }
        if (args[0].equalsIgnoreCase("delete") || args[0].equals("삭제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.deleteIsland(player, islandId);
            return;
        }
        player.sendMessage(Component.text("사용법: /섬 홈, /섬 생성, /섬 삭제 <섬>, /섬 은행 <섬>, /섬 입금 <섬> <금액>, /섬 방문 <섬>, /섬 랭킹"));
    }

    private UUID parseUuidOrNil(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private IslandRole parseRole(String value) {
        try {
            return IslandRole.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IslandRole.MEMBER;
        }
    }

    private IslandPermission parsePermission(String value) {
        try {
            return IslandPermission.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return IslandPermission.BUILD;
        }
    }
}
