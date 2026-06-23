package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.snapshot.IslandSnapshotRepository;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;

public final class AdminIslandLifecycleRoutes implements RouteGroup {
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final String PREFIX = "/v1/admin/islands/";

    private final IslandLifecycleWorkflow lifecycle;
    private final IslandRepository islandRepository;
    private final IslandRuntimeRepository runtimeRepository;
    private final IslandSnapshotRepository snapshotRepository;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;
    private final IslandQueryRoutes.IslandDeleteRequester deleteRequester;

    public AdminIslandLifecycleRoutes(
            IslandLifecycleWorkflow lifecycle,
            IslandRepository islandRepository,
            IslandRuntimeRepository runtimeRepository,
            IslandSnapshotRepository snapshotRepository,
            AuditLogger audit,
            GlobalEventPublisher events,
            IslandQueryRoutes.IslandDeleteRequester deleteRequester) {
        this.lifecycle = lifecycle;
        this.islandRepository = islandRepository;
        this.runtimeRepository = runtimeRepository;
        this.snapshotRepository = snapshotRepository;
        this.audit = audit;
        this.events = events;
        this.deleteRequester = deleteRequester;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/admin/islands/activate", this::activate);
        registry.routePost("/v1/admin/islands/deactivate", this::deactivate);
        registry.routePost("/v1/admin/islands/migrate", this::migrate);
        registry.routePost("/v1/admin/islands/save", this::save);
        registry.routePost("/v1/admin/islands/snapshot", this::snapshot);
        registry.routePost("/v1/admin/islands/restore", this::restore);
        registry.routePost("/v1/admin/islands/rollback", this::rollback);
        registry.routePost("/v1/admin/islands/quarantine", this::quarantine);
        registry.routePost("/v1/admin/islands/info", this::info);
        registry.routePost("/v1/admin/islands/where", this::where);
        registry.routePost("/v1/admin/islands/delete", this::delete);
        registry.routePost("/v1/admin/islands/repair", this::repair);
    }

    public void register(CoreRouteRegistry registry, CoreRouteRegistry prefixRegistry) {
        register(registry);
        prefixRegistry.routePost(PREFIX, this::prefixRoute);
    }

    private void prefixRoute(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String tail = exchange.getRequestURI().getPath().substring(PREFIX.length());
        if (!method.equalsIgnoreCase("POST")) {
            CoreHttpResponses.write(exchange, 405, ApiResponses.error("METHOD_NOT_ALLOWED", "Use POST for admin island lifecycle operations"));
            return;
        }
        if (tail.endsWith("/activate")) {
            lifecycle(exchange, activate(uuidPath(tail.substring(0, tail.length() - "/activate".length()))));
            return;
        }
        if (tail.endsWith("/deactivate")) {
            lifecycle(exchange, deactivate(uuidPath(tail.substring(0, tail.length() - "/deactivate".length()))));
            return;
        }
        if (tail.endsWith("/migrate")) {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/migrate".length()));
            lifecycle(exchange, migrate(islandId, JsonFields.text(body, "targetNode", "")));
            return;
        }
        if (tail.endsWith("/save")) {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/save".length()));
            lifecycle(exchange, save(islandId, JsonFields.text(body, "reason", "ADMIN_SAVE")));
            return;
        }
        if (tail.endsWith("/snapshot")) {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/snapshot".length()));
            lifecycle(exchange, snapshot(islandId, JsonFields.text(body, "reason", "MANUAL")));
            return;
        }
        if (tail.endsWith("/restore")) {
            String body = CoreHttpResponses.readBody(exchange);
            restoreSnapshot(exchange, uuidPath(tail.substring(0, tail.length() - "/restore".length())), JsonFields.longValue(body, "snapshotNo", 0L), "ISLAND_RESTORE_REQUEST");
            return;
        }
        if (tail.endsWith("/rollback")) {
            String body = CoreHttpResponses.readBody(exchange);
            restoreSnapshot(exchange, uuidPath(tail.substring(0, tail.length() - "/rollback".length())), JsonFields.longValue(body, "snapshotNo", 0L), "ISLAND_ROLLBACK_REQUEST");
            return;
        }
        if (tail.endsWith("/quarantine")) {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = uuidPath(tail.substring(0, tail.length() - "/quarantine".length()));
            IslandLifecycleWorkflow.Result result = lifecycle.quarantine(islandId, JsonFields.text(body, "reason", "admin"));
            audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_QUARANTINE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "reason", JsonFields.text(body, "reason", "admin")));
            lifecycle(exchange, result);
            return;
        }
        if (tail.endsWith("/delete")) {
            delete(exchange, uuidPath(tail.substring(0, tail.length() - "/delete".length())));
            return;
        }
        if (tail.endsWith("/repair")) {
            String body = CoreHttpResponses.readBody(exchange);
            repair(exchange, uuidPath(tail.substring(0, tail.length() - "/repair".length())), JsonFields.text(body, "reason", "admin"));
            return;
        }
        CoreHttpResponses.write(exchange, 404, ApiResponses.error("ROUTE_NOT_FOUND", "Route was not found"));
    }

    private void activate(HttpExchange exchange) throws IOException {
        lifecycle(exchange, activate(JsonFields.uuid(CoreHttpResponses.readBody(exchange), "islandId", SYSTEM_ACTOR)));
    }

    private IslandLifecycleWorkflow.Result activate(UUID islandId) {
        IslandLifecycleWorkflow.Result result = lifecycle.activate(islandId);
        audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_ACTIVATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code()));
        return result;
    }

    private void deactivate(HttpExchange exchange) throws IOException {
        lifecycle(exchange, deactivate(JsonFields.uuid(CoreHttpResponses.readBody(exchange), "islandId", SYSTEM_ACTOR)));
    }

    private IslandLifecycleWorkflow.Result deactivate(UUID islandId) {
        IslandLifecycleWorkflow.Result result = lifecycle.deactivate(islandId);
        audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_DEACTIVATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code()));
        return result;
    }

    private void migrate(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        lifecycle(exchange, migrate(JsonFields.uuid(body, "islandId", SYSTEM_ACTOR), JsonFields.text(body, "targetNode", "")));
    }

    private IslandLifecycleWorkflow.Result migrate(UUID islandId, String targetNode) {
        IslandLifecycleWorkflow.Result result = lifecycle.migrate(islandId, targetNode);
        audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_MIGRATE", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "targetNode", targetNode));
        return result;
    }

    private void save(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        lifecycle(exchange, save(JsonFields.uuid(body, "islandId", SYSTEM_ACTOR), JsonFields.text(body, "reason", "ADMIN_SAVE")));
    }

    private IslandLifecycleWorkflow.Result save(UUID islandId, String reason) {
        String safeReason = adminSaveReason(reason);
        IslandLifecycleWorkflow.Result result = lifecycle.snapshot(islandId, safeReason);
        audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_SAVE_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "reason", safeReason));
        return result;
    }

    private void snapshot(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        lifecycle(exchange, snapshot(JsonFields.uuid(body, "islandId", SYSTEM_ACTOR), JsonFields.text(body, "reason", "MANUAL")));
    }

    private IslandLifecycleWorkflow.Result snapshot(UUID islandId, String reason) {
        String safeReason = adminSnapshotReason(reason);
        IslandLifecycleWorkflow.Result result = lifecycle.snapshot(islandId, safeReason);
        audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_SNAPSHOT_REQUEST", "ISLAND", islandId.toString(), Map.of("accepted", Boolean.toString(result.accepted()), "code", result.code(), "reason", safeReason));
        return result;
    }

    private void restore(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        restoreSnapshot(exchange, JsonFields.uuid(body, "islandId", SYSTEM_ACTOR), JsonFields.longValue(body, "snapshotNo", 0L), "ISLAND_RESTORE_REQUEST");
    }

    private void rollback(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        restoreSnapshot(exchange, JsonFields.uuid(body, "islandId", SYSTEM_ACTOR), JsonFields.longValue(body, "snapshotNo", 0L), "ISLAND_ROLLBACK_REQUEST");
    }

    private void quarantine(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        lifecycle(exchange, lifecycle.quarantine(JsonFields.uuid(body, "islandId", SYSTEM_ACTOR), JsonFields.text(body, "reason", "admin")));
    }

    private void info(HttpExchange exchange) throws IOException {
        UUID lookupUuid = JsonFields.uuid(CoreHttpResponses.readBody(exchange), "lookupUuid", SYSTEM_ACTOR);
        Optional<IslandSnapshot> island = islandRepository.findById(lookupUuid).or(() -> islandRepository.findByOwner(lookupUuid));
        CoreHttpResponses.write(exchange, island.isPresent() ? 200 : 404, island.map(IslandCatalogRoutes::islandJson).orElseGet(() -> ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found")));
    }

    private void where(HttpExchange exchange) throws IOException {
        UUID islandId = JsonFields.uuid(CoreHttpResponses.readBody(exchange), "islandId", SYSTEM_ACTOR);
        var runtime = runtimeRepository.find(islandId);
        CoreHttpResponses.write(exchange, runtime.isPresent() ? 200 : 404, runtime.map(IslandQueryRoutes::runtimeJson).orElseGet(() -> ApiResponses.error("ISLAND_RUNTIME_NOT_FOUND", "Island runtime was not found")));
    }

    private void delete(HttpExchange exchange) throws IOException {
        UUID islandId = JsonFields.uuid(CoreHttpResponses.readBody(exchange), "islandId", SYSTEM_ACTOR);
        delete(exchange, islandId);
    }

    private void delete(HttpExchange exchange, UUID islandId) throws IOException {
        Optional<IslandSnapshot> island = islandRepository.findById(islandId);
        boolean deleted = island.isPresent() && deleteRequester.request(islandId, island.get().ownerUuid(), island.get().ownerUuid(), "admin-delete");
        audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_DELETE", "ISLAND", islandId.toString(), Map.of("deleted", Boolean.toString(deleted)));
        CoreHttpResponses.write(exchange, deleted ? 202 : 404, deleted ? ApiResponses.ok(true) : ApiResponses.error("ISLAND_NOT_DELETED", "Island was not found or could not be deleted"));
    }

    private void repair(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        repair(exchange, JsonFields.uuid(body, "islandId", SYSTEM_ACTOR), JsonFields.text(body, "reason", "admin"));
    }

    private void repair(HttpExchange exchange, UUID islandId, String reason) throws IOException {
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        var runtime = runtimeRepository.setState(islandId, IslandState.INACTIVE_READY);
        islandRepository.setState(islandId, IslandState.INACTIVE_READY);
        audit.log(SYSTEM_ACTOR, "ADMIN", "ISLAND_REPAIR", "ISLAND", islandId.toString(), Map.of("reason", reason));
        events.publish(CloudIslandEventType.ISLAND_REPAIRED.name(), Map.of("islandId", islandId.toString(), "reason", reason, "state", runtime.state().name()));
        events.publish(CloudIslandEventType.ISLAND_RUNTIME_CHANGED.name(), Map.of("islandId", islandId.toString(), "state", runtime.state().name(), "reason", "REPAIRED"));
        CoreHttpResponses.write(exchange, 202, IslandQueryRoutes.runtimeJson(runtime));
    }

    private void restoreSnapshot(HttpExchange exchange, UUID islandId, long snapshotNo, String auditAction) throws IOException {
        Optional<IslandSnapshotRecord> snapshot = snapshotRepository.find(islandId, snapshotNo);
        if (snapshotNo <= 0L || snapshot.isEmpty()) {
            audit.log(SYSTEM_ACTOR, "ADMIN", auditAction, "ISLAND", islandId.toString(), Map.of("accepted", "false", "code", "SNAPSHOT_NOT_FOUND", "snapshotNo", Long.toString(snapshotNo)));
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("SNAPSHOT_NOT_FOUND", "Snapshot was not found"));
            return;
        }
        IslandLifecycleWorkflow.Result result = lifecycle.restore(islandId, snapshotNo, snapshot.get().storagePath());
        audit.log(SYSTEM_ACTOR, "ADMIN", auditAction, "ISLAND", islandId.toString(), Map.of(
            "accepted", Boolean.toString(result.accepted()),
            "code", result.code(),
            "snapshotNo", Long.toString(snapshotNo),
            "storagePath", snapshot.get().storagePath() == null ? "" : snapshot.get().storagePath(),
            "restoreManifestRequired", IslandLifecycleWorkflow.RESTORE_MANIFEST_REQUIRED,
            "restoreChecksumPolicy", IslandLifecycleWorkflow.RESTORE_CHECKSUM_POLICY,
            "restorePortableRequired", IslandLifecycleWorkflow.RESTORE_PORTABLE_REQUIRED,
            "restoreSupportedFormats", IslandLifecycleWorkflow.RESTORE_SUPPORTED_FORMATS
        ));
        restoreLifecycle(exchange, result, snapshotNo, snapshot.get().storagePath());
    }

    private static void lifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result) throws IOException {
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409, SimpleJson.stringify(Map.of("accepted", result.accepted(), "code", result.code())));
    }

    private static void restoreLifecycle(HttpExchange exchange, IslandLifecycleWorkflow.Result result, long snapshotNo, String storagePath) throws IOException {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("accepted", result.accepted());
        response.put("code", result.code());
        response.put("snapshotNo", snapshotNo);
        response.put("storagePath", storagePath == null ? "" : storagePath);
        response.put("restoreManifestRequired", IslandLifecycleWorkflow.RESTORE_MANIFEST_REQUIRED);
        response.put("restoreChecksumPolicy", IslandLifecycleWorkflow.RESTORE_CHECKSUM_POLICY);
        response.put("restorePortableRequired", IslandLifecycleWorkflow.RESTORE_PORTABLE_REQUIRED);
        response.put("restoreSupportedFormats", IslandLifecycleWorkflow.RESTORE_SUPPORTED_FORMATS);
        CoreHttpResponses.write(exchange, result.accepted() ? 202 : 409,
            SimpleJson.stringify(response));
    }

    private static String adminSaveReason(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "save" : reason;
        return safeReason.toUpperCase(java.util.Locale.ROOT).contains("ADMIN_SAVE") ? safeReason : "ADMIN_SAVE:" + safeReason;
    }

    private static String adminSnapshotReason(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "snapshot" : reason;
        return safeReason.toUpperCase(java.util.Locale.ROOT).contains("MANUAL") ? safeReason : "MANUAL:" + safeReason;
    }

    private static UUID uuidPath(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return SYSTEM_ACTOR;
        }
    }

}
