package kr.lunaf.cloudislands.paper.bootstrap;

import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.AgentRole;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperPlugin;
import kr.lunaf.cloudislands.paper.RouteTicketConsumer;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.security.ProxySourceAllowlist;
import kr.lunaf.cloudislands.paper.session.PaperRouteSessionListener;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;

public final class PaperRouteSessionRuntimeFactory {
    private PaperRouteSessionRuntimeFactory() {
    }

    public static Runtime create(
        CloudIslandsPaperPlugin plugin,
        CoreApiClient client,
        RouteTicketConsumer routeTickets,
        PaperRuntimeConfig config,
        MessageRenderer messages,
        PlayerLocaleCache locales
    ) {
        PaperRuntimeConfig safeConfig = config == null ? PaperRuntimeConfig.defaults() : config;
        AgentRole role = safeConfig.node().role();
        boolean islandNode = role == AgentRole.ISLAND_NODE;
        boolean enforceRouteSession = islandNode && safeConfig.security().enforceRouteSession();
        boolean requireRouteSession = islandNode && (safeConfig.security().requireRouteSession() || enforceRouteSession);
        boolean forwardingReady = !islandNode
            || !safeConfig.security().requireVelocityForwarding()
            || !safeConfig.security().forwardingSecret().isBlank();
        boolean requireProxySourceAllowlist = islandNode && safeConfig.security().requireProxySourceAllowlist();
        ProxySourceAllowlist allowlist = new ProxySourceAllowlist(safeConfig.security().proxySourceAllowlist());
        PaperRouteSessionListener listener = new PaperRouteSessionListener(
            plugin,
            client,
            routeTickets,
            safeConfig.node().id(),
            requireRouteSession,
            forwardingReady,
            requireProxySourceAllowlist,
            safeConfig.routing().fallbackServerName(),
            allowlist,
            messages,
            locales
        );
        return new Runtime(allowlist, listener);
    }

    public record Runtime(ProxySourceAllowlist proxySourceAllowlist, PaperRouteSessionListener listener) {
    }
}
