package kr.lunaf.cloudislands.common.security;

import java.util.List;
import java.util.Set;

public final class BackendAccessPolicy {
    public static final String CONTRACT = "velocity-modern-forwarding-proxy-only-paper-backends";
    public static final String MODERN_FORWARDING_POLICY = "Velocity modern forwarding is required for Paper backend trust";
    public static final String FORWARDING_SECRET_POLICY = "forwarding secret must be configured on Velocity and Paper";
    public static final String PAPER_DIRECT_ACCESS_POLICY = "Paper island nodes are firewall-blocked from direct access reject non-proxy sources and require route sessions";
    public static final String PAPER_ONLINE_MODE_POLICY = "Paper online-mode=false is allowed only behind Velocity forwarding";
    public static final String CORE_API_AUTH_POLICY = "Core API requires mTLS or bearer token authentication";
    public static final String INFRASTRUCTURE_EXPOSURE_POLICY = "Redis PostgreSQL and Object Storage stay private to the control plane";
    public static final String ADMIN_PERMISSION_POLICY = "admin commands require separated scoped permissions";
    public static final String AUDIT_LOG_POLICY = "security-sensitive admin and system actions are written to audit logs";
    public static final String PLUGIN_MESSAGING_POLICY = "plugin messaging is never used for critical island lifecycle control";
    public static final String PLUGIN_MESSAGING_FORWARDING_POLICY = "velocity-plugin-message-events-for-cloudislands-control-channels-must-return-handled";
    public static final String PLUGIN_MESSAGING_ALLOWED_SCOPE = "bungeecord-connect-fallback-and-client-branding-only-no-core-control-plane";
    public static final String CORE_CONTROL_PLANE_POLICY = "core-control-plane-uses-http-grpc-plus-redis-streams-never-plugin-messaging";
    public static final String ROUTING_CONTROL_POLICY = "routing-is-owned-by-velocity-plugin-direct-core-api-route-ticket-flow";

    public static final List<String> FORBIDDEN_PLUGIN_MESSAGING_OPERATIONS = List.of(
        "island-create",
        "island-delete",
        "island-save",
        "island-migrate",
        "snapshot-restore",
        "addon-state-write",
        "permission-write"
    );

    public static final List<String> ALLOWED_PLUGIN_MESSAGING_SCOPES = List.of(
        "bungeecord-connect-fallback",
        "client-branding",
        "non-authoritative-emergency-assist"
    );

    public static final Set<String> REQUIRED_SECURITY_CONTROLS = Set.of(
        "velocity-modern-forwarding",
        "paper-direct-access-firewall-block",
        "paper-online-mode-proxy-only",
        "forwarding-secret-managed",
        "core-api-mtls-or-token",
        "private-redis-postgresql-object-storage",
        "admin-permission-separation",
        "audit-log-storage",
        "plugin-messaging-minimized"
    );

    private BackendAccessPolicy() {
    }

    public static Set<String> requiredSecurityControls() {
        return REQUIRED_SECURITY_CONTROLS;
    }

    public static boolean requiredSecurityControl(String control) {
        return control != null && REQUIRED_SECURITY_CONTROLS.contains(control);
    }

    public static List<String> forbiddenPluginMessagingOperations() {
        return FORBIDDEN_PLUGIN_MESSAGING_OPERATIONS;
    }

    public static boolean forbiddenPluginMessagingOperation(String operation) {
        return operation != null && FORBIDDEN_PLUGIN_MESSAGING_OPERATIONS.contains(operation.trim().toLowerCase());
    }

    public static List<String> allowedPluginMessagingScopes() {
        return ALLOWED_PLUGIN_MESSAGING_SCOPES;
    }

    public static boolean allowedPluginMessagingScope(String scope) {
        return scope != null && ALLOWED_PLUGIN_MESSAGING_SCOPES.contains(scope.trim().toLowerCase());
    }
}
