package kr.lunaf.cloudislands.velocity.config;

import java.util.List;
import java.util.Map;

public record VelocityConfig(
    String language,
    boolean debug,
    String coreBaseUrl,
    String coreToken,
    String adminToken,
    int timeoutMs,
    String fallbackServer,
    int routeWaitSeconds,
    String islandPool,
    int routeTicketTtlSeconds,
    boolean hideNodeNames,
    boolean useActionBar,
    boolean useBossBarLoading,
    boolean requireModernForwarding,
    String forwardingSecret,
    boolean blockCloudIslandsPluginMessages,
    boolean healthEnabled,
    String healthBindHost,
    int healthPort,
    boolean superiorSkyblock2MigrationEnabled,
    Map<String, String> messages,
    List<String> aliases
) {
}
