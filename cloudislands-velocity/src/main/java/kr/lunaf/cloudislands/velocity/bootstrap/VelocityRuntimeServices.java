package kr.lunaf.cloudislands.velocity.bootstrap;

import java.util.List;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import kr.lunaf.cloudislands.velocity.health.VelocityHealthService;
import kr.lunaf.cloudislands.velocity.health.VelocityStatusReporter;
import kr.lunaf.cloudislands.velocity.message.VelocityMessages;
import kr.lunaf.cloudislands.velocity.security.PluginMessageFirewall;

public record VelocityRuntimeServices(
    VelocityConfig config,
    VelocityMessages messages,
    VelocityRoutingController routingController,
    List<String> commandAliases,
    VelocityStatusReporter statusReporter,
    VelocityHealthService healthService,
    PluginMessageFirewall pluginMessageFirewall
) {
}
