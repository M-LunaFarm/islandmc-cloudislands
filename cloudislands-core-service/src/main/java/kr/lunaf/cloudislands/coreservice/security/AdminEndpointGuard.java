package kr.lunaf.cloudislands.coreservice.security;

import com.sun.net.httpserver.HttpExchange;
import java.util.EnumSet;
import java.util.Set;
import kr.lunaf.cloudislands.coreservice.security.permission.AdminPermission;
import kr.lunaf.cloudislands.coreservice.security.permission.AdminPermissionPolicy;

public final class AdminEndpointGuard {
    private final String adminToken;

    public AdminEndpointGuard(String adminToken) {
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    public boolean allowed(String path, HttpExchange exchange) {
        AdminPermission required = permissionFor(path);
        if (required == null) {
            return true;
        }
        if (!tokenAllowed(exchange)) {
            return false;
        }
        return policy(exchange).allows(required);
    }

    private boolean tokenAllowed(HttpExchange exchange) {
        if (adminToken.isBlank()) {
            return false;
        }
        String header = exchange.getRequestHeaders().getFirst("X-CloudIslands-Admin-Token");
        return adminToken.equals(header);
    }

    private AdminPermissionPolicy policy(HttpExchange exchange) {
        String raw = exchange.getRequestHeaders().getFirst("X-CloudIslands-Admin-Permissions");
        if (raw == null || raw.isBlank() || raw.equals("*")) {
            return AdminPermissionPolicy.all();
        }
        Set<AdminPermission> permissions = EnumSet.noneOf(AdminPermission.class);
        for (String part : raw.split(",")) {
            try {
                permissions.add(AdminPermission.valueOf(part.trim()));
            } catch (IllegalArgumentException ignored) {
                // Unknown permission names are ignored rather than granting access.
            }
        }
        if (permissions.isEmpty()) {
            return new AdminPermissionPolicy(EnumSet.noneOf(AdminPermission.class));
        }
        return new AdminPermissionPolicy(permissions);
    }

    private AdminPermission permissionFor(String path) {
        return switch (path) {
            case "/v1/audit" -> AdminPermission.AUDIT_READ;
            case "/v1/events" -> AdminPermission.AUDIT_READ;
            case "/v1/jobs", "/v1/jobs/claim", "/v1/jobs/complete", "/v1/jobs/fail", "/v1/jobs/recover" -> AdminPermission.JOB_MANAGE;
            case "/v1/admin/nodes/drain" -> AdminPermission.NODE_DRAIN;
            case "/v1/admin/nodes/undrain" -> AdminPermission.NODE_UNDRAIN;
            case "/v1/admin/islands/activate" -> AdminPermission.ISLAND_ACTIVATE;
            case "/v1/admin/islands/deactivate" -> AdminPermission.ISLAND_DEACTIVATE;
            case "/v1/admin/islands/migrate" -> AdminPermission.ISLAND_MIGRATE;
            case "/v1/admin/islands/snapshot" -> AdminPermission.ISLAND_SNAPSHOT;
            case "/v1/admin/islands/restore" -> AdminPermission.ISLAND_RESTORE;
            case "/v1/admin/islands/quarantine" -> AdminPermission.ISLAND_QUARANTINE;
            default -> path.startsWith("/v1/admin") ? AdminPermission.AUDIT_READ : null;
        };
    }
}
