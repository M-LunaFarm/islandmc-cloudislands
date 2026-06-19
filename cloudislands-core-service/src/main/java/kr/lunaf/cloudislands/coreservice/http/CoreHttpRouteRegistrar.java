package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.IpAllowlist;
import kr.lunaf.cloudislands.coreservice.security.MtlsHeaderGuard;

public final class CoreHttpRouteRegistrar {
    private final FixedWindowRateLimiter rateLimiter;
    private final ApiTokenGuard tokenGuard;
    private final MtlsHeaderGuard mtlsGuard;
    private final IpAllowlist ipAllowlist;
    private final AdminEndpointGuard adminGuard;
    private final AtomicLong securityRejectsTotal = new AtomicLong();
    private final AtomicLong securityRejectsRateLimited = new AtomicLong();
    private final AtomicLong securityRejectsUnauthorized = new AtomicLong();
    private final AtomicLong securityRejectsMtlsRequired = new AtomicLong();
    private final AtomicLong securityRejectsIpNotAllowed = new AtomicLong();
    private final AtomicLong securityRejectsAdminPermissionDenied = new AtomicLong();
    private HttpServer server;
    private AuditLogger audit;

    public CoreHttpRouteRegistrar(
            FixedWindowRateLimiter rateLimiter,
            ApiTokenGuard tokenGuard,
            MtlsHeaderGuard mtlsGuard,
            IpAllowlist ipAllowlist,
            AdminEndpointGuard adminGuard) {
        this.rateLimiter = rateLimiter;
        this.tokenGuard = tokenGuard;
        this.mtlsGuard = mtlsGuard;
        this.ipAllowlist = ipAllowlist;
        this.adminGuard = adminGuard;
    }

    public void attach(HttpServer server) {
        this.server = server;
    }

    public void setAudit(AuditLogger audit) {
        this.audit = audit;
    }

    public void route(String path, HttpHandler handler) {
        requireServer().createContext(path, exchange -> {
            String key = exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allow(key)) {
                auditSecurityReject("RATE_LIMITED", path, exchange);
                CoreHttpResponses.write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
                return;
            }
            if (!healthProbePath(path) && !coreApiAuthenticated(exchange)) {
                String rejectCode = coreApiAuthRejectCode();
                auditSecurityReject(rejectCode, path, exchange);
                CoreHttpResponses.write(exchange, 401, ApiResponses.error(rejectCode, coreApiAuthRejectMessage(rejectCode)));
                return;
            }
            if (!ipAllowlist.allowed(exchange)) {
                auditSecurityReject("IP_NOT_ALLOWED", path, exchange);
                CoreHttpResponses.write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
                return;
            }
            if (!adminGuard.allowed(path, exchange)) {
                auditSecurityReject("ADMIN_PERMISSION_DENIED", path, exchange);
                CoreHttpResponses.write(exchange, 403, ApiResponses.error("ADMIN_PERMISSION_DENIED", "Admin permission is required"));
                return;
            }
            try {
                handler.handle(exchange);
            } catch (IllegalStateException exception) {
                if (path.startsWith("/v1/addons/")) {
                    CoreHttpResponses.write(exchange, 503, ApiResponses.error("ADDON_STATE_UNAVAILABLE", "Addon state storage is temporarily unavailable"));
                    return;
                }
                throw exception;
            }
        });
    }

    public void routePrefix(String path, HttpHandler handler) {
        requireServer().createContext(path, exchange -> {
            String requestPath = exchange.getRequestURI().getPath();
            String key = exchange.getRemoteAddress() == null ? "unknown" : exchange.getRemoteAddress().getAddress().getHostAddress();
            if (!rateLimiter.allow(key)) {
                auditSecurityReject("RATE_LIMITED", requestPath, exchange);
                CoreHttpResponses.write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
                return;
            }
            if (!coreApiAuthenticated(exchange)) {
                String rejectCode = coreApiAuthRejectCode();
                auditSecurityReject(rejectCode, requestPath, exchange);
                CoreHttpResponses.write(exchange, 401, ApiResponses.error(rejectCode, coreApiAuthRejectMessage(rejectCode)));
                return;
            }
            if (!ipAllowlist.allowed(exchange)) {
                auditSecurityReject("IP_NOT_ALLOWED", requestPath, exchange);
                CoreHttpResponses.write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
                return;
            }
            if (!adminGuard.allowed(requestPath, exchange)) {
                auditSecurityReject("ADMIN_PERMISSION_DENIED", requestPath, exchange);
                CoreHttpResponses.write(exchange, 403, ApiResponses.error("ADMIN_PERMISSION_DENIED", "Admin permission is required"));
                return;
            }
            handler.handle(exchange);
        });
    }

    public long securityRejectsTotal() {
        return securityRejectsTotal.get();
    }

    public long securityRejectsRateLimited() {
        return securityRejectsRateLimited.get();
    }

    public long securityRejectsUnauthorized() {
        return securityRejectsUnauthorized.get();
    }

    public long securityRejectsMtlsRequired() {
        return securityRejectsMtlsRequired.get();
    }

    public long securityRejectsIpNotAllowed() {
        return securityRejectsIpNotAllowed.get();
    }

    public long securityRejectsAdminPermissionDenied() {
        return securityRejectsAdminPermissionDenied.get();
    }

    private HttpServer requireServer() {
        if (server == null) {
            throw new IllegalStateException("Core HTTP server is not attached");
        }
        return server;
    }

    private boolean coreApiAuthenticated(HttpExchange exchange) {
        return tokenGuard.allowed(exchange) || mtlsGuard.verified(exchange);
    }

    private static boolean healthProbePath(String path) {
        return "/live".equals(path) || "/ready".equals(path) || "/health".equals(path);
    }

    private String coreApiAuthRejectCode() {
        return mtlsGuard.required() ? "MTLS_REQUIRED" : "UNAUTHORIZED";
    }

    private String coreApiAuthRejectMessage(String rejectCode) {
        return "MTLS_REQUIRED".equals(rejectCode)
            ? "mTLS verification or API token authentication is required"
            : "Missing or invalid API token";
    }

    private void auditSecurityReject(String reason, String path, HttpExchange exchange) {
        recordSecurityReject(reason);
        if (audit == null) {
            return;
        }
        try {
            String remote = exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null
                ? "unknown"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
            audit.log(new UUID(0L, 0L), "SECURITY", "SECURITY_REJECT", "HTTP", path == null ? "" : path, Map.of(
                "reason", reason == null ? "" : reason,
                "method", exchange.getRequestMethod() == null ? "" : exchange.getRequestMethod(),
                "remote", remote
            ));
        } catch (RuntimeException ignored) {
            // Security rejection audit must not change the original response.
        }
    }

    private void recordSecurityReject(String reason) {
        securityRejectsTotal.incrementAndGet();
        String normalized = reason == null ? "" : reason;
        switch (normalized) {
            case "RATE_LIMITED" -> securityRejectsRateLimited.incrementAndGet();
            case "UNAUTHORIZED" -> securityRejectsUnauthorized.incrementAndGet();
            case "MTLS_REQUIRED" -> securityRejectsMtlsRequired.incrementAndGet();
            case "IP_NOT_ALLOWED" -> securityRejectsIpNotAllowed.incrementAndGet();
            case "ADMIN_PERMISSION_DENIED" -> securityRejectsAdminPermissionDenied.incrementAndGet();
            default -> {
            }
        }
    }
}
