package kr.lunaf.cloudislands.velocity;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.UUID;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.velocity.event.CoreEventCodec;
import kr.lunaf.cloudislands.velocity.event.CoreEventEnvelope;
import kr.lunaf.cloudislands.velocity.event.CoreEventJsonCodec;
import kr.lunaf.cloudislands.velocity.event.CoreNodeStateEventHandler;
import kr.lunaf.cloudislands.velocity.event.CoreEventPoller;
import kr.lunaf.cloudislands.velocity.message.VelocityRoutePrivacyFormatter;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.metrics.VelocityRoutingMetrics;
import kr.lunaf.cloudislands.velocity.platform.VelocityServerGateway;
import kr.lunaf.cloudislands.velocity.routing.PendingRouteService;
import kr.lunaf.cloudislands.velocity.routing.RouteFallbackService;
import kr.lunaf.cloudislands.velocity.routing.RouteProgressPresenter;
import kr.lunaf.cloudislands.velocity.routing.RouteRequestGuard;
import kr.lunaf.cloudislands.velocity.routing.RouteTicketRouter;
import kr.lunaf.cloudislands.velocity.routing.VelocityTargetResolver;
import net.kyori.adventure.text.Component;

public final class VelocityRoutingController {
    private static final int EVENT_BATCH_SIZE = 512;
    private static final long PLAYER_ROUTE_COOLDOWN_MILLIS = 1_500L;

    private final ProxyServer proxy;
    private final String fallbackServer;
    private final int routeWaitSeconds;
    private final String islandPool;
    private final int routeTicketTtlSeconds;
    private final boolean hideNodeNames;
    private final boolean useActionBar;
    private final boolean useBossBarLoading;
    private final VelocityMessages messages;
    private final CoreEventCodec eventCodec;
    private final CoreNodeStateEventHandler nodeStateEvents;
    private final CoreEventPoller eventPoller;
    private final VelocityRoutingMetrics metrics = new VelocityRoutingMetrics();
    private final VelocityServerGateway servers;
    private final RouteFallbackService fallbackService;
    private final VelocityRoutingActions actions;
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
        this.fallbackServer = fallbackServer;
        this.routeWaitSeconds = Math.max(1, routeWaitSeconds);
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
        this.routeTicketTtlSeconds = Math.max(1, routeTicketTtlSeconds);
        this.hideNodeNames = hideNodeNames;
        this.useActionBar = useActionBar;
        this.useBossBarLoading = useBossBarLoading;
        this.messages = messages == null ? VelocityMessages.defaults() : messages;
        VelocityRoutePrivacyFormatter routePrivacy = new VelocityRoutePrivacyFormatter(hideNodeNames);
        this.eventCodec = eventCodec == null ? new CoreEventJsonCodec() : eventCodec;
        this.servers = new VelocityServerGateway(proxy, this.islandPool, hideNodeNames);
        this.fallbackService = new RouteFallbackService(proxy, fallbackServer, metrics, this::playerComponent);
        this.nodeStateEvents = new CoreNodeStateEventHandler(nodeId -> fallbackService.moveNodePlayersToFallback(nodeId));
        this.eventPoller = new CoreEventPoller(coreApiClient, this.eventCodec, this::handleCoreEvent, EVENT_BATCH_SIZE);
        RouteProgressPresenter progressPresenter = new RouteProgressPresenter(useActionBar, useBossBarLoading, this::playerComponent);
        RouteRequestGuard routeRequestGuard = new RouteRequestGuard(PLAYER_ROUTE_COOLDOWN_MILLIS);
        RouteTicketRouter routeTickets = new RouteTicketRouter(coreApiClient, this.routeWaitSeconds, this.messages, metrics, fallbackService, progressPresenter);
        VelocityTargetResolver targetResolver = new VelocityTargetResolver(coreApiClient, name -> proxy.getPlayer(name).map(Player::getUniqueId));
        PendingRouteService pendingRoutes = new PendingRouteService(coreApiClient, fallbackService, metrics, this::playerComponent);
        this.actions = new VelocityRoutingActions(coreApiClient, hideNodeNames, this.messages, routePrivacy, fallbackService, progressPresenter, routeRequestGuard, routeTickets, targetResolver, pendingRoutes, this::statusSummary);
    }

    public VelocityRoutingActions actions() {
        return actions;
    }

    public void recordPlayerProfile(Player player) {
        actions.playerRouting().recordPlayerProfile(player);
    }

    public void routePendingSession(Player player) {
        actions.playerRouting().routePendingSession(player);
    }

    public void clearPlayerState(UUID playerUuid) {
        actions.playerRouting().clearPlayerState(playerUuid);
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

    private String routeClearMessage(String body) {
        return actions.routeClearMessage(body);
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

    private void handleCoreEvent(CoreEventEnvelope event) {
        nodeStateEvents.handle(event);
    }

}
