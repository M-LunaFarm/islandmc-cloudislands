package kr.lunaf.cloudislands.velocity.bootstrap;

import com.velocitypowered.api.proxy.ProxyServer;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import kr.lunaf.cloudislands.velocity.config.VelocityConfigLoader;
import kr.lunaf.cloudislands.velocity.health.VelocityHealthService;
import kr.lunaf.cloudislands.velocity.health.VelocityStatusReporter;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.security.PluginMessageFirewall;
import org.slf4j.Logger;

public final class VelocityRuntimeFactory {
    private VelocityRuntimeFactory() {
    }

    public static VelocityRuntimeServices create(ProxyServer proxy, Logger logger, Path dataDirectory) {
        VelocityConfig config = VelocityConfigLoader.load(dataDirectory, logger);
        String coreUrl = System.getProperty("cloudislands.core", config.coreBaseUrl());
        String coreToken = System.getenv().getOrDefault("CI_CORE_TOKEN", config.coreToken());
        String adminToken = System.getenv().getOrDefault("CI_ADMIN_TOKEN", config.adminToken());
        long timeoutMs = Long.getLong("cloudislands.timeoutMs", config.timeoutMs());
        String fallbackServer = System.getProperty("cloudislands.fallback", config.fallbackServer());
        int routeWaitSeconds = Integer.getInteger("cloudislands.routeWaitSeconds", config.routeWaitSeconds());
        String islandPool = System.getProperty("cloudislands.islandPool", config.islandPool());
        int routeTicketTtlSeconds = Integer.getInteger("cloudislands.routeTicketTtlSeconds", config.routeTicketTtlSeconds());

        logSecurityPosture(logger, config, coreToken, adminToken);
        CoreApiClient client = new JdkCoreApiClient(URI.create(coreUrl), coreToken, adminToken, Duration.ofMillis(Math.max(1L, timeoutMs)));
        VelocityMessages messages = VelocityMessages.from(config.language(), config.messages());
        VelocityRoutingController routingController = new VelocityRoutingController(
            proxy,
            client,
            fallbackServer,
            routeWaitSeconds,
            config.useActionBar(),
            config.useBossBarLoading(),
            config.hideNodeNames(),
            islandPool,
            routeTicketTtlSeconds,
            messages
        );
        PluginMessageFirewall pluginMessageFirewall = new PluginMessageFirewall();
        VelocityStatusReporter statusReporter = new VelocityStatusReporter(proxy, config, config.aliases(), routingController, pluginMessageFirewall);
        VelocityHealthService healthService = new VelocityHealthService(
            logger,
            config.healthBindHost(),
            config.healthPort(),
            statusReporter::healthJson,
            statusReporter::metricsText
        );

        return new VelocityRuntimeServices(
            config,
            messages,
            routingController,
            config.aliases(),
            statusReporter,
            healthService,
            pluginMessageFirewall
        );
    }

    private static void logSecurityPosture(Logger logger, VelocityConfig config, String coreToken, String adminToken) {
        if (config.requireModernForwarding() && config.forwardingSecret().isBlank()) {
            logger.warn("CloudIslands security: Velocity modern forwarding is required but security.forwarding-secret is empty");
        }
        if (coreToken == null || coreToken.isBlank()) {
            logger.warn("CloudIslands security: core-api auth token is empty");
        }
        if (adminToken == null || adminToken.isBlank()) {
            logger.warn("CloudIslands security: admin token is empty, admin Core requests will be rejected");
        }
        if (!config.blockCloudIslandsPluginMessages()) {
            logger.warn("CloudIslands security: cloudislands plugin messages are not blocked at Velocity");
        }
    }
}
