package kr.lunaf.cloudislands.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
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
        commands.register(commands.metaBuilder("ciadmin").aliases("섬관리").build(), invocation -> {
            if (!(invocation.source() instanceof Player player)) {
                invocation.source().sendMessage(Component.text("플레이어만 사용할 수 있습니다."));
                return;
            }
            dispatchAdmin(player, invocation.arguments());
        });
        logger.info("CloudIslands Velocity router enabled with aliases {}", ALIASES);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        String channel = event.getIdentifier().getId();
        if (channel.equals("cloudislands") || channel.startsWith("cloudislands:")) {
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }

    private void dispatchAdmin(Player player, String[] args) {
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("info")) {
            routingController.adminIslandInfo(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("where")) {
            routingController.adminIslandWhere(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("tp")) {
            player.sendActionBar(Component.text("섬으로 이동하는 중입니다."));
            routingController.adminTeleportIsland(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("save")) {
            routingController.snapshot(player, parseUuidOrNil(args[2]), "ADMIN_SAVE");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("snapshot")) {
            routingController.snapshot(player, parseUuidOrNil(args[2]), args.length > 3 ? joinArgs(args, 3) : "MANUAL");
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("rollback")) {
            routingController.restore(player, parseUuidOrNil(args[2]), parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("restore")) {
            routingController.restore(player, parseUuidOrNil(args[2]), parseLongOrZero(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("delete")) {
            routingController.adminDeleteIsland(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("repair")) {
            routingController.repairIsland(player, parseUuidOrNil(args[2]), args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("activate")) {
            routingController.activateIsland(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("deactivate")) {
            routingController.deactivateIsland(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("migrate")) {
            routingController.migrateIsland(player, parseUuidOrNil(args[2]), args[3]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("island") && args[1].equalsIgnoreCase("quarantine")) {
            routingController.quarantineIsland(player, parseUuidOrNil(args[2]), args.length > 3 ? joinArgs(args, 3) : "admin");
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("debug")) {
            routingController.debugRoutes(player, args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("ticket")) {
            routingController.routeTicket(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("route") && args[1].equalsIgnoreCase("clear")) {
            UUID playerUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            UUID ticketId = args.length > 3 ? parseUuidOrNil(args[3]) : new UUID(0L, 0L);
            routingController.clearRoute(player, playerUuid, ticketId);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("cache") && args[1].equalsIgnoreCase("clear")) {
            routingController.clearCache(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            routingController.reload(player);
            return;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("migrate-superiorskyblock2")) {
            String action = args.length > 1 ? args[1] : "scan";
            String path = args.length > 2 ? joinArgs(args, 2) : "";
            routingController.migrateSuperiorSkyblock2(player, action, path);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("info")) {
            routingController.playerInfo(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("setisland")) {
            routingController.setPlayerIsland(player, parseUuidOrNil(args[2]), parseUuidOrNil(args[3]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("player") && args[1].equalsIgnoreCase("clearisland")) {
            routingController.clearPlayerIsland(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("list")) {
            routingController.listTemplates(player);
            return;
        }
        if (args.length >= 4 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("upsert")) {
            routingController.upsertTemplate(player, args[2], args[3], args.length > 4 ? parseToggle(args, 4, true) : true, args.length > 5 ? args[5] : "");
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("enable")) {
            routingController.enableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 3 && (args[0].equalsIgnoreCase("template") || args[0].equalsIgnoreCase("templates")) && args[1].equalsIgnoreCase("disable")) {
            routingController.disableTemplate(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("list")) {
            routingController.listNodes(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("info")) {
            routingController.nodeInfo(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("drain")) {
            routingController.drainNode(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("undrain")) {
            routingController.undrainNode(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("sweep")) {
            routingController.sweepNode(player, args.length > 2 ? args[2] : "");
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("kickall")) {
            routingController.kickAllNode(player, args[2]);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("node") && args[1].equalsIgnoreCase("shutdown-safe")) {
            routingController.shutdownSafeNode(player, args[2]);
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("list")) {
            routingController.listJobs(player);
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("retry")) {
            routingController.retryJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("cancel")) {
            routingController.cancelJob(player, parseUuidOrNil(args[2]));
            return;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("jobs") && args[1].equalsIgnoreCase("recover")) {
            routingController.recoverJobs(player, args.length > 2 ? args[2] : "recovery", args.length > 3 ? parseLongOrZero(args[3]) : 60000L, args.length > 4 ? (int) parseLongOrZero(args[4]) : 16);
            return;
        }
        player.sendMessage(Component.text("사용법: /ciadmin island info <섬|플레이어>, /ciadmin island where <섬>, /ciadmin template list, /ciadmin template upsert <id> <name> [enabled] [minNodeVersion], /ciadmin node list"));
    }

    private void dispatch(Player player, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("home") || args[0].equals("홈")) {
            player.sendActionBar(Component.text("섬을 준비하는 중입니다."));
            routingController.routeHome(player, args.length > 1 ? args[1] : "default");
            return;
        }
        if (args[0].equalsIgnoreCase("info") || args[0].equals("정보")) {
            routingController.showMyIsland(player);
            return;
        }
        if (args[0].equalsIgnoreCase("settings") || args[0].equals("설정")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandSettings(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("level") || args[0].equals("레벨")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandLevel(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("worth") || args[0].equals("value") || args[0].equals("가치")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandWorth(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("size") || args[0].equals("크기")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandSize(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("border") || args[0].equals("경계")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.showIslandBorder(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("biome") || args[0].equals("바이옴")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 2) {
                routingController.setBiome(player, islandId, args[2]);
            } else {
                routingController.showBiome(player, islandId);
            }
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
            if (args.length < 2) {
                player.sendMessage(Component.text("방문할 섬 또는 플레이어를 입력해주세요."));
                return;
            }
            UUID targetIslandId = parseUuidOrNil(args[1]);
            if (!targetIslandId.equals(new UUID(0L, 0L))) {
                routingController.routeVisit(player, targetIslandId);
                return;
            }
            proxy.getPlayer(args[1])
                .map(Player::getUniqueId)
                .ifPresentOrElse(ownerUuid -> routingController.routeVisitOwner(player, ownerUuid), () -> routingController.routeVisitNamedTarget(player, args[1]));
            return;
        }
        if (args[0].equalsIgnoreCase("randomvisit") || args[0].equals("랜덤방문")) {
            player.sendActionBar(Component.text("방문 가능한 공개 섬을 찾는 중입니다."));
            routingController.routeRandomVisit(player);
            return;
        }
        if (args[0].equalsIgnoreCase("warps") || args[0].equals("워프목록")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listWarps(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("setwarp") || args[0].equals("워프설정")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > 2 ? args[2] : "default";
            routingController.setWarp(player, islandId, warpName, false);
            return;
        }
        if (args[0].equalsIgnoreCase("deletewarp") || args[0].equals("워프삭제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > 2 ? args[2] : "default";
            routingController.deleteWarp(player, islandId, warpName);
            return;
        }
        if (args[0].equalsIgnoreCase("publicwarp") || args[0].equals("워프공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > 2 ? args[2] : "default";
            routingController.setWarpPublicAccess(player, islandId, warpName, true);
            return;
        }
        if (args[0].equalsIgnoreCase("privatewarp") || args[0].equals("워프비공개")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String warpName = args.length > 2 ? args[2] : "default";
            routingController.setWarpPublicAccess(player, islandId, warpName, false);
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
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
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
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.kickMember(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("promote") || args[0].equals("승급")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.setRole(player, islandId, targetUuid, IslandRole.MODERATOR);
            return;
        }
        if (args[0].equalsIgnoreCase("demote") || args[0].equals("강등")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.setRole(player, islandId, targetUuid, IslandRole.MEMBER);
            return;
        }
        if (args[0].equalsIgnoreCase("transfer") || args[0].equals("양도")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.transferOwnership(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("trust") || args[0].equals("신뢰")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.setRole(player, islandId, targetUuid, IslandRole.TRUSTED);
            return;
        }
        if (args[0].equalsIgnoreCase("untrust") || args[0].equals("신뢰해제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.kickMember(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("ban") || args[0].equals("밴")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            String reason = args.length > 3 ? args[3] : "island ban";
            routingController.banVisitor(player, islandId, targetUuid, reason);
            return;
        }
        if (args[0].equalsIgnoreCase("unban") || args[0].equals("밴해제")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parsePlayerUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.pardonVisitor(player, islandId, targetUuid);
            return;
        }
        if (args[0].equalsIgnoreCase("kickvisitor") || args[0].equals("방문자추방")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            UUID targetUuid = args.length > 2 ? parseUuidOrNil(args[2]) : new UUID(0L, 0L);
            routingController.kickVisitor(player, islandId, targetUuid);
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
        if (args[0].equalsIgnoreCase("fly") || args[0].equals("비행")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            boolean enabled = parseToggle(args, 2, true);
            routingController.setFlyFlag(player, islandId, enabled);
            return;
        }
        if (args[0].equalsIgnoreCase("keepinventory") || args[0].equalsIgnoreCase("keepinv") || args[0].equals("인벤보존")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.KEEP_INVENTORY, parseToggle(args, 2, true), "인벤토리 보존");
            return;
        }
        if (args[0].equalsIgnoreCase("pvp") || args[0].equals("피빕")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.PVP, parseToggle(args, 2, true), "PVP");
            return;
        }
        if (args[0].equalsIgnoreCase("publicwarps") || args[0].equals("공개워프")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setBooleanFlag(player, islandId, kr.lunaf.cloudislands.api.model.IslandFlag.PUBLIC_WARPS, parseToggle(args, 2, true), "공개 워프");
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
        if (args[0].equalsIgnoreCase("worthrank") || args[0].equalsIgnoreCase("valuerank") || args[0].equals("가치랭킹") || (args.length > 1 && (args[0].equalsIgnoreCase("rank") || args[0].equalsIgnoreCase("ranking") || args[0].equals("랭킹")) && (args[1].equalsIgnoreCase("worth") || args[1].equalsIgnoreCase("value") || args[1].equals("가치")))) {
            routingController.showWorthRanking(player);
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
        if (args[0].equalsIgnoreCase("mission") || args[0].equals("missions") || args[0].equals("미션")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 2) {
                routingController.completeMission(player, islandId, args[2]);
            } else {
                routingController.listMissions(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("challenge") || args[0].equals("challenges") || args[0].equals("챌린지")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.listChallenges(player, islandId);
            return;
        }
        if (args[0].equalsIgnoreCase("chat") || args[0].equals("채팅")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.sendIslandChat(player, islandId, "ISLAND", joinArgs(args, 2));
            return;
        }
        if (args[0].equalsIgnoreCase("teamchat") || args[0].equals("팀채팅")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.sendIslandChat(player, islandId, "TEAM", joinArgs(args, 2));
            return;
        }
        if (args[0].equalsIgnoreCase("limits") || args[0].equals("제한")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            if (args.length > 3) {
                routingController.setLimit(player, islandId, args[2], parseLongOrZero(args[3]));
            } else {
                routingController.listLimits(player, islandId);
            }
            return;
        }
        if (args[0].equalsIgnoreCase("hoppers") || args[0].equals("호퍼")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "HOPPER", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
            return;
        }
        if (args[0].equalsIgnoreCase("spawners") || args[0].equals("스포너")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "SPAWNER", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
            return;
        }
        if (args[0].equalsIgnoreCase("entities") || args[0].equals("엔티티")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "ENTITY", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
            return;
        }
        if (args[0].equalsIgnoreCase("redstone") || args[0].equals("레드스톤")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.setLimit(player, islandId, "REDSTONE", args.length > 2 ? parseLongOrZero(args[2]) : 0L);
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
        if (args[0].equalsIgnoreCase("reset") || args[0].equals("리셋")) {
            UUID islandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            String reason = args.length > 2 ? joinArgs(args, 2) : "PLAYER_RESET";
            routingController.resetIsland(player, islandId, reason);
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

    private UUID parsePlayerUuidOrNil(String value) {
        return proxy.getPlayer(value).map(Player::getUniqueId).orElseGet(() -> parseUuidOrNil(value));
    }

    private long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private boolean parseToggle(String[] args, int index, boolean fallback) {
        if (args.length <= index) {
            return fallback;
        }
        String value = args[index];
        return value.equalsIgnoreCase("on")
            || value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("yes")
            || value.equals("켜기");
    }

    private String joinArgs(String[] args, int start) {
        if (args.length <= start) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (index > start) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
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
