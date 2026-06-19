package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreservice.MigrationAdminService;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;

public final class SuperiorSkyblock2MigrationRoutes implements RouteGroup {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);

    private final boolean enabled;
    private final MigrationAdminService migrationAdmin;
    private final AuditLogger audit;

    public SuperiorSkyblock2MigrationRoutes(boolean enabled, MigrationAdminService migrationAdmin, AuditLogger audit) {
        this.enabled = enabled;
        this.migrationAdmin = migrationAdmin;
        this.audit = audit;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/admin/migrations/superiorskyblock2/scan", this::scan);
        registry.route("/v1/admin/migrations/superiorskyblock2/status", this::status);
        registry.route("/v1/admin/migrations/superiorskyblock2/dryrun", this::dryRun);
        registry.route("/v1/admin/migrations/superiorskyblock2/extract", this::extract);
        registry.route("/v1/admin/migrations/superiorskyblock2/import", this::importPlan);
        registry.route("/v1/admin/migrations/superiorskyblock2/verify", this::verify);
        registry.route("/v1/admin/migrations/superiorskyblock2/rollback", this::rollback);
    }

    private void scan(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!enabled(exchange)) {
            return;
        }
        String body = CoreHttpResponses.readBody(exchange);
        audit("MIGRATION_SCAN", Map.of());
        CoreHttpResponses.write(exchange, 202, migrationAdmin.scan(JsonFields.text(body, "path", "plugins/SuperiorSkyblock2")));
    }

    private void status(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!enabled(exchange)) {
            return;
        }
        audit("MIGRATION_STATUS", Map.of());
        CoreHttpResponses.write(exchange, 200, migrationAdmin.status());
    }

    private void dryRun(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!enabled(exchange)) {
            return;
        }
        String body = CoreHttpResponses.readBody(exchange);
        String path = JsonFields.text(body, "path", "");
        if (!path.isBlank()) {
            migrationAdmin.scan(path);
        }
        audit("MIGRATION_DRYRUN", path.isBlank() ? Map.of() : Map.of("path", path));
        CoreHttpResponses.write(exchange, 202, migrationAdmin.dryRun());
    }

    private void extract(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!enabled(exchange)) {
            return;
        }
        String body = CoreHttpResponses.readBody(exchange);
        audit("MIGRATION_EXTRACT", Map.of());
        CoreHttpResponses.write(exchange, 202, migrationAdmin.extractWorldBundles(JsonFields.text(body, "path", "")));
    }

    private void importPlan(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!enabled(exchange)) {
            return;
        }
        String body = CoreHttpResponses.readBody(exchange);
        audit("MIGRATION_IMPORT", Map.of());
        CoreHttpResponses.write(exchange, 202, migrationAdmin.importLastPlan(JsonFields.text(body, "approval", "")));
    }

    private void verify(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!enabled(exchange)) {
            return;
        }
        String body = CoreHttpResponses.readBody(exchange);
        String path = JsonFields.text(body, "path", "");
        audit("MIGRATION_VERIFY", path.isBlank() ? Map.of() : Map.of("path", path));
        CoreHttpResponses.write(exchange, 202, migrationAdmin.verify(path));
    }

    private void rollback(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (!enabled(exchange)) {
            return;
        }
        audit("MIGRATION_ROLLBACK", Map.of());
        CoreHttpResponses.write(exchange, 202, migrationAdmin.rollbackLastImport());
    }

    private boolean enabled(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        if (enabled) {
            return true;
        }
        CoreHttpResponses.write(exchange, 403, disabledJson());
        return false;
    }

    private void audit(String action, Map<String, String> details) {
        audit.log(SYSTEM_ACTOR, "ADMIN", action, "MIGRATION", "superiorskyblock2", details);
    }

    static String disabledJson() {
        return "{\"code\":\"MIGRATION_DISABLED\",\"state\":\"DISABLED\",\"sourcePlugin\":\"SuperiorSkyblock2\",\"migrationInputOnly\":true,\"runtimeDependency\":false,\"targetRuntime\":\"CloudIslands\",\"message\":\"SuperiorSkyblock2 migration is disabled by config\"}";
    }
}
