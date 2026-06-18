package kr.lunaf.cloudislands.paper;

import java.util.List;
import java.util.Map;

public final class PaperAgentRolePolicy {
    public static final String INSTALLATION_POLICY = "paper-agent-is-installed-on-lobby-and-island-nodes-role-controls-runtime-behavior";
    public static final String LOBBY_WORLD_EXECUTION_POLICY = "lobby-role-never-activates-runs-saves-or-restores-island-worlds";
    public static final String ISLAND_NODE_EXECUTION_POLICY = "island-node-role-owns-active-world-runtime-protection-teleport-save-snapshot-and-heartbeat";
    public static final String DIRECT_WRITE_POLICY = "paper-agent-never-writes-core-database-directly-uses-core-api-client";

    private static final List<String> COMMON_CAPABILITIES = List.of(
        "core-api-client",
        "redis-client",
        "config-loader",
        "message-renderer",
        "translation-manager",
        "permission-hook",
        "placeholder-hook",
        "metrics-exporter",
        "health-check-endpoint",
        "local-cache-manager"
    );

    private static final List<String> LOBBY_CAPABILITIES = List.of(
        "island-gui",
        "island-create-menu",
        "island-ranking",
        "invite-accept-decline",
        "island-settings-gui",
        "visit-gui",
        "admin-query-gui"
    );

    private static final List<String> ISLAND_NODE_CAPABILITIES = List.of(
        "island-activation",
        "island-deactivation",
        "island-save",
        "island-snapshot-create",
        "shard-world-management",
        "cell-allocate-release",
        "chunk-preload",
        "protection-event-handling",
        "permission-cache-maintenance",
        "island-teleport",
        "visitor-spawn",
        "member-join-quit",
        "active-island-heartbeat"
    );

    private static final Map<String, List<String>> ROLE_CAPABILITIES = Map.of(
        "COMMON", COMMON_CAPABILITIES,
        "LOBBY", LOBBY_CAPABILITIES,
        "ISLAND_NODE", ISLAND_NODE_CAPABILITIES
    );

    private PaperAgentRolePolicy() {
    }

    public static List<String> commonCapabilities() {
        return COMMON_CAPABILITIES;
    }

    public static List<String> lobbyCapabilities() {
        return LOBBY_CAPABILITIES;
    }

    public static List<String> islandNodeCapabilities() {
        return ISLAND_NODE_CAPABILITIES;
    }

    public static Map<String, List<String>> roleCapabilities() {
        return ROLE_CAPABILITIES;
    }

    public static boolean lobbyCapability(String capability) {
        return capability != null && LOBBY_CAPABILITIES.contains(capability.trim().toLowerCase());
    }

    public static boolean islandNodeCapability(String capability) {
        return capability != null && ISLAND_NODE_CAPABILITIES.contains(capability.trim().toLowerCase());
    }

    public static boolean commonCapability(String capability) {
        return capability != null && COMMON_CAPABILITIES.contains(capability.trim().toLowerCase());
    }

    public static String roleSummary(String role) {
        if (role == null) {
            return "";
        }
        List<String> capabilities = ROLE_CAPABILITIES.get(role.trim().toUpperCase());
        return capabilities == null ? "" : String.join(",", capabilities);
    }
}
