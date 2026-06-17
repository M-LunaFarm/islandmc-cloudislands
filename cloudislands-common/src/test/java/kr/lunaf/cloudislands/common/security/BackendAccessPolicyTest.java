package kr.lunaf.cloudislands.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class BackendAccessPolicyTest {
    @Test
    void listsEveryRequiredSecurityControl() {
        assertEquals(
            Set.of(
                "velocity-modern-forwarding",
                "paper-direct-access-firewall-block",
                "paper-online-mode-proxy-only",
                "forwarding-secret-managed",
                "core-api-mtls-or-token",
                "private-redis-postgresql-object-storage",
                "admin-permission-separation",
                "audit-log-storage",
                "plugin-messaging-minimized"
            ),
            BackendAccessPolicy.requiredSecurityControls()
        );
    }

    @Test
    void namesProxyAndPaperBackendTrustRules() {
        assertEquals(
            "Velocity modern forwarding is required for Paper backend trust",
            BackendAccessPolicy.MODERN_FORWARDING_POLICY
        );
        assertEquals(
            "forwarding secret must be configured on Velocity and Paper",
            BackendAccessPolicy.FORWARDING_SECRET_POLICY
        );
        assertEquals(
            "Paper island nodes are firewall-blocked from direct access reject non-proxy sources and require route sessions",
            BackendAccessPolicy.PAPER_DIRECT_ACCESS_POLICY
        );
        assertEquals(
            "Paper online-mode=false is allowed only behind Velocity forwarding",
            BackendAccessPolicy.PAPER_ONLINE_MODE_POLICY
        );
    }

    @Test
    void namesCoreInfrastructureAdminAuditAndMessagingRules() {
        assertEquals(
            "Core API requires mTLS or bearer token authentication",
            BackendAccessPolicy.CORE_API_AUTH_POLICY
        );
        assertEquals(
            "Redis PostgreSQL and Object Storage stay private to the control plane",
            BackendAccessPolicy.INFRASTRUCTURE_EXPOSURE_POLICY
        );
        assertEquals(
            "admin commands require separated scoped permissions",
            BackendAccessPolicy.ADMIN_PERMISSION_POLICY
        );
        assertEquals(
            "security-sensitive admin and system actions are written to audit logs",
            BackendAccessPolicy.AUDIT_LOG_POLICY
        );
        assertEquals(
            "plugin messaging is never used for critical island lifecycle control",
            BackendAccessPolicy.PLUGIN_MESSAGING_POLICY
        );
    }

    @Test
    void detectsRequiredControlsByStableKey() {
        assertTrue(BackendAccessPolicy.requiredSecurityControl("core-api-mtls-or-token"));
        assertTrue(BackendAccessPolicy.requiredSecurityControl("plugin-messaging-minimized"));
        assertFalse(BackendAccessPolicy.requiredSecurityControl("legacy-forwarding"));
        assertFalse(BackendAccessPolicy.requiredSecurityControl(null));
    }
}
