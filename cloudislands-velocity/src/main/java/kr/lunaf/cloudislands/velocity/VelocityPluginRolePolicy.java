package kr.lunaf.cloudislands.velocity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VelocityPluginRolePolicy {
    public static final String ROLE_POLICY =
            "velocity-owns-global-command-routing-ticket-connect-fallback-and-server-state-entry";
    public static final String NO_WORLD_EXECUTION_POLICY =
            "velocity-never-runs-island-worlds-or-writes-island-bundles";
    public static final String COMMAND_PRIORITY_POLICY =
            "global-island-commands-are-handled-at-proxy-before-backend-paper-agents";

    private static final List<String> GLOBAL_COMMAND_ALIASES = List.of(
            "/is",
            "/island",
            "/\uC12C"
    );

    private static final Set<String> ENTRY_RESPONSIBILITIES = orderedSet(
            "global-is-command",
            "global-island-command",
            "global-korean-island-command",
            "server-state-reflection"
    );

    private static final Set<String> ROUTING_RESPONSIBILITIES = orderedSet(
            "route-ticket-create-request",
            "route-ticket-status-check",
            "backend-connect-request",
            "pending-route-on-join",
            "server-name-redaction"
    );

    private static final Set<String> FAILURE_RESPONSIBILITIES = orderedSet(
            "fallback-transfer",
            "route-ticket-expiry-handling",
            "server-switch-failure-recovery",
            "pending-route-clear"
    );

    private static final Set<String> FORBIDDEN_RUNTIME_RESPONSIBILITIES = orderedSet(
            "paper-world-execution",
            "island-region-save",
            "snapshot-bundle-write",
            "protection-event-decision",
            "satis-runtime-tick",
            "direct-island-database-write"
    );

    private VelocityPluginRolePolicy() {
    }

    public static List<String> globalCommandAliases() {
        return GLOBAL_COMMAND_ALIASES;
    }

    public static Set<String> entryResponsibilities() {
        return ENTRY_RESPONSIBILITIES;
    }

    public static Set<String> routingResponsibilities() {
        return ROUTING_RESPONSIBILITIES;
    }

    public static Set<String> failureResponsibilities() {
        return FAILURE_RESPONSIBILITIES;
    }

    public static Set<String> forbiddenRuntimeResponsibilities() {
        return FORBIDDEN_RUNTIME_RESPONSIBILITIES;
    }

    public static boolean ownsProxyResponsibility(String responsibility) {
        return ENTRY_RESPONSIBILITIES.contains(responsibility)
                || ROUTING_RESPONSIBILITIES.contains(responsibility)
                || FAILURE_RESPONSIBILITIES.contains(responsibility);
    }

    public static boolean forbidsRuntimeResponsibility(String responsibility) {
        return FORBIDDEN_RUNTIME_RESPONSIBILITIES.contains(responsibility);
    }

    private static Set<String> orderedSet(String... values) {
        return Set.copyOf(new LinkedHashSet<>(List.of(values)));
    }
}
