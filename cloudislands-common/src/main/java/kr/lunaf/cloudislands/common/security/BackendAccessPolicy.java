package kr.lunaf.cloudislands.common.security;

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
}
