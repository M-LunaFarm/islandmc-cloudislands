package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class AuditRoutes implements RouteGroup {
    private final AuditLogger audit;

    public AuditRoutes(AuditLogger audit) {
        this.audit = audit;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/audit", this::list);
        registry.route("/v1/admin/audit", this::list);
        registry.route("/v1/admin/audit/list", this::list);
    }

    private void list(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 100), 500));
        CoreHttpResponses.write(exchange, 200, audit.toJson(limit));
    }
}
