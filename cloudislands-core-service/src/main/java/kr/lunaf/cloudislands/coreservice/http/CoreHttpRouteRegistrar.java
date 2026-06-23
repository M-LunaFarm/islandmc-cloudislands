package kr.lunaf.cloudislands.coreservice.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.security.AdminEndpointGuard;
import kr.lunaf.cloudislands.coreservice.security.ApiTokenGuard;
import kr.lunaf.cloudislands.coreservice.security.CoreApiAuthGuard;
import kr.lunaf.cloudislands.coreservice.security.FixedWindowRateLimiter;
import kr.lunaf.cloudislands.coreservice.security.ForwardedClientIpResolver;
import kr.lunaf.cloudislands.coreservice.security.IpAllowlist;
import kr.lunaf.cloudislands.coreservice.security.MtlsHeaderGuard;

public final class CoreHttpRouteRegistrar {
    private final FixedWindowRateLimiter rateLimiter;
    private final CoreApiAuthGuard authGuard;
    private final ForwardedClientIpResolver clientIpResolver;
    private final IpAllowlist ipAllowlist;
    private final AdminEndpointGuard adminGuard;
    private final AtomicLong securityRejectsTotal = new AtomicLong();
    private final AtomicLong securityRejectsRateLimited = new AtomicLong();
    private final AtomicLong securityRejectsUnauthorized = new AtomicLong();
    private final AtomicLong securityRejectsMtlsRequired = new AtomicLong();
    private final AtomicLong securityRejectsIpNotAllowed = new AtomicLong();
    private final AtomicLong securityRejectsAdminPermissionDenied = new AtomicLong();
    private final Map<String, RouteDefinition> exactRoutes = new LinkedHashMap<>();
    private final List<RouteDefinition> prefixRoutes = new ArrayList<>();
    private HttpServer server;
    private AuditLogger audit;

    public CoreHttpRouteRegistrar(
            FixedWindowRateLimiter rateLimiter,
            ApiTokenGuard tokenGuard,
            MtlsHeaderGuard mtlsGuard,
            IpAllowlist ipAllowlist,
            AdminEndpointGuard adminGuard) {
        this(rateLimiter, CoreApiAuthGuard.mtlsOrToken(tokenGuard, mtlsGuard), ipAllowlist, adminGuard);
    }

    public CoreHttpRouteRegistrar(
            FixedWindowRateLimiter rateLimiter,
            CoreApiAuthGuard authGuard,
            IpAllowlist ipAllowlist,
            AdminEndpointGuard adminGuard) {
        this(rateLimiter, authGuard, new ForwardedClientIpResolver("127.0.0.1,localhost,::1"), ipAllowlist, adminGuard);
    }

    public CoreHttpRouteRegistrar(
            FixedWindowRateLimiter rateLimiter,
            CoreApiAuthGuard authGuard,
            ForwardedClientIpResolver clientIpResolver,
            IpAllowlist ipAllowlist,
            AdminEndpointGuard adminGuard) {
        this.rateLimiter = rateLimiter;
        this.authGuard = authGuard;
        this.clientIpResolver = clientIpResolver == null
            ? new ForwardedClientIpResolver("127.0.0.1,localhost,::1")
            : clientIpResolver;
        this.ipAllowlist = ipAllowlist;
        this.adminGuard = adminGuard;
    }

    public void attach(HttpServer server) {
        this.server = server;
        this.server.createContext("/", this::dispatch);
    }

    public void setAudit(AuditLogger audit) {
        this.audit = audit;
    }

    public void route(String path, HttpHandler handler) {
        routeMethods(path, handler, "GET", "POST");
    }

    public void routeGet(String path, HttpHandler handler) {
        routeMethods(path, handler, "GET");
    }

    public void routePost(String path, HttpHandler handler) {
        routeMethods(path, handler, "POST");
    }

    public void routeMethods(String path, HttpHandler handler, String... methods) {
        requireServer();
        String normalizedPath = normalizeRegisteredPath(path);
        exactRoutes.put(normalizedPath, new RouteDefinition(normalizedPath, false, allowedMethods(methods), handler));
    }

    public void routePrefix(String path, HttpHandler handler) {
        routePrefixMethods(path, handler, "GET", "POST");
    }

    public void routePrefixMethods(String path, HttpHandler handler, String... methods) {
        requireServer();
        prefixRoutes.add(new RouteDefinition(normalizeRegisteredPath(path), true, allowedMethods(methods), handler));
        prefixRoutes.sort(Comparator.comparingInt((RouteDefinition route) -> route.path().length()).reversed());
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

    private void dispatch(HttpExchange exchange) throws IOException {
        if (rejectBusy(exchange)) {
            return;
        }
        String requestPath = normalizedRequestPath(exchange);
        RouteDefinition route = route(requestPath);
        if (route == null) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("NOT_FOUND", "No Core API route matches this path"));
            return;
        }
        String method = exchange.getRequestMethod() == null ? "" : exchange.getRequestMethod().toUpperCase(java.util.Locale.ROOT);
        if (!route.allowedMethods().contains(method)) {
            exchange.getResponseHeaders().set("Allow", String.join(", ", route.allowedMethods()));
            CoreHttpResponses.write(exchange, 405, ApiResponses.error("METHOD_NOT_ALLOWED", "HTTP method is not allowed for this route"));
            return;
        }
        if (!authorize(requestPath, exchange)) {
            return;
        }
        if (bodyMethod(method) && !jsonContentType(exchange)) {
            CoreHttpResponses.write(exchange, 415, ApiResponses.error("UNSUPPORTED_MEDIA_TYPE", "Content-Type must be application/json"));
            return;
        }
        try {
            route.handler().handle(exchange);
        } catch (CoreHttpException exception) {
            CoreHttpResponses.write(exchange, exception.status(), ApiResponses.error(exception.code(), exception.getMessage()));
        } catch (IllegalStateException exception) {
            if (requestPath.startsWith("/v1/addons/")) {
                CoreHttpResponses.write(exchange, 503, ApiResponses.error("ADDON_STATE_UNAVAILABLE", "Addon state storage is temporarily unavailable"));
                return;
            }
            throw exception;
        }
    }

    private boolean authorize(String requestPath, HttpExchange exchange) throws IOException {
        ForwardedClientIpResolver.ClientIpResolution clientIp = clientIpResolver.resolve(exchange);
        if (!clientIp.accepted()) {
            auditSecurityReject(clientIp.rejectCode(), requestPath, exchange, clientIp);
            CoreHttpResponses.write(exchange, 403, ApiResponses.error(clientIp.rejectCode(), "Forwarded client IP headers are only accepted from trusted proxies"));
            return false;
        }
        String key = clientIp.clientIp();
        if (!rateLimiter.allow(key)) {
            auditSecurityReject("RATE_LIMITED", requestPath, exchange, clientIp);
            CoreHttpResponses.write(exchange, 429, ApiResponses.error("RATE_LIMITED", "Too many requests"));
            return false;
        }
        if (!healthProbePath(requestPath) && !coreApiAuthenticated(exchange)) {
            String rejectCode = authGuard.rejectCode();
            auditSecurityReject(rejectCode, requestPath, exchange, clientIp);
            CoreHttpResponses.write(exchange, 401, ApiResponses.error(rejectCode, authGuard.rejectMessage()));
            return false;
        }
        if (!ipAllowlist.allowed(clientIp.clientIp())) {
            auditSecurityReject("IP_NOT_ALLOWED", requestPath, exchange, clientIp);
            CoreHttpResponses.write(exchange, 403, ApiResponses.error("IP_NOT_ALLOWED", "Remote address is not allowed"));
            return false;
        }
        if (!adminGuard.allowed(requestPath, exchange)) {
            auditSecurityReject("ADMIN_PERMISSION_DENIED", requestPath, exchange, clientIp);
            CoreHttpResponses.write(exchange, 403, ApiResponses.error("ADMIN_PERMISSION_DENIED", "Admin permission is required"));
            return false;
        }
        return true;
    }

    private RouteDefinition route(String requestPath) {
        RouteDefinition exact = exactRoutes.get(requestPath);
        if (exact != null) {
            return exact;
        }
        for (RouteDefinition route : prefixRoutes) {
            if (requestPath.equals(route.path()) || requestPath.startsWith(route.path() + "/")) {
                return route;
            }
        }
        return null;
    }

    private boolean rejectBusy(HttpExchange exchange) throws IOException {
        if (!CoreHttpRequestExecutor.saturatedRequest()) {
            return false;
        }
        CoreHttpResponses.write(exchange, 503, ApiResponses.error("CORE_BUSY", "Core HTTP worker pool is saturated"));
        return true;
    }

    private static String normalizedRequestPath(HttpExchange exchange) {
        String path = exchange.getRequestURI() == null ? "/" : exchange.getRequestURI().getPath();
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = URI.create(path).normalize().getPath();
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeRegisteredPath(String path) {
        if (path == null || path.isBlank() || path.charAt(0) != '/') {
            throw new IllegalArgumentException("Core HTTP route path must start with /");
        }
        String normalized = URI.create(path).normalize().getPath();
        return normalized.length() > 1 && normalized.endsWith("/")
            ? normalized.substring(0, normalized.length() - 1)
            : normalized;
    }

    private static Set<String> allowedMethods(String... methods) {
        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        if (methods != null) {
            for (String method : methods) {
                if (method == null || method.isBlank()) {
                    continue;
                }
                allowed.add(method.trim().toUpperCase(java.util.Locale.ROOT));
            }
        }
        return allowed.isEmpty() ? new LinkedHashSet<>(List.of("GET", "POST")) : allowed;
    }

    private static boolean bodyMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private static boolean jsonContentType(HttpExchange exchange) {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null) {
            return false;
        }
        String mediaType = contentType.split(";", 2)[0].trim();
        return "application/json".equalsIgnoreCase(mediaType);
    }

    private boolean coreApiAuthenticated(HttpExchange exchange) {
        return authGuard.allowed(exchange);
    }

    private static boolean healthProbePath(String path) {
        return "/live".equals(path) || "/ready".equals(path) || "/health".equals(path);
    }

    private void auditSecurityReject(String reason, String path, HttpExchange exchange) {
        auditSecurityReject(reason, path, exchange, clientIpResolver.resolve(exchange));
    }

    private void auditSecurityReject(
            String reason,
            String path,
            HttpExchange exchange,
            ForwardedClientIpResolver.ClientIpResolution clientIp) {
        recordSecurityReject(reason);
        if (audit == null) {
            return;
        }
        try {
            String remote = exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null
                ? "unknown"
                : exchange.getRemoteAddress().getAddress().getHostAddress();
            String resolvedClientIp = clientIp == null || clientIp.clientIp().isBlank() ? remote : clientIp.clientIp();
            String proxyRemote = clientIp == null || clientIp.remoteIp().isBlank() ? remote : clientIp.remoteIp();
            audit.log(new UUID(0L, 0L), "SECURITY", "SECURITY_REJECT", "HTTP", path == null ? "" : path, Map.of(
                "reason", reason == null ? "" : reason,
                "method", exchange.getRequestMethod() == null ? "" : exchange.getRequestMethod(),
                "remote", remote,
                "clientIp", resolvedClientIp,
                "proxyRemote", proxyRemote
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
            case "UNAUTHORIZED", "TOKEN_REQUIRED", "MTLS_OR_TOKEN_REQUIRED" -> securityRejectsUnauthorized.incrementAndGet();
            case "MTLS_REQUIRED" -> securityRejectsMtlsRequired.incrementAndGet();
            case "IP_NOT_ALLOWED", "FORWARDED_HEADER_UNTRUSTED", "FORWARDED_HEADER_INVALID" -> securityRejectsIpNotAllowed.incrementAndGet();
            case "ADMIN_PERMISSION_DENIED" -> securityRejectsAdminPermissionDenied.incrementAndGet();
            default -> {
            }
        }
    }

    private record RouteDefinition(String path, boolean prefix, Set<String> allowedMethods, HttpHandler handler) {
    }
}
