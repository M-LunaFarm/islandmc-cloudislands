package kr.lunaf.cloudislands.velocity;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.boolValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.countObjects;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.doubleValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.longValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.objectValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.parseLong;
import static kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver.parseUuid;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.common.feature.PlayerRouteTicketView;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreApiException;
import kr.lunaf.cloudislands.velocity.event.CoreEventCodec;
import kr.lunaf.cloudislands.velocity.event.CoreEventEnvelope;
import kr.lunaf.cloudislands.velocity.event.CoreEventJsonCodec;
import kr.lunaf.cloudislands.velocity.event.CoreEventPoller;
import kr.lunaf.cloudislands.velocity.message.VelocityCoreStatusMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityEventMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityIslandMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityMigrationMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityRouteMessageFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityRoutePrivacyFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.metrics.VelocityRoutingMetrics;
import kr.lunaf.cloudislands.velocity.platform.VelocityServerGateway;
import kr.lunaf.cloudislands.velocity.routing.PendingRouteService;
import kr.lunaf.cloudislands.velocity.routing.RouteFallbackService;
import kr.lunaf.cloudislands.velocity.routing.RouteProgressPresenter;
import kr.lunaf.cloudislands.velocity.routing.RouteRequestGuard;
import kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public final class VelocityRoutingController {
    private static final int EVENT_BATCH_SIZE = 512;
    private static final long PLAYER_ROUTE_COOLDOWN_MILLIS = 1_500L;

    private final ProxyServer proxy;
    private final CoreApiClient coreApiClient;
    private final String fallbackServer;
    private final int routeWaitSeconds;
    private final String islandPool;
    private final int routeTicketTtlSeconds;
    private final boolean hideNodeNames;
    private final boolean useActionBar;
    private final boolean useBossBarLoading;
    private final VelocityMessages messages;
    private final VelocityRoutePrivacyFormatter routePrivacy;
    private final VelocityCoreStatusMessageFormatter coreStatusMessages = new VelocityCoreStatusMessageFormatter();
    private final VelocityMigrationMessageFormatter migrationMessages = new VelocityMigrationMessageFormatter();
    private final VelocityEventMessageFormatter eventMessages;
    private final VelocityIslandMessageFormatter islandMessages;
    private final VelocityRouteMessageFormatter routeMessages;
    private final CoreEventCodec eventCodec;
    private final CoreEventPoller eventPoller;
    private final VelocityRoutingMetrics metrics = new VelocityRoutingMetrics();
    private final VelocityServerGateway servers;
    private final RouteFallbackService fallbackService;
    private final RouteProgressPresenter progressPresenter;
    private final RouteRequestGuard routeRequestGuard;
    private final VelocityTargetResolver targetResolver;
    private final PendingRouteService pendingRoutes;
    private ScheduledTask eventPollTask;

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer) {
        this(proxy, coreApiClient, fallbackServer, 20);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, true, true);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, useActionBar, useBossBarLoading, true);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading, boolean hideNodeNames) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, useActionBar, useBossBarLoading, hideNodeNames, "island", 30);
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading, boolean hideNodeNames, String islandPool, int routeTicketTtlSeconds) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, useActionBar, useBossBarLoading, hideNodeNames, islandPool, routeTicketTtlSeconds, VelocityMessages.defaults());
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading, boolean hideNodeNames, String islandPool, int routeTicketTtlSeconds, VelocityMessages messages) {
        this(proxy, coreApiClient, fallbackServer, routeWaitSeconds, useActionBar, useBossBarLoading, hideNodeNames, islandPool, routeTicketTtlSeconds, messages, new CoreEventJsonCodec());
    }

    public VelocityRoutingController(ProxyServer proxy, CoreApiClient coreApiClient, String fallbackServer, int routeWaitSeconds, boolean useActionBar, boolean useBossBarLoading, boolean hideNodeNames, String islandPool, int routeTicketTtlSeconds, VelocityMessages messages, CoreEventCodec eventCodec) {
        this.proxy = proxy;
        this.coreApiClient = coreApiClient;
        this.fallbackServer = fallbackServer;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.routeTicketTtlSeconds = Math.max(1, routeTicketTtlSeconds);
        this.hideNodeNames = hideNodeNames;
        this.useActionBar = useActionBar;
        this.useBossBarLoading = useBossBarLoading;
        this.messages = messages == null ? VelocityMessages.defaults() : messages;
        this.routePrivacy = new VelocityRoutePrivacyFormatter(hideNodeNames);
        this.eventMessages = new VelocityEventMessageFormatter(routePrivacy);
        this.islandMessages = new VelocityIslandMessageFormatter(routePrivacy);
        this.routeMessages = new VelocityRouteMessageFormatter(routePrivacy);
        this.eventCodec = eventCodec == null ? new CoreEventJsonCodec() : eventCodec;
        this.eventPoller = new CoreEventPoller(coreApiClient, this.eventCodec, this::handleCoreEvent, EVENT_BATCH_SIZE);
        this.servers = new VelocityServerGateway(proxy, this.islandPool, hideNodeNames);
        this.fallbackService = new RouteFallbackService(proxy, fallbackServer, metrics, this::playerComponent);
        this.progressPresenter = new RouteProgressPresenter(useActionBar, useBossBarLoading, this::playerComponent);
        this.routeRequestGuard = new RouteRequestGuard(PLAYER_ROUTE_COOLDOWN_MILLIS);
        this.targetResolver = new VelocityTargetResolver(coreApiClient, name -> proxy.getPlayer(name).map(Player::getUniqueId));
        this.pendingRoutes = new PendingRouteService(coreApiClient, fallbackService, metrics, this::playerComponent);
    }

    public void createIsland(Player player, String templateId) {
        if (!allowRouteRequest(player)) {
            return;
        }
        coreApiClient.createIsland(player.getUniqueId(), templateId).thenAccept(result -> {
            if (result == null || !result.accepted()) {
                String code = result == null ? "FAILED" : result.code();
                player.sendMessage(Component.text(messageForCreateFailure(code)));
                return;
            }
            progressPresenter.actionBar(player, messages.text("island-create-starting"));
            if (result.ticket() != null) {
                route(player, result.ticket(), "섬으로 이동하지 못했습니다.");
            }
        }).exceptionally(error -> {
            player.sendMessage(messages.component("island-service-maintenance"));
            return null;
        });
    }

    public void deleteIsland(Player player, UUID islandId) {
        coreApiClient.deleteIsland(player.getUniqueId(), islandId).thenAccept(result -> {
            if (result != null && result.accepted()) {
                player.sendMessage(Component.text("섬을 삭제했습니다."));
                return;
            }
            player.sendMessage(Component.text("섬을 삭제할 수 없습니다."));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬 삭제를 처리하지 못했습니다."));
            return null;
        });
    }

    public String statusSummary() {
        return "CloudIslands Velocity router online, fallback=" + servers.displayServerName(fallbackServer)
            + ", fallbackAvailable=" + fallbackServerAvailable()
            + ", routingPolicy=" + routingPolicyName()
            + ", islandPool=" + islandPool
            + ", islandPoolServers=" + servers.islandPoolServerCount()
            + ", islandPoolServerNames=" + servers.islandPoolServerNames()
            + ", routeWaitSeconds=" + routeWaitSeconds
            + ", routeTicketTtlSeconds=" + routeTicketTtlSeconds
            + ", onlinePlayers=" + proxy.getPlayerCount()
            + ", actionbar=" + useActionBar
            + ", bossbarLoading=" + useBossBarLoading
            + ", hideNodeNames=" + hideNodeNames
            + ", topologyPrivacyPolicy=" + CloudIslandsApiContract.TOPOLOGY_PRIVACY_POLICY
            + ", topologyPrivacyActive=" + hideNodeNames
            + metrics.statusSummary();
    }

    public String routingMetricsText() {
        return ""
            + "cloudislands_velocity_routing_policy{policy=\"" + routingPolicyName() + "\"} 1\n"
            + "cloudislands_velocity_route_wait_seconds " + routeWaitSeconds + "\n"
            + "cloudislands_velocity_route_ticket_ttl_seconds " + routeTicketTtlSeconds + "\n"
            + "cloudislands_velocity_hide_node_names " + (hideNodeNames ? 1 : 0) + "\n"
            + "cloudislands_velocity_topology_privacy_active " + (hideNodeNames ? 1 : 0) + "\n"
            + "cloudislands_velocity_actionbar_enabled " + (useActionBar ? 1 : 0) + "\n"
            + "cloudislands_velocity_bossbar_loading_enabled " + (useBossBarLoading ? 1 : 0) + "\n"
            + "cloudislands_velocity_island_pool_servers " + servers.islandPoolServerCount() + "\n"
            + metrics.prometheusText()
            + "cloudislands_velocity_fallback_server_available " + (fallbackServerAvailable() ? 1 : 0) + "\n"
            ;
    }

    private String routingPolicyName() {
        return "core-ticket-wait-fallback";
    }

    private boolean fallbackServerAvailable() {
        return fallbackService.fallbackAvailable();
    }

    public void resetIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.resetIsland(islandId, player.getUniqueId(), reason).thenApply(body -> islandMessages.actionResult("Island reset", islandId.toString(), body)), "섬 리셋을 요청하지 못했습니다.");
    }

    public void showMyIsland(Player player) {
        sendBodyResult(player, coreApiClient.islandInfoByOwner(player.getUniqueId()).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void showIslandSettings(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(islandMessages::islandInfo), "섬 설정을 불러오지 못했습니다.");
    }

    public void showIslandLevel(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 레벨", "level", body)), "섬 레벨을 불러오지 못했습니다.");
    }

    public void showIslandWorth(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 가치", "worth", body)), "섬 가치를 불러오지 못했습니다.");
    }

    public void showIslandSize(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 크기", "size", body)), "섬 크기를 불러오지 못했습니다.");
    }

    public void showIslandBorder(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandInfo(islandId).thenApply(body -> islandMessages.islandStat("섬 경계", "border", body)), "섬 경계를 불러오지 못했습니다.");
    }

    public void showBiome(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.islandBiome(islandId).thenApply(islandMessages::biomeInfo), "섬 바이옴을 불러오지 못했습니다.");
    }

    public void setBiome(Player player, UUID islandId, String biomeKey) {
        sendActionResult(player, coreApiClient.setIslandBiome(islandId, player.getUniqueId(), biomeKey), "섬 바이옴을 변경했습니다.", "섬 바이옴을 변경하지 못했습니다.");
    }

    public void routeHome(Player player) {
        routeHome(player, "default");
    }

    public void routeHome(Player player, String homeName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createHomeTicket(player.getUniqueId(), homeName), "현재 섬 서비스 일부 기능이 점검 중입니다.");
    }

    public void routeVisit(Player player, UUID targetIslandId) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicket(player.getUniqueId(), targetIslandId), "현재 섬 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.");
    }

    public void routeVisitOwner(Player player, UUID ownerUuid) {
        if (ownerUuid.equals(new UUID(0L, 0L))) {
            player.sendMessage(Component.text("방문할 플레이어를 찾을 수 없습니다."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicketForOwner(player.getUniqueId(), ownerUuid), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitName(Player player, String islandName) {
        if (islandName == null || islandName.isBlank()) {
            player.sendMessage(Component.text("방문할 섬 이름을 입력해주세요."));
            return;
        }
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createVisitTicket(player.getUniqueId(), islandName), "해당 섬에 방문할 수 없습니다.");
    }

    public void routeVisitNamedTarget(Player player, String targetName) {
        if (targetName == null || targetName.isBlank()) {
            player.sendMessage(Component.text("방문할 대상 이름을 입력해주세요."));
            return;
        }
        coreApiClient.playerInfoByName(targetName).thenAccept(body -> {
            UUID primaryIslandId = parseUuid(jsonValue(body, "primaryIslandId"));
            if (primaryIslandId.equals(new UUID(0L, 0L))) {
                routeVisitName(player, targetName);
                return;
            }
            UUID ownerUuid = parseUuid(jsonValue(body, "playerUuid"));
            if (ownerUuid.equals(new UUID(0L, 0L))) {
                routeVisit(player, primaryIslandId);
                return;
            }
            routeVisitOwner(player, ownerUuid);
        }).exceptionally(error -> {
            routeVisitName(player, targetName);
            return null;
        });
    }

    public void recordPlayerProfile(Player player) {
        coreApiClient.touchPlayerProfile(player.getUniqueId(), player.getUsername())
            .exceptionally(error -> null);
    }

    public void routePendingSession(Player player) {
        pendingRoutes.routePendingSession(player);
    }

    public void clearPlayerState(UUID playerUuid) {
        routeRequestGuard.clear(playerUuid);
    }

    public void listMyIslands(Player player) {
        coreApiClient.listPlayerIslands(player.getUniqueId())
            .thenAccept(body -> player.sendMessage(Component.text(islandMessages.playerIslands(body))))
            .exceptionally(error -> {
                player.sendMessage(Component.text("내 섬 목록을 불러오지 못했습니다."));
                return null;
            });
    }

    public void routeRandomVisit(Player player) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createRandomVisitTicket(player.getUniqueId()), "방문 가능한 공개 섬을 찾지 못했습니다.");
    }

    public void listPublicIslands(Player player, int limit) {
        sendBodyResult(player, coreApiClient.listPublicIslands(Math.max(1, Math.min(limit, 100))).thenApply(islandMessages::publicIslands), "공개 섬 목록을 불러오지 못했습니다.");
    }

    public void routeWarp(Player player, UUID targetIslandId, String warpName) {
        if (!allowRouteRequest(player)) {
            return;
        }
        routeFuture(player, coreApiClient.createWarpTicket(player.getUniqueId(), targetIslandId, warpName), "해당 워프로 이동할 수 없습니다.");
    }

    public void listWarps(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandWarps(islandId).thenApply(body -> islandMessages.warpList("섬 워프", body)), "섬 워프를 불러오지 못했습니다.");
    }

    public void listPublicWarps(Player player) {
        sendBodyResult(player, coreApiClient.listPublicWarps(27).thenApply(body -> islandMessages.warpList("공개 워프", body)), "공개 워프를 불러오지 못했습니다.");
    }

    public void setWarp(Player player, UUID islandId, String name, boolean publicAccess) {
        IslandLocation defaultLocation = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandWarp(islandId, player.getUniqueId(), name, defaultLocation, publicAccess), "섬 워프를 설정했습니다.", "섬 워프를 설정하지 못했습니다.");
    }

    public void deleteWarp(Player player, UUID islandId, String name) {
        sendActionResult(player, coreApiClient.deleteIslandWarp(islandId, player.getUniqueId(), name), "섬 워프를 삭제했습니다.", "섬 워프를 삭제하지 못했습니다.");
    }

    public void setWarpPublicAccess(Player player, UUID islandId, String name, boolean publicAccess) {
        sendActionResult(player, coreApiClient.setIslandWarpPublicAccess(islandId, player.getUniqueId(), name, publicAccess), publicAccess ? "섬 워프를 공개했습니다." : "섬 워프를 비공개로 변경했습니다.", "섬 워프 공개 상태를 변경하지 못했습니다.");
    }

    public void invite(Player player, UUID islandId, UUID targetUuid) {
        sendBodyResult(player, coreApiClient.createIslandInvite(islandId, player.getUniqueId(), targetUuid).thenApply(islandMessages::inviteCreate), "초대를 생성하지 못했습니다.");
    }

    public void inviteTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("초대할 플레이어를 찾지 못했습니다."));
                return;
            }
            invite(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("초대할 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listInvites(Player player) {
        coreApiClient.listPendingInvites(player.getUniqueId()).thenAccept(body -> player.sendMessage(Component.text(islandMessages.invites(body)))).exceptionally(error -> {
            player.sendMessage(Component.text("초대 목록을 불러오지 못했습니다."));
            return null;
        });
    }

    public void acceptInvite(Player player, UUID inviteId) {
        sendInviteActionResult(player, coreApiClient.acceptIslandInviteResult(inviteId, player.getUniqueId()), "섬 초대를 수락했습니다.", "섬 초대를 수락하지 못했습니다.");
    }

    public void acceptInviteTarget(Player player, String target) {
        targetResolver.resolveInviteTarget(player.getUniqueId(), target).thenAccept(inviteId -> {
            if (inviteId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
                return;
            }
            acceptInvite(player, inviteId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
            return null;
        });
    }

    public void declineInvite(Player player, UUID inviteId) {
        sendInviteActionResult(player, coreApiClient.declineIslandInviteResult(inviteId, player.getUniqueId()), "섬 초대를 거절했습니다.", "섬 초대를 거절하지 못했습니다.");
    }

    public void declineInviteTarget(Player player, String target) {
        targetResolver.resolveInviteTarget(player.getUniqueId(), target).thenAccept(inviteId -> {
            if (inviteId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
                return;
            }
            declineInvite(player, inviteId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 초대를 찾지 못했습니다."));
            return null;
        });
    }

    public void listMembers(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandMembers(islandId).thenApply(islandMessages::memberList), "멤버 목록을 불러오지 못했습니다.");
    }

    public void setRole(Player player, UUID islandId, UUID targetUuid, IslandRole role) {
        sendActionResult(player, coreApiClient.setIslandMember(islandId, player.getUniqueId(), targetUuid, role), "섬 멤버 역할을 변경했습니다.", "섬 멤버 역할을 변경하지 못했습니다.");
    }

    public void setRoleTarget(Player player, UUID islandId, String target, IslandRole role) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setRole(player, islandId, targetUuid, role);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void transferOwnership(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.transferIslandOwnership(islandId, player.getUniqueId(), targetUuid), "섬 소유권을 양도했습니다.", "섬 소유권을 양도하지 못했습니다.");
    }

    public void transferOwnershipTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            transferOwnership(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void kickMember(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.removeIslandMember(islandId, player.getUniqueId(), targetUuid), "섬 멤버를 추방했습니다.", "섬 멤버를 추방하지 못했습니다.");
    }

    public void kickMemberTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            kickMember(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void banVisitor(Player player, UUID islandId, UUID targetUuid, String reason) {
        sendActionResult(player, coreApiClient.banIslandVisitor(islandId, player.getUniqueId(), targetUuid, reason), "방문자를 밴했습니다.", "방문자를 밴하지 못했습니다.");
    }

    public void banVisitorTarget(Player player, UUID islandId, String target, String reason) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            banVisitor(player, islandId, targetUuid, reason);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listBans(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandBans(islandId).thenApply(islandMessages::banList), "밴 목록을 불러오지 못했습니다.");
    }

    public void pardonVisitor(Player player, UUID islandId, UUID targetUuid) {
        sendActionResult(player, coreApiClient.pardonIslandVisitor(islandId, player.getUniqueId(), targetUuid), "방문자 밴을 해제했습니다.", "방문자 밴을 해제하지 못했습니다.");
    }

    public void pardonVisitorTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            pardonVisitor(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void kickVisitor(Player player, UUID islandId, UUID targetUuid) {
        coreApiClient.kickIslandVisitor(islandId, player.getUniqueId(), targetUuid).thenRun(() ->
            player.sendMessage(Component.text("방문자 추방을 요청했습니다."))
        ).exceptionally(error -> {
            player.sendMessage(Component.text("방문자를 추방할 권한이 없거나 처리하지 못했습니다."));
            return null;
        });
    }

    public void kickVisitorTarget(Player player, UUID islandId, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(targetUuid -> {
            if (targetUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            kickVisitor(player, islandId, targetUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setPublicAccess(Player player, UUID islandId, boolean publicAccess) {
        sendActionResult(player, coreApiClient.setIslandPublicAccess(islandId, player.getUniqueId(), publicAccess), publicAccess ? "섬을 공개로 변경했습니다." : "섬을 비공개로 변경했습니다.", "섬 공개 상태를 변경하지 못했습니다.");
    }

    public void setIslandName(Player player, UUID islandId, String name) {
        if (name == null || name.isBlank()) {
            player.sendMessage(Component.text("새 섬 이름을 입력해주세요."));
            return;
        }
        withResolvedIsland(player, islandId, "이름을 변경할 섬을 찾지 못했습니다.", "섬 이름을 변경하지 못했습니다.",
            resolved -> sendActionResult(player, coreApiClient.setIslandName(resolved, player.getUniqueId(), name), "섬 이름을 변경했습니다.", "섬 이름을 변경하지 못했습니다."));
    }

    public void setFlyFlag(Player player, UUID islandId, boolean enabled) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), kr.lunaf.cloudislands.api.model.IslandFlag.FLY, Boolean.toString(enabled)), enabled ? "섬 비행을 허용했습니다." : "섬 비행을 비활성화했습니다.", "섬 비행 설정을 변경하지 못했습니다.");
    }

    public void setBooleanFlag(Player player, UUID islandId, kr.lunaf.cloudislands.api.model.IslandFlag flag, boolean enabled, String label) {
        sendActionResult(player, coreApiClient.setIslandFlag(islandId, player.getUniqueId(), flag, Boolean.toString(enabled)), "섬 " + label + " 설정을 " + (enabled ? "켰습니다." : "껐습니다."), "섬 " + label + " 설정을 변경하지 못했습니다.");
    }

    public void listFlags(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "플래그를 확인할 섬을 찾지 못했습니다.", "섬 플래그를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandFlags(resolved).thenApply(islandMessages::flagList), "섬 플래그를 불러오지 못했습니다."));
    }

    public void listHomes(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.listIslandHomes(islandId).thenApply(islandMessages::homeList), "섬 홈을 불러오지 못했습니다.");
    }

    public void setHome(Player player, UUID islandId, String name) {
        IslandLocation defaultHome = new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
        sendActionResult(player, coreApiClient.setIslandHome(islandId, player.getUniqueId(), name, defaultHome), "섬 홈을 설정했습니다.", "섬 홈을 설정하지 못했습니다.");
    }

    public void setLocked(Player player, UUID islandId, boolean locked) {
        sendActionResult(player, coreApiClient.setIslandLocked(islandId, player.getUniqueId(), locked), locked ? "섬을 잠금 상태로 변경했습니다." : "섬 잠금을 해제했습니다.", "섬 잠금 상태를 변경하지 못했습니다.");
    }

    public void listPermissions(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "권한을 확인할 섬을 찾지 못했습니다.", "섬 권한을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandPermissions(resolved).thenApply(islandMessages::permissionList), "섬 권한을 불러오지 못했습니다."));
    }

    public void setPermission(Player player, UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        sendActionResult(player, coreApiClient.setIslandPermission(islandId, player.getUniqueId(), role, permission, allowed), "섬 권한을 변경했습니다.", "섬 권한을 변경하지 못했습니다.");
    }

    public void listRoles(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "역할을 확인할 섬을 찾지 못했습니다.", "섬 역할을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandRoles(resolved).thenApply(islandMessages::roleList), "섬 역할을 불러오지 못했습니다."));
    }

    public void upsertRole(Player player, UUID islandId, IslandRole role, int weight, String displayName) {
        sendBodyResult(player, coreApiClient.upsertIslandRole(islandId, player.getUniqueId(), role, weight, displayName).thenApply(body -> "섬 역할 저장 완료: " + jsonValue(body, "role") + " weight=" + longValue(body, "weight") + " name=" + jsonValue(body, "displayName")), "섬 역할을 저장하지 못했습니다.");
    }

    public void resetRole(Player player, UUID islandId, IslandRole role) {
        sendBodyResult(player, coreApiClient.resetIslandRole(islandId, player.getUniqueId(), role).thenApply(body -> "섬 역할 초기화 완료: " + jsonValue(body, "role")), "섬 역할을 초기화하지 못했습니다.");
    }

    public void listIslandLogs(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "로그를 확인할 섬을 찾지 못했습니다.", "섬 로그를 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.listIslandLogs(resolved, 30).thenApply(islandMessages::islandLogList), "섬 로그를 불러오지 못했습니다."));
    }

    public void showBank(Player player, UUID islandId) {
        if (rejectExplicitIslandLookup(player, islandId)) {
            return;
        }
        withResolvedIsland(player, islandId, "은행을 확인할 섬을 찾지 못했습니다.", "섬 은행을 불러오지 못했습니다.",
            resolved -> sendBodyResult(player, coreApiClient.islandBank(resolved).thenApply(islandMessages::bankInfo), "섬 은행을 불러오지 못했습니다."));
    }

    public void depositBank(Player player, UUID islandId, String amount) {
        player.sendMessage(Component.text("섬 은행 입금은 경제 플러그인 연동이 필요한 작업이라 Paper Agent에서만 처리합니다."));
    }

    public void withdrawBank(Player player, UUID islandId, String amount) {
        player.sendMessage(Component.text("섬 은행 출금은 경제 플러그인 연동이 필요한 작업이라 Paper Agent에서만 처리합니다."));
    }

    public void showLevelRanking(Player player) {
        showLevelRanking(player, 10);
    }

    public void showLevelRanking(Player player, int limit) {
        sendBodyResult(player, coreApiClient.topIslandsByLevel(Math.max(1, Math.min(limit, 100))).thenApply(body -> islandMessages.rankingList("섬 랭킹", body)), "랭킹을 불러오지 못했습니다.");
    }

    public void showWorthRanking(Player player) {
        showWorthRanking(player, 10);
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

    private boolean rejectExplicitIslandLookup(Player player, UUID islandId) {
        if (islandId == null || islandId.equals(new UUID(0L, 0L))) {
            return false;
        }
        player.sendMessage(Component.text("플레이어 명령에서는 섬 UUID 직접 조회를 사용할 수 없습니다."));
        return true;
    }

    private void withResolvedIsland(Player player, UUID islandId, String missingMessage, String failureMessage, Consumer<UUID> action) {
        UUID emptyIslandId = new UUID(0L, 0L);
        if (islandId != null && !islandId.equals(emptyIslandId)) {
            action.accept(islandId);
            return;
        }
        coreApiClient.islandInfoByOwner(player.getUniqueId()).thenAccept(body -> {
            UUID resolvedIslandId = parseUuid(jsonValue(body, "islandId"));
            if (resolvedIslandId.equals(emptyIslandId)) {
                player.sendMessage(Component.text(missingMessage));
                return;
            }
            action.accept(resolvedIslandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
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

    public void listJobs(Player player) {
        sendBodyResult(player, coreApiClient.listJobs().thenApply(this::jobListMessage), "작업 목록을 불러오지 못했습니다.");
    }

    public void retryJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.retryJob(jobId).thenApply(body -> jobActionMessage("retry", body)), "작업 재시도를 요청하지 못했습니다.");
    }

    public void cancelJob(Player player, UUID jobId) {
        sendBodyResult(player, coreApiClient.cancelJob(jobId).thenApply(body -> jobActionMessage("cancel", body)), "작업 취소를 요청하지 못했습니다.");
    }

    public void recoverJobs(Player player, String nodeId, long minIdleMillis, int maxJobs) {
        sendBodyResult(player, coreApiClient.recoverJobs(nodeId, minIdleMillis, maxJobs).thenApply(body -> jobActionMessage("recover", body)), "작업 복구를 요청하지 못했습니다.");
    }

    public void listNodes(Player player) {
        sendBodyResult(player, coreApiClient.listNodes().thenApply(this::nodeListSummaryMessage), "노드 목록을 불러오지 못했습니다.");
    }

    public void nodeInfo(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.nodeInfo(nodeId).thenApply(this::appendLevelScanSummary), "노드 정보를 불러오지 못했습니다.");
    }

    public void nodeIslands(Player player, String nodeId, int limit) {
        sendBodyResult(player, coreApiClient.nodeIslands(nodeId, Math.max(1, Math.min(limit, 200))).thenApply(this::nodeIslandListMessage), "노드 섬 현황을 불러오지 못했습니다.");
    }

    public void drainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.drainNode(nodeId).thenApply(body -> nodeActionSummaryMessage("Node drain", nodeId, body)), "노드 drain을 요청하지 못했습니다.");
    }

    public void undrainNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.undrainNode(nodeId).thenApply(body -> nodeActionSummaryMessage("Node undrain", nodeId, body)), "노드 undrain을 요청하지 못했습니다.");
    }

    public void sweepNode(Player player, String nodeId) {
        sendBodyResult(player, coreApiClient.sweepNode(nodeId).thenApply(this::nodeSweepMessage), "노드 장애 스윕을 요청하지 못했습니다.");
    }

    public void kickAllNode(Player player, String nodeId, String reason) {
        coreApiClient.kickAllNode(nodeId, reason).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeActionSummaryMessage("Node kickall", nodeId, body) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 kickall을 요청하지 못했습니다."));
            return null;
        });
    }

    public void shutdownSafeNode(Player player, String nodeId, String reason) {
        coreApiClient.shutdownNodeSafely(nodeId, reason).thenAccept(body -> {
            int moved = moveNodePlayersToFallback(nodeId);
            player.sendMessage(Component.text(nodeActionSummaryMessage("Node shutdown-safe", nodeId, body) + " lobbyMoved=" + moved));
        }).exceptionally(error -> {
            player.sendMessage(Component.text("노드 shutdown-safe를 요청하지 못했습니다."));
            return null;
        });
    }

    public void activateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.activateIsland(islandId).thenApply(body -> islandMessages.actionResult("Island activate", islandId.toString(), body)), "섬 활성화를 요청하지 못했습니다.");
    }

    public void activateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> activateIsland(player, islandId));
    }

    public void deactivateIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.deactivateIsland(islandId).thenApply(body -> islandMessages.actionResult("Island deactivate", islandId.toString(), body)), "섬 비활성화를 요청하지 못했습니다.");
    }

    public void deactivateIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> deactivateIsland(player, islandId));
    }

    public void migrateIsland(Player player, UUID islandId, String targetNode) {
        sendBodyResult(player, coreApiClient.migrateIsland(islandId, targetNode).thenApply(body -> islandMessages.actionResult("Island migrate", islandId.toString(), body)), "섬 마이그레이션을 요청하지 못했습니다.");
    }

    public void migrateIslandTarget(Player player, String target, String targetNode) {
        adminIslandTarget(player, target, islandId -> migrateIsland(player, islandId, targetNode));
    }

    public void quarantineIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.quarantineIsland(islandId, reason).thenApply(body -> islandMessages.actionResult("Island quarantine", islandId.toString(), body)), "섬 격리를 요청하지 못했습니다.");
    }

    public void quarantineIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> quarantineIsland(player, islandId, reason));
    }

    public void adminIslandInfo(Player player, UUID lookupUuid) {
        sendBodyResult(player, coreApiClient.adminIslandInfo(lookupUuid).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandInfoTarget(Player player, String target) {
        UUID parsed = parseUuid(target);
        if (!parsed.equals(new UUID(0L, 0L))) {
            adminIslandInfo(player, parsed);
            return;
        }
        sendBodyResult(player, coreApiClient.islandInfoByName(target).thenApply(islandMessages::islandInfo), "섬 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhere(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminIslandWhere(islandId).thenApply(islandMessages::runtimeInfo), "섬 위치 정보를 불러오지 못했습니다.");
    }

    public void adminIslandWhereTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminIslandWhere(player, islandId));
    }

    public void adminTeleportIsland(Player player, UUID islandId) {
        routeFuture(player, coreApiClient.adminIslandTeleport(player.getUniqueId(), islandId), "섬으로 이동하지 못했습니다.");
    }

    public void adminTeleportIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminTeleportIsland(player, islandId));
    }

    public void adminDeleteIsland(Player player, UUID islandId) {
        sendBodyResult(player, coreApiClient.adminDeleteIsland(islandId).thenApply(body -> islandMessages.actionResult("Island delete", islandId.toString(), body)), "섬 삭제를 요청하지 못했습니다.");
    }

    public void adminDeleteIslandTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> adminDeleteIsland(player, islandId));
    }

    public void repairIsland(Player player, UUID islandId, String reason) {
        sendBodyResult(player, coreApiClient.repairIsland(islandId, reason).thenApply(body -> islandMessages.actionResult("Island repair", islandId.toString(), body)), "섬 복구를 요청하지 못했습니다.");
    }

    public void repairIslandTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> repairIsland(player, islandId, reason));
    }

    public void listSnapshotsTarget(Player player, String target) {
        adminIslandTarget(player, target, islandId -> listSnapshots(player, islandId));
    }

    public void snapshotTarget(Player player, String target, String reason) {
        adminIslandTarget(player, target, islandId -> snapshot(player, islandId, reason));
    }

    public void restoreTarget(Player player, String target, long snapshotNo) {
        adminIslandTarget(player, target, islandId -> restore(player, islandId, snapshotNo));
    }

    public void debugRoutes(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.debugRoutes(playerUuid).thenApply(this::routeDebugMessage), "라우트 정보를 불러오지 못했습니다.");
    }

    public void debugRoutesTarget(Player player, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            debugRoutes(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void routeTicket(Player player, UUID ticketId) {
        sendBodyResult(player, coreApiClient.routeTicket(ticketId).thenApply(this::routeTicketMessage), "티켓 정보를 불러오지 못했습니다.");
    }

    public void routeTicketTarget(Player player, String target) {
        UUID ticketId = parseUuid(target);
        if (!ticketId.equals(new UUID(0L, 0L))) {
            routeTicket(player, ticketId);
            return;
        }
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            sendBodyResult(player, coreApiClient.routeTicketForPlayer(playerUuid).thenApply(this::routeTicketMessage), "티켓 정보를 불러오지 못했습니다.");
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearRoute(Player player, UUID playerUuid, UUID ticketId) {
        sendBodyResult(player, coreApiClient.clearRoute(playerUuid, ticketId).thenApply(this::routeClearMessage), "라우트 정리를 요청하지 못했습니다.");
    }

    public void clearRouteTarget(Player player, String target, UUID ticketId) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
                return;
            }
            clearRoute(player, playerUuid, ticketId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearCache(Player player) {
        sendBodyResult(player, coreApiClient.clearCache().thenApply(body -> coreStatusMessages.maintenance("Cache clear", body)), "캐시 정리를 요청하지 못했습니다.");
    }

    public void startEventPolling(Object plugin) {
        stopEventPolling();
        eventPollTask = proxy.getScheduler()
            .buildTask(plugin, eventPoller::pollOnce)
            .repeat(Duration.ofSeconds(2L))
            .schedule();
    }

    public void stopEventPolling() {
        if (eventPollTask != null) {
            eventPollTask.cancel();
            eventPollTask = null;
        }
    }

    public void listEvents(Player player) {
        sendBodyResult(player, coreApiClient.listEvents().thenApply(eventMessages::events), "이벤트 목록을 불러오지 못했습니다.");
    }

    public void listAuditLogs(Player player) {
        sendBodyResult(player, coreApiClient.listAuditLogs().thenApply(eventMessages::audit), "감사 로그를 불러오지 못했습니다.");
    }

    public void metrics(Player player) {
        sendBodyResult(player, coreApiClient.metrics().thenApply(coreStatusMessages::metrics), "Core metrics를 불러오지 못했습니다.");
    }

    public void coreConfig(Player player) {
        sendBodyResult(player, coreApiClient.coreConfig().thenApply(this::coreConfigMessage), "Core config를 불러오지 못했습니다.");
    }

    public void addonEndpoints(Player player) {
        sendBodyResult(player, coreApiClient.coreConfig().thenApply(coreStatusMessages::addonEndpoints), "Addon endpoint 상태를 불러오지 못했습니다.");
    }

    public void storageStatus(Player player) {
        sendBodyResult(player, coreApiClient.storageStatus().thenApply(this::storageStatusMessage), "Storage 상태를 불러오지 못했습니다.");
    }

    public void addonStateSummary(Player player) {
        sendBodyResult(player, coreApiClient.addonStateSummary().thenApply(islandMessages::addonStateSummary), "Addon state 상태를 불러오지 못했습니다.");
    }

    public void listBlockValues(Player player) {
        sendBodyResult(player, coreApiClient.listBlockValues().thenApply(islandMessages::blockValueList), "블록 가치 목록을 불러오지 못했습니다.");
    }

    public void setBlockValue(Player player, String materialKey, String worth, long levelPoints, long limit) {
        sendBodyResult(player, coreApiClient.setBlockValueResult(player.getUniqueId(), materialKey, worth, levelPoints, limit).thenApply(body -> islandMessages.actionResult("Block value set", materialKey, body)), "블록 가치를 변경하지 못했습니다.");
    }

    public void reload(Player player) {
        sendBodyResult(player, coreApiClient.reload().thenApply(body -> coreStatusMessages.maintenance("Core reload", body)), "reload를 요청하지 못했습니다.");
    }

    public void migrateSuperiorSkyblock2(Player player, String action, String path) {
        sendBodyResult(player, coreApiClient.migrateSuperiorSkyblock2(action, path).thenApply(migrationMessages::format), "마이그레이션 명령을 실행하지 못했습니다.");
    }

    private String coreConfigMessage(String body) {
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Core config: failed code=" + code;
        }
        return "Core config: repo=" + jsonValue(body, "repositoryMode")
            + " jobs=" + jsonValue(body, "jobQueueMode")
            + " events=" + jsonValue(body, "eventBusMode")
            + " effectiveRepo=" + jsonValue(body, "effectiveRepositoryMode")
            + " effectiveJobs=" + jsonValue(body, "effectiveJobQueueMode")
            + " storage=" + jsonValue(body, "storageType")
            + " islandModel=" + jsonValue(body, "islandResourceModel")
            + " portableBundle=" + boolValue(body, "islandPortableBundle")
            + " serverPinned=" + boolValue(body, "islandServerPinned")
            + " islandExecution=" + jsonValue(body, "islandExecutionModel")
            + " islandNodeRole=" + jsonValue(body, "islandNodeRole")
            + " islandRouting=" + jsonValue(body, "islandRoutingModel")
            + " createFlow=" + jsonValue(body, "createIslandRequestFlow")
            + " homeFlow=" + jsonValue(body, "homeRequestFlow")
            + " visitFlow=" + jsonValue(body, "visitRequestFlow")
            + " routeUi=" + jsonValue(body, "routePlayerLoadingUi")
            + " routeFailureCodes=" + jsonValue(body, "routePlayerFailureCodes")
            + " routePublicMessages=" + jsonValue(body, "routePublicMessagePolicy")
            + " routeDebugReasons=" + jsonValue(body, "routeDebugReasonPolicy")
            + " routeTransferFailure=" + jsonValue(body, "routeTransferFailurePolicy")
            + " softFullRoute=" + jsonValue(body, "softFullRoutingPolicy")
            + " modules=" + jsonValue(body, "moduleLayout")
            + " dist=" + jsonValue(body, "distributionLayout")
            + " addonRegistry=" + jsonValue(body, "addonRegistryPolicy")
            + " addonStateOwner=" + jsonValue(body, "addonStateOwnershipPolicy")
            + " addonRemovalSafe=" + jsonValue(body, "addonRemovalSafetyPolicy")
            + " addonStateIsolation=" + jsonValue(body, "addonStateFailureIsolationPolicy")
            + " addonExtension=" + jsonValue(body, "addonExtensionModel")
            + " addonApiLookup=" + jsonValue(body, "addonApiLookupPolicy")
            + " addonApiContract=" + jsonValue(body, "addonApiContractVersion")
            + " addonApiContractStatus=" + jsonValue(body, "addonApiContractCompatibility")
            + " addonApiContractCompatible=" + jsonValue(body, "addonApiContractCompatible")
            + " satisMultiNodeSafe=" + boolValue(body, "satisMultiNodeSafe")
            + " satisNodeCountPolicy=" + jsonValue(body, "satisNodeCountPolicy")
            + " addonApiRequiredKeys=" + jsonValue(body, "addonApiRequiredMetadataKeys")
            + " addonApiRead=" + jsonValue(body, "addonApiReadPolicy")
            + " addonApiWrite=" + jsonValue(body, "addonApiWriteAuthority")
            + " addonApiSyncEvent=" + jsonValue(body, "addonApiSyncEventPolicy")
            + " addonApiStorage=" + jsonValue(body, "addonApiStoragePolicy")
            + " addonJavaApi=" + jsonValue(body, "addonJavaPluginApiPolicy")
            + " addonInternalApi=" + jsonValue(body, "addonInternalApiPolicy")
            + " addonEventApi=" + jsonValue(body, "addonEventApiPolicy")
            + " addonCoreAuth=" + jsonValue(body, "addonCoreAuthPolicy")
            + " addonAdminEndpoint=" + jsonValue(body, "addonAdminEndpointPolicy")
            + " addonNetworkExposure=" + jsonValue(body, "addonNetworkExposurePolicy")
            + " addonSecurityPosture=" + jsonValue(body, "addonSecurityPostureSummary")
            + " addonTopologyPrivacy=" + jsonValue(body, "addonTopologyPrivacyPolicy")
            + " addonConsistency=" + jsonValue(body, "addonConsistencyAuthorityPolicy")
            + " addonEvents=" + jsonValue(body, "addonEventDeliveryPolicy")
            + " addonEventCoverage=" + jsonValue(body, "addonEventCoverage")
            + " addonEventBackfill=" + jsonValue(body, "addonEventBackfillPolicy")
            + " satisPackaging=" + jsonValue(body, "satisPackaging")
            + " satisCoreCoupling=" + jsonValue(body, "satisCoreCoupling")
            + " satisAddonRemovalPolicy=" + jsonValue(body, "satisAddonRemovalPolicy")
            + " satisDataRetentionPolicy=" + jsonValue(body, "satisDataRetentionPolicy")
            + " satisCoreBootRequiresAddon=" + boolValue(body, "satisCoreBootRequiresAddon")
            + " satisCommandOwner=" + jsonValue(body, "satisCommandOwner")
            + " velocitySatisCommandPolicy=" + jsonValue(body, "velocitySatisCommandPolicy")
            + " paperSatisCommandPolicy=" + jsonValue(body, "paperSatisCommandPolicy")
            + " paperAgentRolePolicy=" + jsonValue(body, "paperAgentRolePolicy")
            + " paperLobbyRolePolicy=" + jsonValue(body, "paperLobbyRolePolicy")
            + " paperIslandNodeRolePolicy=" + jsonValue(body, "paperIslandNodeRolePolicy")
            + " velocityCommandOwner=" + jsonValue(body, "velocityCommandOwnershipPolicy")
            + " paperCommandFallback=" + jsonValue(body, "paperCommandFallbackPolicy")
            + " pluginMessaging=" + jsonValue(body, "pluginMessagingPolicy")
            + " pluginMessagingAllowed=" + jsonValue(body, "pluginMessagingAllowedUse")
            + " pluginMessagingForbidden=" + jsonValue(body, "pluginMessagingForbiddenUse")
            + " authPolicy=" + jsonValue(body, "coreApiAuthPolicy")
            + " adminPolicy=" + jsonValue(body, "adminPermissionPolicy")
            + " auditPolicy=" + jsonValue(body, "auditLogPolicy")
            + " infraPolicy=" + jsonValue(body, "infrastructureExposurePolicy")
            + " bindPolicy=" + jsonValue(body, "publicBindRiskPolicy")
            + " dbType=" + jsonValue(body, "configuredDatabaseType")
            + " dbTypeSource=" + jsonValue(body, "configuredDatabaseTypeSource")
            + " dbBackend=" + jsonValue(body, "databaseBackend")
            + " jdbcSource=" + jsonValue(body, "jdbcUrlSource")
            + " jdbcSettingsType=" + jsonValue(body, "effectiveJdbcSettingsType")
            + " jdbcSettingsSource=" + jsonValue(body, "effectiveJdbcSettingsSource")
            + " jdbcSupported=" + boolValue(body, "coreJdbcSupported")
            + " jdbcSupportedBackends=" + jsonValue(body, "coreJdbcSupportedBackends")
            + " setupFallbackBackends=" + jsonValue(body, "coreSetupFallbackBackends")
            + " setupFallbackEnabled=" + boolValue(body, "coreSetupFallbackEnabled")
            + " setupFallbackSharedFirst=" + boolValue(body, "coreSetupFallbackRequireSharedBeforeLocal")
            + " setupFallbackLocalLast=" + boolValue(body, "coreSetupFallbackLocalLast")
            + " setupFallbackSafeOrder=" + jsonValue(body, "coreSetupFallbackProductionSafeOrder")
            + " setupFallbackOrder=" + jsonValue(body, "coreSetupFallbackOrder")
            + " setupFallbackMode=" + jsonValue(body, "coreSetupFallbackMode")
            + " setupDbFallbackSummary=" + jsonValue(body, "coreSetupDatabaseFallbackSummary")
            + " setupDbProductionDurable=" + boolValue(body, "coreSetupDatabaseProductionDurable")
            + " setupDbRequested=" + jsonValue(body, "coreSetupDatabaseRequestedBackend")
            + " setupDbAuthority=" + jsonValue(body, "coreSetupDatabaseEffectiveAuthority")
            + " setupDbEffectiveBackend=" + jsonValue(body, "coreSetupDatabaseEffectiveBackend")
            + " setupDbFallbackTarget=" + jsonValue(body, "coreSetupDatabaseFallbackTarget")
            + " setupDbFallbackReason=" + jsonValue(body, "coreSetupDatabaseFallbackReason")
            + " setupDbDurable=" + boolValue(body, "coreSetupDatabaseDurable")
            + " setupDbModes=" + jsonValue(body, "coreSetupDatabaseOperationalModes")
            + " setupDbLoader=" + jsonValue(body, "coreSetupDatabaseConfigLoader")
            + " setupDbPaths=" + jsonValue(body, "coreSetupDatabaseResolvedPathExamples")
            + " setupDbShapes=" + jsonValue(body, "coreSetupDatabaseConfigShapes")
            + " setupDbTypedShapes=" + jsonValue(body, "coreSetupDatabaseTypedShapes")
            + " setupDbTypedCredentials=" + jsonValue(body, "coreSetupDatabaseTypedCredentialKeys")
            + " setupDbTypedHostMode=" + jsonValue(body, "coreSetupDatabaseTypedHostMode")
            + " setupDbTypedProbeOrder=" + jsonValue(body, "coreSetupDatabaseTypedProbeOrder")
            + " setupDbCoreApiMode=" + jsonValue(body, "coreSetupDatabaseCoreApiMode")
            + " setupDbCoreApiBaseUrl=" + jsonValue(body, "coreSetupDatabaseCoreApiBaseUrl")
            + " setupDbCoreApiAuthToken=" + boolValue(body, "coreSetupDatabaseCoreApiAuthTokenConfigured")
            + " setupDbCoreApiAdminToken=" + boolValue(body, "coreSetupDatabaseCoreApiAdminTokenConfigured")
            + " setupDbCoreApiTimeoutMs=" + longValue(body, "coreSetupDatabaseCoreApiTimeoutMs")
            + " setupDbCoreApiPaths=" + jsonValue(body, "coreSetupDatabaseCoreApiConfigPaths")
            + " setupDbEnv=" + jsonValue(body, "coreSetupDatabaseEnv")
            + " setupDbPrecedence=" + jsonValue(body, "coreSetupDatabasePrecedence")
            + " setupDbNameAliases=" + jsonValue(body, "coreSetupDatabaseNameAliases")
            + " setupDbJdbcAliases=" + jsonValue(body, "coreSetupDatabaseJdbcAliases")
            + " setupDbTypeInference=" + jsonValue(body, "coreSetupDatabaseTypeInference")
            + " jdbcFallback=" + jsonValue(body, "coreJdbcFallbackReason")
            + " jdbcFallbackActive=" + boolValue(body, "coreJdbcFallbackActive")
            + " setupFallbackEffective=" + boolValue(body, "coreSetupFallbackEffective")
            + " setupFallbackSafetyForced=" + boolValue(body, "coreSetupFallbackSafetyForced")
            + " setupFallbackPolicy=" + jsonValue(body, "coreSetupFallbackPolicy")
            + " jdbcFallbackStatus=" + jsonValue(body, "coreJdbcFallbackStatus")
            + " addonBulkSave=" + boolValue(body, "addonStateBulkSaveApi")
            + " addonBulkGlobal=" + jsonValue(body, "addonStateBulkSaveGlobalEndpoint")
            + " addonBulkIsland=" + jsonValue(body, "addonStateBulkSaveIslandEndpoint")
            + " addonTableBulkGlobal=" + jsonValue(body, "addonStateTableKeyValueBulkSaveGlobalEndpoint")
            + " addonTableBulkIsland=" + jsonValue(body, "addonStateTableKeyValueBulkSaveIslandEndpoint")
            + " addonTableBulkGlobalAlias=" + jsonValue(body, "addonStateTableKeyValueBulkSaveGlobalAlias")
            + " addonTableBulkIslandAlias=" + jsonValue(body, "addonStateTableKeyValueBulkSaveIslandAlias")
            + " addonTableBulkGlobalCompat=" + jsonValue(body, "addonStateTableKeyValueBulkGlobalEndpoint")
            + " addonTableBulkIslandCompat=" + jsonValue(body, "addonStateTableKeyValueBulkIslandEndpoint")
            + " addonTableBulkGlobalMap=" + jsonValue(body, "addonStateTableBulkGlobalEndpoint")
            + " addonTableBulkIslandMap=" + jsonValue(body, "addonStateTableBulkIslandEndpoint")
            + " addonTablePrefix=" + jsonValue(body, "addonStateTableKeyPrefix")
            + " addonMaxKeys=" + longValue(body, "addonStateMaxKeysPerAddon")
            + " addonMaxValue=" + longValue(body, "addonStateMaxValueLength")
            + " addonGlobalCacheKey=" + jsonValue(body, "addonStateGlobalCacheKey")
            + " addonIslandCacheKey=" + jsonValue(body, "addonStateIslandCacheKey")
            + " addonCacheInvalidationApi=" + jsonValue(body, "addonStateCacheInvalidationApi")
            + " cacheEventFields=" + jsonValue(body, "cacheInvalidationEventFields")
            + " globalEventTypeKeys=" + jsonValue(body, "globalEventTypeKeys")
            + " globalEventRecoveryKeys=" + jsonValue(body, "globalEventRecoveryKeys")
            + " globalEventAddonKeys=" + jsonValue(body, "globalEventAddonKeys")
            + " satisCoreRequiresAddon=" + boolValue(body, "satisCoreBootRequiresAddon")
            + " satisDataRetention=" + jsonValue(body, "satisDataRetentionPolicy")
            + " satisCommandOwner=" + jsonValue(body, "satisCommandOwner")
            + " pool=" + jsonValue(body, "islandPool")
            + " poolNodes=" + longValue(body, "islandPoolNodeCount")
            + " poolRouteCandidates=" + longValue(body, "islandPoolRouteCandidateCount")
            + " poolRouteCandidateMin=" + longValue(body, "islandPoolRouteCandidateRecommendedMinimum")
            + " poolRouteCandidateMinStatus=" + jsonValue(body, "islandPoolRouteCandidateMinimumStatus")
            + " poolScale=" + jsonValue(body, "islandPoolScaleStatus")
            + " poolScaleModel=" + jsonValue(body, "islandPoolScaleModel")
            + " poolElasticLimit=" + jsonValue(body, "islandPoolElasticLimitPolicy")
            + " poolMultiNodeReady=" + boolValue(body, "islandPoolMultiNodeReady")
            + " poolScaleGuidance=" + jsonValue(body, "islandPoolScaleGuidance")
            + " poolHorizontalScale=" + jsonValue(body, "islandPoolHorizontalScalePolicy")
            + " poolFiveSixNodes=" + jsonValue(body, "islandPoolFiveSixNodePolicy")
            + " poolFiveSixHealthy=" + boolValue(body, "islandPoolFiveSixNodeHealthy")
            + " placement=" + jsonValue(body, "islandPlacementPolicy")
            + " placementShards=" + longValue(body, "islandPlacementShardCount")
            + " placementCellsPerAxis=" + longValue(body, "islandPlacementCellsPerAxis")
            + " placementCollision=" + jsonValue(body, "islandPlacementCollisionPolicy")
            + " nodeHardRules=" + jsonValue(body, "islandNodeHardRules")
            + " nodeScoreWeights=" + jsonValue(body, "islandNodeScoreWeights")
            + " nodeSchema=" + jsonValue(body, "islandNodeSchemaColumns")
            + " existingRoutePolicy=" + jsonValue(body, "islandNodeExistingRoutePolicy")
            + " visitorSoftFullPolicy=" + jsonValue(body, "islandNodeVisitorSoftFullPolicy")
            + " routingFailureDetails=" + jsonValue(body, "routingFailureDetailKeys")
            + " poolDegraded=" + boolValue(body, "islandPoolDegraded")
            + " poolCandidateShortfall=" + longValue(body, "islandPoolRouteCandidateShortfall")
            + " poolCandidateBlocks=" + jsonValue(body, "islandPoolRouteCandidateBlockSummary")
            + " poolCandidateNodes=" + jsonValue(body, "islandPoolRouteCandidateNodeIds")
            + " poolBlockedNodes=" + jsonValue(body, "islandPoolBlockedNodeIds")
            + " poolFiveSixStatus=" + jsonValue(body, "islandPoolFiveSixNodeStatus")
            + " poolDuplicateServers=" + longValue(body, "islandPoolDuplicateVelocityServerNameNodeCount")
            + " poolDefaultIdentityRisk=" + longValue(body, "islandPoolDefaultNodeIdentityRiskCount")
            + " dbPool=" + longValue(body, "databasePoolSize")
            + " softFull=" + jsonValue(body, "softFullPolicy")
            + " hardFull=" + jsonValue(body, "hardFullPolicy")
            + " migration=" + jsonValue(body, "migrationPolicy")
            + " superiorMigration=" + boolValue(body, "superiorSkyblock2MigrationEnabled")
            + " superiorInputOnly=" + boolValue(body, "superiorSkyblock2MigrationInputOnly")
            + " superiorRuntimeDependency=" + boolValue(body, "superiorSkyblock2RuntimeDependency")
            + " superiorRuntimePolicy=" + jsonValue(body, "superiorSkyblock2RuntimePolicy")
            + " ticketTtl=" + longValue(body, "routeTicketTtlSeconds") + "s"
            + " prepTtl=" + longValue(body, "routePreparingTicketTtlSeconds") + "s"
            + " heartbeatTimeout=" + longValue(body, "heartbeatTimeoutSeconds") + "s"
            + " leaseDuration=" + longValue(body, "leaseDurationSeconds") + "s"
            + " redisTtl=" + jsonValue(body, "redisCacheTtlPolicy")
            + " redisKeys=" + jsonValue(body, "redisKeyPolicy")
            + " redisStreams=" + jsonValue(body, "redisStreamPolicy")
            + " globalEvents=" + jsonValue(body, "globalEventTypes")
            + " routeMetricServer=" + boolValue(body, "routeMetricsTargetServerName")
            + " routeMetricServerEvents=" + jsonValue(body, "routeMetricsTargetServerNameEvents")
            + " routeMetricRequestedNode=" + boolValue(body, "routeMetricsRequestedNode")
            + " routeMetricRequestedNodeEvents=" + jsonValue(body, "routeMetricsRequestedNodeEvents")
            + " lockPolicy=" + jsonValue(body, "distributedLockPolicy")
            + " fencing=" + jsonValue(body, "fencingTokenPolicy")
            + " staleWrite=" + jsonValue(body, "staleWritePolicy")
            + " storageLayout=" + jsonValue(body, "storageLayout")
            + " storageLatest=" + jsonValue(body, "storageLatestPointer")
            + " storageManifest=" + jsonValue(body, "storageSnapshotManifest")
            + " storageBundle=" + jsonValue(body, "storageBundleObject")
            + " storageChecksumFile=" + jsonValue(body, "storageChecksumFile")
            + " storageBackup=" + jsonValue(body, "storageDeleteBackupPath")
            + " storageRecovery=" + jsonValue(body, "storageRecoveryPath")
            + " storagePortability=" + jsonValue(body, "storagePortabilityPolicy")
            + " snapshotLatest=" + longValue(body, "snapshotKeepLatest")
            + " snapshotRetention=" + longValue(body, "snapshotKeepHourly") + "/" + longValue(body, "snapshotKeepDaily") + "/" + longValue(body, "snapshotKeepWeekly") + "/" + longValue(body, "snapshotKeepManual")
            + " snapshotCompress=" + boolValue(body, "snapshotCompress")
            + " snapshotChecksum=" + jsonValue(body, "snapshotChecksumAlgorithm")
            + " snapshotRestore=" + jsonValue(body, "snapshotRestorePipeline")
            + " rankingPolicy=" + jsonValue(body, "rankingUpdatePolicy")
            + " blockValuePolicy=" + jsonValue(body, "blockValuePolicy")
            + " upgradePolicy=" + jsonValue(body, "upgradePolicy")
            + " generatorPolicy=" + jsonValue(body, "generatorPolicy")
            + " mtls=" + boolValue(body, "requireMtls")
            + " ipAllowlist=" + boolValue(body, "ipAllowlistEnabled");
    }

    public void playerInfo(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.playerInfo(playerUuid).thenApply(islandMessages::playerInfo), "플레이어 정보를 불러오지 못했습니다.");
    }

    public void playerInfoTarget(Player player, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            playerInfo(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void setPlayerIsland(Player player, UUID playerUuid, UUID islandId) {
        sendBodyResult(player, coreApiClient.setPlayerIsland(playerUuid, islandId).thenApply(body -> islandMessages.actionResult("Player setisland", playerUuid.toString(), body)), "플레이어 섬을 설정하지 못했습니다.");
    }

    public void setPlayerIslandTarget(Player player, String target, UUID islandId) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            setPlayerIsland(player, playerUuid, islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void clearPlayerIsland(Player player, UUID playerUuid) {
        sendBodyResult(player, coreApiClient.clearPlayerIsland(playerUuid).thenApply(body -> islandMessages.actionResult("Player clearisland", playerUuid.toString(), body)), "플레이어 섬을 해제하지 못했습니다.");
    }

    public void clearPlayerIslandTarget(Player player, String target) {
        targetResolver.resolvePlayerUuid(target).thenAccept(playerUuid -> {
            if (playerUuid.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
                return;
            }
            clearPlayerIsland(player, playerUuid);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("대상 플레이어를 찾지 못했습니다."));
            return null;
        });
    }

    public void listTemplates(Player player) {
        sendBodyResult(player, coreApiClient.listTemplates().thenApply(islandMessages::templateList), "섬 템플릿 목록을 불러오지 못했습니다.");
    }

    public void upsertTemplate(Player player, String templateId, String displayName, boolean enabled, String minNodeVersion) {
        sendBodyResult(player, coreApiClient.upsertTemplate(templateId, displayName, enabled, minNodeVersion).thenApply(body -> islandMessages.actionResult("Template upsert", templateId, body)), "섬 템플릿을 저장하지 못했습니다.");
    }

    public void enableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.enableTemplate(templateId).thenApply(body -> islandMessages.actionResult("Template enable", templateId, body)), "섬 템플릿을 활성화하지 못했습니다.");
    }

    public void disableTemplate(Player player, String templateId) {
        sendBodyResult(player, coreApiClient.disableTemplate(templateId).thenApply(body -> islandMessages.actionResult("Template disable", templateId, body)), "섬 템플릿을 비활성화하지 못했습니다.");
    }

    private void sendPlayerPayload(Player player, String body, String emptyMessage, String successMessage) {
        player.sendMessage(Component.text(playerPayloadMessage(body, emptyMessage, successMessage)));
    }

    private void sendPlayerPayloadFuture(Player player, CompletableFuture<String> future, String emptyMessage, String successMessage) {
        future.thenAccept(body -> sendPlayerPayload(player, body, emptyMessage, successMessage)).exceptionally(error -> {
            player.sendMessage(Component.text(emptyMessage));
            return null;
        });
    }

    private void sendActionResult(Player player, CompletableFuture<Void> future, String successMessage, String failureMessage) {
        future.thenRun(() -> player.sendMessage(Component.text(successMessage))).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    private void sendBodyResult(Player player, CompletableFuture<String> future, String emptyMessage) {
        future.thenAccept(body -> player.sendMessage(playerComponent(bodyResultMessage(body, emptyMessage)))).exceptionally(error -> {
            player.sendMessage(playerComponent(emptyMessage));
            return null;
        });
    }

    private String bodyResultMessage(String body, String emptyMessage) {
        if (body == null || body.isBlank()) {
            return emptyMessage;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return playerPayloadMessage(trimmed, emptyMessage, emptyMessage);
        }
        return trimmed;
    }

    private void handleCoreEvent(CoreEventEnvelope event) {
        String type = event.type();
        Map<String, String> fields = event.fields();
        if (!type.equals("NODE_STATE_CHANGED")) {
            return;
        }
        String state = fields.getOrDefault("state", "");
        String operation = fields.getOrDefault("operation", "");
        if (!state.equals("KICKALL") && !state.equals("SHUTDOWN_SAFE") && !state.equals("DOWN") && !operation.equals("SHUTDOWN_SAFE")) {
            return;
        }
        String nodeId = fields.getOrDefault("nodeId", "");
        if (nodeId.isBlank() || nodeId.equals("*")) {
            return;
        }
        moveNodePlayersToFallback(nodeId);
    }

    private String appendLevelScanSummary(String body) {
        java.util.List<String> summaries = new java.util.ArrayList<>();
        String activation = activationAllocationSummary(body);
        if (!activation.isBlank()) {
            summaries.add(activation);
        }
        String levelScan = levelScanSummary(body);
        if (!levelScan.isBlank()) {
            summaries.add(levelScan);
        }
        if (summaries.isEmpty()) {
            return body;
        }
        return (body == null || body.isBlank() ? "" : body + " | ") + String.join(" | ", summaries);
    }

    private String activationAllocationSummary(String body) {
        if (body == null || body.isBlank() || !body.contains("\"eligibleForNewActivation\"")) {
            return "";
        }
        boolean eligible = boolValue(body, "eligibleForNewActivation");
        String reason = jsonValue(body, "allocationBlockReason");
        return "활성화 배정=" + (eligible ? "가능" : "차단(" + (reason.isBlank() ? "UNKNOWN" : reason) + ")");
    }

    private String levelScanSummary(String body) {
        String scan = objectValue(body, "levelScan");
        if (scan.isBlank()) {
            return "";
        }
        StringBuilder summary = new StringBuilder("레벨 스캔=");
        summary.append(boolValue(scan, "running") ? "실행 중" : "대기");
        String lastIsland = jsonValue(scan, "lastIsland");
        if (!lastIsland.isBlank()) {
            summary.append(", 마지막 섬=").append(lastIsland);
        }
        appendLongSummary(summary, "시작", longValue(scan, "startedAt"));
        appendLongSummary(summary, "완료", longValue(scan, "finishedAt"));
        appendLongSummary(summary, "실패", longValue(scan, "failedAt"));
        return summary.toString();
    }

    private String nodeIslandSummary(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String id = jsonValue(body, "id");
        String server = jsonValue(body, "server");
        String state = jsonValue(body, "state");
        long active = longValue(body, "activeIslands");
        long max = longValue(body, "maxActiveIslands");
        StringBuilder summary = new StringBuilder("노드 섬 현황");
        if (!id.isBlank()) {
            summary.append(' ').append(id);
        }
        summary.append(": 활성 섬 ").append(active);
        if (max > 0L) {
            summary.append('/').append(max);
        }
        if (!state.isBlank()) {
            summary.append(", 상태=").append(state);
        }
        if (!server.isBlank()) {
            summary.append(", 서버=").append(server);
        }
        return summary.toString();
    }

    private String nodeIslandListMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String nodeId = jsonValue(body, "nodeId");
        long count = longValue(body, "count");
        String islands = arrayValue(body, "islands");
        if (islands.isBlank() || count == 0L) {
            return "노드 섬 현황" + routePrivacy.hiddenNodeLabel(nodeId) + ": 활성 섬 없음";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < islands.length()) {
            int objectStart = islands.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(islands, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = islands.substring(objectStart, objectEnd + 1);
            String islandId = jsonValue(object, "islandId");
            if (!islandId.isBlank()) {
                entries.add(islandId + "(" + routePrivacy.nodeIslandRuntimeSuffix(object) + ")");
            }
            index = objectEnd + 1;
        }
        return "노드 섬 현황" + routePrivacy.hiddenNodeLabel(nodeId) + ": " + (entries.isEmpty() ? "활성 섬 없음" : String.join(", ", entries));
    }

    private String storageStatusMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return "Storage status: registered node 없음";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int unavailable = 0;
        int index = 0;
        while (index < nodes.length()) {
            int objectStart = nodes.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(nodes, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = nodes.substring(objectStart, objectEnd + 1);
            String nodeId = jsonValue(object, "nodeId");
            boolean available = boolValue(object, "storageAvailable");
            if (!nodeId.isBlank()) {
                entries.add(routePrivacy.displayNodeName(nodeId, entries.size() + 1) + "=" + (available ? "OK" : "DOWN") + storageMetricSuffix(object));
                if (!available) {
                    unavailable++;
                }
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty()
            ? "Storage status: registered node 없음"
            : "Storage status: " + String.join(", ", entries) + " / unavailable=" + unavailable;
    }

    private String storageMetricSuffix(String nodeObject) {
        String storage = objectValue(nodeObject, "storage");
        if (storage.isBlank()) {
            return "";
        }
        long failures = longValue(storage, "healthCheckFailures")
            + longValue(storage, "uploadFailures")
            + longValue(storage, "downloadFailures")
            + longValue(storage, "operationFailures");
        String backend = fallback(jsonValue(storage, "backend"), "unknown");
        boolean primaryDegraded = boolValue(storage, "primaryDegraded");
        return "(backend=" + backend
            + ", primaryDegraded=" + primaryDegraded
            + ", failures=" + failures
            + ", up=" + seconds(doubleValue(storage, "uploadSeconds")) + "s"
            + ", down=" + seconds(doubleValue(storage, "downloadSeconds")) + "s)";
    }

    private String nodeListSummaryMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        if (nodes.isBlank()) {
            return "Nodes: empty";
        }
        int total = 0;
        int starting = 0;
        int warming = 0;
        int ready = 0;
        int softFull = 0;
        int hardFull = 0;
        int draining = 0;
        int shuttingDown = 0;
        int down = 0;
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < nodes.length()) {
            int objectStart = nodes.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(nodes, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = nodes.substring(objectStart, objectEnd + 1);
            String state = jsonValue(object, "state");
            total++;
            if (state.equalsIgnoreCase("STARTING")) {
                starting++;
            } else if (state.equalsIgnoreCase("WARMING")) {
                warming++;
            } else if (state.equalsIgnoreCase("READY")) {
                ready++;
            } else if (state.equalsIgnoreCase("SOFT_FULL")) {
                softFull++;
            } else if (state.equalsIgnoreCase("HARD_FULL")) {
                hardFull++;
            } else if (state.equalsIgnoreCase("DRAINING")) {
                draining++;
            } else if (state.equalsIgnoreCase("SHUTTING_DOWN")) {
                shuttingDown++;
            } else if (state.equalsIgnoreCase("DOWN")) {
                down++;
            }
            if (entries.size() < 10) {
                entries.add(nodeSummary(object, entries.size() + 1));
            }
            index = objectEnd + 1;
        }
        return "Nodes: total=" + total
            + " starting=" + starting
            + " warming=" + warming
            + " ready=" + ready
            + " softFull=" + softFull
            + " hardFull=" + hardFull
            + " draining=" + draining
            + " shuttingDown=" + shuttingDown
            + " down=" + down
            + poolSummarySuffix(body)
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String poolSummarySuffix(String body) {
        String pools = arrayValue(body, "pools");
        if (pools.isBlank()) {
            return "";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < pools.length()) {
            int objectStart = pools.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(pools, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = pools.substring(objectStart, objectEnd + 1);
            String pool = fallback(jsonValue(object, "pool"), "island");
            entries.add(pool
                + " nodes=" + longValue(object, "healthyNodeCount") + "/" + longValue(object, "nodeCount")
                + " players=" + longValue(object, "players") + "/" + longValue(object, "softPlayerCap") + "/" + longValue(object, "hardPlayerCap")
                + " reserved=" + longValue(object, "reservedSlots")
                + " islands=" + longValue(object, "activeIslands") + "/" + longValue(object, "maxActiveIslands")
                + " queue=" + longValue(object, "activationQueue") + "/" + longValue(object, "maxActivationQueue"));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "" : " / pools: " + String.join(" | ", entries);
    }

    private String nodeSummary(String object, int displayIndex) {
        String id = jsonValue(object, "id");
        String state = jsonValue(object, "state");
        long players = longValue(object, "players");
        long softCap = longValue(object, "softPlayerCap");
        long hardCap = longValue(object, "hardPlayerCap");
        long reservedSlots = longValue(object, "reservedSlots");
        long activeIslands = longValue(object, "activeIslands");
        long maxActiveIslands = longValue(object, "maxActiveIslands");
        long activationQueue = longValue(object, "activationQueue");
        long maxActivationQueue = longValue(object, "maxActivationQueue");
        boolean activationEligible = boolValue(object, "eligibleForNewActivation");
        String allocationBlockReason = jsonValue(object, "allocationBlockReason");
        String displayNode = id.isBlank() ? "node-" + displayIndex : id;
        return displayNode
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + " players=" + players + "/" + softCap + "/" + hardCap + " reserved=" + reservedSlots
            + " islands=" + activeIslands + "/" + maxActiveIslands
            + " queue=" + activationQueue + "/" + maxActivationQueue
            + " mspt=" + seconds(doubleValue(object, "mspt"))
            + " score=" + seconds(doubleValue(object, "score"))
            + scoreParts(object)
            + " activation=" + (activationEligible ? "ok" : "blocked:" + (allocationBlockReason.isBlank() ? "UNKNOWN" : allocationBlockReason))
            + " storage=" + (boolValue(object, "storageAvailable") ? "ok" : "down");
    }

    private String scoreParts(String nodeObject) {
        String breakdown = objectValue(nodeObject, "scoreBreakdown");
        if (breakdown.isBlank()) {
            return "";
        }
        return " parts=p:" + seconds(doubleValue(breakdown, "playerPressure"))
            + ",a:" + seconds(doubleValue(breakdown, "activeIslandPressure"))
            + ",m:" + seconds(doubleValue(breakdown, "msptPressure"))
            + ",q:" + seconds(doubleValue(breakdown, "activationQueuePressure"))
            + ",mem:" + seconds(doubleValue(breakdown, "memoryPressure"))
            + ",fail:" + seconds(scorePartValue(breakdown, "recentFailurePressure", "recentFailurePenalty"));
    }

    private double scorePartValue(String breakdown, String primaryKey, String fallbackKey) {
        double primary = doubleValue(breakdown, primaryKey);
        if (primary != 0.0D || breakdown.contains("\"" + primaryKey + "\"")) {
            return primary;
        }
        return doubleValue(breakdown, fallbackKey);
    }

    private String nodeActionSummaryMessage(String label, String nodeId, String body) {
        String displayNode = nodeId == null || nodeId.isBlank() ? "target-node" : nodeId;
        if (body == null || body.isBlank()) {
            return label + ": accepted" + routePrivacy.routeNodeSuffix(displayNode);
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return label + ": " + (boolValue(body, "accepted") ? "accepted" : "rejected") + routePrivacy.routeNodeSuffix(displayNode) + " code=" + code;
        }
        return label + ": " + (boolValue(body, "accepted") ? "accepted" : "requested") + routePrivacy.routeNodeSuffix(displayNode);
    }

    private String nodeSweepMessage(String body) {
        String nodes = arrayValue(body, "nodes");
        long recoveryRequired = longValue(body, "recoveryRequired");
        java.util.List<String> swept = new java.util.ArrayList<>();
        int index = 0;
        while (index < nodes.length()) {
            int valueStart = nodes.indexOf('"', index);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = nodes.indexOf('"', valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            String nodeId = nodes.substring(valueStart + 1, valueEnd);
            swept.add(routePrivacy.displayNodeName(nodeId, swept.size() + 1));
            index = valueEnd + 1;
        }
        return "Node sweep: nodes=" + (swept.isEmpty() ? "none" : String.join(",", swept)) + " recoveryRequired=" + recoveryRequired;
    }

    private String jobListMessage(String body) {
        String jobs = arrayValue(body, "jobs");
        if (jobs.isBlank()) {
            return "Jobs: empty";
        }
        int pending = 0;
        int claimed = 0;
        int failed = 0;
        int done = 0;
        int other = 0;
        int total = 0;
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < jobs.length()) {
            int objectStart = jobs.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(jobs, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = jobs.substring(objectStart, objectEnd + 1);
            String state = jsonValue(object, "state");
            total++;
            if (state.equalsIgnoreCase("PENDING")) {
                pending++;
            } else if (state.equalsIgnoreCase("CLAIMED")) {
                claimed++;
            } else if (state.equalsIgnoreCase("FAILED")) {
                failed++;
            } else if (state.equalsIgnoreCase("DONE") || state.equalsIgnoreCase("COMPLETED")) {
                done++;
            } else {
                other++;
            }
            if (entries.size() < 10) {
                entries.add(jobSummary(object));
            }
            index = objectEnd + 1;
        }
        return "Jobs: total=" + total
            + " pending=" + pending
            + " claimed=" + claimed
            + " failed=" + failed
            + " done=" + done
            + " other=" + other
            + (entries.isEmpty() ? "" : " / " + String.join(" | ", entries));
    }

    private String jobSummary(String object) {
        String id = jsonValue(object, "id");
        String type = jsonValue(object, "type");
        String state = jsonValue(object, "state");
        String targetNode = jsonValue(object, "targetNode");
        long attempts = longValue(object, "attempts");
        String error = jsonValue(object, "error");
        String shortId = id.length() > 8 ? id.substring(0, 8) : id;
        StringBuilder builder = new StringBuilder(shortId.isBlank() ? "job" : shortId)
            .append(' ')
            .append(type.isBlank() ? "UNKNOWN" : type)
            .append(' ')
            .append(state.isBlank() ? "UNKNOWN" : state)
            .append(" attempts=")
            .append(attempts);
        if (!targetNode.isBlank()) {
            builder.append(routePrivacy.routeNodeSuffix(targetNode));
        }
        if (!error.isBlank()) {
            builder.append(" error=").append(error);
        }
        return builder.toString();
    }

    private String jobActionMessage(String action, String body) {
        if (body == null || body.isBlank()) {
            return "Job " + action + ": no response";
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Job " + action + ": failed code=" + code;
        }
        if (body.contains("\"recovered\"")) {
            String recoveredText = jsonValue(body, "recovered");
            long recoveredNumber = longValue(body, "recovered");
            return "Job recover: recovered=" + (recoveredText.isBlank() ? Long.toString(recoveredNumber) : recoveredText);
        }
        return "Job " + action + ": " + (boolValue(body, "ok") ? "accepted" : "not applied");
    }

    private String routeDebugMessage(String body) {
        return playerMessage(routeMessages.debug(body));
    }

    private String routeTicketMessage(String body) {
        return playerMessage(routeMessages.ticket(body));
    }

    private String routeClearMessage(String body) {
        return playerMessage(routeMessages.clear(body));
    }

    private String snapshotListMessage(String body) {
        String snapshots = arrayValue(body, "snapshots");
        if (snapshots.isBlank()) {
            return "섬 스냅샷이 없습니다.";
        }
        java.util.List<String> entries = new java.util.ArrayList<>();
        int index = 0;
        while (index < snapshots.length() && entries.size() < 20) {
            int objectStart = snapshots.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(snapshots, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = snapshots.substring(objectStart, objectEnd + 1);
            long snapshotNo = longValue(object, "snapshotNo");
            if (snapshotNo > 0L) {
                String reason = jsonValue(object, "reason");
                long sizeBytes = longValue(object, "sizeBytes");
                String createdAt = jsonValue(object, "createdAt");
                String checksum = jsonValue(object, "checksum");
                entries.add("#" + snapshotNo
                    + (reason.isBlank() ? "" : " 사유=" + reason)
                    + " 크기=" + sizeBytes
                    + (checksum.isBlank() ? "" : " checksum=" + shortChecksum(checksum))
                    + (createdAt.isBlank() ? "" : " 생성=" + createdAt));
            }
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "섬 스냅샷이 없습니다." : "섬 스냅샷: " + String.join(" | ", entries);
    }

    private String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    private String shortChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return "";
        }
        return checksum.length() > 12 ? checksum.substring(0, 12) : checksum;
    }

    private void appendLongSummary(StringBuilder summary, String label, long value) {
        if (value > 0L) {
            summary.append(", ").append(label).append('=').append(value);
        }
    }

    private String seconds(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void sendInviteActionResult(Player player, CompletableFuture<String> future, String successMessage, String failureMessage) {
        future.thenAccept(body -> {
            if (body == null || body.isBlank() || body.contains("\"error\"") || body.contains("\"accepted\":false")) {
                player.sendMessage(Component.text(failureMessage));
                return;
            }
            player.sendMessage(Component.text(successMessage));
        }).exceptionally(error -> {
            player.sendMessage(Component.text(failureMessage));
            return null;
        });
    }

    private void adminIslandTarget(Player player, String target, Consumer<UUID> action) {
        targetResolver.resolveIslandId(target).thenAccept(islandId -> {
            if (islandId.equals(new UUID(0L, 0L))) {
                player.sendMessage(Component.text("섬을 찾지 못했습니다."));
                return;
            }
            action.accept(islandId);
        }).exceptionally(error -> {
            player.sendMessage(Component.text("섬을 찾지 못했습니다."));
            return null;
        });
    }
    private String playerPayloadMessage(String body, String emptyMessage, String successMessage) {
        if (body == null || body.isBlank()) {
            return emptyMessage;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{\"error\"")) {
            String code = jsonValue(trimmed, "code");
            return playerErrorMessage(code, emptyMessage);
        }
        if (trimmed.startsWith("{") && trimmed.contains("\"accepted\":false")) {
            String code = jsonValue(trimmed, "code");
            return playerErrorMessage(code, emptyMessage);
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return successMessage;
        }
        return trimmed;
    }

    private String playerErrorMessage(String code, String fallback) {
        return kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy.playerMessage(code, fallback);
    }

    private boolean internalRouteCapacityCode(String code) {
        return kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy.capacityCode(code);
    }

    private boolean internalRouteMaintenanceCode(String code) {
        return kr.lunaf.cloudislands.protocol.route.RouteFailureMessagePolicy.maintenanceCode(code);
    }

    private void route(Player player, RouteTicket ticket, String failureMessage) {
        metrics.routeAttempt();
        if (ticket == null) {
            fallbackService.transfer(player, failureMessage);
            return;
        }
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        if (ticket.state().name().equals("PREPARING")) {
            String target = routeTargetName(ticket);
            progressPresenter.actionBar(player, messages.text("route-preparing", "target", target));
            BossBar bossBar = progressPresenter.loadingBossBar(messages.text("route-loading-title", "target", target));
            progressPresenter.showBossBar(player, bossBar);
            waitForReadyTicket(player, ticket, failureMessage, bossBar, 0);
            return;
        }
        publishAndConnect(player, ticket);
    }

    private void routeFuture(Player player, CompletableFuture<RouteTicket> ticketFuture, String failureMessage) {
        ticketFuture.thenAccept(ticket -> route(player, ticket, failureMessage)).exceptionally(error -> {
            fallbackService.transfer(player, routeFailureMessage(error, failureMessage), routeFailureCode(error, "ROUTE_FAILED"));
            return null;
        });
    }

    private String routeFailureMessage(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return playerErrorMessage(coreError.code(), fallback);
            }
            if (current instanceof java.io.IOException) {
                return messages.text("island-service-maintenance");
            }
            current = current.getCause();
        }
        return fallback;
    }

    private boolean allowRouteRequest(Player player) {
        if (routeRequestGuard.allow(player.getUniqueId())) {
            return true;
        }
        player.sendMessage(Component.text("섬 이동 요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요."));
        return false;
    }

    private void waitForReadyTicket(Player player, RouteTicket ticket, String failureMessage, BossBar bossBar, int attempt) {
        if (!fallbackService.playerOnline(player)) {
            progressPresenter.hideBossBar(player, bossBar);
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        String target = routeTargetName(ticket);
        String progressValue = RouteProgressPresenter.progressValue(attempt);
        progressPresenter.preparing(player, bossBar, messages.text("route-loading-progress", "target", target, "progress", progressValue), messages.text("route-preparing-progress", "target", target, "progress", progressValue), attempt);
        coreApiClient.routeTicketStatus(ticket.ticketId(), ticket.playerUuid(), ticket.nonce()).thenAccept(status -> {
            Optional<RouteTicket> ready = status.filter(value -> value.state().name().equals("READY"));
            if (ready.isPresent()) {
                String readyTarget = routeTargetName(ready.get());
                progressPresenter.ready(player, bossBar, messages.text("route-ready", "target", readyTarget));
                progressPresenter.hideBossBar(player, bossBar);
                publishAndConnect(player, ready.get());
                return;
            }
            if (status.isPresent() && terminalRouteState(status.get())) {
                progressPresenter.hideBossBar(player, bossBar);
                fallbackService.transfer(player, terminalRouteMessage(status.get(), failureMessage));
                return;
            }
            if (attempt >= routeWaitSeconds) {
                progressPresenter.hideBossBar(player, bossBar);
                clearFailedRoute(ticket, "ROUTE_READY_TIMEOUT");
                fallbackService.transfer(player, failureMessage, "ROUTE_READY_TIMEOUT");
                return;
            }
            CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS).execute(() -> waitForReadyTicket(player, ticket, failureMessage, bossBar, attempt + 1));
        }).exceptionally(error -> {
            progressPresenter.hideBossBar(player, bossBar);
            clearFailedRoute(ticket, "ROUTE_STATUS_FAILED");
            fallbackService.transfer(player, routeFailureMessage(error, failureMessage), routeFailureCode(error, "ROUTE_STATUS_FAILED"));
            return null;
        });
    }

    private boolean terminalRouteState(RouteTicket ticket) {
        if (ticket == null || ticket.state() == null) {
            return false;
        }
        return switch (ticket.state().name()) {
            case "FAILED", "EXPIRED", "CANCELLED", "CONSUMED" -> true;
            default -> false;
        };
    }

    private String terminalRouteMessage(RouteTicket ticket, String fallback) {
        String state = ticket == null || ticket.state() == null ? "" : ticket.state().name();
        if ("EXPIRED".equals(state)) {
            return "섬 이동 준비 시간이 만료되었습니다. 다시 시도해주세요.";
        }
        if ("CANCELLED".equals(state)) {
            return "섬 이동이 취소되었습니다.";
        }
        if ("CONSUMED".equals(state)) {
            return "이미 사용된 섬 이동 요청입니다. 다시 시도해주세요.";
        }
        String reason = ticket == null ? "" : ticket.payload().getOrDefault("failureReason", "");
        if (!reason.isBlank()) {
            return playerErrorMessage(reason, fallback);
        }
        return fallback;
    }

    private void publishAndConnect(Player player, RouteTicket ticket) {
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        coreApiClient.publishRouteSession(ticket).thenRun(() -> {
            String targetServerName = ticket.payload().getOrDefault("targetServerName", ticket.targetNode());
            connectWithTicket(player, ticket, targetServerName);
        }).exceptionally(error -> {
            clearFailedRoute(ticket, "SESSION_PUBLISH_FAILED");
            fallbackService.transfer(player, "섬 이동 정보를 준비하지 못했습니다. 로비로 이동합니다.");
            return null;
        });
    }

    private void connectWithTicket(Player player, RouteTicket ticket, String targetServerName) {
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        RegisteredServer server = fallbackService.findServer(targetServerName);
        if (server == null) {
            clearFailedRoute(ticket, "TARGET_SERVER_NOT_FOUND");
            fallbackService.transfer(player, "섬 이동 경로를 찾을 수 없습니다.", "TARGET_SERVER_NOT_FOUND");
            return;
        }
        connect(player, ticket, server);
    }

    private void connect(Player player, RouteTicket ticket, RegisteredServer server) {
        if (!fallbackService.playerOnline(player)) {
            clearFailedRoute(ticket, "PLAYER_DISCONNECTED");
            return;
        }
        player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
            if (!success) {
                clearFailedRoute(ticket, "CONNECT_FAILED");
                fallbackService.transfer(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.", "CONNECT_FAILED");
                return;
            }
            metrics.routeSuccess();
            progressPresenter.actionBar(player, arrivalMessage(ticket));
        }).exceptionally(error -> {
            clearFailedRoute(ticket, "CONNECT_EXCEPTION");
            fallbackService.transfer(player, "섬으로 이동하지 못했습니다. 로비로 이동합니다.", "CONNECT_EXCEPTION");
            return null;
        });
    }

    private void clearFailedRoute(RouteTicket ticket, String reason) {
        if (ticket == null) {
            return;
        }
        coreApiClient.clearRoute(ticket.playerUuid(), ticket.ticketId(), reason == null || reason.isBlank() ? "ROUTE_FAILED" : reason).exceptionally(error -> null);
    }

    private String routeFailureCode(Throwable error, String fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof CoreApiException coreError) {
                return RouteFallbackService.safeFailureCode(coreError.code(), fallback);
            }
            if (current instanceof java.io.IOException) {
                return "CORE_API_IO";
            }
            current = current.getCause();
        }
        return RouteFallbackService.safeFailureCode(fallback, "ROUTE_FAILED");
    }

    private String routeTargetName(RouteTicket ticket) {
        if (ticket == null) {
            return "섬";
        }
        return routeTargetName(PlayerRouteTicketView.from(ticket).destination());
    }

    private String routeTargetName(String destination) {
        return switch (destination == null ? "" : destination.toLowerCase(Locale.ROOT)) {
            case "my-island" -> "내 섬";
            case "other-island" -> "다른 사람 섬";
            case "island-ranking" -> "섬 랭킹";
            case "island-visit" -> "방문할 섬";
            case "island-settings" -> "섬 설정";
            case "island-warps" -> "섬 워프";
            default -> "섬";
        };
    }

    private Component playerComponent(String message) {
        return Component.text(playerMessage(message));
    }

    private String playerMessage(String message) {
        String value = message == null || message.isBlank() ? "섬 이동을 처리하지 못했습니다." : message;
        if (!hideNodeNames) {
            return value;
        }
        return kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy.sanitize(value);
    }

    private String arrivalMessage(RouteTicket ticket) {
        if (ticket == null) {
            return "섬에 도착했습니다.";
        }
        return switch (PlayerRouteTicketView.from(ticket).destination()) {
            case "my-island" -> "내 섬에 도착했습니다.";
            case "other-island" -> "다른 사람 섬에 도착했습니다.";
            case "island-ranking" -> "섬 랭킹을 열었습니다.";
            case "island-visit" -> "방문한 섬에 도착했습니다.";
            case "island-settings" -> "섬 설정을 열었습니다.";
            case "island-warps" -> "섬 워프에 도착했습니다.";
            default -> "섬에 도착했습니다.";
        };
    }

    private int moveNodePlayersToFallback(String nodeId) {
        return fallbackService.moveNodePlayersToFallback(nodeId);
    }

    private String messageForCreateFailure(String code) {
        if (code != null && internalRouteCapacityCode(code)) {
            return messages.text("island-create-node-unavailable");
        }
        if (code != null && internalRouteMaintenanceCode(code)) {
            return messages.text("island-service-maintenance");
        }
        return switch (code) {
            case "ALREADY_HAS_ISLAND" -> messages.text("island-create-already-has-island");
            case "TEMPLATE_UNAVAILABLE" -> messages.text("island-create-template-unavailable");
            case "CREATE_LOCKED" -> messages.text("island-create-locked");
            case "NODE_UNAVAILABLE" -> messages.text("island-create-node-unavailable");
            case "JOB_QUEUE_UNAVAILABLE", "RECOVERY_UNAVAILABLE" -> messages.text("island-service-maintenance");
            default -> messages.text("island-create-failed");
        };
    }
}
