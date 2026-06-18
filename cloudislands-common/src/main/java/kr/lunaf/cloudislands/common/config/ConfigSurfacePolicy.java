package kr.lunaf.cloudislands.common.config;

import java.util.List;

public final class ConfigSurfacePolicy {
    public static final String VELOCITY_CONFIG_POLICY =
            "velocity-config-owns-plugin-core-api-routing-command-message-security-and-health-keys";
    public static final String PAPER_AGENT_CONFIG_POLICY =
            "paper-agent-config-owns-node-core-api-redis-storage-island-node-protection-heartbeat-routing-keys";
    public static final String CORE_API_CONFIG_POLICY =
            "core-api-config-owns-server-database-redis-storage-routing-security-upgrade-and-block-value-keys";

    private static final List<String> VELOCITY_REQUIRED_KEYS = List.of(
            "plugin.language",
            "plugin.debug",
            "core-api.base-url",
            "core-api.auth-token",
            "core-api.timeout-ms",
            "routing.default-lobby",
            "routing.island-pool",
            "routing.route-ticket-ttl-seconds",
            "routing.wait-for-activation-timeout-seconds",
            "routing.fallback-on-failure",
            "routing.hide-node-names",
            "commands.aliases",
            "messages.use-actionbar",
            "messages.use-bossbar-loading"
    );

    private static final List<String> PAPER_REQUIRED_KEYS = List.of(
            "node.id",
            "node.role",
            "node.pool",
            "core-api.base-url",
            "core-api.auth-token",
            "redis.uri",
            "storage.type",
            "storage.endpoint",
            "storage.bucket",
            "storage.access-key",
            "storage.secret-key",
            "island-node.shard-world-prefix",
            "island-node.shard-count",
            "island-node.cell-size",
            "island-node.default-island-size",
            "island-node.activation.max-concurrent",
            "island-node.activation.preload-radius",
            "island-node.activation.save-on-empty-after-seconds",
            "island-node.activation.periodic-save-seconds",
            "protection.cache-permissions",
            "protection.deny-message-cooldown-ms",
            "heartbeat.interval-ticks",
            "heartbeat.timeout-seconds"
    );

    private static final List<String> CORE_REQUIRED_KEYS = List.of(
            "server.bind",
            "server.port",
            "database.jdbc-url",
            "database.username",
            "database.password",
            "database.pool-size",
            "redis.uri",
            "storage.type",
            "storage.bucket",
            "routing.heartbeat-timeout-seconds",
            "routing.lease-duration-seconds",
            "routing.soft-full-policy",
            "routing.hard-full-policy",
            "routing.migration-policy",
            "security.require-mtls",
            "security.admin-api-enabled"
    );

    private ConfigSurfacePolicy() {
    }

    public static List<String> velocityRequiredKeys() {
        return VELOCITY_REQUIRED_KEYS;
    }

    public static List<String> paperRequiredKeys() {
        return PAPER_REQUIRED_KEYS;
    }

    public static List<String> coreRequiredKeys() {
        return CORE_REQUIRED_KEYS;
    }
}
