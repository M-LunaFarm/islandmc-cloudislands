package kr.lunaf.cloudislands.velocity.health;

import com.velocitypowered.api.proxy.ProxyServer;
import java.util.List;
import kr.lunaf.cloudislands.common.security.BackendAccessPolicy;
import kr.lunaf.cloudislands.velocity.VelocityRoutingController;
import kr.lunaf.cloudislands.velocity.config.VelocityConfig;
import kr.lunaf.cloudislands.velocity.security.PluginMessageFirewall;

public final class VelocityStatusReporter {
    private final ProxyServer proxy;
    private final VelocityConfig config;
    private final List<String> commandAliases;
    private final VelocityRoutingController routingController;
    private final PluginMessageFirewall pluginMessageFirewall;

    public VelocityStatusReporter(
            ProxyServer proxy,
            VelocityConfig config,
            List<String> commandAliases,
            VelocityRoutingController routingController,
            PluginMessageFirewall pluginMessageFirewall) {
        this.proxy = proxy;
        this.config = config;
        this.commandAliases = commandAliases;
        this.routingController = routingController;
        this.pluginMessageFirewall = pluginMessageFirewall;
    }

    public String healthJson() {
        return "{"
            + "\"status\":\"UP\","
            + "\"onlinePlayers\":" + proxy.getPlayerCount() + ","
            + "\"registeredServers\":" + proxy.getAllServers().size() + ","
            + "\"language\":\"" + escapeJson(config.language()) + "\","
            + "\"debug\":" + config.debug() + ","
            + "\"backendAccessPolicy\":\"" + BackendAccessPolicy.CONTRACT + "\","
            + "\"modernForwardingModePolicy\":\"" + BackendAccessPolicy.MODERN_FORWARDING_POLICY + "\","
            + "\"modernForwardingRequired\":" + config.requireModernForwarding() + ","
            + "\"forwardingSecretConfigured\":" + !config.forwardingSecret().isBlank() + ","
            + "\"forwardingSecretPolicy\":\"" + BackendAccessPolicy.FORWARDING_SECRET_POLICY + "\","
            + "\"backendPaperAccessPolicy\":\"" + BackendAccessPolicy.PAPER_DIRECT_ACCESS_POLICY + "\","
            + "\"backendInfrastructurePolicy\":\"" + BackendAccessPolicy.INFRASTRUCTURE_EXPOSURE_POLICY + "\","
            + "\"setupDatabaseMode\":\"CORE_API\","
            + "\"setupDatabasePolicy\":\"velocity-delegates-all-persistent-writes-to-core-api\","
            + "\"setupDatabaseFallbackPolicy\":\"configure-postgresql-mysql-mariadb-fallback-on-core-or-addon-node\","
            + "\"pluginMessagingControlPolicy\":\"block-cloudislands-control-messages-at-proxy\","
            + "\"pluginMessagingForwardResultPolicy\":\"cloudislands-messages-always-handled-never-forwarded\","
            + "\"pluginMessagingAllowedUse\":\"emergency-proxy-assist-only\","
            + "\"pluginMessagingForbiddenUse\":\"island-create-delete-save-migrate-routing-authority\","
            + "\"cloudIslandsPluginMessagesConfiguredBlocked\":" + config.blockCloudIslandsPluginMessages() + ","
            + "\"cloudIslandsPluginMessagesBlocked\":true,"
            + "\"cloudIslandsPluginMessagesEnforcedBlocked\":true,"
            + "\"hideNodeNames\":" + config.hideNodeNames() + ","
            + "\"playerTopologyPolicy\":\"logical-island-only\","
            + "\"playerNodeNamePolicy\":\"" + (config.hideNodeNames() ? "hidden-from-player-routing-messages" : "visible-risk-admin-debug-only") + "\","
            + "\"topologyExposureRisk\":" + !config.hideNodeNames() + ","
            + "\"pluginMessagesBlockedTotal\":" + pluginMessageFirewall.blockedMessages() + ","
            + "\"aliases\":\"" + escapeJson(String.join(",", commandAliases)) + "\","
            + "\"fallbackServer\":\"" + escapeJson(config.fallbackServer()) + "\","
            + "\"fallbackServerRegistered\":" + fallbackServerRegistered() + ","
            + "\"routing\":\"" + escapeJson(routingController.statusSummary()) + "\""
            + "}";
    }

    public String metricsText() {
        return ""
            + "cloudislands_velocity_online_players " + proxy.getPlayerCount() + "\n"
            + "cloudislands_velocity_registered_servers " + proxy.getAllServers().size() + "\n"
            + "cloudislands_velocity_command_aliases " + commandAliases.size() + "\n"
            + "cloudislands_velocity_debug_enabled " + (config.debug() ? 1 : 0) + "\n"
            + "cloudislands_velocity_plugin_message_blocking_configured " + (config.blockCloudIslandsPluginMessages() ? 1 : 0) + "\n"
            + "cloudislands_velocity_plugin_message_blocking 1\n"
            + "cloudislands_velocity_plugin_message_control_channel_allowed 0\n"
            + "cloudislands_velocity_plugin_message_control_channel_enforced_blocked 1\n"
            + "cloudislands_velocity_plugin_messages_blocked_total " + pluginMessageFirewall.blockedMessages() + "\n"
            + "cloudislands_velocity_modern_forwarding_required " + (config.requireModernForwarding() ? 1 : 0) + "\n"
            + "cloudislands_velocity_forwarding_secret_configured " + (config.forwardingSecret().isBlank() ? 0 : 1) + "\n"
            + "cloudislands_velocity_hide_node_names " + (config.hideNodeNames() ? 1 : 0) + "\n"
            + "cloudislands_velocity_topology_exposure_risk " + (config.hideNodeNames() ? 0 : 1) + "\n"
            + "cloudislands_velocity_fallback_server_registered " + (fallbackServerRegistered() ? 1 : 0) + "\n"
            + routingController.routingMetricsText();
    }

    private boolean fallbackServerRegistered() {
        return config.fallbackServer() != null && !config.fallbackServer().isBlank() && proxy.getServer(config.fallbackServer()).isPresent();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
