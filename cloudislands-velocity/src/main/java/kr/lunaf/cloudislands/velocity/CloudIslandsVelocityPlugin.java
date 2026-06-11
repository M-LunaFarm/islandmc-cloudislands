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
            routingController.routeHome(player);
            return;
        }
        if (args[0].equalsIgnoreCase("visit") || args[0].equals("방문")) {
            player.sendActionBar(Component.text("방문할 섬을 불러오는 중입니다."));
            UUID targetIslandId = args.length > 1 ? parseUuidOrNil(args[1]) : new UUID(0L, 0L);
            routingController.routeVisit(player, targetIslandId);
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
        if (args[0].equalsIgnoreCase("create") || args[0].equals("생성")) {
            String templateId = args.length > 1 ? args[1] : "default";
            player.sendActionBar(Component.text("섬 생성 요청을 접수했습니다."));
            routingController.createIsland(player, templateId);
            return;
        }
        player.sendMessage(Component.text("사용법: /섬 홈, /섬 생성, /섬 방문 <섬>, /섬 워프 <섬> <이름>, /섬 초대 <섬> <플레이어>, /섬 수락 <초대>"));
    }

    private UUID parseUuidOrNil(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return new UUID(0L, 0L);
        }
    }
}
