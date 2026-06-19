package kr.lunaf.cloudislands.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.List;
import kr.lunaf.cloudislands.velocity.bootstrap.VelocityRuntimeFactory;
import kr.lunaf.cloudislands.velocity.bootstrap.VelocityRuntimeServices;
import kr.lunaf.cloudislands.velocity.command.VelocityCommandDispatcher;
import kr.lunaf.cloudislands.velocity.command.VelocityCommandRegistrar;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import kr.lunaf.cloudislands.velocity.health.VelocityHealthService;
import kr.lunaf.cloudislands.velocity.health.VelocityStatusReporter;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.security.PluginMessageFirewall;
import org.slf4j.Logger;

@Plugin(id = "cloudislands", name = "CloudIslands", version = BuildInfo.VERSION, description = "Portable island routing and management", authors = {"LeeSeungmin"})
public final class CloudIslandsVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final VelocityRoutingController routingController;
    private final List<String> commandAliases;
    private final VelocityConfig config;
    private final VelocityHealthService healthService;
    private final VelocityStatusReporter statusReporter;
    private final VelocityMessages messages;
    private final VelocityCommandDispatcher commandDispatcher;
    private final PluginMessageFirewall pluginMessageFirewall;

    @Inject
    public CloudIslandsVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        VelocityRuntimeServices services = VelocityRuntimeFactory.create(proxy, logger, dataDirectory);
        this.config = services.config();
        this.messages = services.messages();
        this.routingController = services.routingController();
        this.commandAliases = services.commandAliases();
        this.statusReporter = services.statusReporter();
        this.healthService = services.healthService();
        this.pluginMessageFirewall = services.pluginMessageFirewall();
        this.commandDispatcher = new VelocityCommandDispatcher(proxy, routingController, config);
        if (config.debug()) {
            logger.info("CloudIslands Velocity config loaded: language={}, aliases={}, health={}:{}", config.language(), commandAliases, config.healthBindHost(), config.healthPort());
        }
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        new VelocityCommandRegistrar(messages).register(
            proxy.getCommandManager(),
            commandAliases,
            invocation -> commandDispatcher.dispatch(invocation.player(), invocation.arguments()),
            commandDispatcher::playerSuggestions,
            commandDispatcher::hasAdminAccess,
            invocation -> commandDispatcher.dispatchAdmin(invocation.player(), invocation.arguments()),
            commandDispatcher::adminSuggestions
        );
        routingController.startEventPolling(this);
        if (config.healthEnabled()) {
            healthService.start();
        }
        warnIfFallbackServerMissing();
        logger.info("CloudIslands Velocity router enabled with aliases {}", commandAliases);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        routingController.stopEventPolling();
        healthService.stop();
    }

    private void warnIfFallbackServerMissing() {
        if (config.fallbackServer() == null || config.fallbackServer().isBlank()) {
            logger.warn("CloudIslands routing fallback server is empty; failed island routes will not have a lobby fallback");
            return;
        }
        if (proxy.getServer(config.fallbackServer()).isEmpty()) {
            logger.warn("CloudIslands routing fallback server '{}' is not registered in Velocity", config.fallbackServer());
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        pluginMessageFirewall.handle(event);
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        routingController.recordPlayerProfile(event.getPlayer());
        routingController.routePendingSession(event.getPlayer());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        routingController.clearPlayerState(event.getPlayer().getUniqueId());
    }

}
